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
 *
 * Fix #3 (pending queue): User taps a track before controller is ready — the queue/play
 * request is stored and executed immediately when the controller connects.
 *
 * Fix #4 (audio pop): volume restored to 1.0f AFTER pause() call, not in finally{} which
 * fires while audio is still draining.
 *
 * Fix #5 (IndexOutOfBounds): On reconnect, currentQueue is synced from controller's actual
 * media item count so removeFromQueue() can never crash.
 *
 * Fix #6 (infinite skip loop): Consecutive error counter stops auto-skip after 3 failures.
 */
class PlayerControllerImpl(
    private val context: Context,
    externalScope: CoroutineScope? = null
) : PlayerController {

    private val controllerJob = kotlinx.coroutines.SupervisorJob()
    private val scope: CoroutineScope = externalScope ?: CoroutineScope(Dispatchers.Main.immediate + controllerJob)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentQueue: List<AudioTrack> = emptyList()
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var retryJob: Job? = null
    private var pendingShuffleMode: Boolean? = null
    private var pendingRepeatMode: Int? = null
    private var pendingPlaybackSpeed: Float? = null
    private var pendingSeekPositionMs: Long? = null

    // Fix #3: pending queue/play stored when controller not yet connected
    private data class PendingQueue(val tracks: List<AudioTrack>, val startIndex: Int, val autoPlay: Boolean, val startPositionMs: Long = 0L)
    private var pendingQueue: PendingQueue? = null

    // Fix #6: consecutive error counter to break infinite skip loops
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicPlayerService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                val controller = mediaController ?: return@addListener

                // Fix #5: Sync local queue from service's actual item count on reconnect
                if (controller.mediaItemCount > 0 && currentQueue.isEmpty()) {
                    val restored = mutableListOf<AudioTrack>()
                    for (i in 0 until controller.mediaItemCount) {
                        val item = controller.getMediaItemAt(i)
                        val dur = if (i == controller.currentMediaItemIndex)
                            controller.duration.coerceAtLeast(0L) else 0L
                        restored.add(AudioTrack.fromMediaItem(item, dur))
                    }
                    currentQueue = restored
                }

                // Apply pending shuffle/repeat preferences
                pendingShuffleMode?.let { controller.shuffleModeEnabled = it }
                pendingRepeatMode?.let { mode ->
                    controller.repeatMode = when (mode) {
                        PlaybackState.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                        PlaybackState.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                pendingPlaybackSpeed?.let { controller.setPlaybackSpeed(it) }
                pendingShuffleMode = null
                pendingRepeatMode = null
                pendingPlaybackSpeed = null

                setupPlayerListener()
                updateState()

                // Fix #3: Replay pending setQueue call now that controller is ready
                pendingQueue?.let { pq ->
                    pendingQueue = null
                    if (pq.startPositionMs > 0L) {
                        pendingSeekPositionMs = pq.startPositionMs
                    }
                    setQueue(pq.tracks, pq.startIndex, pq.autoPlay)
                }
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
                // Fix #6: Reset error counter and cancel any pending auto-skip retry on transition
                consecutiveErrors = 0
                retryJob?.cancel()
                retryJob = null
                _playbackState.value = _playbackState.value.copy(errorMessage = null)
                updateState()

                // Prefetch upcoming tracks in queue for instantaneous next track playback
                val controller = mediaController
                if (controller != null && currentQueue.isNotEmpty()) {
                    val currentIndex = controller.currentMediaItemIndex
                    val upcoming = currentQueue.drop(currentIndex + 1).take(3)
                    val upcomingIds = upcoming.map { it.id }.filter { it.startsWith("youtube_") || it.contains("youtu") }
                    YouTubeExtractorService.prefetchNextTracks(upcomingIds)
                }
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

                // Fix #6: Auto-skip to next, but stop after maxConsecutiveErrors to prevent infinite loop
                val controller = mediaController
                if (controller != null && controller.hasNextMediaItem()) {
                    consecutiveErrors++
                    if (consecutiveErrors <= maxConsecutiveErrors) {
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(2500)
                            val activeController = mediaController
                            if (activeController != null && _playbackState.value.errorMessage != null && activeController.hasNextMediaItem()) {
                                activeController.seekToNextMediaItem()
                                activeController.play()
                            }
                        }
                    } else {
                        // Exhausted retries — clear error and stop attempting auto-skip
                        consecutiveErrors = 0
                        retryJob?.cancel()
                        retryJob = null
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
                    val currentState = _playbackState.value
                    if (kotlin.math.abs(currentState.currentPositionMs - currentPos) >= 200L || currentState.durationMs != dur) {
                        _playbackState.value = currentState.copy(
                            currentPositionMs = currentPos,
                            durationMs = dur
                        )
                    }
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
        retryJob?.cancel()
        retryJob = null
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
                controller.volume = 1.0f
            } catch (e: Exception) {
                controller.play()
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
                // Fix #4: Reset volume AFTER pause so there is no audio left to pop
                controller.pause()
                controller.volume = 1.0f
            } catch (e: Exception) {
                controller.pause()
                controller.volume = 1.0f
            }
        }
    }

    override fun playNext() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        consecutiveErrors = 0
        retryJob?.cancel()
        retryJob = null
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0) {
            controller.seekToNextMediaItem()
        }
    }

    override fun playPrevious() {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        consecutiveErrors = 0
        retryJob?.cancel()
        retryJob = null
        val controller = mediaController
        if (controller != null && controller.mediaItemCount > 0) {
            controller.seekToPreviousMediaItem()
        }
    }

    override fun seekTo(positionMs: Long) {
        val controller = mediaController
        if (controller != null) {
            controller.seekTo(positionMs)
            pendingSeekPositionMs = null
        } else {
            pendingSeekPositionMs = positionMs
        }
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int, autoPlay: Boolean) {
        currentQueue = tracks
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        if (tracks.isEmpty()) {
            pendingQueue = null
            mediaController?.clearMediaItems()
            updateState()
            return
        }

        val safeStartIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mediaItems = tracks.map { it.toMediaItem() }
        val controller = mediaController
        val startPos = pendingSeekPositionMs ?: 0L
        if (controller == null) {
            // Fix #3: Store for replay when controller becomes available
            pendingQueue = PendingQueue(tracks, safeStartIndex, autoPlay, startPos)
            return
        }
        pendingSeekPositionMs = null
        controller.setMediaItems(mediaItems, safeStartIndex, startPos)
        controller.prepare()
        if (autoPlay) {
            controller.play()
        }
        updateState()

        val upcoming = tracks.drop(safeStartIndex + 1).take(3)
        val upcomingIds = upcoming.map { it.id }.filter { it.startsWith("youtube_") || it.contains("youtu") }
        YouTubeExtractorService.prefetchNextTracks(upcomingIds)
    }

    override fun playTrack(track: AudioTrack, startPositionMs: Long) {
        _playbackState.value = _playbackState.value.copy(errorMessage = null)
        if (startPositionMs > 0L) {
            pendingSeekPositionMs = startPositionMs
        }
        val existingIndex = currentQueue.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            val controller = mediaController
            val pos = pendingSeekPositionMs ?: 0L
            pendingSeekPositionMs = null
            if (controller == null) {
                // The queue is known locally, but the MediaController is still connecting.
                pendingQueue = PendingQueue(currentQueue, existingIndex, autoPlay = true, startPositionMs = pos)
            } else {
                if (pos > 0L) {
                    controller.seekTo(existingIndex, pos)
                } else {
                    controller.seekToDefaultPosition(existingIndex)
                }
                play()
                updateState()
            }
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
        val controller = mediaController
        if (controller != null) {
            controller.setPlaybackSpeed(clampedSpeed)
            pendingPlaybackSpeed = null
        } else {
            pendingPlaybackSpeed = clampedSpeed
        }
        _playbackState.value = _playbackState.value.copy(playbackSpeed = clampedSpeed)
    }

    override fun addToQueue(track: AudioTrack) {
        currentQueue = currentQueue + track
        val controller = mediaController
        if (controller != null) {
            controller.addMediaItem(track.toMediaItem())
            updateState()
            if (track.id.startsWith("youtube_") || track.id.contains("youtu")) {
                YouTubeExtractorService.prefetchNextTracks(listOf(track.id))
            }
        } else {
            val currentPq = pendingQueue
            pendingQueue = if (currentPq != null) {
                currentPq.copy(tracks = currentPq.tracks + track)
            } else {
                PendingQueue(listOf(track), startIndex = 0, autoPlay = false)
            }
        }
    }

    override fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        // Fix #5: Guard against stale queue mismatch that caused IndexOutOfBoundsException
        val safeQueueSize = minOf(currentQueue.size, controller.mediaItemCount)
        if (index in 0 until safeQueueSize) {
            currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
            controller.removeMediaItem(index)
            updateState()
        }
    }

    override fun release() {
        fadeJob?.cancel()
        retryJob?.cancel()
        retryJob = null
        stopProgressTracker()
        controllerJob.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        pendingQueue = null
        pendingSeekPositionMs = null
        pendingPlaybackSpeed = null
    }
}
