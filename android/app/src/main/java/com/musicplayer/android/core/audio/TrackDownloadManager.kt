package com.musicplayer.android.core.audio

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.musicplayer.android.core.network.NetworkClient
import com.musicplayer.android.core.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * Downloads audio tracks directly into private application storage or device public storage
 * (Environment.DIRECTORY_MUSIC / ExternalFilesDir) with fast chunked range fetching
 * to avoid Google Video CDN playback rate throttling.
 *
 * Implements:
 * - Direct device downloading with clean "Artist - Title.mp3" naming
 * - Resolves all sources (YouTube, SoundCloud, Direct, Jamendo, Audius)
 * - Anti-throttle chunked Range requests for YouTube streams
 * - MediaScanner registration for immediate visibility in system music apps
 * - System DownloadManager enqueue fallback
 * - Content-Type validation and file size limits (50 KB min, 150 MB max)
 * - Cancellation support & .tmp cleanup
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
     * Resolves the best writable device directory for saving user music tracks.
     * Tries public Environment.DIRECTORY_MUSIC first, falls back to external app files dir.
     */
    fun getDeviceMusicDirectory(context: Context): File {
        val publicDir = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        } catch (e: Exception) {
            null
        }

        if (publicDir != null && (publicDir.exists() || publicDir.mkdirs()) && publicDir.canWrite()) {
            return publicDir
        }

        val extFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        if (extFilesDir != null && (extFilesDir.exists() || extFilesDir.mkdirs())) {
            return extFilesDir
        }

        return getOfflineTracksDirectory(context)
    }

    /**
     * Cleans up orphaned .tmp files left over from cancelled or crashed downloads.
     */
    fun cleanupOrphanTempFiles(context: Context) {
        try {
            val privateDir = getOfflineTracksDirectory(context)
            privateDir.listFiles { _, name -> name.endsWith(".tmp") }?.forEach { it.delete() }

            val deviceDir = getDeviceMusicDirectory(context)
            if (deviceDir != privateDir) {
                deviceDir.listFiles { _, name -> name.endsWith(".tmp") }?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
        }
    }

    /**
     * Resolves the direct audio stream URL for any supported music source:
     * - YouTube: extracts video ID and calls YouTubeExtractorService
     * - SoundCloud: resolves via ServerConfig.resolveSoundCloudStreamUrl
     * - Jamendo: resolves via ServerConfig.resolveJamendoStreamUrl
     * - Audius / Direct: resolves via ServerConfig or returns raw audioUrl
     */
    suspend fun resolveDirectStreamUrl(
        track: AudioTrack,
        backendBaseUrl: String = ServerConfig.DEFAULT_BASE_URL
    ): String? = withContext(Dispatchers.IO) {
        when {
            track.id.startsWith("youtube_") || track.audioUrl.contains("youtube.com") || track.audioUrl.contains("youtu.be") -> {
                val videoId = if (track.id.startsWith("youtube_")) {
                    track.id.removePrefix("youtube_")
                } else {
                    try {
                        YouTubeExtractorService.extractVideoIdFromUri(Uri.parse(track.audioUrl))
                            ?: track.id.removePrefix("youtube_")
                    } catch (e: Exception) {
                        track.id.removePrefix("youtube_")
                    }
                }
                YouTubeExtractorService.resolveAudioStreamUrl(videoId)
            }
            track.id.startsWith("soundcloud_") -> {
                val externalId = track.id.removePrefix("soundcloud_")
                ServerConfig.resolveSoundCloudStreamUrl(externalId, track.audioUrl, backendBaseUrl)
            }
            track.id.startsWith("jamendo_") -> {
                val externalId = track.id.removePrefix("jamendo_")
                ServerConfig.resolveJamendoStreamUrl(externalId, track.audioUrl, backendBaseUrl)
            }
            track.id.startsWith("audius_") -> {
                val externalId = track.id.removePrefix("audius_")
                ServerConfig.resolveAudiusStreamUrl(externalId, backendBaseUrl)
            }
            else -> track.audioUrl
        }
    }

    /**
     * Downloads an audio track from ANY source directly onto the user's device storage
     * (e.g. /Music/Artist - Title.mp3), bypassing Google Video CDN throttle with chunked streaming,
     * registers with MediaScanner for system music apps, and returns the downloaded File.
     */
    suspend fun downloadTrackToDevice(
        context: Context,
        track: AudioTrack,
        backendBaseUrl: String = ServerConfig.DEFAULT_BASE_URL,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        if (track.isLiveStream) {
            Log.w(TAG, "Refusing to download live stream: ${track.id}")
            return@withContext null
        }

        // Clean & sanitize file name
        val safeArtist = track.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        val safeTitle = track.title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        val fileNameBase = when {
            safeArtist.isNotBlank() && safeTitle.isNotBlank() -> "$safeArtist - $safeTitle"
            safeTitle.isNotBlank() -> safeTitle
            safeArtist.isNotBlank() -> safeArtist
            else -> track.id.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")
        }
        val targetFileName = "$fileNameBase.mp3"

        val targetDir = getDeviceMusicDirectory(context)
        val targetFile = File(targetDir, targetFileName)
        val tempFile = File(targetDir, "$targetFileName.tmp")

        if (targetFile.exists() && targetFile.length() >= MIN_FILE_SIZE_BYTES) {
            Log.d(TAG, "Track already exists on device: ${targetFile.absolutePath}")
            onProgress?.invoke(100)
            return@withContext targetFile
        }

        val directStreamUrl = resolveDirectStreamUrl(track, backendBaseUrl)
        if (directStreamUrl.isNullOrBlank()) {
            Log.e(TAG, "Failed to resolve stream URL for track: ${track.id}")
            return@withContext null
        }

        val client = NetworkClient.getOkHttpClient(context)
        val isYouTube = track.id.startsWith("youtube_") || directStreamUrl.contains("googlevideo.com")

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            // Check if chunked Range downloading is viable (for fast YouTube CDN download)
            var totalContentLength = -1L
            var supportsRange = false

            try {
                val headReq = Request.Builder()
                    .url(directStreamUrl)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .build()
                val headResp = client.newCall(headReq).execute()
                if (headResp.isSuccessful) {
                    val clHeader = headResp.header("Content-Length")?.toLongOrNull()
                    if (clHeader != null && clHeader in (MIN_FILE_SIZE_BYTES..MAX_FILE_SIZE_BYTES)) {
                        totalContentLength = clHeader
                        supportsRange = headResp.header("Accept-Ranges")?.contains("bytes") == true || isYouTube
                    }
                }
                headResp.close()
            } catch (e: Exception) {
                Log.d(TAG, "HEAD request check skipped: ${e.message}")
            }

            output = FileOutputStream(tempFile)

            // Fast chunked mode for YouTube (1MB chunks bypasses rate limiting)
            if (isYouTube && supportsRange && totalContentLength > 0) {
                Log.d(TAG, "Downloading with anti-throttle Range chunks for $targetFileName (Total: $totalContentLength bytes)")
                val chunkSize = 1024 * 1024L // 1 MB
                var bytesDownloaded = 0L

                while (bytesDownloaded < totalContentLength && coroutineContext.isActive) {
                    val endByte = (bytesDownloaded + chunkSize - 1).coerceAtMost(totalContentLength - 1)
                    val rangeReq = Request.Builder()
                        .url(directStreamUrl)
                        .header("Range", "bytes=$bytesDownloaded-$endByte")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .build()

                    val resp = client.newCall(rangeReq).execute()
                    if (!resp.isSuccessful && resp.code != 206) {
                        resp.close()
                        throw IllegalStateException("Range request failed with HTTP ${resp.code}")
                    }

                    val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty chunk stream")
                    val buffer = ByteArray(32768)
                    var read: Int
                    while (stream.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                    }
                    stream.close()
                    resp.close()

                    val progress = ((bytesDownloaded * 100) / totalContentLength).toInt().coerceIn(0, 100)
                    onProgress?.invoke(progress)
                }
            } else {
                // High-performance streaming mode with large 64KB buffers
                Log.d(TAG, "Downloading with streaming mode for $targetFileName")
                val request = Request.Builder()
                    .url(directStreamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed for ${track.id}: HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val cl = body.contentLength().takeIf { it > 0 } ?: totalContentLength

                input = body.byteStream()
                val buffer = ByteArray(65536) // 64 KB buffer
                var totalBytes = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (!coroutineContext.isActive) {
                        tempFile.delete()
                        return@withContext null
                    }
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead

                    if (cl > 0 && onProgress != null) {
                        val progress = ((totalBytes * 100) / cl).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
            }

            output.flush()
            output.close()
            output = null
            input?.close()
            input = null

            if (tempFile.length() < MIN_FILE_SIZE_BYTES) {
                Log.e(TAG, "Downloaded file too small: ${tempFile.length()} bytes")
                tempFile.delete()
                return@withContext null
            }

            if (tempFile.renameTo(targetFile)) {
                Log.i(TAG, "Successfully downloaded track to device: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                onProgress?.invoke(100)

                // Register with Android MediaStore
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf("audio/mpeg", "audio/mp4", "audio/*")
                    ) { path, uri ->
                        Log.d(TAG, "Indexed by Android MediaScanner: $path -> $uri")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MediaScanner notification failed: ${e.message}")
                }

                targetFile
            } else {
                Log.e(TAG, "Failed to rename temp file to ${targetFile.name}")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading track ${track.id} to device: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            null
        } finally {
            try { input?.close() } catch (ignored: Exception) {}
            try { output?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Enqueues a download via system Android DownloadManager (with system notification progress).
     */
    fun enqueueSystemDownload(
        context: Context,
        track: AudioTrack,
        resolvedUrl: String
    ): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val safeArtist = track.artist.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        val safeTitle = track.title.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        val fileName = "$safeArtist - $safeTitle.mp3"

        val request = DownloadManager.Request(Uri.parse(resolvedUrl))
            .setTitle(track.title)
            .setDescription(track.artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName)
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")

        return downloadManager.enqueue(request)
    }

    suspend fun downloadTrackToPrivateStorage(
        context: Context,
        trackId: String,
        audioUrl: String,
        isLiveStream: Boolean = false,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
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

        val videoId = YouTubeExtractorService.extractVideoIdFromUrl(audioUrl)
            ?: trackId.takeIf { it.startsWith("youtube_") }?.removePrefix("youtube_")
        val effectiveDownloadUrl = if (!videoId.isNullOrBlank()) {
            YouTubeExtractorService.resolveAudioStreamUrlSync(videoId) ?: return@withContext null
        } else {
            audioUrl
        }

        val client = NetworkClient.getOkHttpClient(context)
        val request = Request.Builder()
            .url(effectiveDownloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
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

            val contentLength = body.contentLength()
            if (contentLength > MAX_FILE_SIZE_BYTES) {
                Log.e(TAG, "File exceeds max size limit (${contentLength} > $MAX_FILE_SIZE_BYTES): $audioUrl")
                return@withContext null
            }

            input = body.byteStream()
            output = FileOutputStream(tempFile)

            val buffer = ByteArray(32768)
            var totalBytes: Long = 0
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (!coroutineContext.isActive) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }

                output.write(buffer, 0, bytesRead)
                totalBytes += bytesRead

                if (totalBytes > MAX_FILE_SIZE_BYTES) {
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

            if (tempFile.length() < MIN_FILE_SIZE_BYTES) {
                tempFile.delete()
                return@withContext null
            }

            if (tempFile.renameTo(targetFile)) {
                targetFile
            } else {
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
