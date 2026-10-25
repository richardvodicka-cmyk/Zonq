package com.example.playback

import android.net.Uri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArrayList

@UnstableApi
class RadioForwardingPlayer(
    player: Player,
    private val playbackManager: RadioPlaybackManager
) : ForwardingPlayer(player) {

    private val playerListeners = CopyOnWriteArrayList<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        playerListeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        playerListeners.remove(listener)
        super.removeListener(listener)
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_SEEK_FORWARD,
            COMMAND_SEEK_BACK,
            COMMAND_PLAY_PAUSE,
            COMMAND_STOP,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_GET_METADATA,
            COMMAND_GET_TIMELINE -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(COMMAND_SEEK_TO_NEXT)
            .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(COMMAND_SEEK_TO_PREVIOUS)
            .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(COMMAND_SEEK_FORWARD)
            .add(COMMAND_SEEK_BACK)
            .add(COMMAND_PLAY_PAUSE)
            .add(COMMAND_STOP)
            .add(COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(COMMAND_GET_METADATA)
            .add(COMMAND_GET_TIMELINE)
            .build()
    }

    override fun getMediaMetadata(): MediaMetadata {
        val state = playbackManager.playbackState.value
        val station = state.currentStation
        val stationName = station?.name ?: "zonq"
        val liveDefault = playbackManager.context.getString(com.example.R.string.live_stream_default)
        val parsedArtist = state.trackArtist?.trim()?.ifBlank { null }
        val parsedTitle = state.trackTitle?.trim()?.ifBlank { null }

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
                station?.getDisplaySubtitle(playbackManager.context)?.ifBlank { liveDefault } ?: liveDefault
            }
        }

        val favicon = station?.favicon
        val fallbackUri = Uri.parse("android.resource://${playbackManager.context.packageName}/${com.example.R.mipmap.ic_launcher}")
        val effectiveArt = state.albumArtUrl?.ifBlank { null }
        val artworkUri = when {
            !effectiveArt.isNullOrBlank() -> Uri.parse(effectiveArt)
            !favicon.isNullOrBlank() -> Uri.parse(favicon)
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

    override fun getCurrentMediaItem(): MediaItem? {
        val original = super.getCurrentMediaItem()
        if (original != null) {
            return original.buildUpon()
                .setMediaMetadata(getMediaMetadata())
                .build()
        }
        val station = playbackManager.playbackState.value.currentStation ?: return null
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.url)
            .setMediaMetadata(getMediaMetadata())
            .build()
    }

    override fun pause() {
        super.pause()
        playbackManager.pause()
    }

    override fun play() {
        super.play()
        playbackManager.resume()
    }

    override fun stop() {
        super.stop()
        playbackManager.stop()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        super.setPlayWhenReady(playWhenReady)
        if (playWhenReady) {
            playbackManager.resume()
        } else {
            playbackManager.pause()
        }
    }

    override fun getPlayWhenReady(): Boolean {
        return super.getPlayWhenReady()
    }

    fun dispatchLiveMetadataUpdate() {
        val meta = getMediaMetadata()
        for (listener in playerListeners) {
            try {
                listener.onMediaMetadataChanged(meta)
                listener.onPlaylistMetadataChanged(meta)
            } catch (_: Exception) {}
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun getNextMediaItemIndex(): Int = currentMediaItemIndex + 1

    override fun getPreviousMediaItemIndex(): Int = (currentMediaItemIndex - 1).coerceAtLeast(0)

    override fun seekToNext() {
        playbackManager.playNextFavorite()
    }

    override fun seekToNextMediaItem() {
        playbackManager.playNextFavorite()
    }

    override fun seekToPrevious() {
        playbackManager.playPreviousFavorite()
    }

    override fun seekToPreviousMediaItem() {
        playbackManager.playPreviousFavorite()
    }

    override fun seekForward() {
        playbackManager.playNextFavorite()
    }

    override fun seekBack() {
        playbackManager.playPreviousFavorite()
    }

    override fun seekToDefaultPosition() {
        playbackManager.playNextFavorite()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (mediaItemIndex > currentMediaItemIndex) {
            playbackManager.playNextFavorite()
        } else if (mediaItemIndex < currentMediaItemIndex) {
            playbackManager.playPreviousFavorite()
        }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex > currentMediaItemIndex) {
            playbackManager.playNextFavorite()
        } else if (mediaItemIndex < currentMediaItemIndex) {
            playbackManager.playPreviousFavorite()
        }
    }
}
