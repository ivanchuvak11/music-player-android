package com.musicplayer.android.core.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Manages ExoPlayer SimpleCache for caching online audio streams and tracks locally.
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {
    private const val MAX_CACHE_SIZE: Long = 512 * 1024 * 1024 // 512 MB
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            try {
                simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
            } catch (e: Exception) {
                e.printStackTrace()
                if (SimpleCache.isCacheFolderLocked(cacheDir)) {
                    val altCacheDir = File(context.cacheDir, "media_cache_alt")
                    if (!altCacheDir.exists()) altCacheDir.mkdirs()
                    simpleCache = SimpleCache(altCacheDir, evictor, databaseProvider)
                } else {
                    throw e
                }
            }
        }
        return simpleCache!!
    }

    fun buildCacheDataSourceFactory(context: Context): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun isTrackCached(context: Context, audioUrl: String): Boolean {
        return try {
            val cache = getCache(context)
            cache.getCachedSpans(audioUrl).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getCacheSizeBytes(context: Context): Long {
        return try {
            // Fix #2: count both primary and alt cache directories
            val primarySize = try { getCache(context).cacheSpace } catch (e: Exception) { 0L }
            val altDir = File(context.cacheDir, "media_cache_alt")
            val altSize = if (altDir.exists()) altDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
            primarySize + altSize
        } catch (e: Exception) {
            val cacheDir = File(context.cacheDir, "media_cache")
            val primary = if (cacheDir.exists()) cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
            val altDir = File(context.cacheDir, "media_cache_alt")
            val alt = if (altDir.exists()) altDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
            primary + alt
        }
    }

    @Synchronized
    fun clearCache(context: Context) {
        try {
            val cache = simpleCache
            if (cache != null) {
                val keys = cache.keys.toList()  // snapshot to avoid concurrent modification
                for (key in keys) {
                    try { cache.removeResource(key) } catch (e: Exception) { e.printStackTrace() }
                }
            }
            // Fix #2: always delete BOTH directories regardless of simpleCache state
            val primaryDir = File(context.cacheDir, "media_cache")
            if (primaryDir.exists()) primaryDir.deleteRecursively()
            val altDir = File(context.cacheDir, "media_cache_alt")
            if (altDir.exists()) altDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun releaseCache() {
        simpleCache?.release()
        simpleCache = null
    }
}
