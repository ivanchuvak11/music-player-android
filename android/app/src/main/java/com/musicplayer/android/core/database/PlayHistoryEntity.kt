package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "play_history",
    indices = [Index(value = ["playedAtTimestamp"]), Index(value = ["trackId"])]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0L,
    val trackId: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val lastPositionMs: Long = 0L,
    val playedAtTimestamp: Long = System.currentTimeMillis()
)
