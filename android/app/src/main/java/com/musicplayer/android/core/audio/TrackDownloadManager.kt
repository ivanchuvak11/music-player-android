package com.musicplayer.android.core.audio

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads audio tracks directly into private application storage (context.filesDir/offline_tracks)
 * for guaranteed permanent offline playback that is immune to OS cache clearing.
 */
object TrackDownloadManager {
    private const val TAG = "TrackDownloadManager"
    private const val OFFLINE_FOLDER = "offline_tracks"

    fun getOfflineTracksDirectory(context: Context): File {
        val dir = File(context.filesDir, OFFLINE_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun downloadTrackToPrivateStorage(
        context: Context,
        trackId: String,
        audioUrl: String,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        val dir = getOfflineTracksDirectory(context)
        // Sanitize track ID for filename
        val safeFileName = "${trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.mp3"
        val targetFile = File(dir, safeFileName)
        val tempFile = File(dir, "$safeFileName.tmp")

        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        var input: InputStream? = null
        var output: FileOutputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val url = URL(audioUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Download failed for $audioUrl: HTTP ${connection.responseCode}")
                return@withContext null
            }

            val fileLength = connection.contentLengthLong
            input = connection.inputStream
            output = FileOutputStream(tempFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (input.read(data).also { count = it } != -1) {
                output.write(data, 0, count)
                total += count
                if (fileLength > 0 && onProgress != null) {
                    val progress = ((total * 100) / fileLength).toInt()
                    onProgress(progress)
                }
            }

            output.flush()
            output.close()
            output = null
            input.close()
            input = null

            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "Successfully downloaded track $trackId to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                targetFile
            } else {
                Log.e(TAG, "Failed to rename temp file to $safeFileName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading track $trackId", e)
            if (tempFile.exists()) tempFile.delete()
            null
        } finally {
            try { input?.close() } catch (ignored: Exception) {}
            try { output?.close() } catch (ignored: Exception) {}
            connection?.disconnect()
        }
    }

    fun deleteDownloadedTrack(localFilePath: String?): Boolean {
        if (localFilePath.isNullOrBlank()) return false
        return try {
            val file = File(localFilePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Deleted offline track file: $localFilePath, success = $deleted")
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting offline track file: $localFilePath", e)
            false
        }
    }
}
