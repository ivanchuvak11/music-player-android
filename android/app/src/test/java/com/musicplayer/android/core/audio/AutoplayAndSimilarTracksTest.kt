package com.musicplayer.android.core.audio

import com.musicplayer.android.core.network.YouTubeTrackDto
import kotlinx.coroutines.runBlocking
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

    @Test
    fun extractCleanMetadataStripsVideoNoiseAndFeatures() {
        val dirtyTrack1 = AudioTrack(
            id = "youtube_test1",
            title = "Hello (Official Music Video)",
            artist = "Adele - Topic",
            audioUrl = "https://youtube.com/watch?v=test1"
        )
        val (artist1, title1) = AutoplayService.extractCleanMetadata(dirtyTrack1)
        assertEquals("Adele", artist1)
        assertEquals("Hello", title1)

        val dirtyTrack2 = AudioTrack(
            id = "youtube_test2",
            title = "Blinding Lights [4K Visualizer] feat. Rosalía",
            artist = "The Weeknd",
            audioUrl = "https://youtube.com/watch?v=test2"
        )
        val (artist2, title2) = AutoplayService.extractCleanMetadata(dirtyTrack2)
        assertEquals("The Weeknd", artist2)
        assertEquals("Blinding Lights", title2)
    }

    @Test
    fun resolveDirectStreamUrlHandlesAllSources() = runBlocking {
        // 1. Direct audio track
        val directTrack = AudioTrack(
            id = "local_1",
            title = "Song",
            artist = "Artist",
            audioUrl = "https://example.com/audio.mp3"
        )
        val resolvedDirect = TrackDownloadManager.resolveDirectStreamUrl(directTrack)
        assertEquals("https://example.com/audio.mp3", resolvedDirect)

        // 2. SoundCloud track
        val scTrack = AudioTrack(
            id = "soundcloud_998877",
            title = "SC Song",
            artist = "SC Artist",
            audioUrl = "/api/soundcloud/tracks/998877/stream"
        )
        val resolvedSc = TrackDownloadManager.resolveDirectStreamUrl(scTrack, "http://152.70.19.219/")
        assertEquals("http://152.70.19.219/api/soundcloud/tracks/998877/stream", resolvedSc)

        // 3. Jamendo track
        val jamTrack = AudioTrack(
            id = "jamendo_554433",
            title = "Jam Song",
            artist = "Jam Artist",
            audioUrl = ""
        )
        val resolvedJam = TrackDownloadManager.resolveDirectStreamUrl(jamTrack, "http://152.70.19.219/")
        assertEquals("http://152.70.19.219/api/jamendo/tracks/554433/stream", resolvedJam)
    }

    private fun track(id: String, title: String, artist: String) = YouTubeTrackDto(
        externalId = id,
        title = title,
        artist = artist
    )
}
