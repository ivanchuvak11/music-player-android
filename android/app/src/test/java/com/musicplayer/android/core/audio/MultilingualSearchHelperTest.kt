package com.musicplayer.android.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultilingualSearchHelperTest {

    @Test
    fun detectAndFlipWrongLayout_fixesMistypedEnglishBandNames() {
        assertEquals("metallica", MultilingualSearchHelper.detectAndFlipWrongLayout("ьуефддшсф"))
        assertEquals("ocean", MultilingualSearchHelper.detectAndFlipWrongLayout("щсуфт"))
        assertEquals("linkin park", MultilingualSearchHelper.detectAndFlipWrongLayout("дштлшт зфкл"))
        assertEquals("phonk", MultilingualSearchHelper.detectAndFlipWrongLayout("зрщтл"))
    }

    @Test
    fun detectAndFlipWrongLayout_preservesGenuineUkrainianQueries() {
        assertNull(MultilingualSearchHelper.detectAndFlipWrongLayout("океан ельзи"))
        assertNull(MultilingualSearchHelper.detectAndFlipWrongLayout("христина соловей"))
        assertNull(MultilingualSearchHelper.detectAndFlipWrongLayout("японський рок"))
        assertNull(MultilingualSearchHelper.detectAndFlipWrongLayout("спокійне піаніно"))
        assertNull(MultilingualSearchHelper.detectAndFlipWrongLayout("музика для душі"))
    }

    @Test
    fun translateMusicKeywordsToEnglish_expandsGenresMoodsLanguagesAndInstruments() {
        assertEquals("japanese rock", MultilingualSearchHelper.translateMusicKeywordsToEnglish("японський рок"))
        assertEquals("night drive phonk", MultilingualSearchHelper.translateMusicKeywordsToEnglish("нічний драйв фонк"))
        assertEquals("chill piano", MultilingualSearchHelper.translateMusicKeywordsToEnglish("спокійне піаніно"))
        assertEquals("music workout", MultilingualSearchHelper.translateMusicKeywordsToEnglish("музика для тренувань"))
        assertEquals("korean music", MultilingualSearchHelper.translateMusicKeywordsToEnglish("корейська музика"))
    }

    @Test
    fun translateMusicKeywordsToEnglish_resolvesForeignArtistsPhonetically() {
        assertEquals("billie eilish", MultilingualSearchHelper.translateMusicKeywordsToEnglish("біллі айліш"))
        assertEquals("imagine dragons", MultilingualSearchHelper.translateMusicKeywordsToEnglish("імеджин драгонс"))
        assertEquals("the weeknd", MultilingualSearchHelper.translateMusicKeywordsToEnglish("вікенд"))
        assertEquals("billie eilish piano", MultilingualSearchHelper.translateMusicKeywordsToEnglish("біллі айліш піаніно"))
    }

    @Test
    fun translateMusicKeywordsToEnglish_avoidsFalsePositiveShortRoots() {
        assertNull(MultilingualSearchHelper.translateMusicKeywordsToEnglish("2024 року"))
        assertEquals("best songs 2024 roku", MultilingualSearchHelper.translateMusicKeywordsToEnglish("кращі пісні 2024 року"))
        assertNull(MultilingualSearchHelper.translateMusicKeywordsToEnglish("христина соловей"))
    }

    @Test
    fun buildOnlineFallbackQueries_producesOrderedDeduplicatedVariants() {
        assertEquals(emptyList<String>(), MultilingualSearchHelper.buildOnlineFallbackQueries("metallica"))
        assertEquals(listOf("metallica"), MultilingualSearchHelper.buildOnlineFallbackQueries("ьуефддшсф"))
        assertEquals(listOf("billie eilish"), MultilingualSearchHelper.buildOnlineFallbackQueries("біллі айліш"))
        assertEquals(listOf("night drive"), MultilingualSearchHelper.buildOnlineFallbackQueries("нічний драйв"))
        assertEquals(listOf("okean elzy"), MultilingualSearchHelper.buildOnlineFallbackQueries("океан ельзи"))
    }

    @Test
    fun calculateSearchRelevanceScore_matchesMultilingualAndWrongLayoutLocalTracks() {
        val enTrack = AudioTrack(
            id = "loc_1",
            title = "Ocean Eyes",
            artist = "Billie Eilish",
            audioUrl = "content://loc_1"
        )
        assertTrue(enTrack.calculateSearchRelevanceScore("біллі айліш") >= 0)
        assertTrue(enTrack.calculateSearchRelevanceScore("щсуфт") >= 0)

        val uaRockTrack = AudioTrack(
            id = "loc_ua_rock",
            title = "Рок",
            artist = "Український Гурт",
            audioUrl = "content://loc_ua_rock"
        )
        val enRockTrack = AudioTrack(
            id = "loc_en_rock",
            title = "Rock Anthem",
            artist = "Foreign Band",
            audioUrl = "content://loc_en_rock"
        )
        assertTrue(uaRockTrack.calculateSearchRelevanceScore("рок") > enRockTrack.calculateSearchRelevanceScore("рок"))
    }
}
