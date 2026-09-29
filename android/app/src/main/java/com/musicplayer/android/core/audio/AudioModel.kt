package com.musicplayer.android.core.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.database.FavoriteTrackEntity
import com.musicplayer.android.core.database.LocalPlaylistTrackEntity
import com.musicplayer.android.core.database.PlayHistoryEntity
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

        fun fromJamendo(track: JamendoTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveJamendoStreamUrl(
                track.externalId,
                track.streamUrl,
                backendBaseUrl
            )
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

        fun fromAudius(track: AudiusTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveAudiusStreamUrl(
                track.externalId,
                backendBaseUrl
            )
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

        fun fromSoundCloud(track: SoundCloudTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveSoundCloudStreamUrl(
                track.externalId,
                track.streamUrl,
                backendBaseUrl
            )
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
 * Extension functions converting database entities directly to AudioTrack (Fix #23: Code Duplication elimination).
 */
fun FavoriteTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = id,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = audioUrl.startsWith("content://") || audioUrl.startsWith("file://"),
    isLiveStream = audioUrl.contains("radio")
)

fun PlayHistoryEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = trackId,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = audioUrl.startsWith("content://") || audioUrl.startsWith("file://"),
    isLiveStream = audioUrl.contains("radio")
)

fun LocalPlaylistTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = trackId,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = isLocal,
    isLiveStream = audioUrl.contains("radio")
)

fun CachedTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = id,
    title = title,
    artist = artist,
    audioUrl = localFilePath ?: originalUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = localFilePath != null,
    isLiveStream = false
)

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

/**
 * Calculates a relevance score for a track given a search query.
 * Higher score = higher priority in search results.
 * Negative score = non-relevant (does not match title or artist).
 */
fun AudioTrack.calculateSearchRelevanceScore(query: String): Int {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 0
    val t = title.trim().lowercase()
    val a = artist.trim().lowercase()

    return when {
        t == q -> 1000 // Exact title match
        t.startsWith(q) -> 800 // Title starts with query
        t.contains(" $q") || t.contains("($q") -> 600 // Title contains word boundary
        t.contains(q) -> 400 // Title contains query substring
        a == q -> 300 // Exact artist match
        a.startsWith(q) -> 200 // Artist starts with query
        a.contains(q) -> 100 // Artist contains query
        else -> -1 // Non-relevant (does not match query)
    }
}

