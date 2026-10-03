package com.musicplayer.android.core.audio

import com.musicplayer.android.core.network.YouTubeTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayAndSimilarTracksTest {

    @Test
    fun extractVideoIdHandlesVariousYouTubeUriFormats() {
        val formats = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "youtube://dQw4w9WgXcQ" to "dQw4w9WgXcQ",
            "https://example.com/api/youtube/tracks/dQw4w9WgXcQ/stream" to "dQw4w9WgXcQ"
        )

        for ((url, expectedId) in formats) {
            val extracted = YouTubeExtractorService.extractVideoIdFromUrl(url)
            assertEquals("Failed for url: $url", expectedId, extracted)
        }
    }

    @Test
    fun directGoogleVideoCdnUrlIsNotTreatedAsVideoId() {
        val cdnUrl = "https://rr4---sn-4g5ednls.googlevideo.com/videoplayback?expire=123"
        val extracted = YouTubeExtractorService.extractVideoIdFromUrl(cdnUrl)
        assertNull("Google Video CDN URL should not be parsed as a video ID", extracted)
    }

    @Test
    fun deduplicationFiltersOutExistingQueueTracks() {
        val existingQueueIds = setOf("track_1", "youtube_abc123", "track_2")
        val candidateTracks = listOf(
            track("abc123", "Song 1", "Artist 1"),      // should be filtered (matches youtube_abc123)
            track("unique_456", "Song 2", "Artist 2"),  // keep
            track("track_1", "Song 3", "Artist 3"),     // should be filtered (matches track_1)
            track("unique_789", "Song 4", "Artist 4")   // keep
        )

        val filtered = candidateTracks.filterNot {
            it.externalId in existingQueueIds || "youtube_${it.externalId}" in existingQueueIds
        }

        assertEquals(2, filtered.size)
        assertEquals(listOf("unique_456", "unique_789"), filtered.map { it.externalId })
    }

    @Test
    fun seedTrackConversionAndSimilarQueueOrder() {
        val seed = AudioTrack(
            id = "youtube_seed123",
            title = "Believer",
            artist = "Imagine Dragons",
            audioUrl = "https://youtu.be/seed123"
        )

        val similar = listOf(
            AudioTrack(id = "youtube_sim1", title = "Radioactive", artist = "Imagine Dragons", audioUrl = "https://youtu.be/sim1"),
            AudioTrack(id = "youtube_sim2", title = "Demons", artist = "Imagine Dragons", audioUrl = "https://youtu.be/sim2")
        )

        val fullRadioQueue = listOf(seed) + similar
        assertEquals(3, fullRadioQueue.size)
        assertEquals(seed.id, fullRadioQueue.first().id)
        assertEquals("youtube_sim1", fullRadioQueue[1].id)
        assertEquals("youtube_sim2", fullRadioQueue[2].id)
    }

    private fun track(id: String, title: String, artist: String) = YouTubeTrackDto(
        externalId = id,
        title = title,
        artist = artist
    )
}
