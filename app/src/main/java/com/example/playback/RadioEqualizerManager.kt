package com.example.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EqualizerPreset(
    val id: String,
    val displayNameRes: Int,
    val bandLevels: List<Int> // dB gains for typical 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (range -10 to +10 dB)
) {
    FLAT("flat", com.example.R.string.eq_preset_flat, listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("bass_boost", com.example.R.string.eq_preset_bass_boost, listOf(6, 4, 1, 0, 0)),
    TREBLE_BOOST("treble_boost", com.example.R.string.eq_preset_treble_boost, listOf(0, 0, 1, 4, 6)),
    ROCK("rock", com.example.R.string.eq_preset_rock, listOf(5, 3, -1, 3, 5)),
    POP("pop", com.example.R.string.eq_preset_pop, listOf(-1, 2, 5, 2, -1)),
    JAZZ("jazz", com.example.R.string.eq_preset_jazz, listOf(3, 2, 1, 2, 4)),
    CLASSICAL("classical", com.example.R.string.eq_preset_classical, listOf(4, 3, -1, 2, 3)),
    ELECTRONIC("electronic", com.example.R.string.eq_preset_electronic, listOf(5, 4, 0, 2, 4)),
    VOCAL("vocal", com.example.R.string.eq_preset_vocal, listOf(-2, 1, 5, 3, -1)),
    SPEECH_NEWS("speech_news", com.example.R.string.eq_preset_speech_news, listOf(-3, 0, 5, 2, -2)),
    ACOUSTIC("acoustic", com.example.R.string.eq_preset_acoustic, listOf(4, 2, 1, 3, 4)),
    CUSTOM("custom", com.example.R.string.eq_preset_custom, listOf(0, 0, 0, 0, 0))
}

data class EqualizerBandInfo(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val levelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short
)

data class EqualizerState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val currentPresetId: String = EqualizerPreset.FLAT.id,
    val bands: List<EqualizerBandInfo> = emptyList()
)

class RadioEqualizerManager(private val context: Context) {

    private val tag = "RadioEqualizer"
    private var equalizer: Equalizer? = null
    private var currentSessionId: Int = 0

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    private var activeAudioSessionId: Int = 0

    fun attachAudioSession(audioSessionId: Int) {
        try {
            if (audioSessionId <= 0) return
            activeAudioSessionId = audioSessionId

            // CRITICAL FIX FOR ANDROID TV:
            // Do NOT instantiate android.media.audiofx.Equalizer if equalizer is disabled!
            if (!_equalizerState.value.isEnabled) {
                safeReleaseHardwareEffect()
                return
            }

            if (equalizer != null && currentSessionId == audioSessionId) return
            initializeHardwareEqualizer(audioSessionId)
        } catch (e: Throwable) {
            Log.w(tag, "attachAudioSession failed on session $audioSessionId: ${e.message}")
        }
    }

    private fun initializeHardwareEqualizer(audioSessionId: Int) {
        try {
            safeReleaseHardwareEffect()
            currentSessionId = audioSessionId
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq

            val numBands = try { eq.numberOfBands } catch (_: Throwable) { 0.toShort() }
            if (numBands <= 0) {
                _equalizerState.update { it.copy(isSupported = false) }
                return
            }

            val range = try { eq.bandLevelRange } catch (_: Throwable) { null }
            val (minMb, maxMb) = if (range != null && range.size >= 2 && range[0] < range[1]) {
                range[0] to range[1]
            } else {
                (-1200).toShort() to (1200).toShort()
            }

            val bandList = mutableListOf<EqualizerBandInfo>()
            for (i in 0 until numBands) {
                val band = i.toShort()
                val centerFreq = try { eq.getCenterFreq(band) / 1000 } catch (_: Throwable) { (60 * (i + 1) * 2) }
                val currentLevel = try { eq.getBandLevel(band) } catch (_: Throwable) { 0.toShort() }
                bandList.add(
                    EqualizerBandInfo(
                        bandIndex = band,
                        centerFreqHz = centerFreq,
                        levelMb = currentLevel.coerceIn(minMb, maxMb),
                        minLevelMb = minMb,
                        maxLevelMb = maxMb
                    )
                )
            }

            try {
                eq.enabled = _equalizerState.value.isEnabled
            } catch (_: Throwable) {}

            _equalizerState.update {
                it.copy(
                    isSupported = true,
                    bands = bandList
                )
            }

            // Restore preset or custom band levels
            applyCurrentStateToHardware()
        } catch (e: Throwable) {
            Log.w(tag, "Equalizer initialization not supported or failed on session $audioSessionId: ${e.message}")
            _equalizerState.update { it.copy(isSupported = false) }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _equalizerState.update { it.copy(isEnabled = enabled) }
        if (enabled) {
            if (equalizer == null && activeAudioSessionId > 0) {
                initializeHardwareEqualizer(activeAudioSessionId)
            } else {
                try {
                    equalizer?.enabled = true
                } catch (e: Exception) {
                    Log.w(tag, "Failed to enable equalizer: ${e.message}")
                }
            }
        } else {
            try {
                equalizer?.enabled = false
            } catch (_: Exception) {}
            safeReleaseHardwareEffect()
        }
    }

    private fun safeReleaseHardwareEffect() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        currentSessionId = 0
    }

