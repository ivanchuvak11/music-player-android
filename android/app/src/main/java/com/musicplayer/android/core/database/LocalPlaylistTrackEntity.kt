package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_tracks",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class LocalPlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationMs: Long = 0L,
    val isLocal: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
