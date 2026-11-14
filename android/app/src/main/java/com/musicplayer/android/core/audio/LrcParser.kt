package com.musicplayer.android.core.audio

/**
 * Single timestamped line from a synchronized `.lrc` lyrics file or `lrclib.net` response.
 */
data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

enum class LyricsSource {
    LOCAL_LRC,
    LRCLIB_SYNCED,
    LRCLIB_PLAIN,
    CACHE
}

sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data class Loading(val trackId: String) : LyricsUiState
    data class Ready(
        val trackId: String,
        val syncedLines: List<LyricsLine>,
        val plainLyrics: String?,
        val source: LyricsSource,
        val isInstrumental: Boolean = false
    ) : LyricsUiState
    data class NotFound(val trackId: String) : LyricsUiState
    data class Error(val trackId: String, val message: String) : LyricsUiState
}

/**
 * Parses standard `.lrc` synchronized lyrics (`[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss]`),
 * including multi-timestamp lines and `[offset:±ms]` timing shifts.
 */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_REGEX = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(lrcContent: String): List<LyricsLine> {
        if (lrcContent.isBlank()) return emptyList()

        val offsetMs = OFFSET_REGEX.find(lrcContent)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val parsed = mutableListOf<LyricsLine>()

        lrcContent.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            val matches = TIMESTAMP_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                // Skip non-timestamped metadata headers such as [ar:...] or [ti:...]
                if (METADATA_TAG_REGEX.matches(trimmed)) return@forEach
                return@forEach
            }

            val text = TIMESTAMP_REGEX.replace(trimmed, "").trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds >= 60L) continue

                val fractionRaw = match.groupValues[3]
                val millis = when (fractionRaw.length) {
                    0 -> 0L
                    1 -> fractionRaw.toLong() * 100L
                    2 -> fractionRaw.toLong() * 10L
                    else -> fractionRaw.take(3).toLong()
                }

                val rawTimestampMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                // Per LRC spec, positive [offset:+N] makes lyrics appear N ms earlier.
                val adjustedMs = (rawTimestampMs - offsetMs).coerceAtLeast(0L)
                parsed.add(LyricsLine(timestampMs = adjustedMs, text = text))
            }
        }

        if (parsed.none { it.text.isNotBlank() }) return emptyList()
        return parsed.sortedBy { it.timestampMs }
    }

    /**
     * Returns the index of the active line for [positionMs], or `-1` before the first line.
     */
    fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines.first().timestampMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}
