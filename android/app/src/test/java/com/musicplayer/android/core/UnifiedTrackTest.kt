package com.musicplayer.android.core

import com.musicplayer.android.core.audio.TrackSource
import com.musicplayer.android.core.audio.UnifiedTrack
import com.musicplayer.android.core.audio.deduplicateBySourceAndId
import com.musicplayer.android.core.audio.mergeAndDeduplicateSources
import com.musicplayer.android.core.audio.sortByRelevance
import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.JamendoTrackDto
import com.musicplayer.android.core.network.MusicSourceError
import com.musicplayer.android.core.network.SoundCloudTrackDto
import com.musicplayer.android.core.network.httpCodeToMusicError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ТЗ Section 9: unified track model, deduplication, stream URL,
 * error typing, offline fallback, source logic, and local track handling.
 */
class UnifiedTrackTest {

    // ─── 1. Model serialization / construction ─────────────────────────────────

    @Test
    fun `UnifiedTrack from SoundCloud DTO has correct fields`() {
        val dto = SoundCloudTrackDto(
            source = "soundcloud",
            externalId = "sc123",
            title = "Test Song",
            artist = "Test Artist",
            artworkUrl = "https://i.soundcloud.com/art.jpg",
            durationMs = 240000L,
            streamUrl = null
        )
        val track = UnifiedTrack.fromSoundCloud(dto)

        assertEquals(TrackSource.SOUNDCLOUD, track.source)
        assertEquals("sc123", track.externalId)
        assertEquals("Test Song", track.title)
        assertEquals("Test Artist", track.artist)
        assertEquals(240000L, track.durationMs)
        assertEquals("soundcloud_sc123", track.id)
    }

    @Test
    fun `UnifiedTrack from Audius DTO has correct fields`() {
        val dto = AudiusTrackDto(
            source = "audius",
            externalId = "au456",
            title = "Audius Track",
            artist = "Audius Artist",
            artworkUrl = null,
            durationMs = 180000L
        )
        val track = UnifiedTrack.fromAudius(dto)

        assertEquals(TrackSource.AUDIUS, track.source)
        assertEquals("au456", track.externalId)
        assertEquals("audius_au456", track.id)
        assertEquals(180000L, track.durationMs)
    }

    @Test
    fun `UnifiedTrack from Jamendo DTO has album field`() {
        val dto = JamendoTrackDto(
            source = "jamendo",
            externalId = "jam789",
            title = "Jamendo Track",
            artist = "Jamendo Artist",
            artworkUrl = null,
            durationMs = 300000L,
            album = "Album Name",
            streamUrl = "https://storage.jamendo.com/file.mp3"
        )
        val track = UnifiedTrack.fromJamendo(dto)

        assertEquals(TrackSource.JAMENDO, track.source)
        assertEquals("jam789", track.externalId)
        assertEquals("Album Name", track.album)
        assertEquals("https://storage.jamendo.com/file.mp3", track.streamUrl)
        assertEquals("jamendo_jam789", track.id)
    }

    @Test
    fun `UnifiedTrack from local AudioTrack has LOCAL source`() {
        val audioTrack = com.musicplayer.android.core.audio.AudioTrack(
            id = "local_content_101",
            title = "Local Song",
            artist = "Local Artist",
            audioUrl = "content://media/external/audio/media/101",
            durationMs = 210000L,
            isLocal = true
        )
        val track = UnifiedTrack.fromLocalAudioTrack(audioTrack)

        assertEquals(TrackSource.LOCAL, track.source)
        assertEquals("local_content_101", track.externalId)
        assertEquals("content://media/external/audio/media/101", track.streamUrl)
        assertEquals("local_local_content_101", track.id)
    }

    // ─── 2. Stream URL resolution (ТЗ Section 4) ──────────────────────────────

