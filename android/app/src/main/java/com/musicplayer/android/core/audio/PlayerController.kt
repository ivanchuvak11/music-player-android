package com.musicplayer.android.core.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * High-level interface to control the audio player from ViewModels and UI.
 */
interface PlayerController {
    val playbackState: StateFlow<PlaybackState>

    fun play()
    fun pause()
    fun playNext()
    fun playPrevious()
    fun seekTo(positionMs: Long)
    fun setQueue(tracks: List<AudioTrack>, startIndex: Int = 0, autoPlay: Boolean = true)
    fun playTrack(track: AudioTrack, startPositionMs: Long = 0L)
    fun setShuffleMode(enabled: Boolean)
    fun toggleShuffle()
    fun setRepeatMode(repeatMode: Int)
    fun cycleRepeatMode()

    // M4 FIX: Playback speed control
    fun setPlaybackSpeed(speed: Float)

    // M12 FIX: Granular queue management
    fun addToQueue(track: AudioTrack)
    fun removeFromQueue(index: Int)

    fun release()
}
