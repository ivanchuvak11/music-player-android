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
        PlayHistoryEntity::class,
        LocalPlaylistEntity::class,
        LocalPlaylistTrackEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun localPlaylistDao(): LocalPlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE play_history ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_trackId ON play_history(trackId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_playlists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        audioUrl TEXT NOT NULL,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 1,
                        addedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks(trackId)")
            }
        }

        /**
         * Fix #14 + #30: Adds artworkUrl to cached_tracks and rebuilds playlist_tracks
         * with ForeignKey CASCADE (SQLite requires a full table rebuild to add FK constraints).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add artworkUrl to cached_tracks (nullable, old rows get NULL)
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN artworkUrl TEXT")

                // Rebuild playlist_tracks to add ForeignKey CASCADE + artworkUrl
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playlistId INTEGER NOT NULL,
                        trackId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        audioUrl TEXT NOT NULL,
                        artworkUrl TEXT,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        isLocal INTEGER NOT NULL DEFAULT 1,
                        addedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(playlistId) REFERENCES local_playlists(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO playlist_tracks_new (id, playlistId, trackId, title, artist, audioUrl, artworkUrl, durationMs, isLocal, addedAt)
                    SELECT id, playlistId, trackId, title, artist, audioUrl, NULL, durationMs, isLocal, addedAt FROM playlist_tracks
                """.trimIndent())
                db.execSQL("DROP TABLE playlist_tracks")
                db.execSQL("ALTER TABLE playlist_tracks_new RENAME TO playlist_tracks")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks(trackId)")
            }
        }

        private fun createFullSchema7(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS cached_tracks")
            db.execSQL("DROP TABLE IF EXISTS favorite_tracks")
            db.execSQL("DROP TABLE IF EXISTS play_history")
            db.execSQL("DROP TABLE IF EXISTS local_playlists")
            db.execSQL("DROP TABLE IF EXISTS playlist_tracks")

            db.execSQL("""
                CREATE TABLE cached_tracks (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    localFilePath TEXT,
                    originalUrl TEXT NOT NULL,
                    artworkUrl TEXT,
                    durationMs INTEGER NOT NULL,
                    cachedAtTimestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_cached_tracks_cachedAtTimestamp ON cached_tracks(cachedAtTimestamp)")

            db.execSQL("""
                CREATE TABLE favorite_tracks (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    audioUrl TEXT NOT NULL,
                    artworkUrl TEXT,
                    durationMs INTEGER NOT NULL,
                    favoritedAtTimestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_tracks_favoritedAtTimestamp ON favorite_tracks(favoritedAtTimestamp)")

            db.execSQL("""
                CREATE TABLE play_history (
                    historyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    trackId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    audioUrl TEXT NOT NULL,
                    artworkUrl TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    lastPositionMs INTEGER NOT NULL DEFAULT 0,
                    playedAtTimestamp INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_trackId ON play_history(trackId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_play_history_playedAtTimestamp ON play_history(playedAtTimestamp)")

            db.execSQL("""
                CREATE TABLE local_playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE playlist_tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    playlistId INTEGER NOT NULL,
                    trackId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    audioUrl TEXT NOT NULL,
                    artworkUrl TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    isLocal INTEGER NOT NULL DEFAULT 1,
                    addedAt INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(playlistId) REFERENCES local_playlists(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks(trackId)")
        }

        private val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFullSchema7(db)
            }
        }

        private val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFullSchema7(db)
            }
        }

        private val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFullSchema7(db)
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFullSchema7(db)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_player_db"
                )
                    .addMigrations(
                        MIGRATION_1_7,
                        MIGRATION_2_7,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_5_7,
                        MIGRATION_6_7
                    ).apply {
                        val isDebug = try {
                            (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                        } catch (e: Exception) {
                            false
                        }
                        if (isDebug) {
                            android.util.Log.w("AppDatabase", "WARNING: fallbackToDestructiveMigration is ACTIVE for debug build. Release builds require explicit migrations.")
                            fallbackToDestructiveMigration()
                            fallbackToDestructiveMigrationOnDowngrade()
                        }
                    }
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.execSQL("PRAGMA foreign_keys = ON")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
