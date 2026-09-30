package com.musicplayer.android.core.audio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Coil Fetcher for local audio artwork thumbnails.
 *
 * Prevents UI jank, thread starvation, and decoder thrashing by:
 * 1. Fast-checking a thread-safe cache for audio files without artwork.
 * 2. Utilizing hardware-accelerated [android.content.ContentResolver.loadThumbnail] on Android 10+ (API 29+).
 * 3. Falling back safely to [MediaMetadataRetriever] for ID3 embedded pictures.
 * 4. Supplying a transparent 1x1 bitmap placeholder that Coil caches in [coil.memory.MemoryCache],
 *    guaranteeing 0ms memory hits on subsequent fast scrolls and flings.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val uriKey = data.toString()

        // 1. Fast-path: Already confirmed to have no artwork -> return cached transparent bitmap
        if (missingArtworkCache.contains(uriKey)) {
            return@withContext DrawableResult(
                drawable = BitmapDrawable(context.resources, transparentBitmap),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        }

        var bitmap: Bitmap? = null

        // 2. Hardware-accelerated thumbnail on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data.scheme == "content") {
            try {
                // If it's a MediaStore media uri, loadThumbnail extracts/retrieves cached thumbnail
                if (!uriKey.contains("albumart")) {
                    bitmap = context.contentResolver.loadThumbnail(data, Size(512, 512), null)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }

        // 2b. Legacy albumart content uri attempt
        if (bitmap == null && uriKey.contains("albumart")) {
            try {
                context.contentResolver.openInputStream(data)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                bitmap = null
            }
        }

        // 3. Fallback: MediaMetadataRetriever for embedded picture in audio file ID3 tag
        if (bitmap == null) {
            val retriever = MediaMetadataRetriever()
            try {
                if (data.scheme == "content") {
                    retriever.setDataSource(context, data)
                } else if (data.scheme == "file" || data.path != null) {
                    retriever.setDataSource(data.path)
                }
                val rawPicture = retriever.embeddedPicture
                if (rawPicture != null) {
                    val boundsOptions = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(rawPicture, 0, rawPicture.size, boundsOptions)

                    var inSample = 1
                    val reqSize = 512
                    if (boundsOptions.outHeight > reqSize || boundsOptions.outWidth > reqSize) {
                        val halfH = boundsOptions.outHeight / 2
                        val halfW = boundsOptions.outWidth / 2
                        while ((halfH / inSample) >= reqSize && (halfW / inSample) >= reqSize) {
                            inSample *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = inSample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    bitmap = BitmapFactory.decodeByteArray(rawPicture, 0, rawPicture.size, decodeOptions)
                }
            } catch (_: Exception) {
                bitmap = null
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {
                }
            }
        }

        // 4. Return result
        if (bitmap != null) {
            DrawableResult(
                drawable = BitmapDrawable(context.resources, bitmap),
                isSampled = false,
                dataSource = DataSource.DISK
            )
        } else {
            // Mark missing to avoid repeated storage reads during fast flings
            missingArtworkCache.add(uriKey)
            DrawableResult(
                drawable = BitmapDrawable(context.resources, transparentBitmap),
                isSampled = false,
                dataSource = DataSource.MEMORY
            )
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!isAudioMediaUri(data)) return null
            return AudioArtworkFetcher(context, data, options)
        }

        private fun isAudioMediaUri(uri: Uri): Boolean {
            val scheme = uri.scheme ?: return false
            val uriStr = uri.toString()

            if (scheme == "content") {
                val authority = uri.authority.orEmpty()
                return (authority == "media" || authority.contains("media")) &&
                    (uriStr.contains("audio") || uriStr.contains("albumart"))
            }

            if (scheme == "file") {
                val path = uri.path?.lowercase().orEmpty()
                return path.endsWith(".mp3") ||
                    path.endsWith(".m4a") ||
                    path.endsWith(".flac") ||
                    path.endsWith(".ogg") ||
                    path.endsWith(".opus") ||
                    path.endsWith(".wav") ||
                    path.endsWith(".aac") ||
                    path.endsWith(".wma")
            }

            return false
        }
    }

    companion object {
        private val missingArtworkCache = ConcurrentHashMap.newKeySet<String>()

        private val transparentBitmap: Bitmap by lazy {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
        }

        fun clearCache() {
            missingArtworkCache.clear()
        }
    }
}
