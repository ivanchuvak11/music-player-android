package com.musicplayer.android.core.audio

import android.content.Context
import android.util.Log
import com.musicplayer.android.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Downloads audio tracks directly into private application storage (context.filesDir/offline_tracks)
 * using the shared OkHttpClient (with AuthInterceptor, timeouts, and redirects).
 *
 * Implements:
 * - Content-Type validation (must be audio or octet-stream)
 * - Maximum single file limit (150 MB)
 * - Minimum valid audio size check (50 KB)
 * - Cancellation support & .tmp cleanup
 * - Orphan .tmp cleanup on startup
 */
object TrackDownloadManager {
    private const val TAG = "TrackDownloadManager"
    private const val OFFLINE_FOLDER = "offline_tracks"
    const val MAX_FILE_SIZE_BYTES = 150L * 1024 * 1024 // 150 MB
    const val MIN_FILE_SIZE_BYTES = 50L * 1024 // 50 KB minimum for valid audio

    fun getOfflineTracksDirectory(context: Context): File {
        val dir = File(context.filesDir, OFFLINE_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Cleans up orphaned .tmp files left over from cancelled or crashed downloads.
     */
    fun cleanupOrphanTempFiles(context: Context) {
        try {
            val dir = getOfflineTracksDirectory(context)
            val tempFiles = dir.listFiles { _, name -> name.endsWith(".tmp") }
            tempFiles?.forEach { tempFile ->
                val deleted = tempFile.delete()
                Log.d(TAG, "Cleaned up orphaned temp file: ${tempFile.name}, deleted = $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }

    suspend fun downloadTrackToPrivateStorage(
        context: Context,
        trackId: String,
        audioUrl: String,
        isLiveStream: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        // Rule 1: Never download live radio streams
        if (isLiveStream) {
            Log.w(TAG, "Refusing to download live stream: $trackId")
            return@withContext null
        }

        val dir = getOfflineTracksDirectory(context)
        val safeFileName = "${trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.mp3"
        val targetFile = File(dir, safeFileName)
        val tempFile = File(dir, "$safeFileName.tmp")

        if (targetFile.exists() && targetFile.length() >= MIN_FILE_SIZE_BYTES) {
            return@withContext targetFile
        }

        val client = NetworkClient.getOkHttpClient(context)
        val request = Request.Builder()
            .url(audioUrl)
            .get()
            .build()

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed for $audioUrl: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "Empty body received for $audioUrl")
                return@withContext null
            }

            // Check Content-Length limit
            val contentLength = body.contentLength()
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                Log.e(TAG, "File exceeds max size limit (${contentLength} > $MAX_FILE_SIZE_BYTES): $audioUrl")
                return@withContext null
            }

            // Check Content-Type (audio/*, application/octet-stream, binary)
            val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
            if (contentType.isNotEmpty() &&
                !contentType.contains("audio") &&
                !contentType.contains("octet-stream") &&
                !contentType.contains("application/x-mpegurl") &&
                !contentType.contains("video/mp4") // Some CDNs label m4a/aac as mp4
            ) {
                Log.e(TAG, "Invalid content type for audio: $contentType, URL: $audioUrl")
                return@withContext null
            }

            input = body.byteStream()
            output = FileOutputStream(tempFile)

            val buffer = ByteArray(16384)
            var totalBytes: Long = 0
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) {
                    Log.d(TAG, "Download cancelled for track $trackId")
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }

                output.write(buffer, 0, bytesRead)
                totalBytes += bytesRead

                if (totalBytes > MAX_FILE_SIZE_BYTES) {
                    Log.e(TAG, "Download exceeded max file size during streaming")
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }

                if (contentLength > 0 && onProgress != null) {
                    val progress = ((totalBytes * 100) / contentLength).toInt().coerceIn(0, 100)
                    onProgress(progress)
                }
            }

            output.flush()
            output.close()
            output = null
            input.close()
            input = null

            // Sanity check minimum file length
            if (tempFile.length() < MIN_FILE_SIZE_BYTES) {
                Log.e(TAG, "Downloaded file is suspiciously small (${tempFile.length()} bytes), discarding")
                tempFile.delete()
                return@withContext null
            }

            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "Successfully downloaded track $trackId to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                targetFile
            } else {
                Log.e(TAG, "Failed to rename temp file to ${targetFile.name}")
                if (tempFile.exists()) tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception downloading track $trackId: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            null
        } finally {
            try { input?.close() } catch (ignored: Exception) {}
            try { output?.close() } catch (ignored: Exception) {}
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
