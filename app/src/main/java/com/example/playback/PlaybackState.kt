package com.example.playback

import com.example.data.model.Station

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

data class PlaybackState(
    val currentStation: Station? = null,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val albumArtUrl: String? = null,
    val rawMetadata: String? = null,
    val errorMessage: String? = null,
    val isFavorite: Boolean = false,
    val volume: Float = 1.0f
)
