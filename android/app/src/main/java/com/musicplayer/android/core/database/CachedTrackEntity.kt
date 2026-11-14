package com.musicplayer.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

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
) {
    /**
     * Returns true strictly when a real downloaded audio file exists on disk (>= 50 KB),
     * preventing metadata-only search/recommendation entries (localFilePath == null)
     * from appearing as downloaded tracks.
     */
    fun hasValidDownloadedFile(): Boolean {
        val path = localFilePath ?: return false
        if (path.isBlank()) return false
        val file = File(path)
        return file.exists() && file.length() >= 51_200L
    }
}
