package com.musicplayer.android.core.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.JamendoTrackDto
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.network.SoundCloudTrackDto
import kotlinx.serialization.Serializable

/**
 * Representation of an audio track regardless of its source (Local, Radio, Jamendo, Audius, SoundCloud).
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
            val isLive = mediaItem.mediaId.startsWith("radio_") || (durationMs <= 0L && !isLocal && uriString.contains("radio"))
            return AudioTrack(
                id = mediaItem.mediaId,
                title = metadata.title?.toString() ?: "Unknown Title",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                audioUrl = uriString,
                artworkUrl = metadata.artworkUri?.toString(),
                durationMs = durationMs,
                isLocal = isLocal,
                isLiveStream = isLive
            )
        }

        fun fromJamendo(track: JamendoTrackDto, backendBaseUrl: String = "http://10.0.2.2:5116/"): AudioTrack {
            val streamUri = if (!track.streamUrl.isNullOrBlank()) {
                track.streamUrl
            } else {
                "${backendBaseUrl.trimEnd('/')}/api/jamendo/tracks/${track.externalId}/stream"
            }
            return AudioTrack(
                id = "jamendo_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }

        fun fromRadio(station: RadioStationDto): AudioTrack {
            return AudioTrack(
                id = "radio_${station.stationId}",
                title = station.name,
                artist = station.genre?.takeIf { it.isNotBlank() } ?: station.country ?: "Radio",
                audioUrl = station.streamUrl,
                artworkUrl = station.logoUrl,
                durationMs = 0L,
                isLocal = false,
                isLiveStream = true
            )
        }

        fun fromAudius(track: AudiusTrackDto, backendBaseUrl: String = "http://10.0.2.2:5116/"): AudioTrack {
            val streamUri = "${backendBaseUrl.trimEnd('/')}/api/audius/tracks/${track.externalId}/stream"
            return AudioTrack(
                id = "audius_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }

        fun fromSoundCloud(track: SoundCloudTrackDto, backendBaseUrl: String = "http://10.0.2.2:5116/"): AudioTrack {
            val streamUri = if (!track.streamUrl.isNullOrBlank()) {
                track.streamUrl
            } else {
                "${backendBaseUrl.trimEnd('/')}/api/soundcloud/tracks/${track.externalId}/stream"
            }
            return AudioTrack(
                id = "soundcloud_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
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
    val repeatMode: Int = REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
    }
}
