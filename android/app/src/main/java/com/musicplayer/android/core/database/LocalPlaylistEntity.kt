package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
