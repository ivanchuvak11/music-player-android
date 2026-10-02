package com.musicplayer.android.core.audio

import android.net.Uri
import android.util.Log
import com.musicplayer.android.core.network.YouTubeTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Client-side YouTube audio extractor service using NewPipeExtractor.
 * Extracts direct Google Video CDN audio stream URLs and searches YouTube directly
 * on the mobile device (residential / cellular network), bypassing datacenter IP bans on Oracle Cloud.
 */
object YouTubeExtractorService {
    private const val TAG = "YouTubeExtractor"
    private var isInitialized = false

    private data class CachedStream(val url: String, val expiresAt: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    @Synchronized
    fun init() {
        if (!isInitialized) {
            try {
                NewPipe.init(NewPipeDownloader())
                isInitialized = true
                Log.i(TAG, "NewPipeExtractor successfully initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing NewPipeExtractor: ${e.message}", e)
            }
        }
    }

    /**
     * Resolves direct Google Video audio stream URL for a given video ID on the device.
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        resolveAudioStreamUrlSync(videoId)
    }

    /**
     * Synchronous stream resolution (called by ResolvingDataSource on ExoPlayer background loading thread).
     */
    fun resolveAudioStreamUrlSync(videoId: String): String? {
        if (videoId.isBlank()) return null
        init()

        val cleanId = videoId.trim().removePrefix("youtube_")
        val now = System.currentTimeMillis()

        // 1. Check cache (valid if more than 5 minutes remaining)
        streamCache[cleanId]?.let { cached ->
            if (now < cached.expiresAt - 300_000L) {
                Log.d(TAG, "Using cached audio stream URL for videoId=$cleanId")
                return cached.url
            }
        }

        return try {
            val videoUrl = "https://www.youtube.com/watch?v=$cleanId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val audioStreams = streamInfo.audioStreams

            if (audioStreams.isNullOrEmpty()) {
                Log.w(TAG, "No audio streams found for videoId=$cleanId")
                return null
            }

            // Pick highest bitrate audio stream
            val bestAudio = audioStreams.maxByOrNull { it.bitrate } ?: audioStreams.first()
            val streamUrl = bestAudio.content

            if (!streamUrl.isNullOrBlank()) {
                // Cache for 2 hours (Google Video CDN URLs are valid for ~6 hours)
                streamCache[cleanId] = CachedStream(
                    url = streamUrl,
                    expiresAt = now + (2 * 3600 * 1000L)
                )
                Log.i(TAG, "Resolved audio stream URL for videoId=$cleanId (bitrate=${bestAudio.bitrate}bps)")
                streamUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for videoId=$cleanId: ${e.message}", e)
            null
        }
    }

    /**
     * Searches YouTube directly from the device via NewPipeExtractor.
     */
    suspend fun search(query: String, limit: Int = 20): List<YouTubeTrackDto> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        init()

        return@withContext try {
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(query.trim())
            val searchInfo = SearchInfo.getInfo(service, handler)

            val tracks = mutableListOf<YouTubeTrackDto>()
            for (item in searchInfo.relatedItems) {
                if (item is StreamInfoItem) {
                    val id = try {
                        service.streamLHFactory.getId(item.url)
                    } catch (e: Exception) {
                        item.url.substringAfter("v=").substringBefore("&")
                    }

                    if (id.isNotBlank()) {
                        val thumbnail = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                            ?: item.thumbnails.firstOrNull()?.url

                        tracks.add(
                            YouTubeTrackDto(
                                source = "youtube",
                                externalId = id,
                                title = item.name,
                                artist = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                                artworkUrl = thumbnail,
                                durationMs = if (item.duration > 0) item.duration * 1000L else null,
                                youTubeUrl = item.url,
                                streamUrl = null // Resolved lazily on playback
                            )
                        )
                        if (tracks.size >= limit) break
                    }
                }
            }
            Log.d(TAG, "Search '$query' found ${tracks.size} tracks directly via client extractor")
            tracks
        } catch (e: Exception) {
            Log.e(TAG, "Search failed for '$query': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Extracts YouTube video ID from a playback URI if applicable.
     */
    fun extractVideoIdFromUri(uri: Uri): String? {
        val uriString = uri.toString()

        // 1. If it's already a direct Google Video CDN URL, don't modify
        if (uri.host?.contains("googlevideo.com") == true) return null

        // 2. Backend YouTube stream proxy URL: /api/youtube/tracks/{id}/stream
        if (uriString.contains("/api/youtube/tracks/")) {
            val partAfter = uriString.substringAfter("/api/youtube/tracks/")
            val videoId = partAfter.substringBefore("/").substringBefore("?").removePrefix("youtube_")
            if (videoId.isNotBlank()) return videoId
        }

        // 3. YouTube custom scheme: youtube://{id} or youtube_{id}
        if (uri.scheme.equals("youtube", ignoreCase = true)) {
            val part = uri.schemeSpecificPart.removePrefix("//")
            val videoId = part.substringBefore("/").substringBefore("?").removePrefix("youtube_")
            if (videoId.isNotBlank()) return videoId
        }
        if (uriString.startsWith("youtube_")) {
            val videoId = uriString.removePrefix("youtube_").substringBefore("/").substringBefore("?")
            if (videoId.isNotBlank()) return videoId
        }

        // 4. Web URLs: youtube.com/watch?v={id} or youtu.be/{id}
        if (uri.host?.contains("youtube.com") == true) {
            val vParam = uri.getQueryParameter("v")
            if (!vParam.isNullOrBlank()) return vParam
        }
        if (uri.host?.contains("youtu.be") == true) {
            val segment = uri.lastPathSegment
            if (!segment.isNullOrBlank()) return segment
        }

        return null
    }
}

