package com.musicplayer.android.core.audio

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class ExternalPlaylistSource(val id: String, val label: String) {
    ALL("all", "Усі сервіси"),
    YOUTUBE("youtube", "YouTube"),
    SOUNDCLOUD("soundcloud", "SoundCloud"),
    CURATED("curated", "Добірка")
}

@Serializable
data class ExternalPlaylistSummary(
    val id: String,
    val source: String,
    val title: String,
    val author: String,
    val url: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0
)

data class ExternalPlaylistDetails(
    val summary: ExternalPlaylistSummary,
    val tracks: List<AudioTrack>
)

data class ParsedPlaylistRef(
    val source: String,
    val id: String,
    val canonicalUrl: String
)

/**
 * Client-side extractor and URL resolver for playlists across supported NewPipe services
 * (YouTube / YouTube Music and SoundCloud), without backend dependency.
 */
object ExternalPlaylistService {
    private const val TAG = "ExternalPlaylistService"
    private const val CACHE_TTL_MS = 15L * 60L * 1000L
    private const val STREAM_CACHE_TTL_MS = 25L * 60L * 1000L
    private const val MAX_SEARCH_CACHE = 30
    private const val MAX_DETAIL_CACHE = 25
    private const val MAX_STREAM_CACHE = 80

    private const val SC_URI_PREFIX = "soundcloud://url/"

    private val BARE_YT_PLAYLIST_ID_REGEX = Regex("^(PL|OLAK5uy_|RD|UU|FL)[A-Za-z0-9_-]{10,}$")
    private val YT_LIST_PARAM_REGEX = Regex("[?&]list=([A-Za-z0-9_-]{10,})")
    private val NON_ID_CHARS_REGEX = Regex("[^A-Za-z0-9._-]+")

    private data class TimedEntry<T>(val value: T, val timestampMs: Long = System.currentTimeMillis())

    private val cacheLock = Any()

