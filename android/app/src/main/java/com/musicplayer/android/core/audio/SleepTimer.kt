package com.musicplayer.android.core.audio

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
 */
object SleepTimer {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    fun start(durationMinutes: Int, onFinish: () -> Unit) {
        cancel()
        val totalSeconds = durationMinutes.toLong() * 60L
        _remainingSeconds.value = totalSeconds
        _isActive.value = true

        timerJob = scope.launch {
            var current = totalSeconds
            while (isActive && current > 0) {
                delay(1000L)
                current--
                _remainingSeconds.value = current
            }
            _isActive.value = false
            _remainingSeconds.value = 0L
            onFinish()
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _isActive.value = false
        _remainingSeconds.value = 0L
    }
}
