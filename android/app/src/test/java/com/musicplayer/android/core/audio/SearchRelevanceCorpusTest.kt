package com.musicplayer.android.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRelevanceCorpusTest {
    @Test
    fun exactPhraseOutranksPrefixBoundaryAndPartialTitleHits() {
        val candidates = listOf(
            track("middle", "Queen Live: Bohemian Rhapsody", "Queen"),
            track("partial", "Bohemian", "Unknown"),
            track("prefix", "Bohemian Rhapsody - Remastered", "Queen"),
            track("exact", "Bohemian Rhapsody", "Queen")
        )

        assertEquals(
            listOf("exact", "prefix", "middle", "partial"),
            rank("Bohemian Rhapsody", candidates).map(AudioTrack::id)
        )
    }

    @Test
    fun multiwordSearchCombinesTitleAndArtistButRewardsFullCoverage() {
        val candidates = listOf(
            track("title-only", "Hello", "Someone Else"),
            track("irrelevant", "Shape of You", "Ed Sheeran"),
            track("artist-only", "Another Song", "Adele"),
            track("cross-field", "Hello", "Adele")
        )

        assertEquals(
            listOf("cross-field", "title-only", "artist-only", "irrelevant"),
            rank("Adele Hello", candidates).map(AudioTrack::id)
        )
        assertEquals(-1, candidates.single { it.id == "irrelevant" }
            .calculateSearchRelevanceScore("Adele Hello"))
    }

    @Test
    fun punctuationAndDiacriticsVariantsStillFindTheTrack() {
        val punctuationVariants = listOf(
            track("spaced", "AC DC", "AC DC"),
            track("slash", "AC/DC", "AC/DC"),
            track("compact", "ACDC", "ACDC"),
            track("nearby", "ACEDC", "ACEDC")
        )
        val punctuationRanked = rank("ACDC", punctuationVariants)

        assertEquals(listOf("compact", "spaced", "slash"), punctuationRanked.take(3).map(AudioTrack::id))
        assertEquals(-1, punctuationRanked.last().calculateSearchRelevanceScore("ACDC"))
        assertEquals(
            560,
            track("halo", "Halo", "Beyonc\u00e9").calculateSearchRelevanceScore("Beyonce Halo")
        )
        assertEquals(
            750,
            track("gods-plan", "God's Plan", "Drake").calculateSearchRelevanceScore("Gods Plan")
        )
    }

    @Test
    fun typoToleranceDoesNotLetMisspelledTitleBeatCleanTitlePrefix() {
        val candidates = listOf(
            track("typo", "Shspe of You", "Unknown"),
            track("partial", "Shape", "Unknown"),
            track("prefix", "Shape of You (Live)", "Unknown"),
            track("exact", "Shape of You", "Unknown")
        )

        assertEquals(
            listOf("exact", "prefix", "typo", "partial"),
            rank("shape of you", candidates).map(AudioTrack::id)
        )
        assertTrue(track("beautiful", "Beautiful", "Unknown")
            .calculateSearchRelevanceScore("beatifull") > 0)
        assertEquals(-1, track("believer", "Believer", "Unknown")
            .calculateSearchRelevanceScore("beliivrr"))
    }

    @Test
    fun stopWordsAndRepeatedWordsAreHandledWithoutReusingOneToken() {
        val candidates = listOf(
            track("title-only", "Blinding Lights", "Someone Else"),
            track("full", "Blinding Lights", "The Weeknd"),
            track("single-word", "Love", "Unknown"),
            track("repeat", "Love Love", "Unknown")
        )

        assertTrue(
            candidates.single { it.id == "full" }
                .calculateSearchRelevanceScore("The Weeknd Blinding Lights") >
                candidates.single { it.id == "title-only" }
                    .calculateSearchRelevanceScore("The Weeknd Blinding Lights")
        )
        assertTrue(
            candidates.single { it.id == "repeat" }
                .calculateSearchRelevanceScore("Love Love") >
                candidates.single { it.id == "single-word" }
                    .calculateSearchRelevanceScore("Love Love")
        )
    }

    private fun rank(query: String, candidates: List<AudioTrack>): List<AudioTrack> =
        candidates.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<AudioTrack>> {
                    it.value.calculateSearchRelevanceScore(query)
                }.thenBy { it.index }
            )
            .map { it.value }

    private fun track(id: String, title: String, artist: String = "Unknown") = AudioTrack(
        id = id,
        title = title,
        artist = artist,
        audioUrl = "https://example.com/$id.mp3"
    )
}
