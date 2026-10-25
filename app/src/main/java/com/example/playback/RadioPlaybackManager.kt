package com.example.playback

import android.content.Context
import android.content.Intent
import com.example.R
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.example.data.CuratedStations
import com.example.data.model.Station
import com.example.data.preferences.AudioQuality
import com.example.data.preferences.BufferDuration
import com.example.data.preferences.RadioPreferences
import com.example.data.repository.StationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@UnstableApi
class RadioPlaybackManager private constructor(
    internal val context: Context,
    private val repository: StationRepository,
    private val preferences: RadioPreferences,
    private val albumArtRepository: com.example.data.repository.AlbumArtRepository = com.example.data.repository.AlbumArtRepository()
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var albumArtJob: Job? = null

    var onMetadataUpdatedListener: (() -> Unit)? = null

    @Volatile
    private var isServiceStarted = false

    @Volatile
    private var activeStationId: String? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var activeStationList: List<Station> = CuratedStations.STATIONS
    @Volatile
    private var favoriteStationsList: List<Station> = emptyList()

    // Robust Auto-reconnect handling for streaming stability
    private var retryCount = 0
    private val maxRetries = 25
    private var retryJob: Job? = null
    private var successfulPlaybackStartMs: Long = 0
    private var bufferingWatchdogJob: Job? = null

    // Safe PARTIAL_WAKE_LOCK to prevent CPU from sleeping while streaming over cellular data (screen off / in transit)
    private val wakeLock: PowerManager.WakeLock? by lazy {
        try {
            val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zonq:StreamWakeLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Safe WifiLock to keep streaming alive in background without throwing deprecated/security exceptions
    private val wifiLock: WifiManager.WifiLock? by lazy {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL
            }
            wifiManager?.createWifiLock(lockMode, "zonq::StreamWifiLock")?.apply {
                setReferenceCounted(false)
            }
        } catch (_: Exception) {
            null
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS) // Avoid infinite socket hangs during cellular handover / dead zones
            .addInterceptor { chain ->
                try {
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "zonq/1.0 (Linux; Android)")
                        .header("Icy-MetaData", "1")
                        .build()
                    chain.proceed(request)
                } catch (e: Exception) {
                    if (e is java.io.IOException) throw e else throw java.io.IOException(e.message, e)
                }
            }
            .build()
    }

    val equalizerManager: RadioEqualizerManager = RadioEqualizerManager(context)
    val normalizerProcessor: AudioNormalizerProcessor = AudioNormalizerProcessor()

    val player: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("zonq/1.0")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val isTv = com.example.util.TvUtils.isRunningOnTv(context)

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                val bufferSizeProvider = androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider.Builder()
                    .setMinPcmBufferDurationUs(if (isTv) 500_000 else 250_000)
                    .setMaxPcmBufferDurationUs(if (isTv) 2_500_000 else 1_500_000)
                    .setPcmBufferMultiplicationFactor(if (isTv) 4 else 2)
                    .build()

                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(normalizerProcessor))
                    .setAudioTrackBufferSizeProvider(bufferSizeProvider)
                    .setEnableFloatOutput(if (isTv) false else enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(RadioLoadControl(preferences, isTv = isTv))
            .setAudioAttributes(audioAttributes, true) // Audio focus handled automatically
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                addListener(playerListener)
            }
    }

    init {
        registerNetworkCallback()
        scope.launch {
            try {
                preferences.userPreferencesFlow.collect { prefs ->
                    normalizerProcessor.isNormalizationEnabled = prefs.audioNormalizationEnabled
                    normalizerProcessor.activeProfile = prefs.normalizationProfile
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                val list = repository.getCuratedAndCustomStations()
                if (list.isNotEmpty()) {
                    activeStationList = list
                }
            } catch (_: Exception) {}
        }
        scope.launch {
            try {
                repository.favoritesFlow.collect { favs ->
                    favoriteStationsList = favs
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Automatically recovers and resumes stream when network connection returns or changes
     * (e.g. Wi-Fi to cellular handover, cell tower handoff, coming out of a tunnel).
     */
    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val current = _playbackState.value
                        if (current.currentStation != null &&
                            (player.playWhenReady || current.status == PlaybackStatus.BUFFERING || current.status == PlaybackStatus.ERROR)
                        ) {
                            scope.launch {
                                delay(600) // Brief delay for routing and DNS table to settle
                                if (activeStationId == current.currentStation.id && player.playWhenReady) {
                                    retryCount = 0
                                    retryJob?.cancel()
                                    cancelBufferingWatchdog()
                                    reconnectStation(current.currentStation)
                                }
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        val current = _playbackState.value
                        if (current.currentStation != null && player.playWhenReady) {
                            _playbackState.update {
                                it.copy(status = PlaybackStatus.BUFFERING, errorMessage = "Hálózati váltás…")
                            }
                        }
                    }
                })
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val current = _playbackState.value
                        if (current.currentStation != null &&
                            (current.status == PlaybackStatus.ERROR || current.status == PlaybackStatus.BUFFERING)
                        ) {
                            scope.launch {
                                delay(600)
                                retryCount = 0
                                retryJob?.cancel()
                                cancelBufferingWatchdog()
                                reconnectStation(current.currentStation)
                            }
                        }
                    }
                })
            }
        } catch (_: Exception) {
            // Safe ignore if network callback cannot be registered
        }
    }

    private fun startBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = scope.launch {
            delay(10_000L) // If buffering stalls for 10 seconds without audio
            if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                val current = _playbackState.value.currentStation
                if (current != null && current.id == activeStationId) {
                    reconnectStation(current)
                }
            }
        }
    }

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    updateLocks(true)
                    startBufferingWatchdog()
                    _playbackState.update { it.copy(status = PlaybackStatus.BUFFERING, errorMessage = null) }
                }
                Player.STATE_READY -> {
                    cancelBufferingWatchdog()
                    // Reset retry counter on successful playback
                    successfulPlaybackStartMs = System.currentTimeMillis()
                    retryCount = 0
                    retryJob?.cancel()

                    // Attach audio session to Equalizer
                    val audioSessionId = player.audioSessionId
                    if (audioSessionId > 0) {
                        equalizerManager.attachAudioSession(audioSessionId)
                    }

                    val status = if (player.playWhenReady) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED
                    _playbackState.update { it.copy(status = status, errorMessage = null) }
                    updateLocks(status == PlaybackStatus.PLAYING)
                }
                Player.STATE_ENDED -> {
                    cancelBufferingWatchdog()
                    updateLocks(false)
                    _playbackState.update { it.copy(status = PlaybackStatus.IDLE) }
                }
                Player.STATE_IDLE -> {
                    cancelBufferingWatchdog()
                    if (_playbackState.value.status != PlaybackStatus.ERROR &&
                        _playbackState.value.status != PlaybackStatus.PAUSED &&
                        retryCount == 0 && retryJob?.isActive != true) {
                        updateLocks(false)
                        _playbackState.update { it.copy(status = PlaybackStatus.IDLE) }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val status = if (isPlaying) {
                cancelBufferingWatchdog()
                PlaybackStatus.PLAYING
            } else if (player.playbackState == Player.STATE_BUFFERING) {
                PlaybackStatus.BUFFERING
            } else if (player.playbackState == Player.STATE_IDLE && _playbackState.value.errorMessage != null) {
                PlaybackStatus.ERROR
            } else {
                PlaybackStatus.PAUSED
            }
            updateLocks(isPlaying || status == PlaybackStatus.BUFFERING || retryJob?.isActive == true)
            _playbackState.update { it.copy(status = status) }
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is IcyInfo) {
                    val streamTitle = entry.title
                    if (!streamTitle.isNullOrBlank()) {
                        processLiveMetadata(streamTitle)
                    }
                }
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Intentionally no-op to prevent feedback loop where our own split title is re-processed
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val currentStation = _playbackState.value.currentStation

        // Ignore errors if player is idle/paused intentionally or station has changed
        if (currentStation == null || currentStation.id != activeStationId) {
            return
        }
        if (player.playbackState == Player.STATE_IDLE && !player.playWhenReady) {
            return
        }

        // If played successfully for more than 20 seconds, reset retry counter
        if (System.currentTimeMillis() - successfulPlaybackStartMs > 20_000L) {
            retryCount = 0
        }

        if (retryCount < maxRetries) {
            retryCount++
            val backoffMs = (retryCount * 1000L).coerceAtMost(5000L)
            updateLocks(true)

            _playbackState.update {
                it.copy(
                    status = PlaybackStatus.BUFFERING,
                    errorMessage = "Újracsatlakozás az adáshoz… ($retryCount/$maxRetries)"
                )
            }

            retryJob?.cancel()
            retryJob = scope.launch {
                delay(backoffMs)
                if (_playbackState.value.currentStation?.id == activeStationId && player.playWhenReady) {
                    reconnectStation(currentStation)
                }
            }
        } else {
            updateLocks(false)
            _playbackState.update {
                it.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = "A rádió stream megszakadt. Koppints a lejátszás gombra az újbóli próbálkozáshoz."
                )
            }
        }
    }

    private fun reconnectStation(station: Station) {
        if (activeStationId != station.id) return
        val mediaMetadata = createStationMetadata(station, _playbackState.value.trackTitle, _playbackState.value.trackArtist)
        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.url)
            .setMediaMetadata(mediaMetadata)
            .build()

        try {
            updateLocks(true)
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.setPlaylistMetadata(mediaMetadata)
            player.prepare()
            player.playWhenReady = true
        } catch (_: Exception) {
            // Fail gracefully without crashing
        }
        onMetadataUpdatedListener?.invoke()
    }

    private fun updateLocks(acquire: Boolean) {
        try {
            if (acquire) {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(24 * 60 * 60 * 1000L)
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                if (wifiLock?.isHeld == true) {
                    wifiLock?.release()
                }
            }
        } catch (_: Exception) {
            // Safe ignore
        }
    }

    private fun processLiveMetadata(rawText: String) {
        val station = _playbackState.value.currentStation ?: return
        val stationName = station.name.trim()
        var trimmed = rawText.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (trimmed.isBlank() || trimmed.equals(stationName, ignoreCase = true)) {
            return
        }

        val delimiters = listOf(" - ", " – ", " — ", " : ", " | ")

        // Strip prefix if the station name or station name with separator is at the front
        for (delim in delimiters) {
            val prefix = stationName + delim
            if (trimmed.startsWith(prefix, ignoreCase = true)) {
                trimmed = trimmed.substring(prefix.length).trim().removeSurrounding("\"").removeSurrounding("'").trim()
                break
            }
        }
        val colonPrefix = "$stationName:"
        if (trimmed.startsWith(colonPrefix, ignoreCase = true)) {
            trimmed = trimmed.substring(colonPrefix.length).trim().removeSurrounding("\"").removeSurrounding("'").trim()
        }

        var parsedArtist: String? = null
        var parsedTitle: String = trimmed

        // Try standard delimiters: " - ", " – ", " — ", " : ", " | "
        var foundDelimiter = false
        for (delim in delimiters) {
            if (trimmed.contains(delim)) {
                val parts = trimmed.split(delim, limit = 2)
                val part1 = parts[0].trim().removeSurrounding("\"").removeSurrounding("'")
                val part2 = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                if (part1.isNotBlank() && part2.isNotBlank()) {
                    parsedArtist = part1
                    parsedTitle = part2
                    foundDelimiter = true
                    break
                }
            }
        }

        if (!foundDelimiter && trimmed.contains("-") && !trimmed.startsWith("-") && !trimmed.endsWith("-")) {
            val parts = trimmed.split("-", limit = 2)
            val part1 = parts[0].trim().removeSurrounding("\"").removeSurrounding("'")
            val part2 = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
            if (part1.isNotBlank() && part2.isNotBlank()) {
                parsedArtist = part1
                parsedTitle = part2
                foundDelimiter = true
            }
        }

        // If artist resembles the radio station or contains "rádió"/"radio", check if the title contains the actual "Artist - Title"
        if (parsedArtist != null) {
            val artistLower = parsedArtist.lowercase()
            val stationLower = stationName.lowercase()
            val isStationLike = artistLower == stationLower ||
                    artistLower.contains("rádió") ||
                    artistLower.contains("radio")

            if (isStationLike) {
                var subArtist: String? = null
                var subTitle: String = parsedTitle
                for (delim in delimiters) {
                    if (parsedTitle.contains(delim)) {
                        val parts = parsedTitle.split(delim, limit = 2)
                        val p1 = parts[0].trim().removeSurrounding("\"").removeSurrounding("'")
                        val p2 = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                        if (p1.isNotBlank() && p2.isNotBlank()) {
                            subArtist = p1
                            subTitle = p2
                            break
                        }
                    }
                }
                if (subArtist != null) {
                    parsedArtist = subArtist
                    parsedTitle = subTitle
                }
            }
        }

        // Clean up title & artist
        parsedTitle = parsedTitle.removeSurrounding("\"").removeSurrounding("'").trim()
        parsedArtist = parsedArtist?.removeSurrounding("\"")?.removeSurrounding("'")?.trim()

        if (parsedTitle.isBlank()) {
            parsedTitle = stationName
        }

        // Double check: Never display station name as song artist
        if (parsedArtist != null && (parsedArtist.equals(stationName, ignoreCase = true) ||
                    parsedArtist.equals("$stationName élő adás", ignoreCase = true) ||
                    parsedArtist.equals("$stationName élő", ignoreCase = true))) {
            parsedArtist = null
        }

        // Prevent redundant updates
        if (_playbackState.value.trackTitle == parsedTitle && _playbackState.value.trackArtist == parsedArtist) {
            return
        }

        albumArtJob?.cancel()
        _playbackState.update {
            it.copy(
                trackTitle = parsedTitle,
                trackArtist = parsedArtist,
                albumArtUrl = null,
                rawMetadata = rawText
            )
        }

        val sessionMeta = createStationMetadata(station, parsedTitle, parsedArtist, null)
        try {
            player.setPlaylistMetadata(sessionMeta)
            if (player.mediaItemCount > 0) {
                val currentItem = player.currentMediaItem
                if (currentItem != null) {
                    val updated = currentItem.buildUpon()
                        .setMediaMetadata(sessionMeta)
                        .build()
                    player.replaceMediaItem(player.currentMediaItemIndex, updated)
                }
            }
        } catch (_: Exception) {
            // Safe ignore
        }
        onMetadataUpdatedListener?.invoke()

        // Asynchronously search internet for album cover art for the new track
        if (!parsedTitle.isNullOrBlank() && !parsedTitle.equals(stationName, ignoreCase = true)) {
            albumArtJob = scope.launch {
                val artUrl = albumArtRepository.getAlbumArtUrl(parsedArtist, parsedTitle)
                if (!artUrl.isNullOrBlank()) {
                    if (_playbackState.value.trackTitle == parsedTitle &&
                        _playbackState.value.trackArtist == parsedArtist &&
                        _playbackState.value.currentStation?.id == station.id
                    ) {
                        _playbackState.update { it.copy(albumArtUrl = artUrl) }
                        val updatedMeta = createStationMetadata(station, parsedTitle, parsedArtist, artUrl)
                        try {
                            player.setPlaylistMetadata(updatedMeta)
                            if (player.mediaItemCount > 0) {
                                val currentItem = player.currentMediaItem
                                if (currentItem != null) {
                                    val updated = currentItem.buildUpon()
                                        .setMediaMetadata(updatedMeta)
                                        .build()
                                    player.replaceMediaItem(player.currentMediaItemIndex, updated)
                                }
                            }
                        } catch (_: Exception) {}
                        onMetadataUpdatedListener?.invoke()
                    }
                }
            }
        }
    }

    fun createStationMetadata(
        station: Station,
        title: String? = null,
        artist: String? = null,
        albumArtUrl: String? = null
    ): MediaMetadata {
        val stationName = station.name
        val liveDefault = context.getString(com.example.R.string.live_stream_default)
        val parsedArtist = artist?.trim()?.ifBlank { null }
        val parsedTitle = title?.trim()?.ifBlank { null }

        // Android Auto layout requirement:
        // Ahol a szám címe van -> "előadó - szám címe" formában
        val formattedTitle = when {
            !parsedArtist.isNullOrBlank() && !parsedTitle.isNullOrBlank() &&
                    !parsedArtist.equals(stationName, ignoreCase = true) -> {
                "$parsedArtist - $parsedTitle"
            }
            !parsedTitle.isNullOrBlank() && !parsedTitle.equals(stationName, ignoreCase = true) -> {
                parsedTitle
            }
            else -> {
                station.getDisplaySubtitle(context).ifBlank { liveDefault }
            }
        }

        val fallbackUri = Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        val effectiveArt = albumArtUrl?.ifBlank { null } ?: _playbackState.value.albumArtUrl?.ifBlank { null }
        val artworkUri = when {
            !effectiveArt.isNullOrBlank() -> Uri.parse(effectiveArt)
            station.favicon.isNotBlank() -> Uri.parse(station.favicon)
            else -> fallbackUri
        }

        // Ahol az előadó van -> a rádió neve
        return MediaMetadata.Builder()
            .setTitle(formattedTitle)
            .setDisplayTitle(formattedTitle)
            .setArtist(stationName)
            .setSubtitle(stationName)
            .setAlbumArtist(stationName)
            .setAlbumTitle(stationName)
            .setDescription(stationName)
            .setStation(stationName)
            .setArtworkUri(artworkUri)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setIsPlayable(true)
            .build()
    }

    fun isHighSpeedConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(network) ?: return true
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            isWifi || isUnmetered
        } catch (_: Exception) {
            true
        }
    }

    fun restoreLastStation(station: Station) {
        if (_playbackState.value.currentStation == null) {
            activeStationId = station.id
            normalizerProcessor.onStationChanged(station.id)
            albumArtJob?.cancel()

            _playbackState.update {
                it.copy(
                    currentStation = station,
                    status = PlaybackStatus.IDLE,
                    trackTitle = null,
                    trackArtist = null,
                    albumArtUrl = null
                )
            }

            onMetadataUpdatedListener?.invoke()

            scope.launch {
                try {
                    val isFav = repository.isFavorite(station.id).firstOrNull() ?: false
                    if (activeStationId == station.id) {
                        _playbackState.update { it.copy(isFavorite = isFav) }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun setStationList(stations: List<Station>) {
        activeStationList = stations
    }

    fun ensureMediaServiceStarted() {
        try {
            val serviceIntent = Intent(context, RadioMediaService::class.java)
            context.startService(serviceIntent)
            isServiceStarted = true
        } catch (_: Exception) {
            // Safe ignore
        }
    }

    fun startForegroundPlaybackService() {
        try {
            val serviceIntent = Intent(context, RadioMediaService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            isServiceStarted = true
        } catch (_: Exception) {
            ensureMediaServiceStarted()
        }
    }

    fun playStation(station: Station, listContext: List<Station>? = null) {
        retryCount = 0
        retryJob?.cancel()
        cancelBufferingWatchdog()
        updateLocks(true)
        albumArtJob?.cancel()
        activeStationId = station.id
        normalizerProcessor.onStationChanged(station.id)

        if (listContext != null) {
            activeStationList = listContext
        }

        // Immediate UI update on Main thread for instantaneous response
        _playbackState.update {
            it.copy(
                currentStation = station,
                status = PlaybackStatus.BUFFERING,
                trackTitle = null,
                trackArtist = null,
                albumArtUrl = null,
                rawMetadata = null,
                errorMessage = null
            )
        }

        val mediaMetadata = createStationMetadata(station)
        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.url)
            .setMediaMetadata(mediaMetadata)
            .build()

        try {
            player.setMediaItem(mediaItem, true)
            player.setPlaylistMetadata(mediaMetadata)
            player.prepare()
            player.playWhenReady = true
        } catch (_: Exception) {
            // Safe fallback
        }

        startForegroundPlaybackService()

        onMetadataUpdatedListener?.invoke()

        scope.launch {
            try {
                preferences.saveLastStation(station.id, station.name, station.url)
                val isFav = repository.isFavorite(station.id).firstOrNull() ?: false
                if (activeStationId == station.id) {
                    _playbackState.update { it.copy(isFavorite = isFav) }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Pauses playback while retaining current media item so that the media notification
     * and lock screen player remain visible and controllable.
     */
    fun pause() {
        retryJob?.cancel()
        retryCount = 0
        cancelBufferingWatchdog()
        updateLocks(false)
        player.pause()
        _playbackState.update {
            it.copy(
                status = PlaybackStatus.PAUSED,
                errorMessage = null
            )
        }
        onMetadataUpdatedListener?.invoke()
        startForegroundPlaybackService()
    }

    /**
     * Resumes playback by connecting fresh to the live broadcast stream so playback
     * starts directly at the real-time live point rather than from stale memory buffer.
     */
    fun resume() {
        val station = _playbackState.value.currentStation ?: return
        retryCount = 0
        retryJob?.cancel()
        cancelBufferingWatchdog()
        startForegroundPlaybackService()
        playStation(station)
    }

    fun togglePlayPause() {
        if (_playbackState.value.status == PlaybackStatus.PLAYING ||
            _playbackState.value.status == PlaybackStatus.BUFFERING ||
            player.isPlaying
        ) {
            pause()
        } else {
            resume()
        }
    }

    /**
     * Switches to the next favorite station. Steering wheel controls and Android Auto
     * next track action switch exclusively among user favorites.
     */
    fun playNextFavorite() {
        val favs = favoriteStationsList.ifEmpty { activeStationList.ifEmpty { CuratedStations.STATIONS } }
        if (favs.isEmpty()) return
        val current = _playbackState.value.currentStation
        val currentIndex = if (current != null) {
            favs.indexOfFirst { it.id == current.id || it.url.equals(current.url, ignoreCase = true) }
        } else -1
        val nextIndex = if (currentIndex != -1) {
            (currentIndex + 1) % favs.size
        } else {
            0
        }
        playStation(favs[nextIndex], favs)
    }

    /**
     * Switches to the previous favorite station. Steering wheel controls and Android Auto
     * previous track action switch exclusively among user favorites.
     */
    fun playPreviousFavorite() {
        val favs = favoriteStationsList.ifEmpty { activeStationList.ifEmpty { CuratedStations.STATIONS } }
        if (favs.isEmpty()) return
        val current = _playbackState.value.currentStation
        val currentIndex = if (current != null) {
            favs.indexOfFirst { it.id == current.id || it.url.equals(current.url, ignoreCase = true) }
        } else -1
        val prevIndex = if (currentIndex != -1) {
            if (currentIndex - 1 < 0) favs.size - 1 else currentIndex - 1
        } else {
            favs.size - 1
        }
        playStation(favs[prevIndex], favs)
    }

    fun playNext() {
        playNextFavorite()
    }

    fun playPrevious() {
        playPreviousFavorite()
    }

    fun stop() {
        retryJob?.cancel()
        retryCount = 0
        cancelBufferingWatchdog()
        updateLocks(false)
        player.stop()
        player.clearMediaItems()
        _playbackState.update { it.copy(status = PlaybackStatus.IDLE, errorMessage = null) }
        onMetadataUpdatedListener?.invoke()
        try {
            val stopIntent = Intent(context, RadioMediaService::class.java).apply {
                action = RadioMediaService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (_: Exception) {}
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        player.volume = clamped
        _playbackState.update { it.copy(volume = clamped) }
    }

    fun updateFavoriteStatus(stationId: String, isFav: Boolean) {
        if (_playbackState.value.currentStation?.id == stationId) {
            _playbackState.update { it.copy(isFavorite = isFav) }
        }
    }

    fun release() {
        retryJob?.cancel()
        cancelBufferingWatchdog()
        updateLocks(false)
        equalizerManager.release()
        player.removeListener(playerListener)
        player.release()
    }

    companion object {
        @Volatile
        private var INSTANCE: RadioPlaybackManager? = null

        fun getInstance(
            context: Context,
            repository: StationRepository,
            preferences: RadioPreferences,
            albumArtRepository: com.example.data.repository.AlbumArtRepository = com.example.data.repository.AlbumArtRepository()
        ): RadioPlaybackManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RadioPlaybackManager(
                    context.applicationContext,
                    repository,
                    preferences,
                    albumArtRepository
                ).also { INSTANCE = it }
            }
        }

        fun getInstance(): RadioPlaybackManager? = INSTANCE
    }
}
