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
}
