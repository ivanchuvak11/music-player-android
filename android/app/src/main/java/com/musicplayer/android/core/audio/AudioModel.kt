package com.musicplayer.android.core.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * Representation of an audio track regardless of its source (Local, Radio, SoundCloud, YouTube).
 */
@Serializable
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val isLocal: Boolean = false,
    val isLiveStream: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(audioUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        fun fromMediaItem(mediaItem: MediaItem, durationMs: Long = 0L): AudioTrack {
            val metadata = mediaItem.mediaMetadata
            val uri = mediaItem.localConfiguration?.uri
            val uriString = uri?.toString().orEmpty()
            val isLocal = uri?.scheme == "content" || uri?.scheme == "file"
            return AudioTrack(
                id = mediaItem.mediaId,
                title = metadata.title?.toString() ?: "Unknown Title",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                audioUrl = uriString,
                artworkUrl = metadata.artworkUri?.toString(),
                durationMs = durationMs,
                isLocal = isLocal,
                isLiveStream = durationMs <= 0L && !isLocal
            )
        }
    }
}

/**
 * Current playback state observed by UI components.
 */
data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val queue: List<AudioTrack> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = REPEAT_MODE_OFF
) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
    }
}

