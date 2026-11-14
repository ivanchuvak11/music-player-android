package com.musicplayer.android.core.audio

import android.net.Uri
import android.util.Log
import com.musicplayer.android.core.network.MusicApiService
import com.musicplayer.android.core.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * AutoplayService — Multi-Source Contextual & Semantic Music Similarity.
 *
 * Combines:
 *  1. YouTube: Deep neural sidebar recommendations (Related Streams via NewPipe) + clean song search.
 *  2. SoundCloud: Contextual and artist-level search via ASP.NET backend.
 *  3. Intelligent metadata normalization: strips video suffixes ("Official Video", "Lyrics", "ft.").
 *  4. Harmonious interleaving: alternates YouTube and SoundCloud tracks with source tagging.
 *  5. Background stream prefetching for top tracks for 0ms start latency.
 */
object AutoplayService {

    private const val TAG = "AutoplayService"
    private val contextualSimilarCache = java.util.concurrent.ConcurrentHashMap<String, List<AudioTrack>>()

    /**
     * Extracts clean artist and title by removing noisy video suffixes, features, and bracketed tags.
     */
    fun extractCleanMetadata(track: AudioTrack): Pair<String, String> {
        val noiseRegex = Regex("""\s*[\(\[\{]?(?:official\s*(?:video|audio|music\s*video|clip)?|lyrics|hd|4k|hq|visualizer|remastered|extended|mix)[\)\]\}]?""", RegexOption.IGNORE_CASE)
        val featRegex = Regex("""\s*(?:feat\.|ft\.|featuring).*""", RegexOption.IGNORE_CASE)

        var cleanTitle = track.title
            .replace(noiseRegex, "")
            .replace(featRegex, "")
            .trim()

        if (cleanTitle.contains(" - ")) {
            val parts = cleanTitle.split(" - ", limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) {
                cleanTitle = parts[1].trim()
            }
        }

        var cleanArtist = track.artist
            .replace(Regex("""\s*-\s*Topic""", RegexOption.IGNORE_CASE), "")
            .replace(featRegex, "")
            .removePrefix("YouTube").trim()
            .removePrefix("-").trim()

        if (cleanArtist.isBlank()) cleanArtist = "Music"
        if (cleanTitle.isBlank()) cleanTitle = track.title

        return cleanArtist to cleanTitle
    }

