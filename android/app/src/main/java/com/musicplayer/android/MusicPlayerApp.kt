package com.musicplayer.android

import android.app.Application
import android.util.Log

class MusicPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // L1 FIX: Global uncaught exception logging to preserve crash diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MusicPlayerApp", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
