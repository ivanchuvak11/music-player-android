package com.musicplayer.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponseDto(
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val userId: Int? = null,
    val username: String? = null,
    val email: String? = null
) {
    fun resolvedAccessToken(): String? =
        accessToken?.takeIf { it.isNotBlank() } ?: token?.takeIf { it.isNotBlank() }
}

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String
)

@Serializable
data class RevokeResponseDto(
    val message: String? = null
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    val createdAt: String? = null,
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null
) {
    fun resolvedAccessToken(): String? =
        accessToken?.takeIf { it.isNotBlank() } ?: token?.takeIf { it.isNotBlank() }
}

@Serializable
data class PlaylistSummaryDto(
    val id: Int,
    val name: String,
    val createdAt: String? = null,
    val trackCount: Int = 0
)

@Serializable
data class PlaylistDetailDto(
    val id: Int,
    val name: String,
    val createdAt: String? = null,
    val tracks: List<PlaylistTrackDto> = emptyList()
)

@Serializable
data class PlaylistTrackDto(
    val id: Int,
    val source: String? = null,
    val externalId: String? = null,
    val trackId: String? = null,
    val title: String,
    val artist: String,
    val audioUrl: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val position: Int? = 0,
    val addedAt: String? = null
)

@Serializable
data class CreatePlaylistRequestDto(
    val name: String
)

@Serializable
data class AddTrackToPlaylistRequestDto(
    val source: String = "jamendo",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val trackId: String? = null,
    val audioUrl: String? = null
)

@Serializable
data class FavoriteTrackDto(
    val id: Int,
    val source: String? = null,
    val externalId: String? = null,
    val trackId: String? = null,
    val title: String,
    val artist: String,
    val audioUrl: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val createdAt: String? = null
)

@Serializable
data class AddFavoriteTrackRequestDto(
    val source: String = "jamendo",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val trackId: String? = null,
    val audioUrl: String? = null
)

@Serializable
data class RadioStationDto(
    val id: Int? = null,
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val genre: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val createdAt: String? = null
)

@Serializable
data class AddFavoriteRadioRequestDto(
    val stationId: String,
    val name: String? = null,
    val streamUrl: String? = null,
    val logoUrl: String? = null,
    val country: String? = null,
    val genre: String? = null
)

@Serializable
data class AudiusTrackDto(
    val source: String = "audius",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L
)

@Serializable
data class JamendoTrackDto(
    val source: String = "jamendo",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val album: String? = null,
    val licenseUrl: String? = null,
    val jamendoUrl: String? = null,
    val streamUrl: String? = null
)

@Serializable
data class SoundCloudTrackDto(
    val source: String = "soundcloud",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val genre: String? = null,
    val soundCloudUrl: String? = null,
    val streamUrl: String? = null
)

@Serializable
data class YouTubeTrackDto(
    val source: String = "youtube",
    val externalId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val youTubeUrl: String? = null,
    val streamUrl: String? = null
)

// ─── Lab 5: Stats & Analytics DTOs ───────────────────────────────────────────

@Serializable
data class TopArtistDto(
    val artist: String = "",
    val trackCount: Int = 0
)

@Serializable
data class SourceStatDto(
    val source: String = "",
    val count: Int = 0,
    val percentage: Double = 0.0
)

@Serializable
data class RecentActivityItemDto(
    val activityType: String = "",
    val title: String = "",
    val artistOrDetails: String = "",
    val source: String = "",
    val timestamp: String = ""
)

@Serializable
data class UserStatsSummaryDto(
    val period: String = "",
    val startDateUtc: String? = null,
    val endDateUtc: String = "",
    val playlistsCreatedCount: Int = 0,
    val tracksAddedCount: Int = 0,
    val favoritesAddedCount: Int = 0,
    val radioFavoritesAddedCount: Int = 0,
    val totalSavedItemsCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalDurationMinutes: Double = 0.0,
    val topArtists: List<TopArtistDto> = emptyList(),
    val sourceBreakdown: List<SourceStatDto> = emptyList(),
    val recentActivity: List<RecentActivityItemDto> = emptyList()
)

@Serializable
data class SystemReportDto(
    val period: String = "",
    val startDateUtc: String? = null,
    val endDateUtc: String = "",
    val totalUsers: Int = 0,
    val newUsersInPeriod: Int = 0,
    val totalPlaylists: Int = 0,
    val newPlaylistsInPeriod: Int = 0,
    val totalPlaylistTracks: Int = 0,
    val playlistTracksAddedInPeriod: Int = 0,
    val totalFavoriteTracks: Int = 0,
    val favoriteTracksAddedInPeriod: Int = 0,
    val totalFavoriteRadioStations: Int = 0,
    val platformTopArtists: List<TopArtistDto> = emptyList(),
    val platformSourceDistribution: List<SourceStatDto> = emptyList()
)

@Serializable
data class PublicOverviewDto(
    val status: String = "Online",
    val serverTimeUtc: String = "",
    val supportedAudioSourcesCount: Int = 4,
    val supportedSources: List<String> = emptyList(),
    val totalPublicPlaylistsCount: Int = 0,
    val totalPublicTracksCount: Int = 0
)
