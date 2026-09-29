package com.musicplayer.android.core.network

/**
 * Single source of truth for all backend endpoints and streaming fallback configuration.
 */
object ServerConfig {
    const val DEFAULT_LOCAL_BASE_URL = "http://10.0.2.2:5116/"
    const val PRODUCTION_BASE_URL = "https://musicplayer-api.example.com/" // Production placeholder

    /**
     * Resolves absolute stream URL for external track providers.
     */
    fun resolveJamendoStreamUrl(externalId: String, streamUrl: String?, baseUrl: String): String {
        return if (!streamUrl.isNullOrBlank()) {
            streamUrl
        } else {
            "${baseUrl.trimEnd('/')}/api/jamendo/tracks/$externalId/stream"
        }
    }

    fun resolveAudiusStreamUrl(externalId: String, baseUrl: String): String {
        return "${baseUrl.trimEnd('/')}/api/audius/tracks/$externalId/stream"
    }

    fun resolveSoundCloudStreamUrl(externalId: String, streamUrl: String?, baseUrl: String): String {
        return if (!streamUrl.isNullOrBlank()) {
            streamUrl
        } else {
            "${baseUrl.trimEnd('/')}/api/soundcloud/tracks/$externalId/stream"
        }
    }
}
