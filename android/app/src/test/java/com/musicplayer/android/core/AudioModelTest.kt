package com.musicplayer.android.core

import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.audio.calculateSearchRelevanceScore
import com.musicplayer.android.core.audio.toAudioTrack
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

    @Test
    fun testFromAudiusWithCustomBaseUrl() {
        val audiusDto = com.musicplayer.android.core.network.AudiusTrackDto(
            externalId = "aud-789",
            title = "Physical Device Track",
            artist = "DJ Test",
            artworkUrl = "https://example.com/art3.jpg",
            durationMs = 180000L
        )
        val customBaseUrl = "http://192.168.1.100:5116/"
        val track = AudioTrack.fromAudius(audiusDto, backendBaseUrl = customBaseUrl)
        assertEquals("http://192.168.1.100:5116/api/audius/tracks/aud-789/stream", track.audioUrl)
    }

    @Test
    fun testCachedTrackEntityCreation() {
        val entity = com.musicplayer.android.core.database.CachedTrackEntity(
            id = "jamendo_123",
            title = "Offline Song",
            artist = "Offline Artist",
            localFilePath = "/data/cache/123.mp3",
            originalUrl = "https://example.com/123.mp3",
            durationMs = 200000L,
            cachedAtTimestamp = 1711500000000L
        )
        assertEquals("jamendo_123", entity.id)
        assertEquals("Offline Song", entity.title)
        assertEquals("Offline Artist", entity.artist)
        assertEquals("/data/cache/123.mp3", entity.localFilePath)
        assertEquals(1711500000000L, entity.cachedAtTimestamp)
    }

    @Test
    fun testAudioEffectsStateAndPresets() {
        val defaultState = com.musicplayer.android.core.audio.AudioEffectsState()
        assertFalse(defaultState.isEnabled)
        assertEquals(com.musicplayer.android.core.audio.AudioEffectsState.PRESET_FLAT, defaultState.currentPreset)
        assertEquals(0.toShort(), defaultState.bassBoostStrength)
        assertTrue(com.musicplayer.android.core.audio.AudioEffectsState.AVAILABLE_PRESETS.contains("Підсилений бас"))
        assertTrue(com.musicplayer.android.core.audio.AudioEffectsState.AVAILABLE_PRESETS.contains("Рок"))

        val enabledState = defaultState.copy(
            isEnabled = true,
            currentPreset = com.musicplayer.android.core.audio.AudioEffectsState.PRESET_ROCK,
            bassBoostStrength = 400.toShort()
        )
        assertTrue(enabledState.isEnabled)
        assertEquals(com.musicplayer.android.core.audio.AudioEffectsState.PRESET_ROCK, enabledState.currentPreset)
        assertEquals(400.toShort(), enabledState.bassBoostStrength)
    }

    @Test
    fun testFavoriteTrackEntityCreation() {
        val fav = com.musicplayer.android.core.database.FavoriteTrackEntity(
            id = "fav_456",
            title = "Favorite Song",
            artist = "Great Artist",
            audioUrl = "https://example.com/fav.mp3",
            artworkUrl = "https://example.com/art.png",
            durationMs = 215000L,
            favoritedAtTimestamp = 1711600000000L
        )
        assertEquals("fav_456", fav.id)
        assertEquals("Favorite Song", fav.title)
        assertEquals("Great Artist", fav.artist)
        assertEquals("https://example.com/fav.mp3", fav.audioUrl)
        assertEquals(215000L, fav.durationMs)
        assertEquals(1711600000000L, fav.favoritedAtTimestamp)
    }

    @Test
    fun testPlayHistoryEntityCreation() {
        val history = com.musicplayer.android.core.database.PlayHistoryEntity(
            historyId = 1L,
            trackId = "hist_789",
            title = "History Song",
            artist = "History Artist",
            audioUrl = "https://example.com/history.mp3",
            artworkUrl = null,
            durationMs = 180000L,
            playedAtTimestamp = 1711700000000L
        )
        assertEquals(1L, history.historyId)
        assertEquals("hist_789", history.trackId)
        assertEquals("History Song", history.title)
        assertEquals("History Artist", history.artist)
        assertEquals(180000L, history.durationMs)
        assertEquals(1711700000000L, history.playedAtTimestamp)
    }

    @Test
    fun testPlaybackStateErrorMessage() {
        val defaultState = PlaybackState()
        assertNull(defaultState.errorMessage)

        val errorState = defaultState.copy(errorMessage = "Немає підключення до мережі")
        assertEquals("Немає підключення до мережі", errorState.errorMessage)

        val recoveredState = errorState.copy(errorMessage = null)
        assertNull(recoveredState.errorMessage)
    }

    @Test
    fun testPlayHistoryEntityWithLastPosition() {
        val history = com.musicplayer.android.core.database.PlayHistoryEntity(
            historyId = 2L,
            trackId = "track_pos_1",
            title = "Resume Track",
            artist = "Resume Artist",
            audioUrl = "https://example.com/pos.mp3",
            artworkUrl = null,
            durationMs = 300000L,
            lastPositionMs = 145000L,
            playedAtTimestamp = 1711800000000L
        )
        assertEquals(145000L, history.lastPositionMs)
        assertEquals("track_pos_1", history.trackId)
    }

    @Test
    fun testSleepTimerInitialStateAndCancel() {
        com.musicplayer.android.core.audio.SleepTimer.cancel()
        assertFalse(com.musicplayer.android.core.audio.SleepTimer.isActive.value)
        assertEquals(0L, com.musicplayer.android.core.audio.SleepTimer.remainingSeconds.value)
    }

    @Test
    fun testAudioEffectsStateLoudness() {
        val state = com.musicplayer.android.core.audio.AudioEffectsState(
            loudnessEnhancerGainMb = 500
        )
        assertEquals(500, state.loudnessEnhancerGainMb)
    }

    @Test
    fun testLocalPlaylistEntityCreation() {
        val playlist = com.musicplayer.android.core.database.LocalPlaylistEntity(
            id = 1L,
            name = "Мій Рок"
        )
        assertEquals(1L, playlist.id)
        assertEquals("Мій Рок", playlist.name)
        assertTrue(playlist.createdAt > 0)
    }

    @Test
    fun testLocalPlaylistTrackEntityCreation() {
        val track = com.musicplayer.android.core.database.LocalPlaylistTrackEntity(
            id = 10L,
            playlistId = 1L,
            trackId = "track_123",
            title = "Wind of Change",
            artist = "Scorpions",
            audioUrl = "content://media/123",
            durationMs = 312000L,
            isLocal = true
        )
        assertEquals(1L, track.playlistId)
        assertEquals("track_123", track.trackId)
        assertEquals("Wind of Change", track.title)
        assertTrue(track.isLocal)
    }

    @Test
    fun testDefaultUkrainianRadioStationsPresent() {
        val stations = com.musicplayer.android.core.viewmodel.MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS
        assertTrue("Default stations should not be empty", stations.isNotEmpty())
        assertTrue("Should include Hit FM", stations.any { it.name.contains("Хіт FM") })
        assertTrue("Should include Kiss FM", stations.any { it.name.contains("Kiss FM") })
        assertTrue("Should include Radio ROKS", stations.any { it.name.contains("Radio ROKS") })
        assertTrue("All stations must have valid stream URLs", stations.all { it.streamUrl.startsWith("http") })
    }

    @Test
    fun testAudioEffectsStateHeadphonesDefault() {
        val state = com.musicplayer.android.core.audio.AudioEffectsState()
        assertFalse("Headphones should not be connected by default in empty state", state.isHeadphonesConnected)
        assertFalse("Equalizer should be disabled by default", state.isEnabled)
    }

    @Test
    fun testAudioEffectsManagerRejectsEnableWithoutHeadphones() {
        // Without context/headphones connected, setEnabled(true) must reject enabling
        val result = com.musicplayer.android.core.audio.AudioEffectsManager.setEnabled(true, null)
        assertFalse("Equalizer must not enable when headphones are not connected", result)
        assertFalse("State isEnabled must remain false", com.musicplayer.android.core.audio.AudioEffectsManager.effectsState.value.isEnabled)
    }

    @Test
    fun testEntityToAudioTrackExtensions() {
        val cached = com.musicplayer.android.core.database.CachedTrackEntity(
            id = "cache_1",
            title = "Cached Song",
            artist = "Cached Artist",
            localFilePath = "/data/local.mp3",
            originalUrl = "http://example.com/song.mp3",
            artworkUrl = "http://example.com/art.png",
            durationMs = 120000L,
            cachedAtTimestamp = 1000L
        )
        val cachedTrack = cached.toAudioTrack()
        assertEquals("cache_1", cachedTrack.id)
        assertEquals("/data/local.mp3", cachedTrack.audioUrl)
        assertEquals("http://example.com/art.png", cachedTrack.artworkUrl)
        assertTrue(cachedTrack.isLocal)

        val fav = com.musicplayer.android.core.database.FavoriteTrackEntity(
            id = "fav_1",
            title = "Fav Song",
            artist = "Fav Artist",
            audioUrl = "http://example.com/fav.mp3",
            artworkUrl = "http://example.com/fav_art.png",
            durationMs = 150000L
        )
        val favTrack = fav.toAudioTrack()
        assertEquals("fav_1", favTrack.id)
        assertEquals("http://example.com/fav_art.png", favTrack.artworkUrl)
        assertFalse(favTrack.isLocal)

        val hist = com.musicplayer.android.core.database.PlayHistoryEntity(
            historyId = 5L,
            trackId = "hist_1",
            title = "Hist Song",
            artist = "Hist Artist",
            audioUrl = "content://media/external/audio/media/99",
            artworkUrl = null,
            durationMs = 90000L
        )
        val histTrack = hist.toAudioTrack()
        assertEquals("hist_1", histTrack.id)
        assertTrue(histTrack.isLocal)

        val plTrack = com.musicplayer.android.core.database.LocalPlaylistTrackEntity(
            id = 1L,
            playlistId = 10L,
            trackId = "pl_track_1",
            title = "Playlist Track",
            artist = "Playlist Artist",
            audioUrl = "content://media/external/audio/media/100",
            artworkUrl = "http://example.com/pl_art.png",
            durationMs = 180000L,
            isLocal = true
        )
        val plAudioTrack = plTrack.toAudioTrack()
        assertEquals("pl_track_1", plAudioTrack.id)
        assertEquals("http://example.com/pl_art.png", plAudioTrack.artworkUrl)
        assertTrue(plAudioTrack.isLocal)
    }

    @Test
    fun testSearchRelevanceScoring() {
        val exactTrack = AudioTrack(
            id = "1",
            title = "Believer",
            artist = "Imagine Dragons",
            audioUrl = "http://example.com/1.mp3"
        )
        val prefixTrack = AudioTrack(
            id = "2",
            title = "Believer (Acoustic Remix)",
            artist = "Another Artist",
            audioUrl = "http://example.com/2.mp3"
        )
        val wordBoundaryTrack = AudioTrack(
            id = "3",
            title = "I am a Believer",
            artist = "The Monkees",
            audioUrl = "http://example.com/3.mp3"
        )
        val artistMatchTrack = AudioTrack(
            id = "4",
            title = "Radioactive",
            artist = "Believer Band",
            audioUrl = "http://example.com/4.mp3"
        )
        val irrelevantTrack = AudioTrack(
            id = "5",
            title = "Shape of You",
            artist = "Ed Sheeran",
            audioUrl = "http://example.com/5.mp3"
        )

        val query = "Believer"
        val exactScore = exactTrack.calculateSearchRelevanceScore(query)
        val prefixScore = prefixTrack.calculateSearchRelevanceScore(query)
        val boundaryScore = wordBoundaryTrack.calculateSearchRelevanceScore(query)
        val artistScore = artistMatchTrack.calculateSearchRelevanceScore(query)
        val irrelevantScore = irrelevantTrack.calculateSearchRelevanceScore(query)

        assertTrue("Exact match score must be > prefix score", exactScore > prefixScore)
        assertTrue("Prefix score must be > word boundary score", prefixScore > boundaryScore)
        assertTrue("Boundary score must be > artist score", boundaryScore > artistScore)
        assertTrue("Artist match score must be positive", artistScore > 0)
        assertEquals("Irrelevant score must be -1", -1, irrelevantScore)
    }

    @Test
    fun testSearchRankingUsesFieldCoverageAndBoundedFuzzyMatches() {
        fun track(title: String, artist: String = "Unknown") = AudioTrack(
            id = title,
            title = title,
            artist = artist,
            audioUrl = "http://example.com/$title.mp3"
        )

        val titleAndArtistMatch = track("Hello", "Adele")
        val titleOnlyMatch = track("Hello", "Someone Else")
        val typoMatch = track("Believer")
        val substringMatch = track("Believer")
        val repeatedWordMatch = track("Hello")

        assertTrue(
            titleAndArtistMatch.calculateSearchRelevanceScore("Adele Hello") >
                titleOnlyMatch.calculateSearchRelevanceScore("Adele Hello")
        )
        assertTrue(
            typoMatch.calculateSearchRelevanceScore("believre") >
                substringMatch.calculateSearchRelevanceScore("liev")
        )
        assertTrue(repeatedWordMatch.calculateSearchRelevanceScore("hello hello") < 300)
    }

    @Test
    fun testSearchRelevanceCorpusKeepsExactPrefixAheadOfTypoAndInfix() {
        fun track(title: String, artist: String = "Unknown") = AudioTrack(
            id = title,
            title = title,
            artist = artist,
            audioUrl = "http://example.com/$title.mp3"
        )

        val exactPrefix = track("Shape of You (Live)")
        val typo = track("Shspe of You")
        val internalFragment = track("Believer")
        val exactArtist = track("Unrelated", "Liev")
        val crossField = track("Hello", "Adele")
        val titleOnly = track("Hello", "Someone Else")

        assertTrue(
            exactPrefix.calculateSearchRelevanceScore("shape of you") >
                typo.calculateSearchRelevanceScore("shape of you")
        )
        assertTrue(
            exactArtist.calculateSearchRelevanceScore("liev") >
                internalFragment.calculateSearchRelevanceScore("liev")
        )
        assertTrue(
            crossField.calculateSearchRelevanceScore("Adele Hello") >
                titleOnly.calculateSearchRelevanceScore("Adele Hello")
        )
        assertEquals(800, track("Shape of You").calculateSearchRelevanceScore("shape of y"))
        assertEquals(
            1000,
            track("Beyonc\u00e9: Halo").calculateSearchRelevanceScore("Beyonce Halo")
        )
        assertEquals(750, track("AC/DC").calculateSearchRelevanceScore("ACDC"))
        assertEquals(750, track("God's Plan").calculateSearchRelevanceScore("Gods Plan"))
        assertTrue(track("Heartbreaker").calculateSearchRelevanceScore("break") < 300)
        assertTrue(track("Beautiful").calculateSearchRelevanceScore("beatifull") > 0)
        assertEquals(-1, track("Believer").calculateSearchRelevanceScore("beliivrr"))

        // YouTube video noise tag stripping
        assertEquals(1000, track("Numb (Official Music Video)", "Linkin Park").calculateSearchRelevanceScore("Numb"))
        assertEquals(1000, track("In The End [Official Audio]", "Linkin Park").calculateSearchRelevanceScore("In The End"))

        // Ukrainian Cyrillic to English transliteration
        assertTrue(track("Bohemian Rhapsody", "Queen").calculateSearchRelevanceScore("квін") > 100)
        assertTrue(track("Master of Puppets", "Metallica").calculateSearchRelevanceScore("металіка") > 100)

        // Wrong keyboard layout (Ukrainian keyboard layout typing English query)
        // "ьуефддшсф" typed instead of "metallica"
        assertTrue(track("Master of Puppets", "Metallica").calculateSearchRelevanceScore("ьуефддшсф") > 100)
    }

    @Test
    fun testTrackDownloadManagerConstants() {
        assertEquals(150L * 1024 * 1024, com.musicplayer.android.core.audio.TrackDownloadManager.MAX_FILE_SIZE_BYTES)
        assertEquals(50L * 1024, com.musicplayer.android.core.audio.TrackDownloadManager.MIN_FILE_SIZE_BYTES)
    }

    @Test
    fun testSleepTimerStateAndCancel() {
        com.musicplayer.android.core.audio.SleepTimer.cancel()
        assertFalse(com.musicplayer.android.core.audio.SleepTimer.isActive.value)
        assertEquals(0L, com.musicplayer.android.core.audio.SleepTimer.remainingSeconds.value)
    }
}


