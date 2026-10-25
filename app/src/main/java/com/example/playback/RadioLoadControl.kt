package com.example.playback

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.example.data.preferences.BufferDuration
import com.example.data.preferences.RadioPreferences

/**
 * Custom LoadControl strictly bounding forward buffering to the user's selected buffer time,
 * completely disabling back-buffer data retention, and minimizing network data consumption.
 */
@UnstableApi
class RadioLoadControl(
    private val preferences: RadioPreferences,
    private val isTv: Boolean = false
) : LoadControl {

    private val allocator = DefaultAllocator(true, 32 * 1024)

    private val delegates: Map<BufferDuration, DefaultLoadControl> = BufferDuration.entries.associateWith { duration ->
        // On TV, allocate higher startup and playback buffers to eliminate network jitter and AudioTrack starvation
        val minBuffer = if (isTv) maxOf(duration.minBufferMs, 8000) else duration.minBufferMs
        val maxBuffer = if (isTv) maxOf(duration.maxBufferMs, 16000) else duration.maxBufferMs
        val bufferForPlayback = if (isTv) maxOf(duration.bufferForPlaybackMs, 2500) else duration.bufferForPlaybackMs
        val bufferForRebuffer = if (isTv) maxOf(duration.bufferForRebufferMs, 3500) else duration.bufferForRebufferMs

        DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                minBuffer,
                maxBuffer,
                bufferForPlayback,
                bufferForRebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()
    }

    private val activeDelegate: DefaultLoadControl
        get() = delegates[preferences.currentBufferDuration] ?: delegates.values.first()

    override fun onPrepared() {
        delegates.values.forEach { it.onPrepared() }
    }

    override fun onPrepared(playerId: PlayerId) {
        delegates.values.forEach { it.onPrepared(playerId) }
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>
    ) {
        delegates.values.forEach { it.onTracksSelected(parameters, trackGroups, trackSelections) }
    }

    override fun onTracksSelected(
        renderers: Array<Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<ExoTrackSelection?>
    ) {
        delegates.values.forEach { it.onTracksSelected(renderers, trackGroups, trackSelections) }
    }

    override fun onStopped() {
        delegates.values.forEach { it.onStopped() }
    }

    override fun onStopped(playerId: PlayerId) {
        delegates.values.forEach { it.onStopped(playerId) }
    }

    override fun onReleased() {
        delegates.values.forEach { it.onReleased() }
    }

    override fun onReleased(playerId: PlayerId) {
        delegates.values.forEach { it.onReleased(playerId) }
    }

    override fun getAllocator(): Allocator = allocator

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        return activeDelegate.shouldContinueLoading(parameters)
    }

    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float
    ): Boolean {
        return activeDelegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        return activeDelegate.shouldStartPlayback(parameters)
    }

    override fun shouldStartPlayback(
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean {
        return activeDelegate.shouldStartPlayback(
            timeline, mediaPeriodId, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs
        )
    }

    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long
    ): Boolean {
        return activeDelegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)
    }

    override fun retainBackBufferFromKeyframe(): Boolean = false

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = false

    override fun getBackBufferDurationUs(): Long = 0L

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = 0L
}
