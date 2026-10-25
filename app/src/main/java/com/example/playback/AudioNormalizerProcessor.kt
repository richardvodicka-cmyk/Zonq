package com.example.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

enum class NormalizationProfile(
    val id: String,
    val targetRms: Float,
    val minGain: Float,
    val maxGain: Float,
    val displayNameRes: Int
) {
    BALANCED(
        id = "balanced",
        targetRms = 0.16f, // ~ -15.9 dBFS (Standard broadcast streaming target)
        minGain = 0.45f,   // -6.9 dB max attenuation
        maxGain = 1.8f,    // +5.1 dB max boost
        displayNameRes = R.string.norm_profile_balanced
    ),
    DYNAMIC(
        id = "dynamic",
        targetRms = 0.12f, // ~ -18.4 dBFS (Gentle, preserves broad dynamics)
        minGain = 0.55f,   // -5.2 dB max attenuation
        maxGain = 1.5f,    // +3.5 dB max boost
        displayNameRes = R.string.norm_profile_dynamic
    ),
    LOUD(
        id = "loud",
        targetRms = 0.20f, // ~ -14.0 dBFS (Punchy, optimal for noisy environments)
        minGain = 0.40f,   // -8.0 dB max attenuation
        maxGain = 2.0f,    // +6.0 dB max boost
        displayNameRes = R.string.norm_profile_loud
    )
}

data class NormalizerStatus(
    val isEnabled: Boolean = true,
    val profile: NormalizationProfile = NormalizationProfile.BALANCED,
    val currentGainDb: Float = 0f,
    val measuredRmsDb: Float = -20f
)

/**
 * Real-time Broadcast Audio Normalizer & Limiter with soft-start startup protection.
 * Completely eliminates initial volume surges, clipping distortion, and AudioTrack crackles
 * upon stream connection by holding stable unity gain during stream sync and smoothly integrating RMS.
 */
@UnstableApi
class AudioNormalizerProcessor : BaseAudioProcessor() {

    @Volatile
    var isNormalizationEnabled: Boolean = true
        set(value) {
            field = value
            _statusFlow.value = _statusFlow.value.copy(isEnabled = value)
        }

    @Volatile
    var activeProfile: NormalizationProfile = NormalizationProfile.BALANCED
        set(value) {
            field = value
            _statusFlow.value = _statusFlow.value.copy(profile = value)
        }

    private val _statusFlow = MutableStateFlow(NormalizerStatus())
    val statusFlow: StateFlow<NormalizerStatus> = _statusFlow.asStateFlow()

    // Station memory: stores the steady-state gain learned for each radio station
    private val stationLearnedGains = ConcurrentHashMap<String, Float>()
    @Volatile
    private var currentStationId: String? = null

    // Real-time gain filter state
    @Volatile
    private var currentGain: Float = 1.0f
    @Volatile
    private var targetGain: Float = 1.0f

    // Leaky integrated long-term RMS (moving average, prevents instant fluctuations from drums/pauses)
    @Volatile
    private var integratedRms: Float = 0f

    // Counter of processed frames since stream start/reset for soft start protection
    @Volatile
    private var framesProcessedSinceStart: Long = 0L

    // Constants
    private val silenceThreshold = 0.003f // ~ -50.5 dBFS
    private val softKneeThreshold = 0.82f // -1.7 dBFS
    private val headroom = 1.0f - softKneeThreshold // 0.18f

