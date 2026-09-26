package com.musicplayer.android.core.audio

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
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
                updateState()
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

        _playbackState.value = _playbackState.value.copy(
            currentTrack = currentTrack,
            isPlaying = controller.isPlaying,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.coerceAtLeast(0L),
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            queue = currentQueue,
            hasNext = controller.hasNextMediaItem(),
            hasPrevious = controller.hasPreviousMediaItem()
        )
    }

    override fun play() {
        mediaController?.play()
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun playNext() {
        mediaController?.seekToNextMediaItem()
    }

    override fun playPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    override fun setQueue(tracks: List<AudioTrack>, startIndex: Int, autoPlay: Boolean) {
        currentQueue = tracks
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
        setQueue(listOf(track), startIndex = 0, autoPlay = true)
    }

    override fun release() {
        stopProgressTracker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
