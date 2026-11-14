package com.musicplayer.android.core

import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.network.AuthTokenStore
import com.musicplayer.android.core.network.AuthResponseDto
import com.musicplayer.android.core.network.PublicOverviewDto
import com.musicplayer.android.core.network.SystemReportDto
import com.musicplayer.android.core.network.TokenRefreshAuthenticator
import com.musicplayer.android.core.network.TrackSourceResolver
import com.musicplayer.android.core.network.UserStatsSummaryDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CoreBackendIntegrationAndCacheTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun authResponseSupportsAccessTokenAndRefreshToken() {
        val raw = """
            {
              "token": "legacy-jwt",
              "accessToken": "fresh-access-jwt",
              "refreshToken": "refresh-token-123",
              "expiresIn": 7200,
              "userId": 7,
              "username": "maksym",
              "email": "maks@example.com"
            }
        """.trimIndent()

        val dto = json.decodeFromString<AuthResponseDto>(raw)
        assertEquals("fresh-access-jwt", dto.resolvedAccessToken())
        assertEquals("refresh-token-123", dto.refreshToken)
        assertEquals(7200L, dto.expiresIn)
        assertEquals(7, dto.userId)
    }

    @Test
    fun lab5StatsDtosDeserializeFromBackendJson() {
        val userSummaryJson = """
            {
              "period": "week",
              "startDateUtc": "2026-11-06T00:00:00Z",
              "endDateUtc": "2026-11-13T12:00:00Z",
              "playlistsCreatedCount": 2,
              "tracksAddedCount": 11,
              "favoritesAddedCount": 5,
              "radioFavoritesAddedCount": 1,
              "totalSavedItemsCount": 17,
              "totalDurationMs": 3600000,
              "totalDurationMinutes": 60.0,
              "topArtists": [
                { "artist": "Океан Ельзи", "trackCount": 4 }
              ],
              "sourceBreakdown": [
                { "source": "youtube", "count": 10, "percentage": 58.8 }
              ],
              "recentActivity": [
                {
                  "activityType": "FavoriteTrack",
                  "title": "Обійми",
                  "artistOrDetails": "Океан Ельзи",
                  "source": "youtube",
                  "timestamp": "2026-11-13T10:15:00Z"
                }
              ]
            }
        """.trimIndent()

        val systemReportJson = """
            {
              "period": "week",
              "endDateUtc": "2026-11-13T12:00:00Z",
              "totalUsers": 14,
              "newUsersInPeriod": 3,
              "totalPlaylists": 20,
              "newPlaylistsInPeriod": 4,
              "totalPlaylistTracks": 95,
              "playlistTracksAddedInPeriod": 22,
              "totalFavoriteTracks": 48,
              "favoriteTracksAddedInPeriod": 9,
              "totalFavoriteRadioStations": 6,
              "platformTopArtists": [{ "artist": "Boombox", "trackCount": 7 }],
              "platformSourceDistribution": [{ "source": "soundcloud", "count": 30, "percentage": 21.0 }]
            }
        """.trimIndent()

        val overviewJson = """
            {
              "status": "Online",
              "serverTimeUtc": "2026-11-13T12:00:00Z",
              "supportedAudioSourcesCount": 4,
              "supportedSources": ["YouTube Music", "SoundCloud", "Internet Radio", "Local Storage"],
              "totalPublicPlaylistsCount": 20,
              "totalPublicTracksCount": 95
            }
        """.trimIndent()

        val userSummary = json.decodeFromString<UserStatsSummaryDto>(userSummaryJson)
        assertEquals("week", userSummary.period)
        assertEquals(17, userSummary.totalSavedItemsCount)
        assertEquals(60.0, userSummary.totalDurationMinutes, 0.01)
        assertEquals("Океан Ельзи", userSummary.topArtists.first().artist)
        assertEquals("youtube", userSummary.sourceBreakdown.first().source)
        assertEquals("FavoriteTrack", userSummary.recentActivity.first().activityType)

        val systemReport = json.decodeFromString<SystemReportDto>(systemReportJson)
        assertEquals(14, systemReport.totalUsers)
        assertEquals(3, systemReport.newUsersInPeriod)
        assertEquals("Boombox", systemReport.platformTopArtists.first().artist)

        val overview = json.decodeFromString<PublicOverviewDto>(overviewJson)
        assertEquals("Online", overview.status)
        assertEquals(4, overview.supportedAudioSourcesCount)
        assertEquals(20, overview.totalPublicPlaylistsCount)
    }

    @Test
    fun trackSourceResolverHandlesPrefixesAndRejectsLocalAndRadio() {
        val ytTrack = AudioTrack(
            id = "youtube_dQw4w9WgXcQ",
            title = "Song One",
            artist = "Artist One",
            audioUrl = "https://backend/api/youtube/stream/dQw4w9WgXcQ",
            artworkUrl = "https://img.example.com/1.jpg",
            durationMs = 210_000L
        )
        val ytRef = TrackSourceResolver.resolveTrackRef(ytTrack)
        assertNotNull(ytRef)
        assertEquals("youtube", ytRef!!.source)
        assertEquals("dQw4w9WgXcQ", ytRef.externalId)
        assertEquals("https://img.example.com/1.jpg", ytRef.artworkUrl)

        val scTrack = ytTrack.copy(
            id = "soundcloud_987654",
            artworkUrl = ""
        )
        val scRef = TrackSourceResolver.resolveTrackRef(scTrack)
        assertNotNull(scRef)
        assertEquals("soundcloud", scRef!!.source)
        assertEquals("987654", scRef.externalId)
        assertNull(scRef.artworkUrl)

        val localTrack = ytTrack.copy(id = "local_1001", isLocal = true)
        assertNull(TrackSourceResolver.resolveTrackRef(localTrack))

        val radioTrack = ytTrack.copy(id = "radio_hitfm_ua", isLiveStream = true)
        assertNull(TrackSourceResolver.resolveTrackRef(radioTrack))
    }

    @Test
    fun trackSourceResolverBuildsPlayableAudioTracksFromCloudDtos() {
        val baseUrl = "http://152.70.19.219/"
        val yt = TrackSourceResolver.buildAudioTrackFromCloud(
            source = "youtube",
            externalId = "dQw4w9WgXcQ",
            rawTrackId = null,
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            artworkUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/0.jpg",
            rawAudioUrl = null,
            durationMs = 213_000L,
            baseUrl = baseUrl
        )
        assertNotNull(yt)
        assertEquals("youtube_dQw4w9WgXcQ", yt!!.id)
        assertEquals("http://152.70.19.219/api/youtube/tracks/dQw4w9WgXcQ/stream", yt.audioUrl)
        assertFalse(yt.isLocal)

        val sc = TrackSourceResolver.buildAudioTrackFromCloud(
            source = "soundcloud",
            externalId = "987654",
            rawTrackId = null,
            title = "Cloud Mix",
            artist = "DJ",
            artworkUrl = "content://invalid",
            rawAudioUrl = null,
            durationMs = 150_000L,
            baseUrl = baseUrl
        )
        assertNotNull(sc)
        assertEquals("soundcloud_987654", sc!!.id)
        assertEquals("http://152.70.19.219/api/soundcloud/tracks/987654/stream", sc.audioUrl)
        assertNull(sc.artworkUrl)

        assertNull(
            TrackSourceResolver.buildAudioTrackFromCloud(
                source = "local",
                externalId = "42",
                rawTrackId = "local_42",
                title = "Local",
                artist = "Local",
                artworkUrl = null,
                rawAudioUrl = null,
                durationMs = 1000L,
                baseUrl = baseUrl
            )
        )
    }

    @Test
    fun cachedTrackEntityRequiresValidPhysicalAudioFile() {
        val emptyMeta = CachedTrackEntity(
            id = "youtube_meta",
            title = "Meta",
            artist = "Artist",
            localFilePath = null,
            originalUrl = "https://example.com/stream",
            durationMs = 180_000L,
            cachedAtTimestamp = 1000L
        )
        assertFalse(emptyMeta.hasValidDownloadedFile())

        val tempFile = File.createTempFile("cache_test_", ".m4a")
        try {
            tempFile.writeBytes(ByteArray(1024))
            assertFalse(emptyMeta.copy(localFilePath = tempFile.absolutePath).hasValidDownloadedFile())

            tempFile.writeBytes(ByteArray(60_000))
            assertTrue(emptyMeta.copy(localFilePath = tempFile.absolutePath).hasValidDownloadedFile())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun tokenRefreshAuthenticatorRotatesExpiredJwtAndRetriesRequest() {
        val refreshCalls = AtomicInteger(0)
        val rejectedCalled = AtomicBoolean(false)

        var currentAccessToken = "expired-token"
        var currentRefreshToken: String? = "refresh-v1"

        val serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val startedLatch = CountDownLatch(1)
        val serverThread = Thread {
            startedLatch.countDown()
            try {
                while (!serverSocket.isClosed) {
                    val socket = serverSocket.accept()
                    socket.use { s ->
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        val requestLine = reader.readLine() ?: return@use
                        var authHeader: String? = null
                        var contentLength = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Authorization:", ignoreCase = true)) {
                                authHeader = line.substringAfter(':').trim()
                            } else if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                            }
                        }
                        if (contentLength > 0) {
                            val buf = CharArray(contentLength)
                            reader.read(buf, 0, contentLength)
                        }

                        val (status, body) = when {
                            requestLine.startsWith("POST /api/auth/refresh") -> {
                                refreshCalls.incrementAndGet()
                                "200 OK" to """{"token":"fresh-token","accessToken":"fresh-token","refreshToken":"refresh-v2","expiresIn":7200}"""
                            }
                            requestLine.startsWith("GET /api/playlists") && authHeader == "Bearer fresh-token" -> {
                                "200 OK" to """[]"""
                            }
                            else -> {
                                "401 Unauthorized" to """{"message":"expired"}"""
                            }
                        }

                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val response = buildString {
                            append("HTTP/1.1 ").append(status).append("\r\n")
                            append("Content-Type: application/json\r\n")
                            append("Content-Length: ").append(bytes.size).append("\r\n")
                            append("Connection: close\r\n\r\n")
                        }.toByteArray(Charsets.UTF_8) + bytes
                        s.getOutputStream().write(response)
                        s.getOutputStream().flush()
                    }
                }
            } catch (_: Exception) {
            }
        }
        serverThread.isDaemon = true
        serverThread.start()
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS))

        try {
            val port = serverSocket.localPort
            val baseUrl = "http://127.0.0.1:$port/"

            val store = object : AuthTokenStore {
                override fun getAccessToken(): String? = currentAccessToken
                override fun getRefreshToken(): String? = currentRefreshToken
                override fun saveTokens(accessToken: String, refreshToken: String?) {
                    currentAccessToken = accessToken
                    currentRefreshToken = refreshToken
                }

                override fun onRefreshRejected() {
                    rejectedCalled.set(true)
                }
            }

            val authenticator = TokenRefreshAuthenticator(
                tokenStore = store,
                baseUrlProvider = { baseUrl },
                json = json
            )

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("Authorization", "Bearer $currentAccessToken")
                        .build()
                    chain.proceed(req)
                }
                .authenticator(authenticator)
                .build()

            val resp = client.newCall(
                Request.Builder().url("${baseUrl}api/playlists").get().build()
            ).execute()

            resp.use {
                assertEquals(200, it.code)
                assertEquals("[]", it.body?.string())
            }
            assertEquals(1, refreshCalls.get())
            assertEquals("fresh-token", currentAccessToken)
            assertEquals("refresh-v2", currentRefreshToken)
            assertFalse(rejectedCalled.get())

            // Login 401 must never trigger refresh or session expiry callback
            val loginResp = client.newCall(
                Request.Builder()
                    .url("${baseUrl}api/auth/login")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
            ).execute()
            loginResp.use {
                assertEquals(401, it.code)
            }
            assertEquals(1, refreshCalls.get())
            assertFalse(rejectedCalled.get())
        } finally {
            serverSocket.close()
        }
    }

    @Test
    fun tokenRefreshAuthenticatorCallsOnRefreshRejectedWhenRefreshFails() {
        val rejectedCalled = AtomicBoolean(false)
        val serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val startedLatch = CountDownLatch(1)
        val serverThread = Thread {
            startedLatch.countDown()
            try {
                while (!serverSocket.isClosed) {
                    val socket = serverSocket.accept()
                    socket.use { s ->
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val body = """{"message":"invalid refresh"}"""
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val response = ("HTTP/1.1 401 Unauthorized\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray(Charsets.UTF_8) + bytes
                        s.getOutputStream().write(response)
                        s.getOutputStream().flush()
                    }
                }
            } catch (_: Exception) {
            }
        }
        serverThread.isDaemon = true
        serverThread.start()
        assertTrue(startedLatch.await(2, TimeUnit.SECONDS))

        try {
            val baseUrl = "http://127.0.0.1:${serverSocket.localPort}/"
            val store = object : AuthTokenStore {
                override fun getAccessToken(): String = "stale-token"
                override fun getRefreshToken(): String = "revoked-refresh"
                override fun saveTokens(accessToken: String, refreshToken: String?) {}
                override fun onRefreshRejected() {
                    rejectedCalled.set(true)
                }
            }

            val client = OkHttpClient.Builder()
                .authenticator(
                    TokenRefreshAuthenticator(
                        tokenStore = store,
                        baseUrlProvider = { baseUrl },
                        json = json
                    )
                )
                .build()

            val resp = client.newCall(
                Request.Builder()
                    .url("${baseUrl}api/stats/user-summary")
                    .header("Authorization", "Bearer stale-token")
                    .get()
                    .build()
            ).execute()

            resp.use {
                assertEquals(401, it.code)
            }
            assertTrue(rejectedCalled.get())
        } finally {
            serverSocket.close()
        }
    }
}
