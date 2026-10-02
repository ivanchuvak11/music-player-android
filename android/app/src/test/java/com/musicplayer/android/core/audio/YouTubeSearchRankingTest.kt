package com.musicplayer.android.core.audio

import com.musicplayer.android.core.network.YouTubeTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeSearchRankingTest {
    @Test
    fun supplementIsRequestedOnlyWhenPrimaryResultsAreWeakOrSparse() {
        val weakResults = listOf(track("weak", "Unrelated Song", "Unknown Artist"))
        val strongResults = listOf(
            track("one", "Believer", "Imagine Dragons"),
            track("two", "Believer (Live)", "Imagine Dragons"),
            track("three", "Believer Acoustic", "Imagine Dragons")
        )

        assertTrue(shouldSupplementYouTubeSearch("Believer", weakResults, 20))
        assertFalse(shouldSupplementYouTubeSearch("Believer", strongResults, 20))
        assertFalse(shouldSupplementYouTubeSearch("Believer", strongResults.take(1), 1))
    }

    @Test
    fun mergeRanksMatchesAndDeduplicatesIdsAndNormalizedMetadata() {
        val primary = listOf(
            track("unrelated", "Completely Different", "Someone"),
            track("same-id", "Béliever!", "Imagine Dragons"),
            track("server-prefix", "Believer Acoustic", "Imagine Dragons")
        )
        val supplemental = listOf(
            track("same-id", "Believer", "Imagine Dragons"),
            track("same-song", "Believer", "Imagine Dragons"),
            track("direct-prefix", "Believer Live", "Imagine Dragons")
        )

        val merged = mergeYouTubeSearchResults("Believer", primary, supplemental, 10)

        assertEquals(
            listOf("same-id", "server-prefix", "direct-prefix", "unrelated"),
            merged.map(YouTubeTrackDto::externalId)
        )
    }

    private fun track(id: String, title: String, artist: String) = YouTubeTrackDto(
        externalId = id,
        title = title,
        artist = artist
    )
}
