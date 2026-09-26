package com.musicplayer.android.core.audio

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans on-device audio files using Android MediaStore.
 */
class LocalAudioScanner(private val context: Context) {

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

        // Filter out short audio clips (<20s) such as voice messages, ringtones, and notification sounds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDurationMs.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
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

                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val albumArtUri = ContentUris.withAppendedId(artworkUriBase, albumId)

                tracks.add(
                    AudioTrack(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        audioUrl = contentUri.toString(),
                        artworkUrl = albumArtUri.toString(),
                        durationMs = duration,
                        isLocal = true,
                        isLiveStream = false
                    )
                )
            }
        }
        tracks
    }
}
