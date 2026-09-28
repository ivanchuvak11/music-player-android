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
    val numberOfBands: Short = 0,
    val bandLevels: Map<Short, Short> = emptyMap()
) {
    companion object {
        const val PRESET_FLAT = "Звичайний"
        const val PRESET_BASS = "Підсилений бас"
        const val PRESET_ROCK = "Рок"
        const val PRESET_POP = "Поп"
        const val PRESET_JAZZ = "Джаз"
        const val PRESET_CLASSICAL = "Класика"

        val AVAILABLE_PRESETS = listOf(
            PRESET_FLAT,
            PRESET_BASS,
            PRESET_ROCK,
            PRESET_POP,
            PRESET_JAZZ,
            PRESET_CLASSICAL
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

            val bb = BassBoost(0, audioSessionId).apply {
                enabled = _effectsState.value.isEnabled
                if (strengthSupported) {
                    setStrength(_effectsState.value.bassBoostStrength)
                }
            }
            bassBoost = bb

            val numBands = eq.numberOfBands
            val bandsMap = mutableMapOf<Short, Short>()
            for (i in 0 until numBands) {
                val band = i.toShort()
                bandsMap[band] = eq.getBandLevel(band)
            }

            _effectsState.value = _effectsState.value.copy(
                numberOfBands = numBands,
                bandLevels = bandsMap
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
            _effectsState.value = _effectsState.value.copy(isEnabled = enabled)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun setPreset(presetName: String) {
        _effectsState.value = _effectsState.value.copy(currentPreset = presetName)
        val eq = equalizer ?: return

        try {
            val numBands = eq.numberOfBands
            val minLevel = eq.bandLevelRange?.getOrNull(0) ?: -1000
            val maxLevel = eq.bandLevelRange?.getOrNull(1) ?: 1000
            val midLevel: Short = 0

            val levels = when (presetName) {
                AudioEffectsState.PRESET_BASS -> {
                    setBassBoost(600.toShort())
                    listOf((maxLevel * 0.7).toInt().toShort(), (maxLevel * 0.4).toInt().toShort(), 0.toShort(), 0.toShort(), 0.toShort())
                }
                AudioEffectsState.PRESET_ROCK -> {
                    setBassBoost(300.toShort())
                    listOf((maxLevel * 0.5).toInt().toShort(), (maxLevel * 0.2).toInt().toShort(), (-minLevel * 0.2).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.6).toInt().toShort())
                }
                AudioEffectsState.PRESET_POP -> {
                    setBassBoost(200.toShort())
                    listOf((-minLevel * 0.2).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.5).toInt().toShort(), (maxLevel * 0.2).toInt().toShort(), (-minLevel * 0.1).toInt().toShort())
                }
                AudioEffectsState.PRESET_JAZZ -> {
                    setBassBoost(200.toShort())
                    listOf((maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.2).toInt().toShort(), 0.toShort(), (maxLevel * 0.2).toInt().toShort(), (maxLevel * 0.4).toInt().toShort())
                }
                AudioEffectsState.PRESET_CLASSICAL -> {
                    setBassBoost(0.toShort())
                    listOf((maxLevel * 0.4).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (-minLevel * 0.1).toInt().toShort(), (maxLevel * 0.3).toInt().toShort(), (maxLevel * 0.4).toInt().toShort())
                }
                else -> {
                    setBassBoost(0.toShort())
                    List(numBands.toInt()) { midLevel }
                }
            }

            val bandsMap = _effectsState.value.bandLevels.toMutableMap()
            for (i in 0 until numBands) {
                val band = i.toShort()
                val targetLevel = levels.getOrElse(i) { midLevel }
                eq.setBandLevel(band, targetLevel)
                bandsMap[band] = targetLevel
            }
            _effectsState.value = _effectsState.value.copy(bandLevels = bandsMap)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
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
            _effectsState.value = _effectsState.value.copy(bassBoostStrength = clampedStrength)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            val updatedMap = _effectsState.value.bandLevels.toMutableMap()
            updatedMap[band] = level
            _effectsState.value = _effectsState.value.copy(bandLevels = updatedMap)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
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
