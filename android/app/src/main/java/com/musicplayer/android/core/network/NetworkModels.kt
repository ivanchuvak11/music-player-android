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
    val token: String,
    val username: String
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    val createdAt: String
)

@Serializable
data class PlaylistSummaryDto(
    val id: Int,
    val name: String,
    val createdAt: String,
    val trackCount: Int
)

@Serializable
data class PlaylistDetailDto(
    val id: Int,
    val name: String,
    val createdAt: String,
    val tracks: List<PlaylistTrackDto>
)

@Serializable
data class PlaylistTrackDto(
    val id: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val source: String? = null,
    val position: Int
)

@Serializable
data class CreatePlaylistRequestDto(
    val name: String
)

@Serializable
data class AddTrackToPlaylistRequestDto(
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val source: String? = null
)

@Serializable
data class FavoriteTrackDto(
    val id: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val source: String? = null,
    val createdAt: String
)

@Serializable
data class AddFavoriteTrackRequestDto(
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long? = 0L,
    val source: String? = null
)

@Serializable
data class RadioStationDto(
    val id: Int,
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val country: String? = null,
    val genre: String? = null,
    val createdAt: String
)

@Serializable
data class AddFavoriteRadioRequestDto(
    val stationId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val country: String? = null,
    val genre: String? = null
)
