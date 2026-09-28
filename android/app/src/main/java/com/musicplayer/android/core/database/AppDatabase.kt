package com.musicplayer.android.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedTrackEntity::class,
        FavoriteTrackEntity::class,
        PlayHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun playHistoryDao(): PlayHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // C5 FIX: Explicit migration instead of destructive fallback
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add lastPositionMs column with default 0 to play_history table
                db.execSQL("ALTER TABLE play_history ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
                // Add index on trackId for faster lookups
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_trackId ON play_history(trackId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
