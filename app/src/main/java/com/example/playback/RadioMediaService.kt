package com.example.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.example.MainActivity
import com.example.R
import com.example.RadioApplication
import com.example.data.CuratedStations
import com.example.data.model.Station
import com.example.data.model.distinctStations
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

import android.content.res.Configuration
import android.content.res.Resources
import android.view.KeyEvent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.data.preferences.AppLanguage
import java.util.Locale

@UnstableApi
class RadioMediaService : MediaLibraryService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaLibrarySession: MediaLibrarySession? = null

    @Volatile
    private var lastBitmapUrl: String? = null
    @Volatile
    private var cachedLargeBitmap: Bitmap? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "zonq_playback_channel_v4"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.playback.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.playback.ACTION_PAUSE"
        const val ACTION_PLAY_PAUSE = "com.example.playback.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.playback.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.playback.ACTION_NEXT"
        const val ACTION_STOP = "com.example.playback.ACTION_STOP"

        const val ROOT_ID = "root"
        const val CATEGORY_FAVORITES = "favorites"
        const val CATEGORY_HUNGARIAN = "hungarian"
        const val CATEGORY_SLOVAK = "slovak"
        const val CATEGORY_CZECH = "czech"
        const val CATEGORY_INTERNATIONAL = "international"
        const val CATEGORY_ALL = "all_stations"
    }

    private fun getLocalizedContext(): Context {
        val app = application as RadioApplication
        val lang = app.preferences.currentLanguage
        val targetLocale = when (lang) {
            AppLanguage.HUNGARIAN -> Locale("hu")
            AppLanguage.SLOVAK -> Locale("sk")
            AppLanguage.ENGLISH -> Locale("en")
            AppLanguage.SYSTEM -> null
        }
        val effective = com.example.util.LocaleUtils.getEffectiveLocale(targetLocale)
        val config = Configuration(resources.configuration)
        config.setLocale(effective)
        config.setLayoutDirection(effective)
        return createConfigurationContext(config)
    }

    override fun onCreate() {
        super.onCreate()

        // Create explicit Notification Channel with public lock screen visibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            try {
                notificationManager?.deleteNotificationChannel("zonq_playback_channel")
                notificationManager?.deleteNotificationChannel("zonq_playback_channel_v2")
                notificationManager?.deleteNotificationChannel("zonq_playback_channel_v3")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        val app = application as RadioApplication
        val playbackManager = app.playbackManager

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = RadioForwardingPlayer(playbackManager.player, playbackManager)

        val prevButton = androidx.media3.session.CommandButton.Builder(androidx.media3.session.CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName(getString(R.string.prev_station))
            .build()

        val nextButton = androidx.media3.session.CommandButton.Builder(androidx.media3.session.CommandButton.ICON_NEXT)
            .setPlayerCommand(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName(getString(R.string.next_station))
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, LibrarySessionCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .setMediaButtonPreferences(ImmutableList.of(prevButton, nextButton))
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.notification_channel_name)
            .build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        setMediaNotificationProvider(notificationProvider)

        // Continuously observe playback state to keep notification and lockscreen in sync
        serviceScope.launch {
            playbackManager.playbackState.collect {
                updateForegroundNotification()
            }
        }

        playbackManager.onMetadataUpdatedListener = {
            forwardingPlayer.dispatchLiveMetadataUpdate()
            updateForegroundNotification()
        }

        // Listen for language changes and notify Android Auto to refresh
        serviceScope.launch {
            app.preferences.userPreferencesFlow.collect {
                mediaLibrarySession?.notifyChildrenChanged(ROOT_ID, 0, null)
            }
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        updateForegroundNotification(startInForegroundRequired)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? RadioApplication
        val playbackManager = app?.playbackManager

        when (intent?.action) {
            ACTION_PLAY -> playbackManager?.resume()
            ACTION_PAUSE -> playbackManager?.pause()
            ACTION_PLAY_PAUSE -> playbackManager?.togglePlayPause()
            ACTION_PREVIOUS -> playbackManager?.playPreviousFavorite()
            ACTION_NEXT -> playbackManager?.playNextFavorite()
            ACTION_STOP -> {
                playbackManager?.pause()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(NOTIFICATION_ID)
                } catch (_: Exception) {}
                stopSelf()
                return START_NOT_STICKY
            }
        }

        super.onStartCommand(intent, flags, startId)
        updateForegroundNotification()
        return START_STICKY
    }

    private fun updateForegroundNotification(forceForeground: Boolean = false) {
        val app = application as? RadioApplication ?: return
        val playbackManager = app.playbackManager
        val state = playbackManager.playbackState.value
        val station = state.currentStation

        if (station == null || state.status == PlaybackStatus.IDLE) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            return
        }

        val isPlaying = state.status == PlaybackStatus.PLAYING
        val isBuffering = state.status == PlaybackStatus.BUFFERING

        val stationName = station.name
        val parsedArtist = state.trackArtist?.trim()?.ifBlank { null }
        val parsedTitle = state.trackTitle?.trim()?.ifBlank { null }
        val subtitle = station.getDisplaySubtitle(this)

        val title = when {
            !parsedTitle.isNullOrBlank() -> parsedTitle
            else -> stationName
        }

        val text = when {
            !parsedArtist.isNullOrBlank() && !parsedTitle.isNullOrBlank() -> parsedArtist
            !parsedTitle.isNullOrBlank() -> stationName
            else -> subtitle
        }

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        fun createServicePendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(this, RadioMediaService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val prevPendingIntent = createServicePendingIntent(ACTION_PREVIOUS, 1)
        val playPausePendingIntent = createServicePendingIntent(ACTION_PLAY_PAUSE, 2)
        val nextPendingIntent = createServicePendingIntent(ACTION_NEXT, 3)
        val stopPendingIntent = createServicePendingIntent(ACTION_STOP, 4)

        val session = mediaLibrarySession

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(stationName)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying || isBuffering)
            .setShowWhen(false)

        builder.addAction(
            R.drawable.ic_skip_previous,
            getString(R.string.prev_station),
            prevPendingIntent
        )
        if (isPlaying || isBuffering) {
            builder.addAction(
                R.drawable.ic_pause,
                getString(R.string.action_pause),
                playPausePendingIntent
            )
        } else {
            builder.addAction(
                R.drawable.ic_play_arrow,
                getString(R.string.action_play),
                playPausePendingIntent
            )
        }
        builder.addAction(
            R.drawable.ic_skip_next,
            getString(R.string.next_station),
            nextPendingIntent
        )

        if (session != null) {
            val mediaStyle = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopPendingIntent)
            builder.setStyle(mediaStyle)
        }

        val artUrl = state.albumArtUrl?.ifBlank { null } ?: station.favicon.ifBlank { null }
        val currentCached = cachedLargeBitmap
        if (currentCached != null && lastBitmapUrl == artUrl) {
            builder.setLargeIcon(currentCached)
        } else {
            val fallbackBitmap = try {
                BitmapFactory.decodeResource(resources, R.drawable.ic_zonq_symbol)
            } catch (_: Exception) { null }
            if (fallbackBitmap != null) {
                builder.setLargeIcon(fallbackBitmap)
            }
        }

        val notification = builder.build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        try {
            if (isPlaying || isBuffering || forceForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                nm?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w("RadioMediaService", "Failed to update foreground notification: ${e.message}")
            try {
                nm?.notify(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        if (!artUrl.isNullOrBlank() && artUrl != lastBitmapUrl) {
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(this@RadioMediaService)
                    val req = ImageRequest.Builder(this@RadioMediaService)
                        .data(artUrl)
                        .allowHardware(false)
                        .size(256, 256)
                        .build()
                    val res = (loader.execute(req) as? SuccessResult)?.drawable
                    val bmp = (res as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        cachedLargeBitmap = bmp
                        lastBitmapUrl = artUrl
                        builder.setLargeIcon(bmp)
                        nm?.notify(NOTIFICATION_ID, builder.build())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? RadioApplication
        val isPlaying = app?.playbackManager?.playbackState?.value?.let {
            it.status == PlaybackStatus.PLAYING || it.status == PlaybackStatus.BUFFERING
        } ?: false

        if (!isPlaying) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        mediaLibrarySession?.run {
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(androidx.media3.common.Player.COMMAND_SEEK_FORWARD)
                .add(androidx.media3.common.Player.COMMAND_SEEK_BACK)
                .add(androidx.media3.common.Player.COMMAND_PLAY_PAUSE)
                .add(androidx.media3.common.Player.COMMAND_STOP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(connectionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val app = application as RadioApplication
            val playbackManager = app.playbackManager

            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            if (keyEvent != null) {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_NAVIGATE_NEXT,
                        KeyEvent.KEYCODE_PAGE_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            playbackManager.playNextFavorite()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                        KeyEvent.KEYCODE_PAGE_UP,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            playbackManager.playPreviousFavorite()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            playbackManager.resume()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            playbackManager.pause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            playbackManager.togglePlayPause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            playbackManager.stop()
                            return true
                        }
                    }
                } else if (keyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_NAVIGATE_NEXT,
                        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS,
                        KeyEvent.KEYCODE_PAGE_UP,
                        KeyEvent.KEYCODE_PAGE_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK,
                        KeyEvent.KEYCODE_MEDIA_STOP -> return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val appIconUri = Uri.parse("android.resource://$packageName/${R.mipmap.ic_launcher}")
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("zonq")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setArtworkUri(appIconUri)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build()
                )
                .build()

            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val app = application as RadioApplication
            val locContext = getLocalizedContext()

            return when (parentId) {
                ROOT_ID -> {
                    val categories = listOf(
                        createFolderItem(CATEGORY_FAVORITES, locContext.getString(R.string.auto_cat_favorites)),
                        createFolderItem(CATEGORY_HUNGARIAN, locContext.getString(R.string.auto_cat_hungarian)),
                        createFolderItem(CATEGORY_SLOVAK, locContext.getString(R.string.auto_cat_slovak)),
                        createFolderItem(CATEGORY_CZECH, locContext.getString(R.string.auto_cat_czech)),
                        createFolderItem(CATEGORY_INTERNATIONAL, locContext.getString(R.string.auto_cat_international)),
                        createFolderItem(CATEGORY_ALL, locContext.getString(R.string.auto_cat_all))
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(categories), params))
                }

                CATEGORY_FAVORITES -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val favs = (app.repository.favoritesFlow.firstOrNull() ?: emptyList()).distinctStations()
                        val items = favs.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                CATEGORY_HUNGARIAN -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val all = app.repository.getCuratedAndCustomStations()
                        val huStations = all.filter { it.countryCode.equals("HU", ignoreCase = true) || it.country == "Magyarország" }.distinctStations()
                        val items = huStations.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                CATEGORY_SLOVAK -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val all = app.repository.getCuratedAndCustomStations()
                        val skStations = all.filter { it.countryCode.equals("SK", ignoreCase = true) || it.country == "Szlovákia" }.distinctStations()
                        val items = skStations.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                CATEGORY_CZECH -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val all = app.repository.getCuratedAndCustomStations()
                        val czStations = all.filter { it.countryCode.equals("CZ", ignoreCase = true) || it.country == "Csehország" }.distinctStations()
                        val items = czStations.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                CATEGORY_INTERNATIONAL -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val all = app.repository.getCuratedAndCustomStations()
                        val intlStations = all.filter {
                            !it.countryCode.equals("HU", ignoreCase = true) &&
                            !it.countryCode.equals("SK", ignoreCase = true) &&
                            !it.countryCode.equals("CZ", ignoreCase = true)
                        }.distinctStations()
                        val items = intlStations.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                CATEGORY_ALL -> {
                    val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch {
                        val stations = app.repository.getCuratedAndCustomStations().distinctStations().shuffled()
                        val items = stations.map { stationToMediaItem(it, locContext) }
                        future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
                    }
                    future
                }

                else -> Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val app = application as RadioApplication
            val playbackManager = app.playbackManager

            serviceScope.launch {
                val targetItem = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
                if (targetItem != null) {
                    val station = resolveStation(targetItem.mediaId, targetItem.localConfiguration?.uri?.toString(), app)
                    if (station != null) {
                        val allStations = app.repository.getCuratedAndCustomStations().distinctStations()
                        playbackManager.playStation(station, allStations)
                        val fullMediaItem = stationToMediaItem(station)
                        future.set(MediaSession.MediaItemsWithStartPosition(listOf(fullMediaItem), 0, C.TIME_UNSET))
                        return@launch
                    }
                }
                future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
            }
            return future
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val app = application as RadioApplication
            serviceScope.launch {
                val prefs = app.preferences.userPreferencesFlow.firstOrNull()
                val lastId = prefs?.lastStationId
                val lastUrl = prefs?.lastStationUrl
                val stations = app.repository.getCuratedAndCustomStations().distinctStations()

                val lastStation = stations.firstOrNull { it.id == lastId || it.url == lastUrl }
                    ?: (if (!lastId.isNullOrBlank() && !lastUrl.isNullOrBlank()) {
                        Station(
                            id = lastId,
                            name = prefs.lastStationName ?: "zonq",
                            url = lastUrl
                        )
                    } else null)
                    ?: stations.firstOrNull()

                if (lastStation != null) {
                    val item = stationToMediaItem(lastStation)
                    future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET))
                } else {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET))
                }
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            val app = application as RadioApplication

            serviceScope.launch {
                val updated = mediaItems.map { item ->
                    val station = resolveStation(item.mediaId, item.localConfiguration?.uri?.toString(), app)
                    if (station != null) {
                        stationToMediaItem(station)
                    } else {
                        item
                    }
                }.toMutableList()
                future.set(updated)
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            mediaLibrarySession?.notifySearchResultChanged(browser, query, 15, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val app = application as RadioApplication
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()

            serviceScope.launch {
                val all = app.repository.getCuratedAndCustomStations()
                val results = all.filter {
                    it.name.contains(query, ignoreCase = true) || it.tags.contains(query, ignoreCase = true)
                }.distinctStations()
                val items = results.map { stationToMediaItem(it) }
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
            }
            return future
        }
    }

    private suspend fun resolveStation(mediaId: String, uriString: String?, app: RadioApplication): Station? {
        val all = app.repository.getCuratedAndCustomStations()

        // 1. Direct ID match
        val byId = all.firstOrNull { it.id == mediaId }
        if (byId != null) return byId

        // 2. Direct URI match
        if (!uriString.isNullOrBlank()) {
            val byUri = all.firstOrNull { it.url.equals(uriString, ignoreCase = true) }
            if (byUri != null) return byUri
        }

        // 3. Match from favorites
        val favs = app.repository.favoritesFlow.firstOrNull() ?: emptyList()
        val favMatch = favs.firstOrNull { it.id == mediaId || (!uriString.isNullOrBlank() && it.url.equals(uriString, ignoreCase = true)) }
        if (favMatch != null) return favMatch

        return null
    }

    private fun createFolderItem(id: String, title: String): MediaItem {
        val appIconUri = Uri.parse("android.resource://$packageName/${R.mipmap.ic_launcher}")
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setArtworkUri(appIconUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                    .build()
            )
            .build()
    }

    private fun stationToMediaItem(station: Station, context: Context = getLocalizedContext()): MediaItem {
        val app = application as RadioApplication
        val currentState = app.playbackManager.playbackState.value
        val isCurrentPlaying = currentState.currentStation?.id == station.id
        val albumArt = if (isCurrentPlaying) currentState.albumArtUrl else null

        val appIconUri = Uri.parse("android.resource://$packageName/${R.mipmap.ic_launcher}")
        val artworkUri = when {
            !albumArt.isNullOrBlank() -> Uri.parse(albumArt)
            station.favicon.isNotBlank() -> Uri.parse(station.favicon)
            else -> appIconUri
        }

        val subtitle = station.getDisplaySubtitle(context)

        val meta = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(subtitle)
            .setDisplayTitle(station.name)
            .setSubtitle(subtitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .build()

        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.url)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(station.url))
                    .build()
            )
            .setMediaMetadata(meta)
            .build()
    }
}
