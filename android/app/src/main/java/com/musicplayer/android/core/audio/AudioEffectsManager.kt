package com.musicplayer.android.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.musicplayer.android.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State representing audio equalizer, bass boost, and loudness normalization settings.
 */
data class AudioEffectsState(
    val isEnabled: Boolean = false,
    val isHeadphonesConnected: Boolean = false,
    val currentPreset: String = PRESET_FLAT,
    val bassBoostStrength: Short = 0,
    val loudnessEnhancerGainMb: Int = 0,
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
 * Singleton managing hardware Equalizer, BassBoost, and LoudnessEnhancer
 * attached to the ExoPlayer audio session. Requires headphones/headset to enable.
 *
 * Fix #11: release() now properly unregisters AudioDeviceCallback and BroadcastReceiver
 * to prevent memory leaks when the audio session is recycled.
 *
 * Fix #12: restorePersistedState() re-applies hardware DSP after state is loaded, so the
 * hardware EQ matches the UI state (no more "EQ shows ON but DSP is OFF" mismatch).
 *
 * Fix #13: isHeadphonesConnected() now also checks USB-C DAC, AUX analog line, and BLE
 * audio so users with adapters can enable the equalizer.
 *
 * Fix #20: release() resets currentSessionId = 0 inside a finally block so the next
 * init() after a zombie singleton state is never silently ignored.
 */
object AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0
    private var audioManager: AudioManager? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var headsetReceiver: android.content.BroadcastReceiver? = null
    // Fix #11: keep context reference so we can unregister the receiver in release()
    private var appContextRef: Context? = null
    // Fix: Dedicated persistence scope and job to avoid orphaned coroutines
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistenceJob: kotlinx.coroutines.Job? = null

    private val _effectsState = MutableStateFlow(AudioEffectsState())
    val effectsState: StateFlow<AudioEffectsState> = _effectsState.asStateFlow()

    fun isHeadphonesConnected(context: Context): Boolean {
        val appContext = context.applicationContext
        val am = audioManager ?: (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also {
            audioManager = it
        } ?: return false

        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasHeadphone = devices.any { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    // Fix #13: USB-C DAC / AUX analog / digital line adapters
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
                    else -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                        } else {
                            false
                        }
                    }
                }
            }
            if (hasHeadphone) return true
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Secondary fallback for vendor HALs where getDevices may lag
        try {
            @Suppress("DEPRECATION")
            if (am.isWiredHeadsetOn || am.isBluetoothA2dpOn || am.isBluetoothScoOn) {
                return true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        return false
    }

    @Synchronized
    fun registerAudioDeviceCallback(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext  // Fix #11: store for unregister in release()
        val am = audioManager ?: (appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.also {
            audioManager = it
        } ?: return

        val connected = isHeadphonesConnected(appContext)
        _effectsState.value = _effectsState.value.copy(isHeadphonesConnected = connected)

        if (audioDeviceCallback == null) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkAndUpdateHeadphoneStatus(appContext)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkAndUpdateHeadphoneStatus(appContext)
                }
            }
            audioDeviceCallback = callback
            try {
                am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        if (headsetReceiver == null) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                    checkAndUpdateHeadphoneStatus(appContext)
                }
            }
            headsetReceiver = receiver
            try {
                val filter = android.content.IntentFilter().apply {
                    addAction(android.content.Intent.ACTION_HEADSET_PLUG)
                    addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                }
                appContext.registerReceiver(receiver, filter)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    fun checkAndUpdateHeadphoneStatus(context: Context) {
        val connected = isHeadphonesConnected(context)
        val currentlyEnabled = _effectsState.value.isEnabled
        if (!connected && currentlyEnabled) {
            // Auto-disable hardware effects immediately when headphones are disconnected
            setEnabled(false, context)
        } else {
            _effectsState.value = _effectsState.value.copy(isHeadphonesConnected = connected)
        }
    }

    @Synchronized
    fun init(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentSessionId) return
        release()

        try {
            val canEnable = _effectsState.value.isEnabled && _effectsState.value.isHeadphonesConnected
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = canEnable
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
                val targetLevel = if (canEnable) (currentLevels[band] ?: 0.toShort()) else 0.toShort()
                try {
                    eq.setBandLevel(band, targetLevel)
                } catch (e: Throwable) { }
                bandsMap[band] = currentLevels[band] ?: 0.toShort()
            }

            val bb = BassBoost(0, audioSessionId).apply {
                enabled = canEnable
                if (strengthSupported) {
                    setStrength(if (canEnable) _effectsState.value.bassBoostStrength else 0.toShort())
                }
            }
            bassBoost = bb

            try {
                val le = LoudnessEnhancer(audioSessionId).apply {
                    val gain = _effectsState.value.loudnessEnhancerGainMb
                    enabled = canEnable && gain > 0
                    if (gain > 0) setTargetGain(gain)
                }
                loudnessEnhancer = le
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            // Only mark session as active once hardware effects are successfully attached
            currentSessionId = audioSessionId

            _effectsState.value = _effectsState.value.copy(
                numberOfBands = numBands,
                bandLevels = bandsMap,
                minBandLevel = minL,
                maxBandLevel = maxL
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            // Clean up partially initialized effects and reset currentSessionId to 0 for clean retry
            release()
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean, context: Context? = null): Boolean {
        val appContext = context?.applicationContext
        val headphonesConnected = appContext?.let { isHeadphonesConnected(it) } ?: _effectsState.value.isHeadphonesConnected

        val actualEnabled = enabled && headphonesConnected

        if (actualEnabled) {
            try {
                equalizer?.enabled = true
                val numBands = equalizer?.numberOfBands ?: 0
                val savedLevels = _effectsState.value.bandLevels
                for (i in 0 until numBands) {
                    val band = i.toShort()
                    val targetLevel = savedLevels[band] ?: 0.toShort()
                    try {
                        equalizer?.setBandLevel(band, targetLevel)
                    } catch (e: Throwable) { }
                }
                bassBoost?.let {
                    it.enabled = true
                    if (it.strengthSupported) {
                        it.setStrength(_effectsState.value.bassBoostStrength)
                    }
                }
                loudnessEnhancer?.enabled = _effectsState.value.loudnessEnhancerGainMb > 0
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        } else {
            try {
                // Neutralize all hardware filters immediately
                val numBands = equalizer?.numberOfBands ?: 0
                for (i in 0 until numBands) {
                    try {
                        equalizer?.setBandLevel(i.toShort(), 0.toShort())
                    } catch (e: Throwable) { }
                }
                bassBoost?.let {
                    if (it.strengthSupported) {
                        try { it.setStrength(0.toShort()) } catch (e: Throwable) { }
                    }
                    it.enabled = false
                }
                loudnessEnhancer?.enabled = false
                equalizer?.enabled = false
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        _effectsState.value = _effectsState.value.copy(
            isEnabled = actualEnabled,
            isHeadphonesConnected = headphonesConnected
        )
        if (appContext != null) persistState(appContext)
        return actualEnabled
    }

    @Synchronized
    fun setPreset(presetName: String, context: Context? = null) {
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

        _effectsState.value = _effectsState.value.copy(
            currentPreset = presetName,
            bandLevels = bandsMap,
            bassBoostStrength = targetBassStrength
        )
        if (context != null) persistState(context)
    }

    @Synchronized
    fun setBassBoost(strength: Short, context: Context? = null) {
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

        _effectsState.value = _effectsState.value.copy(
            bassBoostStrength = clampedStrength
        )
        if (context != null) persistState(context)
    }

    @Synchronized
    fun setLoudnessEnhancerGain(gainMb: Int) {
        val clampedGain = gainMb.coerceIn(0, 1000)
        try {
            loudnessEnhancer?.let {
                it.enabled = _effectsState.value.isEnabled && clampedGain > 0
                if (clampedGain > 0) it.setTargetGain(clampedGain)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        _effectsState.value = _effectsState.value.copy(loudnessEnhancerGainMb = clampedGain)
    }

    @Synchronized
    fun setBandLevel(band: Short, level: Short, context: Context? = null) {
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
        if (context != null) persistState(context)
    }

    @Synchronized
    fun resetToFlat(context: Context? = null) {
        setPreset(AudioEffectsState.PRESET_FLAT, context)
        setBassBoost(0.toShort(), context)
        setLoudnessEnhancerGain(0)
    }

    /**
     * Fix #12: After restoring UI state from SharedPrefs, re-apply hardware DSP so the
     * equalizer hardware matches the UI state ("on" in UI = actually on in DSP).
     */
    fun restorePersistedState(context: Context) {
        try {
            val appContext = context.applicationContext
            registerAudioDeviceCallback(appContext)
            val sessionManager = SessionManager.getInstance(appContext)
            val savedEnabled = sessionManager.getEqualizerEnabled()
            val headphonesConnected = isHeadphonesConnected(appContext)
            val actualEnabled = savedEnabled && headphonesConnected
            val preset = sessionManager.getEqualizerPreset()
            val bass = sessionManager.getEqualizerBassBoost()
            val levels = sessionManager.getEqualizerBandLevels(_effectsState.value.numberOfBands.toInt())

            _effectsState.value = _effectsState.value.copy(
                isEnabled = actualEnabled,
                isHeadphonesConnected = headphonesConnected,
                currentPreset = preset,
                bassBoostStrength = bass,
                bandLevels = levels
            )

            // Fix #12: If hardware session already exists, apply effects immediately to sync DSP
            if (currentSessionId != 0 && actualEnabled) {
                setEnabled(true, appContext)
                if (bass > 0) setBassBoost(bass, appContext)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun persistState(context: Context) {
        val state = _effectsState.value
        val appContext = context.applicationContext
        persistenceJob?.cancel()
        persistenceJob = persistenceScope.launch {
            try {
                SessionManager.getInstance(appContext).saveEqualizerState(
                    enabled = state.isEnabled,
                    preset = state.currentPreset,
                    bassBoost = state.bassBoostStrength,
                    bandLevels = state.bandLevels
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Fix #11: Properly unregisters AudioDeviceCallback and BroadcastReceiver.
     * Fix #20: currentSessionId is always reset (in finally) so the next init() is never skipped.
     */
    @Synchronized
    fun release() {
        // Unregister listeners FIRST to prevent callbacks on dead hardware objects
        val am = audioManager
        try {
            audioDeviceCallback?.let { am?.unregisterAudioDeviceCallback(it) }
            audioDeviceCallback = null
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        try {
            val ctx = appContextRef
            headsetReceiver?.let { ctx?.unregisterReceiver(it) }
            headsetReceiver = null
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            equalizer = null
            bassBoost = null
            loudnessEnhancer = null
            // Fix #20: always reset so next init() is not ignored
            currentSessionId = 0
        }
    }
}
