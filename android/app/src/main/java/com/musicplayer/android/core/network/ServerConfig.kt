package com.musicplayer.android.core.network

/**
 * Single source of truth for all backend endpoints and streaming fallback configuration.
 */
object ServerConfig {
    const val DEFAULT_LOCAL_BASE_URL = "http://10.0.2.2:5116/"
    const val PRODUCTION_BASE_URL = "http://152.70.19.219/"
    const val DEFAULT_BASE_URL = PRODUCTION_BASE_URL

    /**
     * Resolves absolute stream URL for external track providers.
     */
    fun resolveJamendoStreamUrl(externalId: String, streamUrl: String?, baseUrl: String): String {
        return if (streamUrl.isAbsoluteHttpUrl()) {
            streamUrl!!
        } else {
            "${baseUrl.trimEnd('/')}/api/jamendo/tracks/$externalId/stream"
        }
    }

    fun resolveAudiusStreamUrl(externalId: String, baseUrl: String): String {
        return "${baseUrl.trimEnd('/')}/api/audius/tracks/$externalId/stream"
    }

    fun resolveSoundCloudStreamUrl(externalId: String, streamUrl: String?, baseUrl: String): String {
        return if (streamUrl.isAbsoluteHttpUrl()) {
            streamUrl!!
        } else {
            "${baseUrl.trimEnd('/')}/api/soundcloud/tracks/$externalId/stream"
        }
    }

    fun resolveYouTubeStreamUrl(externalId: String, streamUrl: String?, baseUrl: String): String {
        return if (streamUrl.isAbsoluteHttpUrl()) {
            streamUrl!!
        } else {
            "${baseUrl.trimEnd('/')}/api/youtube/tracks/$externalId/stream"
        }
    }

    private fun String?.isAbsoluteHttpUrl(): Boolean {
        return this?.startsWith("http://", ignoreCase = true) == true ||
            this.startsWith("https://", ignoreCase = true)
    }
}
