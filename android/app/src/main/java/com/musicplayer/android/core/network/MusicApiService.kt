package com.musicplayer.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val username: String? = null
)

interface MusicApiService {
    // Endpoints corresponding to backend (AuthController, etc.)
}
