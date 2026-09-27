package com.musicplayer.android.core

import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioModelTest {

    @Test
    fun testLocalTrackCreation() {
        val track = AudioTrack(
            id = "local_101",
            title = "Track Alpha",
            artist = "Artist Alpha",
            audioUrl = "content://media/external/audio/media/101",
            artworkUrl = "content://media/external/audio/albumart/5",
            durationMs = 354000L,
            isLocal = true,
            isLiveStream = false
        )

        assertEquals("local_101", track.id)
        assertEquals("Track Alpha", track.title)
        assertEquals("Artist Alpha", track.artist)
        assertEquals(354000L, track.durationMs)
        assertTrue(track.isLocal)
        assertFalse(track.isLiveStream)
    }

    @Test
    fun testRadioStreamTrackCreation() {
        val radioTrack = AudioTrack(
            id = "radio_uuid_999",
            title = "Station One",
            artist = "Genre Classic",
            audioUrl = "http://example.com/radio/stream",
            artworkUrl = "http://example.com/radio/logo.png",
            durationMs = 0L,
            isLocal = false,
            isLiveStream = true
        )

        assertEquals("radio_uuid_999", radioTrack.id)
        assertEquals("Station One", radioTrack.title)
        assertFalse(radioTrack.isLocal)
        assertTrue(radioTrack.isLiveStream)
        assertEquals(0L, radioTrack.durationMs)
    }

    @Test
    fun testMediaItemConversionRoundTrip() {
        val originalTrack = AudioTrack(
            id = "stream_42",
            title = "Track Beta",
            artist = "Artist Beta",
            audioUrl = "https://example.com/stream/42.mp3",
            artworkUrl = "https://example.com/art/42.jpg",
            durationMs = 230000L,
            isLocal = false,
            isLiveStream = false
        )

        // Convert to Media3 MediaItem
        val mediaItem = originalTrack.toMediaItem()
        assertNotNull(mediaItem)
        assertEquals("stream_42", mediaItem.mediaId)
        val title = mediaItem.mediaMetadata.title?.toString()
        if (title != null) {
            assertEquals("Track Beta", title)
        }
        val artist = mediaItem.mediaMetadata.artist?.toString()
        if (artist != null) {
            assertEquals("Artist Beta", artist)
        }

        // Convert back from MediaItem
        val restoredTrack = AudioTrack.fromMediaItem(mediaItem, durationMs = originalTrack.durationMs)
        assertEquals(originalTrack.id, restoredTrack.id)
        assertEquals(originalTrack.durationMs, restoredTrack.durationMs)
    }

    @Test
    fun testPlaybackStateTransitions() {
        val initialState = PlaybackState()
        assertNull(initialState.currentTrack)
        assertFalse(initialState.isPlaying)
        assertEquals(0L, initialState.currentPositionMs)
        assertFalse(initialState.hasNext)
        assertFalse(initialState.hasPrevious)

        val track1 = AudioTrack(id = "1", title = "Track 1", artist = "Artist 1", audioUrl = "url1")
        val track2 = AudioTrack(id = "2", title = "Track 2", artist = "Artist 2", audioUrl = "url2")
        val queue = listOf(track1, track2)

        val playingState = initialState.copy(
            currentTrack = track1,
            isPlaying = true,
            currentPositionMs = 15000L,
            durationMs = 180000L,
            isBuffering = false,
            queue = queue,
            hasNext = true,
            hasPrevious = false
        )

        assertTrue(playingState.isPlaying)
        assertEquals("1", playingState.currentTrack?.id)
        assertEquals(15000L, playingState.currentPositionMs)
        assertEquals(180000L, playingState.durationMs)
        assertEquals(2, playingState.queue.size)
        assertTrue(playingState.hasNext)
        assertFalse(playingState.hasPrevious)
    }

    @Test
    fun testShuffleAndRepeatModes() {
        val state = PlaybackState()
        assertFalse(state.shuffleModeEnabled)
        assertEquals(PlaybackState.REPEAT_MODE_OFF, state.repeatMode)

        val shuffledState = state.copy(shuffleModeEnabled = true)
        assertTrue(shuffledState.shuffleModeEnabled)

        val repeatOneState = state.copy(repeatMode = PlaybackState.REPEAT_MODE_ONE)
        assertEquals(PlaybackState.REPEAT_MODE_ONE, repeatOneState.repeatMode)

        val repeatAllState = state.copy(repeatMode = PlaybackState.REPEAT_MODE_ALL)
        assertEquals(PlaybackState.REPEAT_MODE_ALL, repeatAllState.repeatMode)
    }

    @Test
    fun testFromJamendoFactory() {
        val jamendoDto = com.musicplayer.android.core.network.JamendoTrackDto(
            externalId = "9988",
            title = "Chill Guitar",
            artist = "Acoustic Band",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 210000L,
            streamUrl = "https://example.com/stream.mp3"
        )
        val track = AudioTrack.fromJamendo(jamendoDto)
        assertEquals("jamendo_9988", track.id)
        assertEquals("Chill Guitar", track.title)
        assertEquals("Acoustic Band", track.artist)
        assertEquals("https://example.com/stream.mp3", track.audioUrl)
        assertEquals(210000L, track.durationMs)
        assertFalse(track.isLocal)
        assertFalse(track.isLiveStream)
    }

    @Test
    fun testFromRadioFactory() {
        val radioDto = com.musicplayer.android.core.network.RadioStationDto(
            stationId = "rad-123",
            name = "Radio Rocks",
            streamUrl = "https://example.com/stream.aac",
            logoUrl = "https://example.com/logo.png",
            genre = "Rock"
        )
        val track = AudioTrack.fromRadio(radioDto)
        assertEquals("radio_rad-123", track.id)
        assertEquals("Radio Rocks", track.title)
        assertEquals("Rock", track.artist)
        assertEquals("https://example.com/stream.aac", track.audioUrl)
        assertTrue(track.isLiveStream)
        assertFalse(track.isLocal)
    }

    @Test
    fun testFromAudiusFactory() {
        val audiusDto = com.musicplayer.android.core.network.AudiusTrackDto(
            externalId = "aud-456",
            title = "Audius Beat",
            artist = "DJ Electron",
            artworkUrl = "https://example.com/art2.jpg",
            durationMs = 150000L
        )
        val track = AudioTrack.fromAudius(audiusDto)
        assertEquals("audius_aud-456", track.id)
        assertEquals("Audius Beat", track.title)
        assertTrue(track.audioUrl.contains("api/audius/tracks/aud-456/stream"))
    }
}
