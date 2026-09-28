package com.musicplayer.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history ORDER BY playedAtTimestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<PlayHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE trackId = :trackId")
    suspend fun deleteTrackHistory(trackId: String)

    @Query("DELETE FROM play_history")
    suspend fun clearAllHistory()

    // M5 FIX: Get last position for a specific track to resume playback
    @Query("SELECT lastPositionMs FROM play_history WHERE trackId = :trackId ORDER BY playedAtTimestamp DESC LIMIT 1")
    suspend fun getLastPosition(trackId: String): Long?

    // M8 FIX: Trim old history entries to prevent unbounded growth (keep most recent 500)
    @Query("DELETE FROM play_history WHERE historyId NOT IN (SELECT historyId FROM play_history ORDER BY playedAtTimestamp DESC LIMIT 500)")
    suspend fun trimOldHistory()
}
