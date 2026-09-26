package com.musicplayer.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface connecting Android Core to the ASP.NET Core backend.
 */
interface MusicApiService {

    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequestDto): Response<AuthResponseDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequestDto): Response<AuthResponseDto>

    // Users
    @GET("api/users/me")
    suspend fun getCurrentUser(): Response<UserDto>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(): Response<List<PlaylistSummaryDto>>

    @GET("api/playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Int): Response<PlaylistDetailDto>

    @POST("api/playlists")
    suspend fun createPlaylist(@Body request: CreatePlaylistRequestDto): Response<PlaylistSummaryDto>

    @POST("api/playlists/{playlistId}/tracks")
    suspend fun addTrackToPlaylist(
        @Path("playlistId") playlistId: Int,
        @Body request: AddTrackToPlaylistRequestDto
    ): Response<PlaylistTrackDto>

    @DELETE("api/playlists/{playlistId}/tracks/{trackId}")
    suspend fun removeTrackFromPlaylist(
        @Path("playlistId") playlistId: Int,
        @Path("trackId") trackId: Int
    ): Response<Unit>

    @DELETE("api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Int): Response<Unit>

    // Favorite Tracks
    @GET("api/favorites/tracks")
    suspend fun getFavoriteTracks(): Response<List<FavoriteTrackDto>>

    @POST("api/favorites/tracks")
    suspend fun addFavoriteTrack(@Body request: AddFavoriteTrackRequestDto): Response<FavoriteTrackDto>

    @DELETE("api/favorites/tracks/{id}")
    suspend fun removeFavoriteTrack(@Path("id") id: Int): Response<Unit>

    // Radio (Radio Browser integration)
    @GET("api/radio/search")
    suspend fun searchRadioStations(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<List<RadioStationDto>>

    @GET("api/radio/popular")
    suspend fun getPopularRadioStations(
        @Query("limit") limit: Int = 20
    ): Response<List<RadioStationDto>>

    @GET("api/radio/favorites")
    suspend fun getFavoriteRadioStations(): Response<List<RadioStationDto>>

    @POST("api/radio/favorites")
    suspend fun addFavoriteRadioStation(@Body request: AddFavoriteRadioRequestDto): Response<RadioStationDto>

    @DELETE("api/radio/favorites/{id}")
    suspend fun removeFavoriteRadioStation(@Path("id") id: Int): Response<Unit>
}
