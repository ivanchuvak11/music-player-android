package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_tracks",
    indices = [Index(value = ["favoritedAtTimestamp"])]
)
data class FavoriteTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val favoritedAtTimestamp: Long = System.currentTimeMillis()
)
