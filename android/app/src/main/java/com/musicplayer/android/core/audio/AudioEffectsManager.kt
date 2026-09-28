package com.musicplayer.android.core.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representing audio equalizer and sound enhancement settings.
 */
data class AudioEffectsState(
    val isEnabled: Boolean = false,
    val currentPreset: String = PRESET_FLAT,
    val bassBoostStrength: Short = 0,
    val numberOfBands: Short = 5,
    val bandLevels: Map<Short, Short> = defaultBandLevels(),
    val minBandLevel: Short = -1000,
    val maxBandLevel: Short = 1000
) {
    companion object {
        const val PRESET_FLAT = "Звичайний"
        const val PRESET_BASS = "Підсилений бас"
        const val PRESET_ROCK = "Рок"
        const val PRESET_POP = "Поп"
        const val PRESET_JAZZ = "Джаз"
        const val PRESET_CLASSICAL = "Класика"
        const val PRESET_CUSTOM = "Власний"

        val AVAILABLE_PRESETS = listOf(
            PRESET_FLAT,
            PRESET_BASS,
            PRESET_ROCK,
            PRESET_POP,
            PRESET_JAZZ,
            PRESET_CLASSICAL
        )

        fun defaultBandLevels(): Map<Short, Short> = mapOf(
            0.toShort() to 0.toShort(),
            1.toShort() to 0.toShort(),
            2.toShort() to 0.toShort(),
            3.toShort() to 0.toShort(),
            4.toShort() to 0.toShort()
        )

        val BAND_LABELS = mapOf(
            0.toShort() to "60 Hz (Глибокий бас)",
            1.toShort() to "230 Hz (Бас)",
            2.toShort() to "910 Hz (Середні)",
            3.toShort() to "3.6 kHz (Високі середні)",
            4.toShort() to "14 kHz (Високі)"
        )
    }
}

/**
 * Singleton managing hardware Equalizer and BassBoost effects attached to ExoPlayer audio session.
 */
object AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    private val _effectsState = MutableStateFlow(AudioEffectsState())
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    @Synchronized
    fun init(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentSessionId) return
        release()
        currentSessionId = audioSessionId

        try {
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = _effectsState.value.isEnabled
            }
            equalizer = eq

            val minL = eq.bandLevelRange?.getOrNull(0) ?: -1000
            val maxL = eq.bandLevelRange?.getOrNull(1) ?: 1000
            val numBands = eq.numberOfBands

            // Apply currently active band levels to newly attached hardware equalizer
            val currentLevels = _effectsState.value.bandLevels
            val bandsMap = mutableMapOf<Short, Short>()
            for (i in 0 until numBands) {
                val band = i.toShort()
                val targetLevel = currentLevels[band] ?: 0.toShort()
                try {
                    eq.setBandLevel(band, targetLevel)
                } catch (e: Throwable) { }
                bandsMap[band] = targetLevel
            }

            val bb = BassBoost(0, audioSessionId).apply {
                enabled = _effectsState.value.isEnabled
                if (strengthSupported) {
                    setStrength(_effectsState.value.bassBoostStrength)
                }
            }
            bassBoost = bb

            _effectsState.value = _effectsState.value.copy(
                numberOfBands = numBands,
                bandLevels = bandsMap,
                minBandLevel = minL,
                maxBandLevel = maxL
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        _effectsState.value = _effectsState.value.copy(isEnabled = enabled)
    }

    @Synchronized
    fun setPreset(presetName: String) {
        val minLevel = _effectsState.value.minBandLevel
        val maxLevel = _effectsState.value.maxBandLevel
        val numBands = _effectsState.value.numberOfBands.toInt().coerceAtLeast(5)

        val targetBassStrength: Short
        val levels = when (presetName) {
            AudioEffectsState.PRESET_BASS -> {
                targetBassStrength = 750.toShort()
                listOf((maxLevel * 0.8).toInt().toShort(), (maxLevel * 0.5).toInt().toShort(), 0.toShort(), 0.toShort(), 0.toShort())
            }
            AudioEffectsState.PRESET_ROCK -> {
                targetBassStrength = 350.toShort()
                listOf((maxLevel * 0.5).toInt().toShort(), (maxLevel * 0.25).toInt().toShort(), (minLevel * 0.2).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.6).toInt().toShort())
            }
            AudioEffectsState.PRESET_POP -> {
                targetBassStrength = 250.toShort()
                listOf((minLevel * 0.15).toInt().toShort(), (maxLevel * 0.25).toInt().toShort(), (maxLevel * 0.5).toInt().toShort(), (maxLevel * 0.25).toInt().toShort(), (minLevel * 0.1).toInt().toShort())
            }
            AudioEffectsState.PRESET_JAZZ -> {
                targetBassStrength = 200.toShort()
                listOf((maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.2).toInt().toShort(), 0.toShort(), (maxLevel * 0.2).toInt().toShort(), (maxLevel * 0.4).toInt().toShort())
            }
            AudioEffectsState.PRESET_CLASSICAL -> {
                targetBassStrength = 0.toShort()
                listOf((maxLevel * 0.4).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (minLevel * 0.1).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.4).toInt().toShort())
            }
            else -> {
                targetBassStrength = 0.toShort()
                List(numBands) { 0.toShort() }
            }
        }

        val bandsMap = mutableMapOf<Short, Short>()
        for (i in 0 until numBands) {
            val band = i.toShort()
            val targetLevel = levels.getOrElse(i) { 0.toShort() }
            bandsMap[band] = targetLevel
            try {
                equalizer?.setBandLevel(band, targetLevel)
            } catch (e: Throwable) { }
        }

        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(targetBassStrength)
                }
            }
        } catch (e: Throwable) { }

        // Immediately update state so UI reacts instantly
        _effectsState.value = _effectsState.value.copy(
            currentPreset = presetName,
            bandLevels = bandsMap,
            bassBoostStrength = targetBassStrength
        )
    }

    @Synchronized
    fun setBassBoost(strength: Short) {
        val clampedStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(clampedStrength)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Dynamically boost low frequencies in equalizer to guarantee punch on all speakers
        val maxLevel = _effectsState.value.maxBandLevel
        val lowBoost = (clampedStrength.toFloat() / 1000f * (maxLevel * 0.7f)).toInt().toShort()
        val midBassBoost = (clampedStrength.toFloat() / 1000f * (maxLevel * 0.4f)).toInt().toShort()

        val updatedMap = _effectsState.value.bandLevels.toMutableMap()
        if (_effectsState.value.currentPreset == AudioEffectsState.PRESET_FLAT ||
            _effectsState.value.currentPreset == AudioEffectsState.PRESET_BASS) {
            updatedMap[0.toShort()] = lowBoost
            updatedMap[1.toShort()] = midBassBoost
            try {
                equalizer?.setBandLevel(0.toShort(), lowBoost)
                equalizer?.setBandLevel(1.toShort(), midBassBoost)
            } catch (e: Throwable) { }
        }

        _effectsState.value = _effectsState.value.copy(
            bassBoostStrength = clampedStrength,
            bandLevels = updatedMap
        )
    }

    @Synchronized
    fun setBandLevel(band: Short, level: Short) {
        val clampedLevel = level.coerceIn(_effectsState.value.minBandLevel, _effectsState.value.maxBandLevel)
        try {
            equalizer?.setBandLevel(band, clampedLevel)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val updatedMap = _effectsState.value.bandLevels.toMutableMap()
        updatedMap[band] = clampedLevel
        _effectsState.value = _effectsState.value.copy(
            currentPreset = AudioEffectsState.PRESET_CUSTOM,
            bandLevels = updatedMap
        )
    }

    @Synchronized
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            equalizer = null
            bassBoost = null
            currentSessionId = 0
        }
    }
}
