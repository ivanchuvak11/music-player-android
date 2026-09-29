package com.musicplayer.android.core.audio

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages the music sleep timer, providing remaining duration state and
 * executing a callback (e.g. pause playback) upon completion.
 *
 * Fix #8: Doze-mode resilient timer — uses SystemClock.elapsedRealtime() as
 * the source of truth for remaining time instead of counting 1-second delays.
 * When Doze extends a delay(), the countdown still advances correctly on wake.
 */
object SleepTimer {

    private val timerSupervisor = kotlinx.coroutines.SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + timerSupervisor)
    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun start(durationMinutes: Int, onFinish: () -> Unit) {
        cancel()
        val totalMs = durationMinutes.toLong() * 60L * 1000L
        // Record the absolute wall-clock deadline using elapsedRealtime (not affected by clock changes)
        val endElapsedMs = SystemClock.elapsedRealtime() + totalMs

        _remainingSeconds.value = durationMinutes.toLong() * 60L
        _isActive.value = true

        timerJob = scope.launch {
            while (isActive) {
                val remainingMs = endElapsedMs - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) break
                // Emit actual remaining (Doze may have eaten several seconds)
                _remainingSeconds.value = (remainingMs / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
            _isActive.value = false
            _remainingSeconds.value = 0L
            if (isActive) onFinish()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingSeconds.value = 0L
    }

    fun shutdown() {
        cancel()
        timerSupervisor.cancel()
    }
}
