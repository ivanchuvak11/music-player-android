package com.musicplayer.android.core.audio

import android.util.Log
import com.musicplayer.android.core.network.YouTubeTrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AutoplayService — Smart Hybrid Autoplay (Variant 4).
 *
 * Strategy:
 *  1. YouTube tracks: fetch "Related Streams" from YouTube sidebar (best relevance).
 *  2. Any other track: search YouTube for "Artist - Title" (genre/mood-aware results).
 *  3. Filter out tracks already in the current queue (by externalId).
 *  4. Return up to [limit] fresh AudioTrack instances ready to append to the queue.
 *
 * This service is called by MainPlayerViewModel when:
 *  - Autoplay is enabled (user preference, default ON).
 *  - Repeat mode is OFF (when repeat is ALL or ONE, the queue loops itself, no need to extend).
 *  - The current track is the last track in the queue (hasNext == false).
 */
object AutoplayService {

    private const val TAG = "AutoplayService"

    /**
     * Fetches up to [limit] autoplay tracks similar to [currentTrack].
     * [existingIds] are the IDs already in the current queue (to avoid duplicates).
     */
    suspend fun fetchSimilarTracks(
        currentTrack: AudioTrack,
        existingIds: Set<String>,
        limit: Int = 15
    ): List<AudioTrack> = withContext(Dispatchers.IO) {
        val backendBase = com.musicplayer.android.core.network.ServerConfig.DEFAULT_LOCAL_BASE_URL

        // Strategy 1: YouTube track → use Related Streams from YouTube sidebar
        if (currentTrack.id.startsWith("youtube_")) {
            val videoId = currentTrack.id.removePrefix("youtube_")
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

        // Strategy 2 (fallback): Search by "Artist - Title" on YouTube to find similar-sounding tracks
        val searchQuery = buildSearchQuery(currentTrack)
        Log.d(TAG, "Autoplay: searching YouTube for \"$searchQuery\"")
        val searched = YouTubeExtractorService.search(searchQuery, limit + existingIds.size)
        val filtered = searched
            .filterNot { it.externalId in existingIds || "youtube_${it.externalId}" in existingIds }
            // Don't re-play the exact same song
            .filterNot { it.externalId == currentTrack.id.removePrefix("youtube_") }
            .take(limit)
            .map { AudioTrack.fromYouTube(it, backendBase) }

        Log.d(TAG, "Autoplay (search): got ${filtered.size} tracks for \"$searchQuery\"")
        filtered
    }

    /**
     * Builds a YouTube search query for finding similar tracks.
     * Examples:
     *  - "Adele - Hello"  → finds covers, live versions, similar ballads in sidebar
     *  - "Imagine Dragons" → finds their discography if track has no clear title hint
     */
    private fun buildSearchQuery(track: AudioTrack): String {
        val artist = track.artist.trim()
            .removePrefix("YouTube").trim()
            .removePrefix("-").trim()
        val title = track.title.trim()
        return when {
            artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
            artist.isNotBlank() -> artist
            title.isNotBlank() -> title
            else -> "popular music"
        }
    }
}
