package com.musicplayer.android.core.audio

import com.musicplayer.android.core.network.YouTubeTrackDto

internal fun shouldSupplementYouTubeSearch(
    query: String,
    primaryResults: List<YouTubeTrackDto>,
    limit: Int
): Boolean {
    if (primaryResults.isEmpty()) return true

    val minimumStrongResults = minOf(3, limit.coerceAtLeast(1))
    val strongResults = primaryResults.count { track ->
        AudioTrack.fromYouTube(track).calculateSearchRelevanceScore(query) >= 300
    }
    return strongResults < minimumStrongResults
}

internal fun mergeYouTubeSearchResults(
    query: String,
    primaryResults: List<YouTubeTrackDto>,
    supplementalResults: List<YouTubeTrackDto>,
    limit: Int
): List<YouTubeTrackDto> {
    if (limit <= 0) return emptyList()

    val seenIds = mutableSetOf<String>()
    val seenMetadata = mutableSetOf<String>()
    val combined = (primaryResults + supplementalResults)

    class RankedItem(
        val dto: YouTubeTrackDto,
        val originalIndex: Int,
        val score: Int
    )

    val ranked = mutableListOf<RankedItem>()
    combined.forEachIndexed { index, track ->
        val id = track.externalId.trim().lowercase()
        val uniqueId = id.isBlank() || seenIds.add(id)
        if (!uniqueId) return@forEachIndexed

        val audioTrack = AudioTrack.fromYouTube(track)
        val metadata = audioTrack.toSearchMetadata()
        val metadataKey = if (metadata.normalizedTitle.isNotBlank() && metadata.normalizedArtist.isNotBlank()) {
            "${metadata.normalizedTitle}|${metadata.normalizedArtist}"
        } else ""
        if (metadataKey.isNotBlank() && !seenMetadata.add(metadataKey)) {
            return@forEachIndexed
        }

        val rawScore = calculateSearchRelevanceScoreWithMetadata(query, metadata)
        // If track explicitly matches query words (artist/title), boost it with high score.
        // Otherwise, preserve natural YouTube AI rank so foreign/genre/thematic songs stay at the top.
        val effectiveScore = if (rawScore >= 250) rawScore else (150 - index.coerceAtMost(100))
        ranked.add(RankedItem(track, index, effectiveScore))
    }

    return ranked
        .sortedWith(
            compareByDescending<RankedItem> { it.score }
                .thenBy { it.originalIndex }
        )
        .take(limit)
        .map { it.dto }
}
