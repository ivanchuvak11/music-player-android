package com.musicplayer.android.core

import com.musicplayer.android.core.network.AddFavoriteRadioRequestDto
import com.musicplayer.android.core.network.AuthResponseDto
import com.musicplayer.android.core.network.PlaylistDetailDto
import com.musicplayer.android.core.network.PlaylistSummaryDto
import com.musicplayer.android.core.network.RadioStationDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testRadioSearchResponseDeserialization() {
        val rawJson = """
            [
              {
                "stationId": "01b61e49-18bd-486d-b0e1-cb51cbaf9a6d",
                "name": "Station Example",
                "streamUrl": "http://stream.example.com/stream.mp3",
                "logoUrl": "https://example.com/logo.png",
                "country": "Example Country",
                "countryCode": "EC",
                "genre": "pop",
                "codec": "MP3",
                "bitrate": 128
              }
            ]
        """.trimIndent()

        val stations = json.decodeFromString<List<RadioStationDto>>(rawJson)
        assertEquals(1, stations.size)

        val station = stations.first()
        assertEquals("01b61e49-18bd-486d-b0e1-cb51cbaf9a6d", station.stationId)
        assertEquals("Station Example", station.name)
        assertEquals("http://stream.example.com/stream.mp3", station.streamUrl)
        assertEquals("Example Country", station.country)
        assertEquals("EC", station.countryCode)
        assertEquals("MP3", station.codec)
        assertEquals(128, station.bitrate)
        assertNull(station.id)
        assertNull(station.createdAt)
    }

    @Test
    fun testFavoriteRadioDeserialization() {
        val rawJson = """
            [
              {
                "id": 5,
                "stationId": "station_abc",
                "name": "Station Two",
                "streamUrl": "http://stream.example.com/station2",
                "logoUrl": "http://example.com/logo2.png",
                "country": "Country Two",
                "genre": "pop",
                "createdAt": "2026-09-26T12:00:00Z"
              }
            ]
        """.trimIndent()

        val favorites = json.decodeFromString<List<RadioStationDto>>(rawJson)
        assertEquals(1, favorites.size)

        val fav = favorites.first()
        assertEquals(5, fav.id)
        assertEquals("station_abc", fav.stationId)
        assertEquals("Station Two", fav.name)
        assertEquals("Country Two", fav.country)
        assertEquals("2026-09-26T12:00:00Z", fav.createdAt)
    }

    @Test
    fun testAddFavoriteRadioSerialization() {
        val request = AddFavoriteRadioRequestDto(stationId = "uuid-12345")
        val serialized = json.encodeToString(AddFavoriteRadioRequestDto.serializer(), request)
        assertNotNull(serialized)

        val deserialized = json.decodeFromString<AddFavoriteRadioRequestDto>(serialized)
        assertEquals("uuid-12345", deserialized.stationId)
    }

    @Test
    fun testAuthResponseDeserialization() {
        val rawJson = """
            {
              "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy",
              "userId": 42,
              "username": "maksym",
              "email": "maksym@example.com"
            }
        """.trimIndent()

        val auth = json.decodeFromString<AuthResponseDto>(rawJson)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy", auth.token)
        assertEquals(42, auth.userId)
        assertEquals("maksym", auth.username)
        assertEquals("maksym@example.com", auth.email)
    }

    @Test
    fun testPlaylistsDeserialization() {
        val rawJson = """
            [
              {
                "id": 1,
                "name": "Gym Workout",
                "createdAt": "2026-09-26T11:00:00Z",
                "trackCount": 15
              }
            ]
        """.trimIndent()

        val playlists = json.decodeFromString<List<PlaylistSummaryDto>>(rawJson)
        assertEquals(1, playlists.size)
        assertEquals("Gym Workout", playlists[0].name)
        assertEquals(15, playlists[0].trackCount)
    }
}
