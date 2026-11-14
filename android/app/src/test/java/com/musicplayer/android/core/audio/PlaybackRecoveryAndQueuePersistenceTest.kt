package com.musicplayer.android.core.audio

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.musicplayer.android.core.session.SessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlaybackRecoveryAndQueuePersistenceTest {

    @Test
    fun isExpiredStreamPlaybackError_detectsHttp403And410InCauseChain() {
        val dataSpec = DataSpec(Uri.EMPTY)
        val http403 = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            IOException("403"),
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val wrapped403 = PlaybackException(
            "Source error",
            RuntimeException(http403),
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertTrue(isExpiredStreamPlaybackError(wrapped403))

        val http410 = HttpDataSource.InvalidResponseCodeException(
            410,
            "Gone",
            IOException("410"),
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val wrapped410 = PlaybackException(
            "Gone",
            http410,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertTrue(isExpiredStreamPlaybackError(wrapped410))
    }

    @Test
    fun isExpiredStreamPlaybackError_ignoresNonExpiredHttpCodes() {
        val dataSpec = DataSpec(Uri.EMPTY)
        val http500 = HttpDataSource.InvalidResponseCodeException(
            500,
            "Server Error",
            IOException("500"),
            emptyMap(),
            dataSpec,
            ByteArray(0)
        )
        val wrapped500 = PlaybackException(
            "Server error",
            http500,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )
        assertFalse(isExpiredStreamPlaybackError(wrapped500))
    }

    @Test
    fun sliceQueueWindow_preservesCurrentIndexWhenQueueExceedsMaxSize() {
        val tracks = (0 until 150).map { idx ->
            AudioTrack(
                id = "t_$idx",
                title = "Track $idx",
                artist = "Artist",
                audioUrl = "https://example.com/$idx.mp3"
            )
        }

        val (sliced, adjustedIndex) = SessionManager.sliceQueueWindow(tracks, currentIndex = 75, maxSize = 100)
        assertEquals(100, sliced.size)
        assertEquals("t_75", sliced[adjustedIndex].id)
    }

    @Test
    fun sliceQueueWindow_handlesEdgeIndicesCleanly() {
        val tracks = (0 until 120).map { idx ->
            AudioTrack(
                id = "t_$idx",
                title = "Track $idx",
                artist = "Artist",
                audioUrl = "https://example.com/$idx.mp3"
            )
        }

        val (headSlice, headIdx) = SessionManager.sliceQueueWindow(tracks, currentIndex = 0, maxSize = 100)
        assertEquals(100, headSlice.size)
        assertEquals(0, headIdx)
        assertEquals("t_0", headSlice[headIdx].id)

        val (tailSlice, tailIdx) = SessionManager.sliceQueueWindow(tracks, currentIndex = 119, maxSize = 100)
        assertEquals(100, tailSlice.size)
        assertEquals(99, tailIdx)
        assertEquals("t_119", tailSlice[tailIdx].id)
    }
}