    // Smoothing coefficients per frame
    private var attackCoeff = 0.0010f
    private var releaseCoeff = 0.00004f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        attackCoeff = (1.0f / (0.060f * sampleRate)).coerceIn(0.0003f, 0.005f)
        releaseCoeff = (1.0f / (1.500f * sampleRate)).coerceIn(0.00001f, 0.0003f)
        return inputAudioFormat
    }

    fun onStationChanged(stationId: String?) {
        currentStationId = stationId
        framesProcessedSinceStart = 0L
        integratedRms = 0f
        val learned = if (stationId != null) stationLearnedGains[stationId] else null
        val safeInitialGain = learned?.coerceIn(0.80f, 1.20f) ?: 1.0f
        currentGain = safeInitialGain
        targetGain = safeInitialGain
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val count = inputBuffer.remaining()
        if (!isNormalizationEnabled) {
            val outputBuffer = replaceOutputBuffer(count)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val numShorts = count / 2
        val channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        val numFrames = numShorts / channelCount
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        val startPosition = inputBuffer.position()

        // Pass 1: Measure short-term RMS of this buffer
        var sumSquares = 0.0
        var sIndex = 0
        while (sIndex < numShorts) {
            val sample = inputBuffer.short
            val norm = sample / 32768.0
            sumSquares += norm * norm
            sIndex++
        }
        val bufferRms = sqrt(sumSquares / numShorts.coerceAtLeast(1)).toFloat()

        // Update leaky integrated RMS (~500ms time constant)
        val alpha = (numFrames.toFloat() / (0.500f * sampleRate)).coerceIn(0.02f, 0.20f)
        integratedRms = if (integratedRms <= 0f) {
            bufferRms
        } else {
            integratedRms * (1f - alpha) + bufferRms * alpha
        }

        framesProcessedSinceStart += numFrames
        val warmupDurationFrames = (sampleRate * 2.5).toLong() // 2.5 seconds warm-up protection
        val isWarmup = framesProcessedSinceStart < warmupDurationFrames

        val profile = activeProfile
        val effectiveRms = integratedRms

        if (effectiveRms > silenceThreshold) {
            val rawTarget = (profile.targetRms / effectiveRms)
            targetGain = if (isWarmup) {
                // During initial 2.5 seconds, strictly bound target gain near 1.0 to eliminate startup surges
                rawTarget.coerceIn(0.85f, 1.15f)
            } else {
                rawTarget.coerceIn(profile.minGain, profile.maxGain)
            }

            // Only learn steady-state gain after warm-up is completely finished
            val stationId = currentStationId
            if (stationId != null && !isWarmup) {
                val existing = stationLearnedGains[stationId] ?: targetGain
                stationLearnedGains[stationId] = existing * 0.99f + targetGain * 0.01f
            }
        }

        // Pass 2: Apply smooth gain and C1-continuous soft-knee limiter
        inputBuffer.position(startPosition)
        val outputBuffer = replaceOutputBuffer(count)

        var g = currentGain
        val target = targetGain

        for (f in 0 until numFrames) {
            val stepCoeff = if (target < g) attackCoeff else releaseCoeff
            g += stepCoeff * (target - g)

            for (ch in 0 until channelCount) {
                val rawSample = inputBuffer.short
                val floatSample = rawSample / 32768.0f
                val amplified = floatSample * g

                val absAmp = abs(amplified)
                val processedFloat = if (absAmp <= softKneeThreshold) {
                    amplified
                } else {
                    // C1-smooth rational saturation curve: zero slope discontinuity, zero harsh harmonics
                    val excess = absAmp - softKneeThreshold
                    val saturated = excess / (1.0f + (excess / headroom))
                    val compressed = (softKneeThreshold + saturated).coerceAtMost(0.999f)
                    if (amplified >= 0f) compressed else -compressed
                }

                val outShort = kotlin.math.round(processedFloat * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
                outputBuffer.putShort(outShort)
            }
        }

        currentGain = g
        outputBuffer.flip()

        // Periodically update status flow for UI reflection
        val gainDb = (20.0 * kotlin.math.log10(g.toDouble().coerceAtLeast(0.01))).toFloat()
        val rmsDb = (20.0 * kotlin.math.log10(effectiveRms.toDouble().coerceAtLeast(0.0001))).toFloat()
        if (abs(_statusFlow.value.currentGainDb - gainDb) > 0.5f) {
            _statusFlow.value = _statusFlow.value.copy(
                currentGainDb = gainDb,
                measuredRmsDb = rmsDb
            )
        }
    }

    override fun onFlush() {
        super.onFlush()
        framesProcessedSinceStart = 0L
        integratedRms = 0f
        currentGain = 1.0f
        targetGain = 1.0f
    }

    override fun onReset() {
        super.onReset()
        framesProcessedSinceStart = 0L
        integratedRms = 0f
        currentGain = 1.0f
        targetGain = 1.0f
    }
}
