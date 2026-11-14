package com.musicplayer.android.core

import com.musicplayer.android.core.audio.ExternalPlaylistService
import com.musicplayer.android.core.audio.ExternalPlaylistSource
import com.musicplayer.android.core.audio.ExternalPlaylistSummary
import com.musicplayer.android.core.network.TrackSourceResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlaylistServiceTest {

    @Test
    fun `parsePlaylistUrlOrId parses YouTube, YouTube Music, watch-list, and bare IDs`() {
        val ytStandard = ExternalPlaylistService.parsePlaylistUrlOrId(
            "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        )
        assertNotNull(ytStandard)
        assertEquals(ExternalPlaylistSource.YOUTUBE.id, ytStandard?.source)
        assertEquals("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", ytStandard?.id)
        assertEquals(
            "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI",
            ytStandard?.canonicalUrl
        )

        val ytMusic = ExternalPlaylistService.parsePlaylistUrlOrId(
            "https://music.youtube.com/playlist?list=OLAK5uy_nTestPlaylist12345"
        )
        assertEquals(ExternalPlaylistSource.YOUTUBE.id, ytMusic?.source)
        assertEquals("OLAK5uy_nTestPlaylist12345", ytMusic?.id)

        val ytWatchMix = ExternalPlaylistService.parsePlaylistUrlOrId(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ&index=2"
        )
        assertEquals(ExternalPlaylistSource.YOUTUBE.id, ytWatchMix?.source)
        assertEquals("RDdQw4w9WgXcQ", ytWatchMix?.id)

        val ytShort = ExternalPlaylistService.parsePlaylistUrlOrId(
            "https://youtu.be/dQw4w9WgXcQ?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"
        )
        assertEquals("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", ytShort?.id)

        val bareId = ExternalPlaylistService.parsePlaylistUrlOrId("  PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI ")
        assertEquals(ExternalPlaylistSource.YOUTUBE.id, bareId?.source)
        assertEquals("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", bareId?.id)
    }

    @Test
    fun `parsePlaylistUrlOrId parses SoundCloud sets and rejects single tracks or queries`() {
        val scSet = ExternalPlaylistService.parsePlaylistUrlOrId(
            "https://soundcloud.com/chillhopdotcom/sets/lofi-hip-hop-beats"
        )
        assertNotNull(scSet)
        assertEquals(ExternalPlaylistSource.SOUNDCLOUD.id, scSet?.source)
        assertEquals("https://soundcloud.com/chillhopdotcom/sets/lofi-hip-hop-beats", scSet?.canonicalUrl)

        val scSchemeless = ExternalPlaylistService.parsePlaylistUrlOrId("soundcloud.com/dj/sets/night-drive/")
        assertNotNull(scSchemeless)
        assertEquals("https://soundcloud.com/dj/sets/night-drive", scSchemeless?.canonicalUrl)

        assertNull(ExternalPlaylistService.parsePlaylistUrlOrId("https://soundcloud.com/artist/single-track"))
        assertNull(ExternalPlaylistService.parsePlaylistUrlOrId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(ExternalPlaylistService.parsePlaylistUrlOrId("ambient electronic mix"))
        assertNull(ExternalPlaylistService.parsePlaylistUrlOrId("   "))
    }

    @Test
    fun `SoundCloud custom URI round-trips and produces backend-valid external track ID`() {
        val pageUrl = "https://soundcloud.com/artist-one/track-two?in=artist-one/sets/chill-mix"
        val encoded = ExternalPlaylistService.encodeSoundCloudStreamUri(pageUrl)
        assertTrue(encoded.startsWith("soundcloud://url/"))
        assertEquals(pageUrl, ExternalPlaylistService.decodeSoundCloudStreamUri(encoded))
        assertNull(ExternalPlaylistService.decodeSoundCloudStreamUri("youtube://dQw4w9WgXcQ"))

        val extId = ExternalPlaylistService.buildExternalSoundCloudTrackId(pageUrl, "Track Two", "Artist One")
        assertTrue(extId.isNotBlank() && extId.length <= 200)
        val resolved = TrackSourceResolver.resolveFromId("soundcloud_$extId")
        assertEquals("soundcloud" to extId, resolved)
    }

    @Test
    fun `mergePlaylistSummaries interleaves sources and deduplicates identical playlist URLs`() {
        val yt = listOf(
            ExternalPlaylistSummary("1", "youtube", "YT 1", "A", "https://youtube.com/playlist?list=PL1"),
            ExternalPlaylistSummary("2", "youtube", "YT 2", "B", "https://youtube.com/playlist?list=PL2")
        )
        val sc = listOf(
            ExternalPlaylistSummary("s1", "soundcloud", "SC 1", "C", "https://soundcloud.com/c/sets/1"),
            ExternalPlaylistSummary("dup", "youtube", "YT 1 dup", "A", "https://youtube.com/playlist?list=PL1")
        )
        val merged = ExternalPlaylistService.mergePlaylistSummaries(yt, sc)
        assertEquals(3, merged.size)
        assertEquals("YT 1", merged[0].title)
        assertEquals("SC 1", merged[1].title)
        assertEquals("YT 2", merged[2].title)
    }
}
