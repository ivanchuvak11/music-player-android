package com.musicplayer.android.core.network

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Token persistence bridge used by [TokenRefreshAuthenticator] so OkHttp authentication
 * can be unit tested independently of Android EncryptedSharedPreferences.
 */
interface AuthTokenStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String?)
    fun onRefreshRejected()
}

/**
 * Thread-safe OkHttp Authenticator implementing transparent JWT refresh via `POST /api/auth/refresh`.
 *
 * Guarantees:
 * - Never intercepts auth endpoints (`/api/auth/login`, `register`, `refresh`, `revoke`)
 * - Prevents infinite retry loops when a refreshed token is still rejected
 * - Coalesces concurrent 401 responses under one lock so refresh token rotation runs only once
 * - Preserves offline session on transient IO errors; wipes session only when backend rejects refresh
 */
class TokenRefreshAuthenticator(
    private val tokenStore: AuthTokenStore,
    private val baseUrlProvider: () -> String? = { null },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    },
    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : Authenticator {

    private val refreshLock = Any()
    private val lastRejectedNotifyMs = AtomicLong(0L)

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath.lowercase()
        if (isAuthEndpoint(path)) {
            return null
        }

        // Already retried after refresh and still got 401 -> stop loop and expire session
        if (responseCount(response) >= 2) {
            notifySessionRejectedOnce()
            return null
        }

        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        synchronized(refreshLock) {
            val latestToken = tokenStore.getAccessToken()
            // Another concurrent request already rotated the access token -> reuse it without calling /refresh again
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestToken")
                    .build()
            }

            val refreshToken = tokenStore.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                if (!failedToken.isNullOrBlank() || !latestToken.isNullOrBlank()) {
                    notifySessionRejectedOnce()
                }
                return null
            }

            return executeRefreshAndRetry(response.request, refreshToken)
        }
    }

    private fun executeRefreshAndRetry(originalRequest: Request, refreshToken: String): Request? {
        val refreshUrl = resolveRefreshUrl(originalRequest.url, baseUrlProvider())
        val payload = json.encodeToString(
            RefreshTokenRequestDto.serializer(),
            RefreshTokenRequestDto(refreshToken = refreshToken)
        )
        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            refreshClient.newCall(refreshRequest).execute().use { refreshResp ->
                if (!refreshResp.isSuccessful) {
                    if (refreshResp.code == 400 || refreshResp.code == 401 || refreshResp.code == 403) {
                        notifySessionRejectedOnce()
                    }
                    return null
                }

                val bodyString = refreshResp.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    notifySessionRejectedOnce()
                    return null
                }

                val authDto = json.decodeFromString(AuthResponseDto.serializer(), bodyString)
                val newAccessToken = authDto.resolvedAccessToken()
                if (newAccessToken.isNullOrBlank()) {
                    notifySessionRejectedOnce()
                    return null
                }

                tokenStore.saveTokens(
                    accessToken = newAccessToken,
                    refreshToken = authDto.refreshToken?.takeIf { it.isNotBlank() } ?: refreshToken
                )

                originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            }
        } catch (_: IOException) {
            // Transient network failure: do not wipe local session offline
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun notifySessionRejectedOnce() {
        val now = System.currentTimeMillis()
        val prev = lastRejectedNotifyMs.get()
        if (now - prev >= 2_000L && lastRejectedNotifyMs.compareAndSet(prev, now)) {
            tokenStore.onRefreshRejected()
        }
    }

    private fun isAuthEndpoint(path: String): Boolean {
        return path.endsWith("/api/auth/login") ||
            path.endsWith("/api/auth/register") ||
            path.endsWith("/api/auth/refresh") ||
            path.endsWith("/api/auth/revoke")
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun resolveRefreshUrl(requestUrl: HttpUrl, configuredBaseUrl: String?): HttpUrl {
        val configured = configuredBaseUrl?.trim()?.toHttpUrlOrNull()
        if (configured != null && configured.host == requestUrl.host && configured.port == requestUrl.port) {
            val baseBuilder = configured.newBuilder().query(null).fragment(null)
            return baseBuilder.addPathSegments("api/auth/refresh").build()
        }
        return requestUrl.newBuilder()
            .encodedPath("/api/auth/refresh")
            .query(null)
            .fragment(null)
            .build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
