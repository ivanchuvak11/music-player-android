package com.musicplayer.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {
    @Query("SELECT * FROM cached_tracks ORDER BY cachedAtTimestamp DESC")
    fun getAllCachedTracks(): Flow<List<CachedTrackEntity>>

    @Query("SELECT * FROM cached_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): CachedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: CachedTrackEntity)

    @Query("DELETE FROM cached_tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("DELETE FROM cached_tracks")
    suspend fun clearAll()
}
