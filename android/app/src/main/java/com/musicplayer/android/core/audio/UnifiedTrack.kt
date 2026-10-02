package com.musicplayer.android.core.audio

import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.JamendoTrackDto
import com.musicplayer.android.core.network.ServerConfig
import com.musicplayer.android.core.network.SoundCloudTrackDto

/**
 * Unified track model as per ТЗ Section 2.
 * Single model representing tracks from all sources: soundcloud, audius, jamendo, local.
 *
 * The `id` follows the pattern: "{source}_{externalId}" to ensure globally unique IDs.
 * Duplicates are detected via (source + externalId) pair.
 */
data class UnifiedTrack(
    val source: TrackSource,
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val streamUrl: String? = null,   // Direct/cached stream URL from backend response
    val album: String? = null
) {
    /** Globally unique composite ID: "{source}_{externalId}" */
    val id: String get() = "${source.value}_${externalId}"

    /**
     * Converts to AudioTrack for the existing PlayerController.
     * Stream URL is resolved through the correct backend endpoint per source (ТЗ Section 4).
     */
    fun toAudioTrack(backendBaseUrl: String = ServerConfig.DEFAULT_BASE_URL): AudioTrack {
        val resolvedStreamUrl = resolveStreamUrl(backendBaseUrl)
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            audioUrl = resolvedStreamUrl,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            isLocal = source == TrackSource.LOCAL,
            isLiveStream = false
        )
    }

    /**
     * Resolves stream URL based on source per ТЗ Section 4:
     * - SoundCloud: /api/soundcloud/tracks/{id}/stream
     * - Audius:     /api/audius/tracks/{id}/stream
     * - Jamendo:    /api/jamendo/tracks/{id}/stream (or direct streamUrl if available)
     * - local:      streamUrl (file path / content URI)
     */
    fun resolveStreamUrl(backendBaseUrl: String = ServerConfig.DEFAULT_BASE_URL): String {
        if (!streamUrl.isNullOrBlank() && source == TrackSource.LOCAL) return streamUrl
        val base = backendBaseUrl.trimEnd('/')
        return when (source) {
            TrackSource.SOUNDCLOUD -> "$base/api/soundcloud/tracks/$externalId/stream"
            TrackSource.AUDIUS     -> "$base/api/audius/tracks/$externalId/stream"
            TrackSource.JAMENDO    -> {
                // Use direct streamUrl from Jamendo if available (CDN direct link),
                // otherwise route through backend proxy
                if (!streamUrl.isNullOrBlank()) streamUrl
                else "$base/api/jamendo/tracks/$externalId/stream"
            }
            TrackSource.LOCAL      -> streamUrl.orEmpty()
            TrackSource.YOUTUBE    -> {
                if (!streamUrl.isNullOrBlank() && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) && !streamUrl.contains("/api/youtube/tracks/")) {
                    streamUrl
                } else {
                    "$base/api/youtube/tracks/$externalId/stream"
                }
            }
        }
    }

    companion object {
        /** Construct from YouTubeTrackDto */
        fun fromYouTube(dto: com.musicplayer.android.core.network.YouTubeTrackDto): UnifiedTrack = UnifiedTrack(
            source = TrackSource.YOUTUBE,
            externalId = dto.externalId,
            title = dto.title,
            artist = dto.artist,
            artworkUrl = dto.artworkUrl,
            durationMs = dto.durationMs ?: 0L,
            streamUrl = dto.streamUrl,
            album = null
        )

        /** Construct from SoundCloudTrackDto */
        fun fromSoundCloud(dto: SoundCloudTrackDto): UnifiedTrack = UnifiedTrack(
            source = TrackSource.SOUNDCLOUD,
            externalId = dto.externalId,
            title = dto.title,
            artist = dto.artist,
            artworkUrl = dto.artworkUrl,
            durationMs = dto.durationMs ?: 0L,
            streamUrl = dto.streamUrl,
            album = null
        )

        /** Construct from AudiusTrackDto */
        fun fromAudius(dto: AudiusTrackDto): UnifiedTrack = UnifiedTrack(
            source = TrackSource.AUDIUS,
            externalId = dto.externalId,
            title = dto.title,
            artist = dto.artist,
            artworkUrl = dto.artworkUrl,
            durationMs = dto.durationMs ?: 0L,
            streamUrl = null,
            album = null
        )

        /** Construct from JamendoTrackDto */
        fun fromJamendo(dto: JamendoTrackDto): UnifiedTrack = UnifiedTrack(
            source = TrackSource.JAMENDO,
            externalId = dto.externalId,
            title = dto.title,
            artist = dto.artist,
            artworkUrl = dto.artworkUrl,
            durationMs = dto.durationMs ?: 0L,
            streamUrl = dto.streamUrl,
            album = dto.album
        )

        /** Construct from local AudioTrack */
        fun fromLocalAudioTrack(track: AudioTrack): UnifiedTrack = UnifiedTrack(
            source = TrackSource.LOCAL,
            externalId = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.audioUrl,
            album = null
        )
    }
}

/**
 * Enum for the supported track sources.
 */
enum class TrackSource(val value: String) {
    SOUNDCLOUD("soundcloud"),
    AUDIUS("audius"),
    JAMENDO("jamendo"),
    LOCAL("local"),
    YOUTUBE("youtube");

    companion object {
        fun fromString(value: String?): TrackSource = when (value?.lowercase()) {
            "soundcloud" -> SOUNDCLOUD
            "audius"     -> AUDIUS
            "jamendo"    -> JAMENDO
            "local"      -> LOCAL
            "youtube"    -> YOUTUBE
            else         -> JAMENDO // safe default
        }
    }
}
