package com.musicplayer.android.core.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
 * Concrete implementation of PlayerController connecting to MusicPlayerService via MediaController.
 */
class PlayerControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : PlayerController {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentQueue: List<AudioTrack> = emptyList()
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var pendingShuffleMode: Boolean? = null
    private var pendingRepeatMode: Int? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
                pendingShuffleMode?.let { mediaController?.shuffleModeEnabled = it }
                pendingRepeatMode?.let { mode ->
                    mediaController?.repeatMode = when (mode) {
                        PlaybackState.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                        PlaybackState.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                updateState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _playbackState.value = _playbackState.value.copy(errorMessage = null)
                updateState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateState()
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                updateState()
            }

            override fun onPlayerError(error: PlaybackException) {
                val isNetworkError = error.errorCode in listOf(
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                ) || error.message?.contains("Unable to connect", ignoreCase = true) == true
                   || error.message?.contains("timeout", ignoreCase = true) == true

                val message = if (isNetworkError) {
                    "Немає підключення до мережі або сервер недоступний. Ви можете слухати збережені пісні та музику з пам'яті пристрою офлайн."
                } else {
                    "Помилка відтворення: ${error.localizedMessage ?: "невідомий формат або пошкоджений файл"}"
                }

                _playbackState.value = _playbackState.value.copy(
                    errorMessage = message,
                    isBuffering = false,
                    isPlaying = false
                )

                // If next track exists in queue, automatically retry with next item
                val controller = mediaController
                if (controller != null && controller.hasNextMediaItem()) {
                    scope.launch {
                        delay(2500)
                        if (_playbackState.value.errorMessage != null && controller.hasNextMediaItem()) {
                            controller.seekToNextMediaItem()
                            controller.play()
                        }
                    }
                }
            }
        })
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val currentPos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.coerceAtLeast(0L)
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = currentPos,
                        durationMs = dur
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun updateState() {
        val controller = mediaController ?: return
        val currentMediaItem = controller.currentMediaItem
        val currentTrack = currentMediaItem?.let { item ->
            currentQueue.find { it.id == item.mediaId }
                ?: AudioTrack.fromMediaItem(item, controller.duration.coerceAtLeast(0L))
        }

        val mappedRepeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> PlaybackState.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ALL -> PlaybackState.REPEAT_MODE_ALL
            else -> PlaybackState.REPEAT_MODE_OFF
        }

        _playbackState.value = _playbackState.value.copy(
            currentTrack = currentTrack,
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.coerceAtLeast(0L),
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            queue = currentQueue,
            hasNext = controller.hasNextMediaItem(),
            hasPrevious = controller.hasPreviousMediaItem(),
            shuffleModeEnabled = controller.shuffleModeEnabled,
            repeatMode = mappedRepeatMode,
            playbackSpeed = controller.playbackParameters.speed
        )
    }

    override fun play() {
        val controller = mediaController ?: return
        fadeJob?.cancel()
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        fadeJob = scope.launch {
            try {
                controller.volume = 0.6f
                controller.play()
                val steps = 2
                val stepDelay = 20L
                for (i in 1..steps) {
                    delay(stepDelay)
                    controller.volume = 0.6f + (0.4f * i / steps)
                }
            } catch (e: Exception) {
                controller.play()
            } finally {
                controller.volume = 1.0f
            }
        }
    }

    override fun pause() {
        val controller = mediaController ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            try {
                val steps = 2
                val stepDelay = 20L
                for (i in (steps - 1) downTo 0) {
                    controller.volume = i.toFloat() / steps
                    delay(stepDelay)
                }
                controller.pause()
            } catch (e: Exception) {
                controller.pause()
            } finally {
                controller.volume = 1.0f
            }
        }
    }

    override fun playNext() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        mediaController?.seekToNextMediaItem()
    }

    override fun playPrevious() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        mediaController?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int, autoPlay: Boolean) {
        currentQueue = tracks
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        val mediaItems = tracks.map { it.toMediaItem() }
        mediaController?.apply {
            setMediaItems(mediaItems, startIndex, 0L)
            prepare()
            if (autoPlay) {
                play()
            }
        }
        updateState()
    }

    override fun playTrack(track: AudioTrack) {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            mediaController?.seekToDefaultPosition(existingIndex)
            play()
            updateState()
        } else {
            setQueue(listOf(track), startIndex = 0, autoPlay = true)
        }
    }

    override fun setShuffleMode(enabled: Boolean) {
        val controller = mediaController
        if (controller != null) {
            controller.shuffleModeEnabled = enabled
            updateState()
        } else {
            pendingShuffleMode = enabled
            _playbackState.value = _playbackState.value.copy(shuffleModeEnabled = enabled)
        }
    }

    override fun toggleShuffle() {
        setShuffleMode(!_playbackState.value.shuffleModeEnabled)
    }

    override fun setRepeatMode(repeatMode: Int) {
        val controller = mediaController
        if (controller != null) {
            controller.repeatMode = when (repeatMode) {
                PlaybackState.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                PlaybackState.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            updateState()
        } else {
            pendingRepeatMode = repeatMode
            _playbackState.value = _playbackState.value.copy(repeatMode = repeatMode)
        }
    }

    override fun cycleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            PlaybackState.REPEAT_MODE_OFF -> PlaybackState.REPEAT_MODE_ALL
            PlaybackState.REPEAT_MODE_ALL -> PlaybackState.REPEAT_MODE_ONE
            else -> PlaybackState.REPEAT_MODE_OFF
        }
        setRepeatMode(nextMode)
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 3.0f)
        mediaController?.setPlaybackSpeed(clampedSpeed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clampedSpeed)
    }

    override fun addToQueue(track: AudioTrack) {
        currentQueue = currentQueue + track
        mediaController?.addMediaItem(track.toMediaItem())
        updateState()
    }

    override fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
            controller.removeMediaItem(index)
            updateState()
        }
    }

    override fun release() {
        fadeJob?.cancel()
        stopProgressTracker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
