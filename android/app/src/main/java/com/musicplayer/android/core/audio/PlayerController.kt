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
    fun setQueue(tracks: List<AudioTrack>, startIndex: Int = 0)
    fun playTrack(track: AudioTrack)
}