    /**
     * Contextual multi-source similarity search (YouTube + SoundCloud).
     * Used by the HOME screen (Variant 2) and user-initiated similar track exploration.
     */
    suspend fun fetchContextualSimilarTracks(
        seedTrack: AudioTrack,
        apiService: MusicApiService? = null,
        backendBaseUrl: String = ServerConfig.DEFAULT_BASE_URL,
        existingIds: Set<String> = emptySet(),
        limit: Int = 16
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val cacheKey = "${seedTrack.id}_$limit"
        val cached = contextualSimilarCache[cacheKey]
        if (cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }

        val (cleanArtist, cleanTitle) = extractCleanMetadata(seedTrack)
        val combinedQuery = "$cleanArtist $cleanTitle".trim()

        // Extract YouTube video ID if available
        val videoId = if (seedTrack.id.startsWith("youtube_")) {
            seedTrack.id.removePrefix("youtube_")
        } else {
            try {
                YouTubeExtractorService.extractVideoIdFromUri(Uri.parse(seedTrack.audioUrl))
            } catch (e: Exception) {
                null
            }
        }

        // Parallel Fetch: YouTube + SoundCloud
        val ytDeferred = async {
            val list = mutableListOf<AudioTrack>()
            try {
                if (!videoId.isNullOrBlank()) {
                    Log.d(TAG, "Fetching YouTube related streams for videoId=$videoId")
                    val related = YouTubeExtractorService.getRelatedTracks(videoId, limit = 12)
                    list.addAll(related.map { AudioTrack.fromYouTube(it, backendBaseUrl) })
                }

                if (list.size < 6) {
                    Log.d(TAG, "Supplementing YouTube with clean search for \"$combinedQuery\"")
                    val searched = YouTubeExtractorService.search(combinedQuery, limit = 10)
                    list.addAll(searched.map { AudioTrack.fromYouTube(it, backendBaseUrl) })
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube contextual fetch error: ${e.message}")
            }
            list
        }

        val scDeferred = async {
            val list = mutableListOf<AudioTrack>()
            if (apiService != null) {
                kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    try {
                        // Query 1: Track-specific match (remixes, originals, edits)
                        val q1Resp = apiService.searchSoundCloudTracks(combinedQuery, limit = 6)
                        if (q1Resp.isSuccessful) {
                            q1Resp.body()?.forEach { dto ->
                                list.add(AudioTrack.fromSoundCloud(dto, backendBaseUrl))
                            }
                        }

                        // Query 2: Artist-level match (same vibe/genre)
                        if (cleanArtist.isNotBlank() && cleanArtist != "Music") {
                            val q2Resp = apiService.searchSoundCloudTracks(cleanArtist, limit = 6)
                            if (q2Resp.isSuccessful) {
                                q2Resp.body()?.forEach { dto ->
                                    list.add(AudioTrack.fromSoundCloud(dto, backendBaseUrl))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SoundCloud contextual fetch error: ${e.message}")
                    }
                    Unit
                }
            }
            list
        }

        val ytResults = ytDeferred.await()
        val scResults = scDeferred.await()

        val seedCleanId = seedTrack.id.removePrefix("youtube_").removePrefix("soundcloud_")
        val normalizedSeedTitle = seedTrack.title.trim().lowercase()

        // Filter YouTube results
        val filteredYt = ytResults
            .filterNot { it.id == seedTrack.id || it.id in existingIds || it.id.removePrefix("youtube_") == seedCleanId }
            .filterNot { it.title.trim().lowercase() == normalizedSeedTitle }
            .distinctBy { it.id }

        // Filter SoundCloud results
        val filteredSc = scResults
            .filterNot { it.id == seedTrack.id || it.id in existingIds || it.id.removePrefix("soundcloud_") == seedCleanId }
            .filterNot { it.title.trim().lowercase() == normalizedSeedTitle }
            .distinctBy { it.id }

        // Interleave YouTube and SoundCloud tracks
        val interleaved = mutableListOf<AudioTrack>()
        val seenTrackSignatures = mutableSetOf<String>()

        fun addIfDistinct(track: AudioTrack): Boolean {
            val signature = "${track.artist.trim().lowercase()}:${track.title.trim().lowercase()}"
            return if (seenTrackSignatures.add(signature)) {
                interleaved.add(track)
                true
            } else false
        }

        val ytIter = filteredYt.iterator()
        val scIter = filteredSc.iterator()

        while (interleaved.size < limit && (ytIter.hasNext() || scIter.hasNext())) {
            if (ytIter.hasNext()) {
                addIfDistinct(ytIter.next())
            }
            if (interleaved.size < limit && scIter.hasNext()) {
                addIfDistinct(scIter.next())
            }
        }

        // Prefetch top 2 YouTube tracks for instant zero-buffer playback
        val upcomingYtIds = interleaved
            .filter { it.id.startsWith("youtube_") }
            .take(2)
            .map { it.id.removePrefix("youtube_") }
        if (upcomingYtIds.isNotEmpty()) {
            YouTubeExtractorService.prefetchNextTracks(upcomingYtIds)
        }

        Log.d(TAG, "Contextual similar tracks: got ${interleaved.size} (YT: ${filteredYt.size}, SC: ${filteredSc.size})")
        if (interleaved.isNotEmpty()) {
            contextualSimilarCache[cacheKey] = interleaved
        }
        interleaved
    }

    /**
     * Backward-compatible similar tracks fetcher for queue autoplay.
     */
    suspend fun fetchSimilarTracks(
        currentTrack: AudioTrack,
        existingIds: Set<String>,
        limit: Int = 15
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val backendBase = ServerConfig.DEFAULT_BASE_URL

        val videoId = if (currentTrack.id.startsWith("youtube_")) {
            currentTrack.id.removePrefix("youtube_")
        } else {
            try {
                YouTubeExtractorService.extractVideoIdFromUri(Uri.parse(currentTrack.audioUrl))
            } catch (e: Exception) {
                null
            }
        }

        if (!videoId.isNullOrBlank()) {
            Log.d(TAG, "Autoplay: fetching related for YouTube videoId=$videoId")
            val related = YouTubeExtractorService.getRelatedTracks(videoId, limit + existingIds.size)
            val filtered = related.filterNot { it.externalId in existingIds || "youtube_${it.externalId}" in existingIds }
                .take(limit)
                .map { AudioTrack.fromYouTube(it, backendBase) }
            if (filtered.isNotEmpty()) {
                Log.d(TAG, "Autoplay (related): got ${filtered.size} tracks")
                return@withContext filtered
            }
        }

        val searchQuery = buildSearchQuery(currentTrack)
        Log.d(TAG, "Autoplay: searching YouTube for \"$searchQuery\"")
        val searched = YouTubeExtractorService.search(searchQuery, limit + existingIds.size)
        val filtered = searched
            .filterNot { it.externalId in existingIds || "youtube_${it.externalId}" in existingIds }
            .filterNot { it.externalId == currentTrack.id.removePrefix("youtube_") }
            .take(limit)
            .map { AudioTrack.fromYouTube(it, backendBase) }

        Log.d(TAG, "Autoplay (search): got ${filtered.size} tracks for \"$searchQuery\"")
        filtered
    }

    private fun buildSearchQuery(track: AudioTrack): String {
        val (cleanArtist, cleanTitle) = extractCleanMetadata(track)
        return when {
            cleanArtist.isNotBlank() && cleanTitle.isNotBlank() -> "$cleanArtist - $cleanTitle"
            cleanArtist.isNotBlank() -> cleanArtist
            cleanTitle.isNotBlank() -> cleanTitle
            else -> "popular music"
        }
    }
}
