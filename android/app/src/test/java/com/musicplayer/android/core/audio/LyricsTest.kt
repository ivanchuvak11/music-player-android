package com.musicplayer.android.core.audio

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LyricsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `LrcParser parses timestamps, multi-timestamps, metadata and offset`() {
        val lrc = """
            [ar:Test Artist]
            [ti:Test Song]
            [offset:+500]
            [00:10.50]First line
            [00:20.125][01:00.00]Repeated chorus
            [00:15]Second line
        """.trimIndent()

        val lines = LrcParser.parse(lrc)
        assertEquals(4, lines.size)
        assertEquals(LyricsLine(10_000L, "First line"), lines[0])
        assertEquals(LyricsLine(14_500L, "Second line"), lines[1])
        assertEquals(LyricsLine(19_625L, "Repeated chorus"), lines[2])
        assertEquals(LyricsLine(59_500L, "Repeated chorus"), lines[3])
    }

    @Test
    fun `LrcParser findActiveLineIndex locates active line via binary search`() {
        val lines = listOf(
            LyricsLine(5_000L, "Line 0"),
            LyricsLine(12_000L, "Line 1"),
            LyricsLine(25_000L, "Line 2")
        )

        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 4_999L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 11_999L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 12_000L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 30_000L))
    }

    @Test
    fun `LyricsService normalizeQuery cleans YouTube tags and splits Artist - Title`() {
        val service = LyricsService(OkHttpClient(), null)

        val q1 = service.normalizeQuery(
            rawTitle = "Okean Elzy - Obijmy (Official Video) [4K]",
            rawArtist = "YouTube"
        )
        assertEquals("Okean Elzy", q1.artistName)
        assertEquals("Obijmy", q1.trackName)

        val q2 = service.normalizeQuery(
            rawTitle = "Blinding Lights (Official Audio) (feat. Someone)",
            rawArtist = "The Weeknd - Topic"
        )
        assertEquals("The Weeknd", q2.artistName)
        assertEquals("Blinding Lights", q2.trackName)
    }

    @Test
    fun `LyricsService prefers local sibling lrc file over network`() = runBlocking {
        val audioDir = tempFolder.newFolder("music")
        val audioFile = File(audioDir, "track_one.mp3").apply { writeText("dummy") }
        File(audioDir, "track_one.lrc").writeText("[00:02.00]Local lyric line")

        var networkCalled = false
        val client = mockOkHttpClient {
            networkCalled = true
            404 to ""
        }

        val service = LyricsService(client, tempFolder.newFolder("cache"))
        val track = AudioTrack(
            id = "local_1",
            title = "Track One",
            artist = "Local Artist",
            audioUrl = audioFile.absolutePath,
            durationMs = 180_000L
        )

        val result = service.fetchLyrics(track)
        assertTrue(result is LyricsFetchResult.Found)
        result as LyricsFetchResult.Found
        assertEquals(LyricsSource.LOCAL_LRC, result.source)
        assertEquals(listOf(LyricsLine(2_000L, "Local lyric line")), result.syncedLines)
        assertEquals(false, networkCalled)
    }

    @Test
    fun `LyricsService falls back from api get 404 to api search and caches result`() = runBlocking {
        var callCount = 0
        val client = mockOkHttpClient { url ->
            callCount++
            if (url.contains("/api/get")) {
                404 to """{"statusCode":404,"error":"NotFound"}"""
            } else {
                200 to """
                    [
                      {
                        "id": 1,
                        "trackName": "Test Song",
                        "artistName": "Test Artist",
                        "duration": 200.0,
                        "instrumental": false,
                        "plainLyrics": "Plain only",
                        "syncedLyrics": null
                      },
                      {
                        "id": 2,
                        "trackName": "Test Song",
                        "artistName": "Test Artist",
                        "duration": 181.0,
                        "instrumental": false,
                        "plainLyrics": "Line A\nLine B",
                        "syncedLyrics": "[00:05.00]Line A\n[00:10.00]Line B"
                      }
                    ]
                """.trimIndent()
            }
        }

        val service = LyricsService(client, tempFolder.newFolder("cache"), "https://lrclib.test")
        val track = AudioTrack(
            id = "yt_123",
            title = "Test Song (Official Video)",
            artist = "Test Artist - Topic",
            audioUrl = "https://example.com/stream.mp3",
            durationMs = 180_000L
        )

        val first = service.fetchLyrics(track)
        assertTrue(first is LyricsFetchResult.Found)
        first as LyricsFetchResult.Found
        assertEquals(LyricsSource.LRCLIB_SYNCED, first.source)
        assertEquals(2, first.syncedLines.size)
        assertEquals(2, callCount)

        // Second call must hit disk cache without any network call
        val second = service.fetchLyrics(track)
        assertTrue(second is LyricsFetchResult.Found)
        second as LyricsFetchResult.Found
        assertEquals(LyricsSource.CACHE, second.source)
        assertEquals(2, second.syncedLines.size)
        assertEquals(2, callCount)
    }

    @Test
    fun `LyricsService skips live streams and supports saveCustomLrc`() = runBlocking {
        var networkCalled = false
        val client = mockOkHttpClient {
            networkCalled = true
            404 to ""
        }
        val service = LyricsService(client, tempFolder.newFolder("cache"), "https://lrclib.test")

        val radioTrack = AudioTrack(
            id = "radio_1",
            title = "Hit FM",
            artist = "Live",
            audioUrl = "https://radio.example/stream",
            isLiveStream = true
        )
        assertEquals(LyricsFetchResult.NotFound, service.fetchLyrics(radioTrack))
        assertEquals(false, networkCalled)

        val regularTrack = AudioTrack(
            id = "track_custom",
            title = "Custom",
            artist = "Artist",
            audioUrl = "https://example.com/a.mp3"
        )
        val saved = service.saveCustomLrc(regularTrack, "[00:01.00]Custom synced line")
        assertTrue(saved is LyricsFetchResult.Found)
        val cached = service.fetchLyrics(regularTrack)
        assertTrue(cached is LyricsFetchResult.Found)
        assertEquals(
            listOf(LyricsLine(1_000L, "Custom synced line")),
            (cached as LyricsFetchResult.Found).syncedLines
        )
    }

    private fun mockOkHttpClient(handler: (String) -> Pair<Int, String>): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val (code, body) = handler(request.url.toString())
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Error")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }
}
