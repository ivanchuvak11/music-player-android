package com.musicplayer.android.core.audio

import android.net.Uri
import android.util.Log
import com.musicplayer.android.core.network.YouTubeTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private const val MAX_STREAM_CACHE_SIZE = 150
    private data class CachedStream(val url: String, val expiresAt: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    private fun putStreamInCache(id: String, stream: CachedStream) {
        if (streamCache.size >= MAX_STREAM_CACHE_SIZE) {
            val now = System.currentTimeMillis()
            // Evict expired entries
            val iterator = streamCache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now >= entry.value.expiresAt) {
                    iterator.remove()
                }
            }
            // If still over capacity, remove arbitrary excess
            if (streamCache.size >= MAX_STREAM_CACHE_SIZE) {
                val excess = streamCache.keys.take(streamCache.size - MAX_STREAM_CACHE_SIZE + 1)
                excess.forEach { streamCache.remove(it) }
            }
        }
        streamCache[id] = stream
    }

    // In-memory LRU query caches: 40 entries, 15 min TTL (0ms latency on repeated queries or tab switches)
    private data class CachedSearchResults(val tracks: List<YouTubeTrackDto>, val timestamp: Long)
    private const val MAX_SEARCH_CACHE = 40
    private const val SEARCH_CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes

    private val searchCacheLock = Any()
    private val searchCache = object : LinkedHashMap<String, CachedSearchResults>(MAX_SEARCH_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearchResults>?): Boolean {
            return size > MAX_SEARCH_CACHE
        }
    }

    private val relatedCache = object : LinkedHashMap<String, CachedSearchResults>(MAX_SEARCH_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearchResults>?): Boolean {
            return size > MAX_SEARCH_CACHE
        }
    }

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

    private val prefetchJob = kotlinx.coroutines.SupervisorJob()
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + prefetchJob)

    /**
     * Pre-resolves audio stream URLs for upcoming tracks in background concurrently.
     * When user skips or track transitions, playback starts with 0ms delay!
     */
    fun prefetchNextTracks(videoIds: List<String>) {
        if (videoIds.isEmpty()) return
        prefetchScope.launch {
            videoIds.take(4).forEach { id ->
                launch {
                    val cleanId = id.trim().removePrefix("youtube_")
                    if (cleanId.isNotBlank()) {
                        val now = System.currentTimeMillis()
                        val cached = streamCache[cleanId]
                        if (cached == null || now >= cached.expiresAt - 300_000L) {
                            try {
                                Log.d(TAG, "Prefetching audio stream for track: videoId=$cleanId")
                                resolveAudioStreamUrlSync(cleanId)
                            } catch (e: Exception) {
                                Log.w(TAG, "Prefetch failed for $cleanId: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
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
                // Cache for 2 hours (Google Video CDN URLs are valid for ~6 hours) with LRU bounded capacity
                putStreamInCache(
                    cleanId,
                    CachedStream(
                        url = streamUrl,
                        expiresAt = now + (2 * 3600 * 1000L)
                    )
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
     * Prioritizes YouTube Music songs (music_songs filter) for pristine studio track results,
     * cleans out podcasts/long compilations (>20min), and falls back to videos if needed.
     */
    suspend fun search(query: String, limit: Int = 20): List<YouTubeTrackDto> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val normalizedQuery = query.trim().lowercase()
        val cacheKey = "${normalizedQuery}_$limit"
        val now = System.currentTimeMillis()

        // 0ms instant cache hit on backspace, tab switch, or repeated queries
        synchronized(searchCacheLock) {
            val cached = searchCache[cacheKey]
            if (cached != null && now - cached.timestamp < SEARCH_CACHE_TTL_MS) {
                Log.d(TAG, "Instant search cache HIT for '$query' (${cached.tracks.size} tracks)")
                return@withContext cached.tracks
            }
        }

        init()

        return@withContext try {
            val service = ServiceList.YouTube
            val tracks = mutableListOf<YouTubeTrackDto>()
            val seenIds = mutableSetOf<String>()

            fun collectItems(items: List<Any?>) {
                for (item in items) {
                    if (item is StreamInfoItem) {
                        val durationSec = item.duration
                        // Filter out podcasts, 1-hour albums, or 5-second junk clips (songs are 15s to 20min)
                        if (durationSec > 1200L || (durationSec > 0 && durationSec < 15L)) continue

                        val id = try {
                            service.streamLHFactory.getId(item.url)
                        } catch (e: Exception) {
                            item.url.substringAfter("v=").substringBefore("&")
                        }

                        if (id.isNotBlank() && seenIds.add(id)) {
                            val thumbnail = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                                ?: item.thumbnails.firstOrNull()?.url

                            tracks.add(
                                YouTubeTrackDto(
                                    source = "youtube",
                                    externalId = id,
                                    title = item.name,
                                    artist = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                                    artworkUrl = thumbnail,
                                    durationMs = if (durationSec > 0) durationSec * 1000L else null,
                                    youTubeUrl = item.url,
                                    streamUrl = null // Resolved lazily on playback
                                )
                            )
                            if (tracks.size >= limit) break
                        }
                    }
                }
            }

            // 1. Primary: YouTube Music songs filter (pure studio tracks, clean artist and title)
            try {
                val musicHandler = service.searchQHFactory.fromQuery(query.trim(), listOf("music_songs"), "")
                val musicSearchInfo = SearchInfo.getInfo(service, musicHandler)
                collectItems(musicSearchInfo.relatedItems)
            } catch (e: Exception) {
                Log.w(TAG, "music_songs search failed for '$query', falling back: ${e.message}")
            }

            // 2. Secondary: Supplement up to limit with regular video search (for thematic/genre queries and clips)
            if (tracks.size < limit) {
                try {
                    val videoHandler = service.searchQHFactory.fromQuery(query.trim(), listOf("videos"), "")
                    val videoSearchInfo = SearchInfo.getInfo(service, videoHandler)
                    collectItems(videoSearchInfo.relatedItems)
                } catch (e: Exception) {
                    try {
                        val fallbackHandler = service.searchQHFactory.fromQuery(query.trim())
                        val fallbackInfo = SearchInfo.getInfo(service, fallbackHandler)
                        collectItems(fallbackInfo.relatedItems)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Video fallback search failed for '$query': ${e2.message}")
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                synchronized(searchCacheLock) {
                    searchCache[cacheKey] = CachedSearchResults(tracks, now)
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
     * Fetches related / similar tracks for a given YouTube video ID.
     * Used by AutoplayService to extend the queue when it reaches the last track.
     *
     * Returns up to [limit] tracks ordered by their position in YouTube's sidebar
     * (top = most relevant) with podcasts and junk clips filtered out.
     */
    suspend fun getRelatedTracks(videoId: String, limit: Int = 15): List<YouTubeTrackDto> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        val cleanId = videoId.trim().removePrefix("youtube_")
        val cacheKey = "${cleanId}_$limit"
        val now = System.currentTimeMillis()

        // 0ms instant cache hit on back-navigation or repeat mix requests
        synchronized(searchCacheLock) {
            val cached = relatedCache[cacheKey]
            if (cached != null && now - cached.timestamp < SEARCH_CACHE_TTL_MS) {
                Log.d(TAG, "Instant related tracks cache HIT for '$cleanId' (${cached.tracks.size} tracks)")
                return@withContext cached.tracks
            }
        }

        init()
        try {
            val videoUrl = "https://www.youtube.com/watch?v=$cleanId"
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
            val related = streamInfo.relatedItems ?: return@withContext emptyList()
            val service = ServiceList.YouTube
            val results = mutableListOf<YouTubeTrackDto>()
            val seenIds = mutableSetOf<String>()
            for (item in related) {
                if (item !is StreamInfoItem) continue
                val durationSec = item.duration
                if (durationSec > 1200L || (durationSec > 0 && durationSec < 15L)) continue
                val id = try {
                    service.streamLHFactory.getId(item.url)
                } catch (e: Exception) {
                    item.url.substringAfter("v=").substringBefore("&")
                }
                if (id.isBlank() || !seenIds.add(id)) continue
                val thumbnail = item.thumbnails.maxByOrNull { it.width * it.height }?.url
                    ?: item.thumbnails.firstOrNull()?.url
                results.add(
                    YouTubeTrackDto(
                        source = "youtube",
                        externalId = id,
                        title = item.name,
                        artist = item.uploaderName.orEmpty().ifBlank { "YouTube" },
                        artworkUrl = thumbnail,
                        durationMs = if (durationSec > 0) durationSec * 1000L else null,
                        youTubeUrl = item.url,
                        streamUrl = null
                    )
                )
                if (results.size >= limit) break
            }

            if (results.isNotEmpty()) {
                synchronized(searchCacheLock) {
                    relatedCache[cacheKey] = CachedSearchResults(results, now)
                }
            }

            Log.d(TAG, "Related tracks for videoId=$cleanId: ${results.size} found")
            results
        } catch (e: Exception) {
            Log.e(TAG, "getRelatedTracks failed for videoId=$cleanId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Extracts YouTube video ID from a playback URL string if applicable.
     */
    fun extractVideoIdFromUrl(uriString: String): String? {
        if (uriString.isBlank()) return null

        // 1. If it's already a direct Google Video CDN URL, don't modify
        if (uriString.contains("googlevideo.com")) return null

        // 2. Backend YouTube stream proxy URL: /api/youtube/tracks/{id}/stream
        if (uriString.contains("/api/youtube/tracks/")) {
            val partAfter = uriString.substringAfter("/api/youtube/tracks/")
            val videoId = partAfter.substringBefore("/").substringBefore("?").removePrefix("youtube_")
            if (videoId.isNotBlank()) return videoId
        }

        // 3. YouTube custom scheme: youtube://{id} or youtube_{id}
        if (uriString.startsWith("youtube://", ignoreCase = true)) {
            val part = uriString.substringAfter("youtube://")
            val videoId = part.substringBefore("/").substringBefore("?").removePrefix("youtube_")
            if (videoId.isNotBlank()) return videoId
        }
        if (uriString.startsWith("youtube_")) {
            val videoId = uriString.removePrefix("youtube_").substringBefore("/").substringBefore("?")
            if (videoId.isNotBlank()) return videoId
        }

        // 4. Web URLs: youtube.com/watch?v={id} or youtu.be/{id}
        if (uriString.contains("youtube.com/watch")) {
            val vParam = uriString.substringAfter("v=", "").substringBefore("&").substringBefore("#")
            if (vParam.isNotBlank()) return vParam
        }
        if (uriString.contains("youtu.be/")) {
            val segment = uriString.substringAfter("youtu.be/").substringBefore("/").substringBefore("?").substringBefore("#")
            if (segment.isNotBlank()) return segment
        }

        return null
    }

    /**
     * Extracts YouTube video ID from a playback URI if applicable.
     */
    fun extractVideoIdFromUri(uri: Uri): String? {
        return extractVideoIdFromUrl(uri.toString())
    }
}

