package com.musicplayer.android.core.network

import com.musicplayer.android.core.audio.AudioTrack

/**
 * Validated backend source/externalId reference accepted by FavoritesController & PlaylistsController.
 * Backend strictly accepts only: "youtube", "soundcloud", "audius", "jamendo".
 */
data class BackendTrackRef(
    val source: String,
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long
)

object TrackSourceResolver {
    private val ALLOWED_SOURCES = setOf("youtube", "soundcloud", "audius", "jamendo")

    /**
     * Resolves backend (source, externalId) from a track ID or returns null if the track is
     * local-only, radio stream, or lacks a valid external ID.
     */
    fun resolveFromId(
        trackId: String,
        isLocal: Boolean = false,
        isLiveStream: Boolean = false,
        explicitSource: String? = null
    ): Pair<String, String>? {
        if (isLocal || isLiveStream) return null
        val trimmedId = trackId.trim()
        if (trimmedId.isBlank()) return null

        val prefix = trimmedId.substringBefore('_', "").lowercase()
        if (prefix == "local" || prefix == "radio") return null

        val source = when {
            prefix in ALLOWED_SOURCES -> prefix
            !explicitSource.isNullOrBlank() && explicitSource.trim().lowercase() in ALLOWED_SOURCES ->
                explicitSource.trim().lowercase()
            else -> return null
        }

        val externalId = if (trimmedId.contains('_')) {
            trimmedId.substringAfter('_').trim()
        } else {
            trimmedId
        }
        if (externalId.isBlank()) return null

        return source to externalId.take(256)
    }

    /**
     * Converts an [AudioTrack] into a [BackendTrackRef] ready for `/api/favorites/tracks` or
     * `/api/playlists/{id}/tracks`, sanitizing fields to satisfy server DataAnnotations (`[Url]`, lengths).
     */
    fun resolveTrackRef(track: AudioTrack, explicitSource: String? = null): BackendTrackRef? {
        val (source, externalId) = resolveFromId(
            trackId = track.id,
            isLocal = track.isLocal,
            isLiveStream = track.isLiveStream,
            explicitSource = explicitSource
        ) ?: return null

        val cleanTitle = track.title.trim().ifBlank { "Untitled" }.take(500)
        val cleanArtist = track.artist.trim().take(300)
        val cleanArtwork = sanitizeHttpUrl(track.artworkUrl)

        return BackendTrackRef(
            source = source,
            externalId = externalId,
            title = cleanTitle,
            artist = cleanArtist,
            artworkUrl = cleanArtwork,
            durationMs = track.durationMs.coerceAtLeast(0L)
        )
    }

    fun buildAudioTrackFromCloud(
        source: String?,
        externalId: String?,
        rawTrackId: String?,
        title: String,
        artist: String,
        artworkUrl: String?,
        rawAudioUrl: String?,
        durationMs: Long?,
        baseUrl: String
    ): AudioTrack? {
        val normalizedSource = source?.trim()?.lowercase()?.takeIf { it in ALLOWED_SOURCES }
        val cleanExternalId = externalId?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedPair = if (normalizedSource != null && cleanExternalId != null) {
            normalizedSource to cleanExternalId.take(256)
        } else {
            resolveFromId(rawTrackId.orEmpty(), explicitSource = normalizedSource)
        } ?: return null

        val (resolvedSource, resolvedExternalId) = resolvedPair
        val streamUrl = when (resolvedSource) {
            "youtube" -> ServerConfig.resolveYouTubeStreamUrl(resolvedExternalId, rawAudioUrl, baseUrl)
            "soundcloud" -> ServerConfig.resolveSoundCloudStreamUrl(resolvedExternalId, rawAudioUrl, baseUrl)
            "audius" -> ServerConfig.resolveAudiusStreamUrl(resolvedExternalId, baseUrl)
            "jamendo" -> ServerConfig.resolveJamendoStreamUrl(resolvedExternalId, rawAudioUrl, baseUrl)
            else -> return null
        }

        return AudioTrack(
            id = "${resolvedSource}_${resolvedExternalId}",
            title = title.trim().ifBlank { "Untitled" },
            artist = artist.trim(),
            audioUrl = streamUrl,
            artworkUrl = sanitizeHttpUrl(artworkUrl),
            durationMs = (durationMs ?: 0L).coerceAtLeast(0L),
            isLocal = false,
            isLiveStream = false
        )
    }

    fun sanitizeHttpUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        val isHttp = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        return if (isHttp && trimmed.length <= 2048) trimmed else null
    }
}