    fun setPreset(preset: EqualizerPreset) {
        val eq = equalizer
        val bands = _equalizerState.value.bands

        if (preset == EqualizerPreset.CUSTOM) {
            _equalizerState.update { it.copy(currentPresetId = preset.id) }
            return
        }

        val updatedBands = bands.mapIndexed { index, bandInfo ->
            val targetGainDb = if (index < preset.bandLevels.size) preset.bandLevels[index] else 0
            val (safeMin, safeMax) = if (bandInfo.minLevelMb < bandInfo.maxLevelMb) {
                bandInfo.minLevelMb to bandInfo.maxLevelMb
            } else {
                (-1200).toShort() to (1200).toShort()
            }
            val targetGainMb = (targetGainDb * 100).toShort().coerceIn(safeMin, safeMax)
            try {
                eq?.setBandLevel(bandInfo.bandIndex, targetGainMb)
            } catch (e: Throwable) {
                Log.w(tag, "Error setting band level: ${e.message}")
            }
            bandInfo.copy(levelMb = targetGainMb, minLevelMb = safeMin, maxLevelMb = safeMax)
        }

        _equalizerState.update {
            it.copy(
                currentPresetId = preset.id,
                bands = updatedBands
            )
        }
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer
        try {
            eq?.setBandLevel(bandIndex, levelMb)
        } catch (e: Throwable) {
            Log.w(tag, "Error setting band level: ${e.message}")
        }

        val updatedBands = _equalizerState.value.bands.map { band ->
            if (band.bandIndex == bandIndex) {
                val (safeMin, safeMax) = if (band.minLevelMb < band.maxLevelMb) {
                    band.minLevelMb to band.maxLevelMb
                } else {
                    (-1200).toShort() to (1200).toShort()
                }
                band.copy(levelMb = levelMb.coerceIn(safeMin, safeMax), minLevelMb = safeMin, maxLevelMb = safeMax)
            } else {
                band
            }
        }

        _equalizerState.update {
            it.copy(
                currentPresetId = EqualizerPreset.CUSTOM.id,
                bands = updatedBands
            )
        }
    }

    private fun applyCurrentStateToHardware() {
        val eq = equalizer ?: return
        val state = _equalizerState.value
        try {
            try {
                eq.enabled = state.isEnabled
            } catch (_: Throwable) {}
            val preset = EqualizerPreset.entries.find { it.id == state.currentPresetId } ?: EqualizerPreset.FLAT
            if (preset != EqualizerPreset.CUSTOM) {
                state.bands.forEachIndexed { index, bandInfo ->
                    val gainDb = if (index < preset.bandLevels.size) preset.bandLevels[index] else 0
                    val (safeMin, safeMax) = if (bandInfo.minLevelMb < bandInfo.maxLevelMb) {
                        bandInfo.minLevelMb to bandInfo.maxLevelMb
                    } else {
                        (-1200).toShort() to (1200).toShort()
                    }
                    val targetGainMb = (gainDb * 100).toShort().coerceIn(safeMin, safeMax)
                    try {
                        eq.setBandLevel(bandInfo.bandIndex, targetGainMb)
                    } catch (_: Throwable) {}
                }
            } else {
                state.bands.forEach { bandInfo ->
                    try {
                        eq.setBandLevel(bandInfo.bandIndex, bandInfo.levelMb)
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w(tag, "Error applying equalizer to hardware: ${e.message}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Throwable) {
            Log.w(tag, "Error releasing equalizer: ${e.message}")
        }
        equalizer = null
        currentSessionId = 0
    }
}