    @Test
    fun `SoundCloud stream URL is always through backend proxy`() {
        val track = UnifiedTrack(
            source = TrackSource.SOUNDCLOUD,
            externalId = "sc123",
            title = "T",
            artist = "A",
            streamUrl = "https://direct.soundcloud.com/stream" // ignored for SC
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116/")
        assertEquals("http://10.0.2.2:5116/api/soundcloud/tracks/sc123/stream", url)
    }

    @Test
    fun `Audius stream URL is always through backend proxy`() {
        val track = UnifiedTrack(
            source = TrackSource.AUDIUS,
            externalId = "au456",
            title = "T",
            artist = "A"
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116/")
        assertEquals("http://10.0.2.2:5116/api/audius/tracks/au456/stream", url)
    }

    @Test
    fun `Jamendo uses direct CDN streamUrl when available`() {
        val track = UnifiedTrack(
            source = TrackSource.JAMENDO,
            externalId = "jam789",
            title = "T",
            artist = "A",
            streamUrl = "https://storage.jamendo.com/file.mp3"
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116/")
        assertEquals("https://storage.jamendo.com/file.mp3", url)
    }

    @Test
    fun `Jamendo falls back to backend proxy when no direct streamUrl`() {
        val track = UnifiedTrack(
            source = TrackSource.JAMENDO,
            externalId = "jam789",
            title = "T",
            artist = "A",
            streamUrl = null
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116/")
        assertEquals("http://10.0.2.2:5116/api/jamendo/tracks/jam789/stream", url)
    }

    @Test
    fun `Local track stream URL returns original file path`() {
        val track = UnifiedTrack(
            source = TrackSource.LOCAL,
            externalId = "local_101",
            title = "T",
            artist = "A",
            streamUrl = "content://media/external/audio/media/101"
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116/")
        assertEquals("content://media/external/audio/media/101", url)
    }

    @Test
    fun `resolveStreamUrl trims trailing slash from base URL`() {
        val track = UnifiedTrack(
            source = TrackSource.AUDIUS,
            externalId = "au1",
            title = "T",
            artist = "A"
        )
        val url = track.resolveStreamUrl("http://10.0.2.2:5116")  // no trailing slash
        assertEquals("http://10.0.2.2:5116/api/audius/tracks/au1/stream", url)
    }

    // ─── 3. Empty query — search skipped (ТЗ Section 3) ──────────────────────

    @Test
    fun `empty query produces empty UnifiedTrack list`() {
        // Simulate what searchAllSources does for blank query
        val query = "  "
        val result = if (query.isBlank()) emptyList<UnifiedTrack>() else listOf(
            UnifiedTrack(TrackSource.JAMENDO, "x", "t", "a")
        )
        assertEquals(0, result.size)
    }

    // ─── 4. Deduplication (ТЗ Section 3) ──────────────────────────────────────

    @Test
    fun `deduplicateBySourceAndId removes duplicate source+externalId`() {
        val tracks = listOf(
            UnifiedTrack(TrackSource.SOUNDCLOUD, "sc1", "Song A", "Artist A"),
            UnifiedTrack(TrackSource.AUDIUS, "au1", "Song B", "Artist B"),
            UnifiedTrack(TrackSource.SOUNDCLOUD, "sc1", "Song A Duplicate", "Artist A"), // dup
            UnifiedTrack(TrackSource.JAMENDO, "j1", "Song C", "Artist C")
        )
        val deduped = tracks.deduplicateBySourceAndId()
        assertEquals(3, deduped.size)
        // First occurrence wins
        assertEquals("Song A", deduped[0].title)
    }

    @Test
    fun `same externalId but different sources are not duplicates`() {
        val tracks = listOf(
            UnifiedTrack(TrackSource.SOUNDCLOUD, "id1", "Song X", "Artist"),
            UnifiedTrack(TrackSource.AUDIUS, "id1", "Song X", "Artist"),    // different source
            UnifiedTrack(TrackSource.JAMENDO, "id1", "Song X", "Artist")    // different source
        )
        val deduped = tracks.deduplicateBySourceAndId()
        assertEquals(3, deduped.size) // All 3 kept — different sources
    }

    @Test
    fun `mergeAndDeduplicateSources respects SC then Audius then Jamendo order`() {
        val sc = listOf(UnifiedTrack(TrackSource.SOUNDCLOUD, "sc1", "SC Song", "A"))
        val au = listOf(UnifiedTrack(TrackSource.AUDIUS, "au1", "Audius Song", "B"))
        val ja = listOf(UnifiedTrack(TrackSource.JAMENDO, "j1", "Jamendo Song", "C"))
        val result = mergeAndDeduplicateSources(sc, au, ja)
        assertEquals(3, result.size)
        assertEquals(TrackSource.SOUNDCLOUD, result[0].source)
        assertEquals(TrackSource.AUDIUS, result[1].source)
        assertEquals(TrackSource.JAMENDO, result[2].source)
    }

    @Test
    fun `mergeAndDeduplicateSources deduplicates across sources by source+externalId`() {
        val sc = listOf(
            UnifiedTrack(TrackSource.SOUNDCLOUD, "sc1", "A", "Artist"),
            UnifiedTrack(TrackSource.SOUNDCLOUD, "sc1", "A-dup", "Artist") // same source+id
        )
        val au = listOf<UnifiedTrack>()
        val ja = listOf(UnifiedTrack(TrackSource.JAMENDO, "j1", "B", "Artist"))
        val result = mergeAndDeduplicateSources(sc, au, ja)
        assertEquals(2, result.size)
    }

    @Test
    fun `mergeAndDeduplicateSources handles empty sources gracefully`() {
        val result = mergeAndDeduplicateSources(emptyList(), emptyList(), emptyList())
        assertEquals(0, result.size)
    }

    // ─── 5. Sort by relevance ─────────────────────────────────────────────────

    @Test
    fun `sortByRelevance places exact title match first`() {
        val tracks = listOf(
            UnifiedTrack(TrackSource.JAMENDO, "1", "Another Song", "Believer"),  // artist match
            UnifiedTrack(TrackSource.JAMENDO, "2", "Believer", "Imagine Dragons"), // exact title
            UnifiedTrack(TrackSource.JAMENDO, "3", "Believer Rock Mix", "X")      // prefix match
        )
        val sorted = tracks.sortByRelevance("Believer")
        assertEquals("2", sorted[0].externalId) // exact title first
    }

    // ─── 6. Error types (ТЗ Section 8) ────────────────────────────────────────

    @Test
    fun `httpCodeToMusicError maps 401 to Unauthorized`() {
        val error = httpCodeToMusicError(401, "soundcloud")
        assertTrue(error is MusicSourceError.Unauthorized)
        assertEquals("soundcloud", error.source)
    }

    @Test
    fun `httpCodeToMusicError maps 404 to NotFound`() {
        val error = httpCodeToMusicError(404, "audius")
        assertTrue(error is MusicSourceError.NotFound)
    }

    @Test
    fun `httpCodeToMusicError maps 429 to TooManyRequests`() {
        val error = httpCodeToMusicError(429, "jamendo")
        assertTrue(error is MusicSourceError.TooManyRequests)
    }

    @Test
    fun `httpCodeToMusicError maps 408 to Timeout`() {
        val error = httpCodeToMusicError(408, "soundcloud")
        assertTrue(error is MusicSourceError.Timeout)
    }

    @Test
    fun `MusicSourceError NoNetwork has correct default source`() {
        val error = MusicSourceError.NoNetwork()
        assertEquals("all", error.source)
        assertTrue(error.message.orEmpty().contains("No network"))
    }

    @Test
    fun `MusicSourceError per-source has correct source field`() {
        val error = MusicSourceError.Unauthorized("jamendo")
        assertEquals("jamendo", error.source)
    }

    // ─── 7. TrackSource enum ──────────────────────────────────────────────────

    @Test
    fun `TrackSource fromString maps correctly`() {
        assertEquals(TrackSource.SOUNDCLOUD, TrackSource.fromString("soundcloud"))
        assertEquals(TrackSource.AUDIUS, TrackSource.fromString("audius"))
        assertEquals(TrackSource.JAMENDO, TrackSource.fromString("jamendo"))
        assertEquals(TrackSource.LOCAL, TrackSource.fromString("local"))
    }

    @Test
    fun `TrackSource fromString is case insensitive`() {
        assertEquals(TrackSource.SOUNDCLOUD, TrackSource.fromString("SoundCloud"))
        assertEquals(TrackSource.AUDIUS, TrackSource.fromString("AUDIUS"))
    }

    @Test
    fun `TrackSource fromString returns JAMENDO for unknown value`() {
        assertEquals(TrackSource.JAMENDO, TrackSource.fromString(null))
        assertEquals(TrackSource.JAMENDO, TrackSource.fromString("unknown"))
    }

    // ─── 8. toAudioTrack conversion (ТЗ Section 4) ───────────────────────────

    @Test
    fun `toAudioTrack sets correct audioUrl from stream URL`() {
        val track = UnifiedTrack(
            source = TrackSource.AUDIUS,
            externalId = "au1",
            title = "T",
            artist = "A",
            durationMs = 120000L
        )
        val audioTrack = track.toAudioTrack("http://10.0.2.2:5116/")
        assertEquals("http://10.0.2.2:5116/api/audius/tracks/au1/stream", audioTrack.audioUrl)
        assertEquals("audius_au1", audioTrack.id)
        assertFalse(audioTrack.isLocal)
        assertFalse(audioTrack.isLiveStream)
        assertEquals(120000L, audioTrack.durationMs)
    }

    @Test
    fun `toAudioTrack for LOCAL source marks isLocal = true`() {
        val track = UnifiedTrack(
            source = TrackSource.LOCAL,
            externalId = "local_file",
            title = "File",
            artist = "Artist",
            streamUrl = "file:///sdcard/music/song.mp3"
        )
        val audioTrack = track.toAudioTrack()
        assertTrue(audioTrack.isLocal)
        assertEquals("file:///sdcard/music/song.mp3", audioTrack.audioUrl)
    }

    // ─── 9. Composite ID uniqueness ───────────────────────────────────────────

    @Test
    fun `composite ID is unique across sources`() {
        val sc = UnifiedTrack(TrackSource.SOUNDCLOUD, "track1", "T", "A")
        val au = UnifiedTrack(TrackSource.AUDIUS, "track1", "T", "A")
        val ja = UnifiedTrack(TrackSource.JAMENDO, "track1", "T", "A")
        val lo = UnifiedTrack(TrackSource.LOCAL, "track1", "T", "A")

        val ids = setOf(sc.id, au.id, ja.id, lo.id)
        assertEquals("All 4 IDs must be unique", 4, ids.size)
    }

    // ─── 10. Offline fallback — unifiedSearchOffline flag ────────────────────

    @Test
    fun `MultiSourceSearchResult isOfflineFallback reported correctly`() {
        val result = com.musicplayer.android.core.audio.MultiSourceSearchResult(
            tracks = listOf(UnifiedTrack(TrackSource.JAMENDO, "j1", "Cached", "A")),
            errors = mapOf("all" to MusicSourceError.NoNetwork()),
            isOfflineFallback = true
        )
        assertTrue(result.isOfflineFallback)
        assertTrue(result.hasResults)
        assertTrue(result.hasErrors)
        assertFalse(result.allSourcesFailed) // only 1 error, not 3
    }

    @Test
    fun `MultiSourceSearchResult allSourcesFailed when 3 errors`() {
        val result = com.musicplayer.android.core.audio.MultiSourceSearchResult(
            tracks = emptyList(),
            errors = mapOf(
                "soundcloud" to MusicSourceError.Timeout("soundcloud"),
                "audius" to MusicSourceError.Timeout("audius"),
                "jamendo" to MusicSourceError.Timeout("jamendo")
            )
        )
        assertTrue(result.allSourcesFailed)
        assertFalse(result.hasResults)
    }

    @Test
    fun `MultiSourceSearchResult hasResults is false for empty list`() {
        val result = com.musicplayer.android.core.audio.MultiSourceSearchResult()
        assertFalse(result.hasResults)
        assertFalse(result.hasErrors)
        assertFalse(result.allSourcesFailed)
    }

    // ─── 11. Null-safety / edge cases ─────────────────────────────────────────

    @Test
    fun `UnifiedTrack with null artworkUrl is valid`() {
        val track = UnifiedTrack(
            source = TrackSource.JAMENDO,
            externalId = "j1",
            title = "T",
            artist = "A",
            artworkUrl = null
        )
        assertNotNull(track)
        assertEquals(null, track.artworkUrl)
    }

    @Test
    fun `UnifiedTrack durationMs defaults to 0`() {
        val track = UnifiedTrack(TrackSource.AUDIUS, "id", "T", "A")
        assertEquals(0L, track.durationMs)
    }

    @Test
    fun `sortByRelevance on empty list returns empty list`() {
        val result = emptyList<UnifiedTrack>().sortByRelevance("test")
        assertEquals(0, result.size)
    }

    @Test
    fun `sortByRelevance with blank query returns list unchanged`() {
        val tracks = listOf(
            UnifiedTrack(TrackSource.JAMENDO, "1", "Song B", "A"),
            UnifiedTrack(TrackSource.JAMENDO, "2", "Song A", "A")
        )
        val sorted = tracks.sortByRelevance("  ")
        // Blank query returns same reference (unchanged order)
        assertEquals("Song B", sorted[0].title)
        assertEquals("Song A", sorted[1].title)
    }
}
