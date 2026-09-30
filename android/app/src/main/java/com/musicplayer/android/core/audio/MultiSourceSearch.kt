package com.musicplayer.android.core.audio

import android.util.Log

/**
 * Result of a multi-source search.
 * Contains unified results plus per-source errors so UI can show partial failures
 * while continuing to display results from working sources (ТЗ Section 3, 8).
 */
data class MultiSourceSearchResult(
    val tracks: List<UnifiedTrack> = emptyList(),
    val errors: Map<String, com.musicplayer.android.core.network.MusicSourceError> = emptyMap(),
    val isOfflineFallback: Boolean = false
) {
    val hasResults: Boolean get() = tracks.isNotEmpty()
    val hasErrors: Boolean get() = errors.isNotEmpty()
    val allSourcesFailed: Boolean get() = errors.size == 3 // SoundCloud + Audius + Jamendo
}

/**
 * Deduplicates a list of UnifiedTrack by (source + externalId) pair.
 * Preserves ordering (first occurrence wins) per ТЗ Section 3.
 */
fun List<UnifiedTrack>.deduplicateBySourceAndId(): List<UnifiedTrack> {
    val seen = mutableSetOf<String>()
    return filter { track ->
        val key = "${track.source.value}::${track.externalId}"
        seen.add(key) // returns true if not yet seen
    }
}

/**
 * Sorts UnifiedTrack list by relevance score against a query.
 * Higher relevance scores come first.
 */
fun List<UnifiedTrack>.sortByRelevance(query: String): List<UnifiedTrack> {
    if (query.isBlank()) return this
    val q = query.trim().lowercase()
    return sortedByDescending { track ->
        val t = track.title.trim().lowercase()
        val a = track.artist.trim().lowercase()
        when {
            t == q                                          -> 1000
            t.startsWith(q)                                -> 800
            t.contains(" $q") || t.contains("($q")        -> 600
            t.contains(q)                                  -> 400
            a == q                                         -> 300
            a.startsWith(q)                                -> 200
            a.contains(q)                                  -> 100
            else                                           -> 0
        }
    }
}

/**
 * Merges results from SoundCloud, Audius, Jamendo into a single deduplicated list.
 * Order per ТЗ Section 3: SoundCloud → Audius → Jamendo.
 * Deduplicates by (source + externalId).
 */
fun mergeAndDeduplicateSources(
    soundCloudTracks: List<UnifiedTrack>,
    audiusTracks: List<UnifiedTrack>,
    jamendoTracks: List<UnifiedTrack>
): List<UnifiedTrack> {
    // Concatenate in source priority order
    val merged = soundCloudTracks + audiusTracks + jamendoTracks
    val result = merged.deduplicateBySourceAndId()
    Log.d(
        "MusicSearch",
        "Merged: SC=${soundCloudTracks.size}, Audius=${audiusTracks.size}, Jamendo=${jamendoTracks.size} " +
            "→ total=${merged.size}, after dedup=${result.size}"
    )
    return result
}
