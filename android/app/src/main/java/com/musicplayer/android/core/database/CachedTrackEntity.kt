package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val durationMs: Long,
    val cachedAtTimestamp: Long
)
