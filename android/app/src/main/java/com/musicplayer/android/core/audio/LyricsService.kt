package com.musicplayer.android.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

@Serializable
data class LrcLibTrackDto(
    val id: Long = 0L,
    val trackName: String = "",
    val artistName: String = "",
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null
)

@Serializable
private data class CachedLyricsPayload(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val instrumental: Boolean = false,
    val sourceName: String = LyricsSource.LRCLIB_SYNCED.name
)

sealed interface LyricsFetchResult {
    data class Found(
        val syncedLines: List<LyricsLine>,
        val plainLyrics: String?,
        val source: LyricsSource,
        val isInstrumental: Boolean = false
    ) : LyricsFetchResult

    data object NotFound : LyricsFetchResult
    data class Error(val message: String) : LyricsFetchResult
}

/**
 * Resolves synchronized (`.lrc`) and plain lyrics for any [AudioTrack]:
 * 1. Sibling local `.lrc` file next to local/downloaded audio files
 * 2. Persistent disk cache for offline reuse
 * 3. `lrclib.net` free public API (`/api/get` -> `/api/search` fallback)
 */
class LyricsService(
    private val httpClient: OkHttpClient,
    private val cacheRootDir: File?,
    private val baseUrl: String = DEFAULT_BASE_URL
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val lyricsCacheDir: File?
        get() = cacheRootDir?.let { File(it, "lyrics_cache").apply { mkdirs() } }

    suspend fun fetchLyrics(track: AudioTrack, forceRefresh: Boolean = false): LyricsFetchResult =
        withContext(Dispatchers.IO) {
            if (track.isLiveStream) {
                return@withContext LyricsFetchResult.NotFound
            }

            // 1. Sibling local .lrc file always takes highest priority
            findLocalLrcContent(track.audioUrl)?.let { localLrc ->
                val lines = LrcParser.parse(localLrc)
                if (lines.isNotEmpty() || localLrc.isNotBlank()) {
                    return@withContext LyricsFetchResult.Found(
                        syncedLines = lines,
                        plainLyrics = if (lines.isEmpty()) localLrc.trim() else null,
                        source = LyricsSource.LOCAL_LRC
                    )
                }
            }

            val cacheKey = buildCacheKey(track)

            // 2. Disk cache (skip when forceRefresh = true)
            if (!forceRefresh) {
                readFromCache(cacheKey)?.let { cached ->
                    return@withContext cached
                }
            }

            // 3. Query lrclib.net
            val normalized = normalizeQuery(track.title, track.artist)
            if (normalized.trackName.isBlank()) {
                return@withContext LyricsFetchResult.NotFound
            }

            try {
                val exactMatch = queryExact(normalized, track.durationMs)
                val resolved = exactMatch ?: querySearch(normalized, track.durationMs)

                if (resolved == null) {
                    return@withContext LyricsFetchResult.NotFound
                }

                val syncedLines = resolved.syncedLyrics?.let(LrcParser::parse).orEmpty()
                val plain = resolved.plainLyrics?.trim()?.takeIf { it.isNotBlank() }

                if (!resolved.instrumental && syncedLines.isEmpty() && plain == null) {
                    return@withContext LyricsFetchResult.NotFound
                }

                val source = if (syncedLines.isNotEmpty()) {
                    LyricsSource.LRCLIB_SYNCED
                } else {
                    LyricsSource.LRCLIB_PLAIN
                }

                writeToCache(
                    cacheKey,
                    CachedLyricsPayload(
                        syncedLyrics = resolved.syncedLyrics,
                        plainLyrics = plain,
                        instrumental = resolved.instrumental,
                        sourceName = source.name
                    )
                )

                LyricsFetchResult.Found(
                    syncedLines = syncedLines,
                    plainLyrics = plain,
                    source = source,
                    isInstrumental = resolved.instrumental
                )
            } catch (e: Exception) {
                LyricsFetchResult.Error(e.message ?: "Помилка завантаження тексту пісні")
            }
        }

    suspend fun saveCustomLrc(track: AudioTrack, lrcContent: String): LyricsFetchResult =
        withContext(Dispatchers.IO) {
            val trimmed = lrcContent.trim()
            if (trimmed.isEmpty()) return@withContext LyricsFetchResult.NotFound

            val syncedLines = LrcParser.parse(trimmed)
            val source = if (syncedLines.isNotEmpty()) LyricsSource.LOCAL_LRC else LyricsSource.LRCLIB_PLAIN
            writeToCache(
                buildCacheKey(track),
                CachedLyricsPayload(
                    syncedLyrics = if (syncedLines.isNotEmpty()) trimmed else null,
                    plainLyrics = if (syncedLines.isEmpty()) trimmed else null,
                    instrumental = false,
                    sourceName = source.name
                )
            )
            LyricsFetchResult.Found(
                syncedLines = syncedLines,
                plainLyrics = if (syncedLines.isEmpty()) trimmed else null,
                source = source
            )
        }

    internal fun findLocalLrcContent(audioUrl: String): String? {
        val localPath = when {
            audioUrl.startsWith("file://", ignoreCase = true) ->
                runCatching { java.net.URI(audioUrl).path }.getOrNull()
                    ?: audioUrl.removePrefix("file://")
            audioUrl.contains("://") -> return null
            else -> audioUrl
        }

        val audioFile = File(localPath)
        val parent = audioFile.parentFile ?: return null
        if (!parent.exists()) return null

        val baseName = audioFile.nameWithoutExtension
        val candidates = listOf(
            File(parent, "$baseName.lrc"),
            File(parent, "$baseName.LRC")
        )
        return candidates.firstOrNull { it.isFile && it.canRead() }
            ?.readText(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() }
    }

    internal fun normalizeQuery(rawTitle: String, rawArtist: String): NormalizedLyricsQuery {
        var title = rawTitle.trim()
        var artist = rawArtist
            .replace(TOPIC_SUFFIX_REGEX, "")
            .replace(VEVO_SUFFIX_REGEX, "")
            .trim()

        val isGenericArtist = artist.isBlank() ||
            artist.equals("Unknown Artist", ignoreCase = true) ||
            artist.equals("YouTube", ignoreCase = true) ||
            artist.equals("SoundCloud", ignoreCase = true) ||
            artist.equals("Невідомий виконавець", ignoreCase = true)

        if (isGenericArtist && title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                artist = parts[0].trim()
                title = parts[1].trim()
            }
        }

        title = title
            .replace(VIDEO_TAG_REGEX, " ")
            .replace(BRACKET_TAG_REGEX, " ")
            .replace(FEAT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim(' ', '-', '|', '/')

        return NormalizedLyricsQuery(
            trackName = title,
            artistName = if (isGenericArtist && artist == rawArtist.trim()) "" else artist
        )
    }

    private fun queryExact(query: NormalizedLyricsQuery, durationMs: Long): LrcLibTrackDto? {
        if (query.artistName.isBlank()) return null

        val urlBuilder = "$baseUrl/api/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", query.trackName)
        urlBuilder.addQueryParameter("artist_name", query.artistName)
        if (durationMs > 0L) {
            urlBuilder.addQueryParameter("duration", (durationMs / 1000L).toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 404 || !response.isSuccessful) return null
            val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val dto = json.decodeFromString<LrcLibTrackDto>(body)
            return dto.takeIf { it.hasUsableLyrics() }
        }
    }

    private fun querySearch(query: NormalizedLyricsQuery, durationMs: Long): LrcLibTrackDto? {
        val urlBuilder = "$baseUrl/api/search".toHttpUrlOrNull()?.newBuilder() ?: return null
        if (query.artistName.isNotBlank()) {
            urlBuilder.addQueryParameter("track_name", query.trackName)
            urlBuilder.addQueryParameter("artist_name", query.artistName)
        } else {
            urlBuilder.addQueryParameter("q", query.trackName)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val candidates = json.decodeFromString<List<LrcLibTrackDto>>(body)
                .filter { it.hasUsableLyrics() }
            if (candidates.isEmpty()) return null

            val targetDurationSec = if (durationMs > 0L) durationMs / 1000.0 else null

            return candidates.maxWithOrNull(
                compareBy<LrcLibTrackDto>(
                    { !it.syncedLyrics.isNullOrBlank() },
                    {
                        if (targetDurationSec != null && it.duration != null) {
                            abs(it.duration - targetDurationSec) <= 10.0
                        } else {
                            false
                        }
                    },
                    { !it.plainLyrics.isNullOrBlank() }
                ).thenBy { candidate ->
                    if (targetDurationSec != null && candidate.duration != null) {
                        -abs(candidate.duration - targetDurationSec)
                    } else {
                        0.0
                    }
                }
            )
        }
    }

    private fun LrcLibTrackDto.hasUsableLyrics(): Boolean =
        instrumental || !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    private fun readFromCache(cacheKey: String): LyricsFetchResult.Found? {
        val file = lyricsCacheDir?.let { File(it, "$cacheKey.json") } ?: return null
        if (!file.isFile) return null
        return try {
            val payload = json.decodeFromString<CachedLyricsPayload>(file.readText(Charsets.UTF_8))
            val syncedLines = payload.syncedLyrics?.let(LrcParser::parse).orEmpty()
            val plain = payload.plainLyrics?.trim()?.takeIf { it.isNotBlank() }
            if (!payload.instrumental && syncedLines.isEmpty() && plain == null) {
                null
            } else {
                LyricsFetchResult.Found(
                    syncedLines = syncedLines,
                    plainLyrics = plain,
                    source = LyricsSource.CACHE,
                    isInstrumental = payload.instrumental
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToCache(cacheKey: String, payload: CachedLyricsPayload) {
        val file = lyricsCacheDir?.let { File(it, "$cacheKey.json") } ?: return
        try {
            file.writeText(json.encodeToString(payload), Charsets.UTF_8)
        } catch (_: Exception) {
            // Disk cache failure is non-fatal
        }
    }

    private fun buildCacheKey(track: AudioTrack): String {
        val normalized = normalizeQuery(track.title, track.artist)
        val raw = "${normalized.artistName.lowercase()}|${normalized.trackName.lowercase()}|${track.id}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    internal data class NormalizedLyricsQuery(
        val trackName: String,
        val artistName: String
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "MusicPlayerAndroid/1.0 (https://github.com/music-player-android)"

        private val TOPIC_SUFFIX_REGEX = Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE)
        private val VEVO_SUFFIX_REGEX = Regex("""VEVO$""", RegexOption.IGNORE_CASE)
        private val VIDEO_TAG_REGEX = Regex(
            """\((?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video)|lyrics?|visualizer|hd|4k|hq|audio)\)""",
            RegexOption.IGNORE_CASE
        )
        private val BRACKET_TAG_REGEX = Regex(
            """\[(?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video)|lyrics?|visualizer|hd|4k|hq|audio)]""",
            RegexOption.IGNORE_CASE
        )
        private val FEAT_REGEX = Regex("""\s*(?:\(|\[)?\b(?:feat\.?|ft\.?)\s+[^)\]]+(?:\)|\])?""", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    }
}
