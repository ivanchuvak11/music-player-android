package com.musicplayer.android

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicPlayerApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        // L1 FIX: Global uncaught exception logging to preserve crash diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MusicPlayerApp", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Core Optimization: Warm up client-side YouTube extractor asynchronously on background thread
        // Prevents blocking the Main UI thread on cold start while ensuring it's ready when user interacts
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.musicplayer.android.core.audio.YouTubeExtractorService.init()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .components {
                add(com.musicplayer.android.core.audio.AudioArtworkFetcher.Factory(this@MusicPlayerApp))
            }
            .respectCacheHeaders(false)
            .build()
    }
}
