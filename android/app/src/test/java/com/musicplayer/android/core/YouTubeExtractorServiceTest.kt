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
}

