package com.musicplayer.android.core.audio

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Scans on-device audio files using Android MediaStore with robust error-handling
 * for permission revocations, OEM cursor idiosyncrasies, and audio clips filtering.
 * Also provides a real-time ContentObserver to automatically detect newly downloaded audio files.
 */
class LocalAudioScanner(private val context: Context) {

    /**
     * Observes Android MediaStore for file system changes (e.g. newly downloaded songs).
     * Emits whenever audio files are added, modified, or deleted on the device.
     */
    fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getLocalAudioTracks(minDurationMs: Long = 20_000L): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA
        )

        // Broader selection to catch downloaded tracks that might not have IS_MUSIC set immediately
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                val artworkUriBase = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val filePath = if (dataColumn != -1) cursor.getString(dataColumn)?.lowercase().orEmpty() else ""

                    // Ignore voice messages of any length from messengers or voice recorders
                    val isMessengerOrVoiceNote = filePath.contains("telegram") ||
                        filePath.contains("whatsapp") ||
                        filePath.contains("viber") ||
                        filePath.contains("voice") ||
                        filePath.contains("record") ||
                        filePath.contains("notifications") ||
                        filePath.contains("ringtones") ||
                        filePath.contains("alarms")

                    if (isMessengerOrVoiceNote) {
                        continue
                    }

                    // Check valid audio extensions
                    val isAudioExtension = filePath.endsWith(".mp3") ||
                        filePath.endsWith(".m4a") ||
                        filePath.endsWith(".aac") ||
                        filePath.endsWith(".flac") ||
                        filePath.endsWith(".wav") ||
                        filePath.endsWith(".ogg") ||
                        filePath.endsWith(".opus") ||
                        filePath.endsWith(".wma") ||
                        filePath.isBlank() // If dataColumn isn't provided by Android 11+ scoped storage, pass through

                    if (!isAudioExtension) {
                        continue
                    }

                    val id = if (idColumn != -1) cursor.getLong(idColumn) else continue
                    val title = if (titleColumn != -1) cursor.getString(titleColumn) ?: "Unknown" else "Unknown"
                    val artist = if (artistColumn != -1) cursor.getString(artistColumn) ?: "Unknown" else "Unknown"
                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val albumArtUri = if (albumId > 0) {
                        ContentUris.withAppendedId(artworkUriBase, albumId)
                    } else null

                    tracks.add(
                        AudioTrack(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            audioUrl = contentUri.toString(),
                            artworkUrl = albumArtUri?.toString(),
                            durationMs = duration,
                            isLocal = true,
                            isLiveStream = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Safely catch SecurityException or custom OEM database errors
            e.printStackTrace()
        }

        tracks
    }
}
