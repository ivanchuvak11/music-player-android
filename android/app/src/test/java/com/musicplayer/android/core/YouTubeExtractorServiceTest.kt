package com.musicplayer.android.core

import com.musicplayer.android.core.audio.YouTubeExtractorService
import org.junit.Test

class YouTubeExtractorServiceTest {

    @Test
    fun testExtraction() {
        println("--- Starting YouTubeExtractor test ---")
        try {
            YouTubeExtractorService.init()
            val videoUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
            val streamInfo = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(org.schabi.newpipe.extractor.ServiceList.YouTube, videoUrl)
            println("Stream title: ${streamInfo.name}")
            println("Audio streams count: ${streamInfo.audioStreams?.size}")
            streamInfo.audioStreams?.forEach {
                println("Audio stream: bitrate=${it.bitrate}, format=${it.format}, url=${it.content}")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        val streamUrl = YouTubeExtractorService.resolveAudioStreamUrlSync("dQw4w9WgXcQ")
        println("Resolved stream URL: $streamUrl")
        assert(!streamUrl.isNullOrBlank()) { "Stream URL should not be null or blank" }
    }

    @Test
    fun testSearch() = kotlinx.coroutines.runBlocking {
        println("--- Starting YouTubeExtractor search test ---")
        val results = YouTubeExtractorService.search("Adele", 5)
        println("Search results count: ${results.size}")
        results.forEach {
            println("Result: id=${it.externalId}, title=${it.title}, artist=${it.artist}")
        }
        assert(results.isNotEmpty()) { "Search results should not be empty" }
    }

    @Test
    fun testMusicSongsFilter() = kotlinx.coroutines.runBlocking {
        val results = YouTubeExtractorService.search("японська музика", 10)
        println("--- японська музика count: ${results.size} ---")
        results.forEach {
            println("Result: id=${it.externalId}, title=${it.title}, artist=${it.artist}, dur=${it.durationMs}")
        }
        assert(results.isNotEmpty()) { "Search results should not be empty" }
    }

    @Test
    fun testSearchCacheHit() = kotlinx.coroutines.runBlocking {
        val start1 = System.currentTimeMillis()
        val firstResults = YouTubeExtractorService.search("phonk", 5)
        val duration1 = System.currentTimeMillis() - start1

        val start2 = System.currentTimeMillis()
        val secondResults = YouTubeExtractorService.search("phonk", 5)
        val duration2 = System.currentTimeMillis() - start2

        println("First search took ${duration1}ms, second search (cache hit) took ${duration2}ms")
        assert(secondResults.size == firstResults.size) { "Cache hit should return identical list" }
        assert(duration2 < 50) { "Second search should be instant from memory cache (<50ms)" }
    }

    @Test
    fun testExtractVideoId() {
        assert(YouTubeExtractorService.extractVideoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ") == "dQw4w9WgXcQ")
        assert(YouTubeExtractorService.extractVideoIdFromUrl("https://youtu.be/dQw4w9WgXcQ?t=4") == "dQw4w9WgXcQ")
        assert(YouTubeExtractorService.extractVideoIdFromUrl("youtube://dQw4w9WgXcQ") == "dQw4w9WgXcQ")
        assert(YouTubeExtractorService.extractVideoIdFromUrl("youtube_dQw4w9WgXcQ") == "dQw4w9WgXcQ")
        assert(YouTubeExtractorService.extractVideoIdFromUrl("https://rr1---sn.googlevideo.com/videoplayback") == null)
    }
}