    private val searchCache = object : LinkedHashMap<String, TimedEntry<List<ExternalPlaylistSummary>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimedEntry<List<ExternalPlaylistSummary>>>?): Boolean {
            return size > MAX_SEARCH_CACHE
        }
    }

    private val detailCache = object : LinkedHashMap<String, TimedEntry<ExternalPlaylistDetails>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimedEntry<ExternalPlaylistDetails>>?): Boolean {
            return size > MAX_DETAIL_CACHE
        }
    }

    private val scStreamCache = object : LinkedHashMap<String, TimedEntry<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TimedEntry<String>>?): Boolean {
            return size > MAX_STREAM_CACHE
        }
    }

    /**
     * Parses a user-entered URL or bare playlist ID into a canonical YouTube or SoundCloud playlist ref.
     * Pure JVM logic (safe for unit tests without Android framework mocks).
     */
    fun parsePlaylistUrlOrId(input: String): ParsedPlaylistRef? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val lower = trimmed.lowercase()

        // 1. Bare YouTube playlist ID (e.g. PL..., OLAK5uy_..., RD...)
        if (!trimmed.contains('/') && !trimmed.contains(' ') && BARE_YT_PLAYLIST_ID_REGEX.matches(trimmed)) {
            return ParsedPlaylistRef(
                source = ExternalPlaylistSource.YOUTUBE.id,
                id = trimmed,
                canonicalUrl = "https://www.youtube.com/playlist?list=$trimmed"
            )
        }

        // 2. SoundCloud set / playlist URL (with or without scheme)
        if (lower.contains("soundcloud.com/")) {
            val normalizedUrl = normalizeHttpUrl(trimmed) ?: return null
            val uri = try {
                URI(normalizedUrl)
            } catch (_: Exception) {
                return null
            }
            val path = uri.path.orEmpty().trimEnd('/')
            if (path.contains("/sets/") && path.substringAfter("/sets/").isNotBlank()) {
                val slug = path.trimStart('/').replace('/', '_').take(200)
                return ParsedPlaylistRef(
                    source = ExternalPlaylistSource.SOUNDCLOUD.id,
                    id = slug,
                    canonicalUrl = "https://soundcloud.com$path"
                )
            }
            return null
        }

        // 3. YouTube / YouTube Music / youtu.be URL with `list=` parameter
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            val listMatch = YT_LIST_PARAM_REGEX.find(trimmed) ?: return null
            val listId = listMatch.groupValues.getOrNull(1)?.takeIf { it.length >= 10 } ?: return null
            return ParsedPlaylistRef(
                source = ExternalPlaylistSource.YOUTUBE.id,
                id = listId,
                canonicalUrl = "https://www.youtube.com/playlist?list=$listId"
            )
        }

        return null
    }

    fun encodeSoundCloudStreamUri(trackPageUrl: String): String {
        val clean = normalizeHttpUrl(trackPageUrl) ?: trackPageUrl.trim()
        val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
        return "$SC_URI_PREFIX$encoded"
    }

    fun decodeSoundCloudStreamUri(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        if (!uriString.startsWith(SC_URI_PREFIX)) return null
        val payload = uriString.removePrefix(SC_URI_PREFIX)
        if (payload.isBlank()) return null
        return try {
            URLDecoder.decode(payload, StandardCharsets.UTF_8.name())
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }
        } catch (_: Exception) {
            null
        }
    }

    fun buildExternalSoundCloudTrackId(trackUrl: String, title: String, artist: String): String {
        val cleanedPath = try {
            URI(normalizeHttpUrl(trackUrl).orEmpty()).path.orEmpty().trim('/')
        } catch (_: Exception) {
            ""
        }
        val slug = cleanedPath
            .replace('/', '_')
            .replace(NON_ID_CHARS_REGEX, "_")
            .trim('_')
        if (slug.isNotBlank()) {
            return slug.take(200)
        }
        val fallbackHash = ("$artist|$title|$trackUrl").hashCode().toUInt().toString(16)
        return "sc_$fallbackHash"
    }

    suspend fun searchPlaylists(
        query: String,
        sourceFilter: ExternalPlaylistSource = ExternalPlaylistSource.ALL,
        limitPerSource: Int = 12
    ): List<ExternalPlaylistSummary> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        // Direct playlist URL/ID lookup if user typed/pasted a valid link into search
        parsePlaylistUrlOrId(cleanQuery)?.let { parsed ->
            val details = fetchPlaylistDetails(parsed.canonicalUrl, sourceHint = parsed.source)
            if (details != null) {
                return listOf(details.summary)
            }
        }

        val cacheKey = "${sourceFilter.id}|${cleanQuery.lowercase()}|$limitPerSource"
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = searchCache[cacheKey]
            if (cached != null && now - cached.timestampMs < CACHE_TTL_MS) {
                return cached.value
            }
        }

        return withContext(Dispatchers.IO) {
            YouTubeExtractorService.init()

            val primaryResults = coroutineScope {
                val ytDeferred = if (sourceFilter == ExternalPlaylistSource.ALL || sourceFilter == ExternalPlaylistSource.YOUTUBE) {
                    async { searchYouTubePlaylistsInternal(cleanQuery, limitPerSource) }
                } else null

                val scDeferred = if (sourceFilter == ExternalPlaylistSource.ALL || sourceFilter == ExternalPlaylistSource.SOUNDCLOUD) {
                    async { searchSoundCloudPlaylistsInternal(cleanQuery, limitPerSource) }
                } else null

                val ytList = ytDeferred?.await().orEmpty()
                val scList = scDeferred?.await().orEmpty()
                mergePlaylistSummaries(ytList, scList)
            }

            val results = if (primaryResults.size < 2) {
                val fallbackQuery = MultilingualSearchHelper.buildOnlineFallbackQueries(cleanQuery).firstOrNull()
                if (fallbackQuery != null) {
                    val fallbackResults = coroutineScope {
                        val ytFb = if (sourceFilter == ExternalPlaylistSource.ALL || sourceFilter == ExternalPlaylistSource.YOUTUBE) {
                            async { searchYouTubePlaylistsInternal(fallbackQuery, limitPerSource) }
                        } else null

                        val scFb = if (sourceFilter == ExternalPlaylistSource.ALL || sourceFilter == ExternalPlaylistSource.SOUNDCLOUD) {
                            async { searchSoundCloudPlaylistsInternal(fallbackQuery, limitPerSource) }
                        } else null

                        mergePlaylistSummaries(ytFb?.await().orEmpty(), scFb?.await().orEmpty())
                    }
                    (primaryResults + fallbackResults).distinctBy { "${it.source}::${it.url}" }
                } else {
                    primaryResults
                }
            } else {
                primaryResults
            }

            if (results.isNotEmpty()) {
                synchronized(cacheLock) {
                    searchCache[cacheKey] = TimedEntry(results)
                }
            }
            results
        }
    }

    suspend fun fetchPlaylistDetails(
        playlistUrlOrId: String,
        sourceHint: String? = null,
        titleHint: String? = null,
        authorHint: String? = null,
        artworkHint: String? = null,
        maxTracks: Int = 60
    ): ExternalPlaylistDetails? {
        val trimmed = playlistUrlOrId.trim()
        if (trimmed.isBlank()) return null

        val parsed = parsePlaylistUrlOrId(trimmed)
        val resolvedSource = when {
            parsed != null -> parsed.source
            sourceHint == ExternalPlaylistSource.SOUNDCLOUD.id -> ExternalPlaylistSource.SOUNDCLOUD.id
            else -> ExternalPlaylistSource.YOUTUBE.id
        }
        val canonicalUrl = parsed?.canonicalUrl ?: when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            resolvedSource == ExternalPlaylistSource.YOUTUBE.id -> "https://www.youtube.com/playlist?list=$trimmed"
            else -> normalizeHttpUrl(trimmed) ?: trimmed
        }

        val cacheKey = "$resolvedSource|$canonicalUrl|$maxTracks"
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = detailCache[cacheKey]
            if (cached != null && now - cached.timestampMs < CACHE_TTL_MS) {
                return cached.value
            }
        }

        return withContext(Dispatchers.IO) {
            YouTubeExtractorService.init()
            val streamingService: StreamingService = if (resolvedSource == ExternalPlaylistSource.SOUNDCLOUD.id) {
                ServiceList.SoundCloud
            } else {
                ServiceList.YouTube
            }

            try {
                val info = PlaylistInfo.getInfo(streamingService, canonicalUrl)
                val collected = mutableListOf<AudioTrack>()
                val seenIds = HashSet<String>()

                collectStreamItems(resolvedSource, info.relatedItems, seenIds, collected, maxTracks)

                if (collected.size < maxTracks && info.hasNextPage()) {
                    try {
                        val nextPage = PlaylistInfo.getMoreItems(streamingService, canonicalUrl, info.nextPage)
                        collectStreamItems(resolvedSource, nextPage.items, seenIds, collected, maxTracks)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Next page fetch skipped for $canonicalUrl: ${e.message}")
                    }
                }

                val summaryId = parsed?.id
                    ?: runCatching { streamingService.playlistLHFactory.getId(canonicalUrl) }.getOrNull()
                    ?: canonicalUrl.hashCode().toUInt().toString(16)

                val summary = ExternalPlaylistSummary(
                    id = summaryId,
                    source = resolvedSource,
                    title = info.name.takeIf { it.isNotBlank() } ?: titleHint.orEmpty().ifBlank { "Плейлист" },
                    author = info.uploaderName.takeIf { !it.isNullOrBlank() } ?: authorHint.orEmpty().ifBlank {
                        if (resolvedSource == ExternalPlaylistSource.SOUNDCLOUD.id) "SoundCloud" else "YouTube Music"
                    },
                    url = canonicalUrl,
                    artworkUrl = extractBestThumbnailUrl(info.thumbnails) ?: artworkHint,
                    trackCount = if (info.streamCount > 0L) info.streamCount.toInt() else collected.size
                )

                val details = ExternalPlaylistDetails(summary = summary, tracks = collected)
                if (collected.isNotEmpty()) {
                    synchronized(cacheLock) {
                        detailCache[cacheKey] = TimedEntry(details)
                    }
                }
                details
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract playlist ($resolvedSource) from $canonicalUrl: ${e.message}", e)
                null
            }
        }
    }

    fun invalidateSoundCloudStreamCache(trackUriOrUrl: String) {
        val pageUrl = decodeSoundCloudStreamUri(trackUriOrUrl)
            ?: normalizeHttpUrl(trackUriOrUrl)?.takeIf { it.contains("soundcloud.com", ignoreCase = true) }
            ?: return
        synchronized(cacheLock) {
            scStreamCache.remove(pageUrl)
        }
    }

    /**
     * Resolves a direct progressive audio stream URL for a SoundCloud track via NewPipe.
     */
    fun resolveSoundCloudAudioStreamUrlSync(trackUriOrUrl: String): String? {
        val pageUrl = decodeSoundCloudStreamUri(trackUriOrUrl)
            ?: normalizeHttpUrl(trackUriOrUrl)?.takeIf { it.contains("soundcloud.com", ignoreCase = true) }
            ?: return null

        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            val cached = scStreamCache[pageUrl]
            if (cached != null && now - cached.timestampMs < STREAM_CACHE_TTL_MS) {
                return cached.value
            }
        }

        return try {
            YouTubeExtractorService.init()
            val streamInfo = StreamInfo.getInfo(ServiceList.SoundCloud, pageUrl)
            val audioStreams = streamInfo.audioStreams.orEmpty().filter { it.content.isNotBlank() }
            val best = audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams.maxByOrNull { it.averageBitrate }
            val url = best?.content?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            if (url != null) {
                synchronized(cacheLock) {
                    scStreamCache[pageUrl] = TimedEntry(url)
                }
            }
            url
        } catch (e: Exception) {
            Log.w(TAG, "SoundCloud direct stream resolve failed for $pageUrl: ${e.message}")
            null
        }
    }

    internal fun mergePlaylistSummaries(
        primary: List<ExternalPlaylistSummary>,
        secondary: List<ExternalPlaylistSummary>
    ): List<ExternalPlaylistSummary> {
        val seen = HashSet<String>()
        val merged = mutableListOf<ExternalPlaylistSummary>()
        val maxLen = maxOf(primary.size, secondary.size)
        for (i in 0 until maxLen) {
            if (i < primary.size) {
                val item = primary[i]
                if (seen.add("${item.source}::${item.url}")) merged.add(item)
            }
            if (i < secondary.size) {
                val item = secondary[i]
                if (seen.add("${item.source}::${item.url}")) merged.add(item)
            }
        }
        return merged
    }

    private fun searchYouTubePlaylistsInternal(query: String, limit: Int): List<ExternalPlaylistSummary> {
        val results = mutableListOf<ExternalPlaylistSummary>()
        val seen = HashSet<String>()

        fun collectFromFilter(filter: String) {
            if (results.size >= limit) return
            try {
                val searchInfo = SearchInfo.getInfo(
                    ServiceList.YouTube,
                    ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf(filter), "")
                )
                for (item in searchInfo.relatedItems) {
                    if (item is PlaylistInfoItem) {
                        val summary = playlistItemToSummary(ExternalPlaylistSource.YOUTUBE.id, ServiceList.YouTube, item)
                        if (summary != null && seen.add(summary.url)) {
                            results.add(summary)
                            if (results.size >= limit) break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "YouTube playlist search [$filter] failed: ${e.message}")
            }
        }

        collectFromFilter(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS)
        if (results.size < limit) {
            collectFromFilter(YoutubeSearchQueryHandlerFactory.PLAYLISTS)
        }
        return results
    }

    private fun searchSoundCloudPlaylistsInternal(query: String, limit: Int): List<ExternalPlaylistSummary> {
        val results = mutableListOf<ExternalPlaylistSummary>()
        val seen = HashSet<String>()
        try {
            val searchInfo = SearchInfo.getInfo(
                ServiceList.SoundCloud,
                ServiceList.SoundCloud.searchQHFactory.fromQuery(
                    query,
                    listOf(SoundcloudSearchQueryHandlerFactory.PLAYLISTS),
                    ""
                )
            )
            for (item in searchInfo.relatedItems) {
                if (item is PlaylistInfoItem) {
                    val summary = playlistItemToSummary(ExternalPlaylistSource.SOUNDCLOUD.id, ServiceList.SoundCloud, item)
                    if (summary != null && seen.add(summary.url)) {
                        results.add(summary)
                        if (results.size >= limit) break
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SoundCloud playlist search failed: ${e.message}")
        }
        return results
    }

    private fun playlistItemToSummary(
        sourceId: String,
        service: StreamingService,
        item: PlaylistInfoItem
    ): ExternalPlaylistSummary? {
        val url = item.url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val playlistId = runCatching { service.playlistLHFactory.getId(url) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: url.hashCode().toUInt().toString(16)

        return ExternalPlaylistSummary(
            id = playlistId,
            source = sourceId,
            title = title,
            author = item.uploaderName?.trim()?.takeIf { it.isNotBlank() }
                ?: if (sourceId == ExternalPlaylistSource.SOUNDCLOUD.id) "SoundCloud" else "YouTube",
            url = url,
            artworkUrl = extractBestThumbnailUrl(item.thumbnails),
            trackCount = item.streamCount.toInt().coerceAtLeast(0)
        )
    }

    private fun collectStreamItems(
        sourceId: String,
        items: List<StreamInfoItem>?,
        seenIds: MutableSet<String>,
        out: MutableList<AudioTrack>,
        maxTracks: Int
    ) {
        if (items.isNullOrEmpty()) return
        for (item in items) {
            if (out.size >= maxTracks) break
            val track = streamItemToAudioTrack(sourceId, item) ?: continue
            if (seenIds.add(track.id)) {
                out.add(track)
            }
        }
    }

    private fun streamItemToAudioTrack(sourceId: String, item: StreamInfoItem): AudioTrack? {
        val durationSec = item.duration
        if (durationSec > 3600L || (durationSec in 1 until 10L)) return null

        val url = item.url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = item.name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val artist = item.uploaderName?.trim()?.takeIf { it.isNotBlank() }
            ?: if (sourceId == ExternalPlaylistSource.SOUNDCLOUD.id) "SoundCloud" else "YouTube"
        val thumb = extractBestThumbnailUrl(item.thumbnails)
        val durationMs = if (durationSec > 0L) durationSec * 1000L else 0L

        return if (sourceId == ExternalPlaylistSource.YOUTUBE.id) {
            val videoId = runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: YouTubeExtractorService.extractVideoIdFromUrl(url)
                ?: return null
            AudioTrack(
                id = "youtube_$videoId",
                title = title,
                artist = artist,
                audioUrl = "youtube://$videoId",
                artworkUrl = thumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                durationMs = durationMs,
                isLocal = false,
                isLiveStream = false
            )
        } else {
            val externalId = buildExternalSoundCloudTrackId(url, title, artist)
            AudioTrack(
                id = "soundcloud_$externalId",
                title = title,
                artist = artist,
                audioUrl = encodeSoundCloudStreamUri(url),
                artworkUrl = thumb,
                durationMs = durationMs,
                isLocal = false,
                isLiveStream = false
            )
        }
    }

    private fun extractBestThumbnailUrl(thumbnails: List<Image>?): String? {
        if (thumbnails.isNullOrEmpty()) return null
        val best = thumbnails.maxByOrNull { it.height * it.width }?.url ?: thumbnails.lastOrNull()?.url
        return best?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun normalizeHttpUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> null
        }
    }
}
