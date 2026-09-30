package com.musicplayer.android.core

import com.musicplayer.android.core.network.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun testDefaultBaseUrlFormat() {
        assertTrue(ServerConfig.DEFAULT_LOCAL_BASE_URL.startsWith("http"))
        assertTrue(ServerConfig.DEFAULT_LOCAL_BASE_URL.endsWith("/"))
    }

    @Test
    fun testResolveJamendoStreamUrlWithDirectUrl() {
        val direct = "https://prod-1.storage.jamendo.com/stream/123.mp3"
        val resolved = ServerConfig.resolveJamendoStreamUrl("123", direct, "http://10.0.2.2:5116/")
        assertEquals(direct, resolved)
    }

    @Test
    fun testResolveJamendoStreamUrlWithFallback() {
        val resolved = ServerConfig.resolveJamendoStreamUrl("123", null, "http://10.0.2.2:5116/")
        assertEquals("http://10.0.2.2:5116/api/jamendo/tracks/123/stream", resolved)
    }

    @Test
    fun testResolveAudiusStreamUrl() {
        val resolved = ServerConfig.resolveAudiusStreamUrl("aud_456", "http://192.168.1.50:5116/")
        assertEquals("http://192.168.1.50:5116/api/audius/tracks/aud_456/stream", resolved)
    }

    @Test
    fun testRelativeProviderStreamUrlsUseBackendBaseUrl() {
        val baseUrl = "http://152.70.19.219/"

        assertEquals(
            "http://152.70.19.219/api/soundcloud/tracks/123/stream",
            ServerConfig.resolveSoundCloudStreamUrl("123", "/api/soundcloud/tracks/123/stream", baseUrl)
        )
        assertEquals(
            "http://152.70.19.219/api/youtube/tracks/abc/stream",
            ServerConfig.resolveYouTubeStreamUrl("abc", "/api/youtube/tracks/abc/stream", baseUrl)
        )
    }
}
