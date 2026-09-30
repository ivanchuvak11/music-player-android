
package com.musicplayer.android.core

import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.calculateSearchRelevanceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAndSearchLogicTest {

    @Test
    fun testRelevanceScoringMatchesAllTiers() {
        val track = AudioTrack(
            id = "1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            audioUrl = "http://example.com/stream"
        )

        // Exact title match -> 1000
        assertEquals(1000, track.calculateSearchRelevanceScore("Bohemian Rhapsody"))
        // Title prefix match -> 800
        assertEquals(800, track.calculateSearchRelevanceScore("Bohemian"))
        // Title word boundary -> 600
        assertEquals(600, track.calculateSearchRelevanceScore("Rhapsody"))
        // Exact artist match -> 300
        assertEquals(300, track.calculateSearchRelevanceScore("Queen"))
        // Artist prefix match -> 200
        assertEquals(200, track.calculateSearchRelevanceScore("Que"))
        // Irrelevant query -> -1
        assertEquals(-1, track.calculateSearchRelevanceScore("Metallica"))
    }

    @Test
    fun testServerConfigDefaultBaseUrlResolution() {
        val url = com.musicplayer.android.core.network.ServerConfig.DEFAULT_BASE_URL
        assertTrue("Base URL must be non-empty", url.isNotBlank())
        assertTrue("Base URL must start with http", url.startsWith("http"))
        assertTrue("Base URL must end with slash", url.endsWith("/"))
    }

    @Test
    fun testServerConfigStreamResolution() {
        val jamendoResolved = com.musicplayer.android.core.network.ServerConfig.resolveJamendoStreamUrl(
            externalId = "track_99",
            streamUrl = "https://cdn.jamendo.com/stream/99.mp3",
            baseUrl = "http://10.0.2.2:5116/"
        )
        assertEquals("https://cdn.jamendo.com/stream/99.mp3", jamendoResolved)

        val jamendoFallback = com.musicplayer.android.core.network.ServerConfig.resolveJamendoStreamUrl(
            externalId = "track_99",
            streamUrl = null,
            baseUrl = "http://10.0.2.2:5116/"
        )
        assertEquals("http://10.0.2.2:5116/api/jamendo/tracks/track_99/stream", jamendoFallback)
    }
}

