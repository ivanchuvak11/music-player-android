package com.musicplayer.android.core.audio

/**
 * Predefined music genres / categories for discovery and browsing.
 */
data class MusicGenre(
    val id: String,
    val name: String,
    val searchQuery: String,
    val iconEmoji: String,
    val gradientColors: List<Long>
)

/**
 * Represents an artist profile aggregated from local and online tracks.
 */
data class ArtistInfo(
    val name: String,
    val artworkUrl: String? = null,
    val trackCount: Int = 0,
    val tracks: List<AudioTrack> = emptyList()
)

/**
 * Pre-made curated playlist (ready-made playlist) with metadata, themes, and tracks.
 */
data class CuratedPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String? = null,
    val gradientColors: List<Long>,
    val tracks: List<AudioTrack> = emptyList(),
    val searchQuery: String = ""
)
