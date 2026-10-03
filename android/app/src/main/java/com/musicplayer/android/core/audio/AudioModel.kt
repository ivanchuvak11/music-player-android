package com.musicplayer.android.core.audio

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.database.FavoriteTrackEntity
import com.musicplayer.android.core.database.LocalPlaylistTrackEntity
import com.musicplayer.android.core.database.PlayHistoryEntity
import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.JamendoTrackDto
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.network.SoundCloudTrackDto
import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * Representation of an audio track regardless of its source (Local, Radio, Jamendo, Audius, SoundCloud).
 */
@Serializable
data class AudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val isLocal: Boolean = false,
    val isLiveStream: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        val effectiveUriString = if (id.startsWith("youtube_") || audioUrl.contains("youtube.com") || audioUrl.contains("youtu.be")) {
            val videoId = try {
                YouTubeExtractorService.extractVideoIdFromUri(Uri.parse(audioUrl)) ?: id.removePrefix("youtube_")
            } catch (e: Exception) {
                id.removePrefix("youtube_")
            }
            YouTubeExtractorService.getCachedStreamUrl(videoId) ?: audioUrl
        } else {
            audioUrl
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
            .setIsPlayable(true)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(effectiveUriString)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        fun fromMediaItem(mediaItem: MediaItem, durationMs: Long = 0L): AudioTrack {
            val metadata = mediaItem.mediaMetadata
            val uri = mediaItem.localConfiguration?.uri
            val uriString = uri?.toString().orEmpty()
            val isLocal = uri?.scheme == "content" || uri?.scheme == "file"
            val isLive = mediaItem.mediaId.startsWith("radio_") || (durationMs <= 0L && !isLocal && uriString.contains("radio"))
            return AudioTrack(
                id = mediaItem.mediaId,
                title = metadata.title?.toString() ?: "Unknown Title",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                audioUrl = uriString,
                artworkUrl = metadata.artworkUri?.toString(),
                durationMs = durationMs,
                isLocal = isLocal,
                isLiveStream = isLive
            )
        }

        fun fromJamendo(track: JamendoTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveJamendoStreamUrl(
                track.externalId,
                track.streamUrl,
                backendBaseUrl
            )
            return AudioTrack(
                id = "jamendo_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }

        fun fromRadio(station: RadioStationDto): AudioTrack {
            return AudioTrack(
                id = "radio_${station.stationId}",
                title = station.name,
                artist = station.genre?.takeIf { it.isNotBlank() } ?: station.country ?: "Radio",
                audioUrl = station.streamUrl,
                artworkUrl = station.logoUrl,
                durationMs = 0L,
                isLocal = false,
                isLiveStream = true
            )
        }

        fun fromAudius(track: AudiusTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveAudiusStreamUrl(
                track.externalId,
                backendBaseUrl
            )
            return AudioTrack(
                id = "audius_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }

        fun fromSoundCloud(track: SoundCloudTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveSoundCloudStreamUrl(
                track.externalId,
                track.streamUrl,
                backendBaseUrl
            )
            return AudioTrack(
                id = "soundcloud_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }

        fun fromYouTube(track: com.musicplayer.android.core.network.YouTubeTrackDto, backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack {
            val streamUri = com.musicplayer.android.core.network.ServerConfig.resolveYouTubeStreamUrl(
                track.externalId,
                track.streamUrl,
                backendBaseUrl
            )
            return AudioTrack(
                id = "youtube_${track.externalId}",
                title = track.title,
                artist = track.artist,
                audioUrl = streamUri,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs ?: 0L,
                isLocal = false,
                isLiveStream = false
            )
        }
    }
}

/**
 * Extension functions converting database entities directly to AudioTrack (Fix #23: Code Duplication elimination).
 */
fun FavoriteTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = id,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = audioUrl.startsWith("content://") || audioUrl.startsWith("file://"),
    isLiveStream = audioUrl.contains("radio")
)

fun PlayHistoryEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = trackId,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = audioUrl.startsWith("content://") || audioUrl.startsWith("file://"),
    isLiveStream = audioUrl.contains("radio")
)

fun LocalPlaylistTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = trackId,
    title = title,
    artist = artist,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = isLocal,
    isLiveStream = audioUrl.contains("radio")
)

fun CachedTrackEntity.toAudioTrack(): AudioTrack = AudioTrack(
    id = id,
    title = title,
    artist = artist,
    audioUrl = localFilePath ?: originalUrl,
    artworkUrl = artworkUrl,
    durationMs = durationMs,
    isLocal = localFilePath != null,
    isLiveStream = false
)

fun com.musicplayer.android.core.network.YouTubeTrackDto.toAudioTrack(backendBaseUrl: String = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL): AudioTrack =
    AudioTrack.fromYouTube(this, backendBaseUrl)

/**
 * Current playback state observed by UI components.
 */
data class PlaybackState(
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    val queue: List<AudioTrack> = emptyList(),
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
) {
    companion object {
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val REPEAT_MODE_ALL = 2
    }
}

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
private val NON_LETTER_DIGIT_REGEX = Regex("[^\\p{L}\\p{N}]+")
private val MULTI_SPACE_REGEX = Regex("\\s+")
private val YOUTUBE_NOISE_REGEX = Regex(
    "(?i)\\s*[\\[\\(](?:official\\s+(?:music\\s+)?(?:video|audio|lyric\\s+video)|lyrics?|4k|hd|remastered|visualizer|clip|audio)[\\]\\)]"
)

internal data class TrackSearchMetadata(
    val normalizedTitle: String,
    val cleanedTitle: String,
    val normalizedArtist: String,
    val titleTokens: List<String>,
    val artistTokens: List<String>,
    val compactTitle: String,
    val compactArtist: String
)

internal fun AudioTrack.toSearchMetadata(): TrackSearchMetadata {
    val normTitle = normalizeSearchText(title)
    val cleanTitle = normalizeSearchText(cleanTrackTitle(title))
    val normArtist = normalizeSearchText(artist)
    val effectiveTitle = if (cleanTitle.isNotBlank()) cleanTitle else normTitle
    val tTokens = searchTokens(effectiveTitle)
    val aTokens = searchTokens(normArtist)
    return TrackSearchMetadata(
        normalizedTitle = normTitle,
        cleanedTitle = cleanTitle,
        normalizedArtist = normArtist,
        titleTokens = tTokens,
        artistTokens = aTokens,
        compactTitle = normTitle.replace(" ", ""),
        compactArtist = normArtist.replace(" ", "")
    )
}

/**
 * Calculates a relevance score for a track given a search query.
 * Higher score = higher priority in search results.
 * Negative score = non-relevant (does not match title or artist).
 */
fun AudioTrack.calculateSearchRelevanceScore(query: String): Int {
    if (query.isBlank()) return -1
    val metadata = toSearchMetadata()
    return calculateSearchRelevanceScoreWithMetadata(query, metadata)
}

internal fun calculateSearchRelevanceScoreWithMetadata(
    query: String,
    metadata: TrackSearchMetadata
): Int {
    if (metadata.normalizedTitle.isEmpty() && metadata.normalizedArtist.isEmpty()) return -1

    val nativeScore = calculateSingleQueryRelevanceWithMetadata(query, metadata)
    if (nativeScore >= 600) return nativeScore

    val transliterated = transliterateCyrillicToLatin(query)
    val transScores = mutableListOf<Int>()
    if (transliterated != query.lowercase()) {
        val s = calculateSingleQueryRelevanceWithMetadata(transliterated, metadata)
        if (s > 0) transScores.add((s - 50).coerceAtLeast(1))
        // Common phonetic variant: "kvin" -> "queen", "kv" -> "qu"
        if (transliterated.contains("kvin")) {
            val quVariant = transliterated.replace("kvin", "queen")
            val sQu = calculateSingleQueryRelevanceWithMetadata(quVariant, metadata)
            if (sQu > 0) transScores.add((sQu - 50).coerceAtLeast(1))
        } else if (transliterated.contains("kv")) {
            val quVariant = transliterated.replace("kv", "qu")
            val sQu = calculateSingleQueryRelevanceWithMetadata(quVariant, metadata)
            if (sQu > 0) transScores.add((sQu - 50).coerceAtLeast(1))
        }
        // Double consonant variants (e.g. "metalika" -> "metallica", "l" -> "ll")
        if (transliterated.contains("l") && !transliterated.contains("ll")) {
            val llVariant = transliterated.replace("l", "ll")
            val sLl = calculateSingleQueryRelevanceWithMetadata(llVariant, metadata)
            if (sLl > 0) transScores.add((sLl - 50).coerceAtLeast(1))
        }
    }
    val transScore = transScores.maxOrNull() ?: -1

    val flipped = flipKeyboardLayoutToEnglish(query)
    val flippedScore = if (flipped != query.lowercase()) {
        val s = calculateSingleQueryRelevanceWithMetadata(flipped, metadata)
        if (s > 0) (s - 50).coerceAtLeast(1) else -1
    } else -1

    val maxScore = maxOf(nativeScore, transScore, flippedScore)
    return maxScore
}

private fun calculateSingleQueryRelevanceWithMetadata(
    query: String,
    metadata: TrackSearchMetadata
): Int {
    val normalizedQuery = normalizeSearchText(query)
    if (normalizedQuery.isEmpty()) return 0

    val (normalizedTitle, cleanedTitle, normalizedArtist, titleTokens, artistTokens, compactTitle, compactArtist) = metadata

    when {
        normalizedTitle == normalizedQuery || cleanedTitle == normalizedQuery -> return 1000
        normalizedTitle.startsWith(normalizedQuery) || cleanedTitle.startsWith(normalizedQuery) -> return 800
        normalizedTitle.contains(" $normalizedQuery") || cleanedTitle.contains(" $normalizedQuery") -> return 600
        normalizedArtist == normalizedQuery -> return 300
        normalizedArtist.startsWith(normalizedQuery) -> return 200
        normalizedArtist.contains(" $normalizedQuery") -> return 100
    }

    val compactQuery = normalizedQuery.replace(" ", "")
    if (compactQuery.length >= 4) {
        when {
            compactTitle == compactQuery -> return 750
            tokensStartWithCompactSequence(titleTokens, compactQuery) -> return 650
            compactArtist == compactQuery -> return 290
            tokensStartWithCompactSequence(artistTokens, compactQuery) -> return 180
        }
    }

    val allQueryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
    val meaningfulTokens = allQueryTokens.filterNot { it in SEARCH_STOP_WORDS }
    val queryTokens = meaningfulTokens.ifEmpty { allQueryTokens }
    if (queryTokens.isEmpty()) return -1

    val candidatesByQueryToken = queryTokens.mapIndexed { queryIndex, queryToken ->
        val candidates = buildList {
            titleTokens.forEachIndexed { tokenIndex, candidateToken ->
                val score = tokenMatchScore(queryToken, candidateToken)
                if (score > 0) {
                    add(SearchTokenMatch(field = 0, tokenIndex = tokenIndex, score = score * 2, matchQuality = score))
                }
            }
            artistTokens.forEachIndexed { tokenIndex, candidateToken ->
                val score = tokenMatchScore(queryToken, candidateToken)
                if (score > 0) {
                    add(SearchTokenMatch(field = 1, tokenIndex = tokenIndex, score = score, matchQuality = score))
                }
            }
        }
        queryIndex to candidates
    }

    val usedTokens = mutableSetOf<Pair<Int, Int>>()
    val selectedMatches = arrayOfNulls<SearchTokenMatch>(queryTokens.size)
    candidatesByQueryToken
        .sortedWith(compareBy<Pair<Int, List<SearchTokenMatch>>> { it.second.size }
            .thenByDescending { queryTokens[it.first].length })
        .forEach { (queryIndex, candidates) ->
            val best = candidates
                .asSequence()
                .filterNot { (it.field to it.tokenIndex) in usedTokens }
                .maxByOrNull(SearchTokenMatch::score)
                ?: return@forEach
            selectedMatches[queryIndex] = best
            usedTokens += best.field to best.tokenIndex
        }

    val matches = selectedMatches.filterNotNull()
    val matchedTokens = matches.size

    if (matchedTokens == 0) return -1
    val minimumMatches = when {
        queryTokens.size <= 2 -> 1
        else -> ((queryTokens.size * 3) + 4) / 5
    }
    if (matchedTokens < minimumMatches) return -1

    val titleScore = matches.filter { it.field == 0 }.sumOf(SearchTokenMatch::score)
    val artistScore = matches.filter { it.field == 1 }.sumOf(SearchTokenMatch::score)
    val titlePositions = matches.filter { it.field == 0 }.map(SearchTokenMatch::tokenIndex)
    val proximityBonus = if (titlePositions.size > 1) {
        val span = titlePositions.maxOrNull()!! - titlePositions.minOrNull()!! + 1
        val gaps = span - titlePositions.size
        val isInQueryOrder = matches
            .filter { it.field == 0 }
            .map(SearchTokenMatch::tokenIndex)
            .zipWithNext()
            .all { (first, second) -> first < second }
        (90 - gaps * 20).coerceAtLeast(0) + if (isInQueryOrder) 30 else 0
    } else 0

    val coverageBonus = if (matchedTokens == queryTokens.size) 160 else 0
    val missingTokenPenalty = (queryTokens.size - matchedTokens) * 180
    val compositeScore = 100 + titleScore + artistScore + coverageBonus + proximityBonus - missingTokenPenalty
    val compositeCeiling = when {
        matches.any { it.matchQuality == 25 } -> 249
        matches.any { it.matchQuality == 35 } -> 399
        matches.any { it.matchQuality == 50 } -> 499
        else -> 599
    }
    return compositeScore.coerceIn(0, compositeCeiling)
}

private data class SearchTokenMatch(
    val field: Int,
    val tokenIndex: Int,
    val score: Int,
    val matchQuality: Int
)

private val SEARCH_STOP_WORDS = setOf(
    "a", "an", "and", "the", "of", "to", "in", "on", "for", "by",
    "official", "audio", "video", "lyrics", "lyric", "topic", "hd", "4k"
)

private fun searchTokens(value: String): List<String> =
    normalizeSearchText(value).split(' ').filter(String::isNotBlank)

private fun tokensStartWithCompactSequence(tokens: List<String>, compactQuery: String): Boolean =
    tokens.indices.any { startIndex ->
        buildString {
            for (index in startIndex until tokens.size) append(tokens[index])
        }.startsWith(compactQuery)
    }

private fun cleanTrackTitle(title: String): String {
    return title.replace(YOUTUBE_NOISE_REGEX, "").trim()
}

private fun normalizeSearchText(value: String): String {
    if (value.isEmpty()) return ""
    val withoutDiacritics = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(DIACRITICS_REGEX, "")
    return withoutDiacritics
        .replace(NON_LETTER_DIGIT_REGEX, " ")
        .trim()
        .replace(MULTI_SPACE_REGEX, " ")
}

/**
 * Transliterates Cyrillic text to Latin phonetic equivalent (and Ukrainian Latin standards).
 * Handles common Ukrainian and general Cyrillic music search transliteration.
 */
internal fun transliterateCyrillicToLatin(text: String): String {
    val lower = text.lowercase()
    val sb = StringBuilder(lower.length * 2)
    var i = 0
    while (i < lower.length) {
        val c = lower[i]
        when (c) {
            'щ' -> sb.append("shch")
            'ш' -> sb.append("sh")
            'ч' -> sb.append("ch")
            'ж' -> sb.append("zh")
            'ю' -> sb.append("yu")
            'я' -> sb.append("ya")
            'є' -> sb.append("ye")
            'ї' -> sb.append("yi")
            'а' -> sb.append("a")
            'б' -> sb.append("b")
            'в' -> sb.append("v")
            'г' -> sb.append("h")
            'ґ' -> sb.append("g")
            'д' -> sb.append("d")
            'е' -> sb.append("e")
            'з' -> sb.append("z")
            'и' -> sb.append("y")
            'і' -> sb.append("i")
            'й' -> sb.append("y")
            'к' -> sb.append("k")
            'л' -> sb.append("l")
            'м' -> sb.append("m")
            'н' -> sb.append("n")
            'о' -> sb.append("o")
            'п' -> sb.append("p")
            'р' -> sb.append("r")
            'с' -> sb.append("s")
            'т' -> sb.append("t")
            'у' -> sb.append("u")
            'ф' -> sb.append("f")
            'х' -> sb.append("kh")
            'ц' -> sb.append("ts")
            'ь', 'ъ', '\'' -> { /* omit */ }
            else -> sb.append(c)
        }
        i++
    }
    return sb.toString()
}

/**
 * Converts text typed in Ukrainian keyboard layout to English QWERTY characters.
 * E.g., if user forgot to switch keyboard layout: "щсуфт" -> "ocean", "ьуефддшсф" -> "metallica".
 */
internal fun flipKeyboardLayoutToEnglish(text: String): String {
    val cyrToEng = mapOf(
        'й' to 'q', 'ц' to 'w', 'у' to 'e', 'к' to 'r', 'е' to 't', 'н' to 'y', 'г' to 'u', 'ш' to 'i', 'щ' to 'o', 'з' to 'p',
        'ф' to 'a', 'і' to 's', 'ы' to 's', 'в' to 'd', 'а' to 'f', 'п' to 'g', 'р' to 'h', 'о' to 'j', 'л' to 'k', 'д' to 'l',
        'я' to 'z', 'ч' to 'x', 'с' to 'c', 'м' to 'v', 'и' to 'b', 'т' to 'n', 'ь' to 'm', 'є' to '\''
    )
    val lower = text.lowercase()
    val hasCyrillic = lower.any { it in cyrToEng }
    if (!hasCyrillic) return lower
    return buildString(lower.length) {
        for (c in lower) {
            append(cyrToEng[c] ?: c)
        }
    }
}

fun AudioTrack.searchDeduplicationKey(): String {
    if (title.isBlank() || artist.isBlank()) return ""
    val normalizedTitle = normalizeSearchText(title)
    val normalizedArtist = normalizeSearchText(artist)
    return if (normalizedTitle.isBlank() || normalizedArtist.isBlank()) {
        ""
    } else {
        "$normalizedTitle|$normalizedArtist"
    }
}

private fun tokenMatchScore(query: String, candidate: String): Int {
    if (candidate == query) return 100
    if (candidate.startsWith(query)) return 75
    if (query.length >= 5) {
        val maxTypos = if (query.length >= 9) 2 else 1
        when (editDistanceAtMost(query, candidate, maxTypos)) {
            1 -> return 50
            2 -> return 35
        }
    }
    if (query.length >= 4 && isAdjacentTransposition(query, candidate)) return 50
    if (query.length >= 4 && candidate.contains(query)) return 25
    return 0
}

private fun editDistanceAtMost(first: String, second: String, maxDistance: Int): Int? {
    if (kotlin.math.abs(first.length - second.length) > maxDistance) return null

    var twoRowsBack: IntArray? = null
    var previousRow = IntArray(second.length + 1) { it }
    for (firstIndex in first.indices) {
        val currentRow = IntArray(second.length + 1)
        currentRow[0] = firstIndex + 1
        for (secondIndex in second.indices) {
            val substitutionCost = if (first[firstIndex] == second[secondIndex]) 0 else 1
            var distance = minOf(
                previousRow[secondIndex + 1] + 1,
                currentRow[secondIndex] + 1,
                previousRow[secondIndex] + substitutionCost
            )

            if (firstIndex > 0 && secondIndex > 0 &&
                first[firstIndex] == second[secondIndex - 1] &&
                first[firstIndex - 1] == second[secondIndex]
            ) {
                twoRowsBack?.let { row -> distance = minOf(distance, row[secondIndex - 1] + 1) }
            }
            currentRow[secondIndex + 1] = distance
        }
        twoRowsBack = previousRow
        previousRow = currentRow
    }

    return previousRow[second.length].takeIf { it <= maxDistance }
}

private fun isAdjacentTransposition(first: String, second: String): Boolean {
    if (first.length != second.length) return false
    val mismatches = first.indices.filter { first[it] != second[it] }
    return mismatches.size == 2 &&
        mismatches[1] == mismatches[0] + 1 &&
        first[mismatches[0]] == second[mismatches[1]] &&
        first[mismatches[1]] == second[mismatches[0]]
}

/**
 * Robust track ID comparison that normalizes prefixes (such as "youtube_", "jamendo_", etc.)
 * so tracks match seamlessly between ExoPlayer media items, view models, and UI components.
 */
fun areTrackIdsEqual(id1: String?, id2: String?): Boolean {
    if (id1 == null || id2 == null) return false
    if (id1 == id2) return true
    val clean1 = id1.trim().removePrefix("youtube_")
    val clean2 = id2.trim().removePrefix("youtube_")
    if (clean1 == clean2) return true
    val stripped1 = clean1.removePrefix("jamendo_").removePrefix("audius_").removePrefix("soundcloud_").removePrefix("radio_")
    val stripped2 = clean2.removePrefix("jamendo_").removePrefix("audius_").removePrefix("soundcloud_").removePrefix("radio_")
    return stripped1.isNotEmpty() && stripped1 == stripped2
}

