package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fix #14: Added artworkUrl so cached tracks preserve their album artwork
 * when displayed in offline mode.
 */
@Entity(
    tableName = "cached_tracks",
    indices = [Index(value = ["cachedAtTimestamp"])]
)
data class CachedTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val localFilePath: String?,
    val originalUrl: String,
    val artworkUrl: String? = null,
    val durationMs: Long,
    val cachedAtTimestamp: Long
)
