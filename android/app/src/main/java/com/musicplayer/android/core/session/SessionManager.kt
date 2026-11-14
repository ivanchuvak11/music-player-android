package com.musicplayer.android.core.session

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.musicplayer.android.core.audio.AudioTrack
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlaybackSession(
    val queue: List<AudioTrack>,
    val startIndex: Int,
    val positionMs: Long
)

/**
 * Manages persistent user session, authentication token (encrypted via Keystore),
 * playback preferences, and equalizer state.
 */
class SessionManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val isEncrypted: Boolean
    private val prefs: SharedPreferences

    // In-memory token cache: avoids repetitive AES-256 decryption calls through Android Keystore JNI
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    init {
        val (p, encrypted) = createPrefs(appContext)
        prefs = p
        isEncrypted = encrypted
        cachedToken = if (isEncrypted) prefs.getString(KEY_AUTH_TOKEN, null) else null
        cachedRefreshToken = if (isEncrypted) prefs.getString(KEY_REFRESH_TOKEN, null) else null
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val PREFS_NAME = "music_player_session_prefs"
        private const val SECURE_PREFS_NAME = "music_player_secure_prefs"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_BASE_URL = "key_base_url"
        private const val KEY_SHUFFLE_MODE = "key_shuffle_mode"
        private const val KEY_REPEAT_MODE = "key_repeat_mode"
        private const val KEY_AUTOPLAY_ENABLED = "key_autoplay_enabled"

        // Equalizer persistence keys
        private const val KEY_EQ_ENABLED = "key_eq_enabled"
        private const val KEY_EQ_PRESET = "key_eq_preset"
        private const val KEY_EQ_BASS_BOOST = "key_eq_bass_boost"
        private const val KEY_EQ_LOUDNESS_GAIN = "key_eq_loudness_gain"
        private const val KEY_EQ_BAND_PREFIX = "key_eq_band_"

        // Resume position & queue persistence
        private const val KEY_LAST_TRACK_ID = "key_last_track_id"
        private const val KEY_LAST_POSITION_MS = "key_last_position_ms"
        private const val KEY_LAST_QUEUE_JSON = "key_last_queue_json"
        private const val KEY_LAST_QUEUE_INDEX = "key_last_queue_index"
        const val MAX_SAVED_QUEUE_SIZE = 100

        internal fun sliceQueueWindow(
            queue: List<AudioTrack>,
            currentIndex: Int,
            maxSize: Int = MAX_SAVED_QUEUE_SIZE
        ): Pair<List<AudioTrack>, Int> {
            if (queue.isEmpty() || maxSize <= 0) return emptyList<AudioTrack>() to 0
            val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
            if (queue.size <= maxSize) return queue to safeIndex
            val half = maxSize / 2
            val start = (safeIndex - half).coerceIn(0, queue.size - maxSize)
            return queue.subList(start, start + maxSize) to (safeIndex - start)
        }

        internal fun serializeQueueToJson(tracks: List<AudioTrack>): String {
            val arr = JSONArray()
            for (t in tracks) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("audioUrl", t.audioUrl)
                    if (!t.artworkUrl.isNullOrBlank()) put("artworkUrl", t.artworkUrl)
                    put("durationMs", t.durationMs)
                    put("isLocal", t.isLocal)
                    put("isLiveStream", t.isLiveStream)
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        internal fun deserializeQueueFromJson(raw: String?): List<AudioTrack> {
            if (raw.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").trim()
                        val title = obj.optString("title")
                        val audioUrl = obj.optString("audioUrl").trim()
                        if (id.isBlank() || audioUrl.isBlank()) continue
                        add(
                            AudioTrack(
                                id = id,
                                title = title.ifBlank { id },
                                artist = obj.optString("artist"),
                                audioUrl = audioUrl,
                                artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                                durationMs = obj.optLong("durationMs", 0L),
                                isLocal = obj.optBoolean("isLocal", false),
                                isLiveStream = obj.optBoolean("isLiveStream", false)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        // Lightweight recent search queries (no Room schema migration)
        private const val KEY_SEARCH_HISTORY = "key_search_history"
        private const val MAX_SEARCH_HISTORY = 12

        val DEFAULT_BASE_URL: String
            get() = com.musicplayer.android.core.network.ServerConfig.DEFAULT_BASE_URL

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private fun createPrefs(ctx: Context): Pair<SharedPreferences, Boolean> {
            return try {
                val masterKey = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val securePrefs = EncryptedSharedPreferences.create(
                    ctx,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // One-time automatic migration: transfer JWT, username, email, server URL, and settings
                val oldPrefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val allOld = oldPrefs.all
                if (allOld.isNotEmpty()) {
                    val editor = securePrefs.edit()
                    allOld.forEach { (k, v) ->
                        if (!securePrefs.contains(k)) {
                            when (v) {
                                is String -> editor.putString(k, v)
                                is Boolean -> editor.putBoolean(k, v)
                                is Int -> editor.putInt(k, v)
                                is Long -> editor.putLong(k, v)
                                is Float -> editor.putFloat(k, v)
                            }
                        }
                    }
                    editor.apply()
                    oldPrefs.edit().clear().apply()
                }

                Pair(securePrefs, true)
            } catch (e: Exception) {
                Log.e(TAG, "Keystore/EncryptedSharedPreferences unavailable: ${e.message}", e)
                // Fallback to standard SharedPreferences for non-sensitive preferences only; token won't be stored in plaintext
                Pair(ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), false)
            }
        }
    }

    fun isStorageEncrypted(): Boolean = isEncrypted

    fun saveSession(
        token: String,
        userId: Int? = null,
        username: String? = null,
        email: String? = null,
        refreshToken: String? = null
    ) {
        if (!isEncrypted) {
            Log.e(TAG, "Cannot save auth token: Hardware encryption is unavailable.")
            return
        }
        cachedToken = token
        if (!refreshToken.isNullOrBlank()) {
            cachedRefreshToken = refreshToken
        }
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            if (userId != null) putInt(KEY_USER_ID, userId) else remove(KEY_USER_ID)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            apply()
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String? = null) {
        if (!isEncrypted) return
        cachedToken = accessToken
        if (!refreshToken.isNullOrBlank()) {
            cachedRefreshToken = refreshToken
        }
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, accessToken)
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
            apply()
        }
    }

    fun getToken(): String? {
        if (!isEncrypted) return null
        return cachedToken ?: prefs.getString(KEY_AUTH_TOKEN, null)?.also { cachedToken = it }
    }

    fun getRefreshToken(): String? {
        if (!isEncrypted) return null
        return cachedRefreshToken ?: prefs.getString(KEY_REFRESH_TOKEN, null)?.also { cachedRefreshToken = it }
    }

    fun getUserId(): Int? {
        return if (prefs.contains(KEY_USER_ID)) prefs.getInt(KEY_USER_ID, -1) else null
    }

    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }

    fun getEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank()
    }

    fun clearSession() {
        cachedToken = null
        cachedRefreshToken = null
        prefs.edit().apply {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            apply()
        }
        // Also wipe unencrypted old storage if anything lingered
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // Server Configuration
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        val isDebug = try {
            (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
        val uriHost = try {
            java.net.URI(normalized).host.orEmpty()
        } catch (e: Exception) {
            ""
        }
        val isLocalOrPrivateIp = uriHost == "localhost" ||
            uriHost == "10.0.2.2" ||
            uriHost == "127.0.0.1" ||
            uriHost.startsWith("192.168.") ||
            uriHost.startsWith("10.") ||
            uriHost.matches(Regex("""^172\.(1[6-9]|2[0-9]|3[0-1])\..*"""))

        // In release builds, only prevent public cleartext HTTP URLs, but allow local Wi-Fi / dev IPs
        if (!isDebug && !normalized.startsWith("https://") && !isLocalOrPrivateIp) {
            Log.w(TAG, "Rejecting insecure public cleartext HTTP URL in release build: $normalized")
            return
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
    }

    // Playback Preferences Persistence
    fun getShuffleMode(): Boolean {
        return prefs.getBoolean(KEY_SHUFFLE_MODE, false)
    }

    fun saveShuffleMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHUFFLE_MODE, enabled).apply()
    }

    fun getRepeatMode(): Int {
        return prefs.getInt(KEY_REPEAT_MODE, 0)
    }

    fun saveRepeatMode(repeatMode: Int) {
        prefs.edit().putInt(KEY_REPEAT_MODE, repeatMode).apply()
    }

    fun getAutoplayEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOPLAY_ENABLED, true)
    }

    fun saveAutoplayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_ENABLED, enabled).apply()
    }

    // Equalizer State Persistence
    fun saveEqualizerState(
        enabled: Boolean,
        preset: String,
        bassBoost: Short,
        bandLevels: Map<Short, Short>,
        loudnessGain: Int = 0
    ) {
        prefs.edit().apply {
            putBoolean(KEY_EQ_ENABLED, enabled)
            putString(KEY_EQ_PRESET, preset)
            putInt(KEY_EQ_BASS_BOOST, bassBoost.toInt())
            putInt(KEY_EQ_LOUDNESS_GAIN, loudnessGain)
            bandLevels.forEach { (band, level) ->
                putInt("${KEY_EQ_BAND_PREFIX}$band", level.toInt())
            }
            apply()
        }
    }

    fun getEqualizerEnabled(): Boolean = prefs.getBoolean(KEY_EQ_ENABLED, false)
    fun getEqualizerPreset(): String = prefs.getString(KEY_EQ_PRESET, "Звичайний") ?: "Звичайний"
    fun getEqualizerBassBoost(): Short = prefs.getInt(KEY_EQ_BASS_BOOST, 0).toShort()
    fun getEqualizerLoudnessGain(): Int = prefs.getInt(KEY_EQ_LOUDNESS_GAIN, 0)
    fun getEqualizerBandLevels(numberOfBands: Int = 5): Map<Short, Short> {
        val map = mutableMapOf<Short, Short>()
        for (i in 0 until numberOfBands) {
            val band = i.toShort()
            map[band] = prefs.getInt("${KEY_EQ_BAND_PREFIX}$band", 0).toShort()
        }
        return map
    }

    // Resume position & last track memory persistence
    fun saveLastPlaybackPosition(trackId: String, positionMs: Long) {
        prefs.edit().apply {
            putString(KEY_LAST_TRACK_ID, trackId)
            putLong(KEY_LAST_POSITION_MS, positionMs)
            apply()
        }
    }

    fun saveLastPlayedTrack(track: AudioTrack, positionMs: Long) {
        prefs.edit().apply {
            putString(KEY_LAST_TRACK_ID, track.id)
            putString("last_track_title", track.title)
            putString("last_track_artist", track.artist)
            putString("last_track_url", track.audioUrl)
            putString("last_track_artwork", track.artworkUrl)
            putLong("last_track_duration", track.durationMs)
            putBoolean("last_track_is_local", track.isLocal)
            putBoolean("last_track_is_live", track.isLiveStream)
            putLong(KEY_LAST_POSITION_MS, positionMs)
            apply()
        }
    }

    fun getLastPlayedTrack(): Pair<AudioTrack, Long>? {
        val id = prefs.getString(KEY_LAST_TRACK_ID, null) ?: return null
        val title = prefs.getString("last_track_title", null) ?: return null
        val artist = prefs.getString("last_track_artist", "") ?: ""
        val url = prefs.getString("last_track_url", null) ?: return null
        val artwork = prefs.getString("last_track_artwork", null)
        val duration = prefs.getLong("last_track_duration", 0L)
        val isLocal = prefs.getBoolean("last_track_is_local", true)
        val isLive = prefs.getBoolean("last_track_is_live", false)
        val pos = prefs.getLong(KEY_LAST_POSITION_MS, 0L)
        val track = AudioTrack(
            id = id,
            title = title,
            artist = artist,
            audioUrl = url,
            artworkUrl = artwork,
            durationMs = duration,
            isLocal = isLocal,
            isLiveStream = isLive
        )
        return Pair(track, pos)
    }

    fun getLastTrackId(): String? = prefs.getString(KEY_LAST_TRACK_ID, null)
    fun getLastPositionMs(): Long = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    fun savePlaybackQueue(queue: List<AudioTrack>, currentIndex: Int) {
        if (queue.isEmpty()) return
        val (sliced, adjustedIndex) = sliceQueueWindow(queue, currentIndex)
        prefs.edit().apply {
            putString(KEY_LAST_QUEUE_JSON, serializeQueueToJson(sliced))
            putInt(KEY_LAST_QUEUE_INDEX, adjustedIndex)
            apply()
        }
    }

    fun getLastPlaybackSession(): SavedPlaybackSession? {
        val queue = deserializeQueueFromJson(prefs.getString(KEY_LAST_QUEUE_JSON, null))
        if (queue.isNotEmpty()) {
            val savedIndex = prefs.getInt(KEY_LAST_QUEUE_INDEX, 0).coerceIn(0, queue.lastIndex)
            val lastTrackId = getLastTrackId()
            val resolvedIndex = if (!lastTrackId.isNullOrBlank()) {
                val matched = queue.indexOfFirst { it.id == lastTrackId }
                if (matched >= 0) matched else savedIndex
            } else {
                savedIndex
            }
            return SavedPlaybackSession(
                queue = queue,
                startIndex = resolvedIndex,
                positionMs = getLastPositionMs()
            )
        }
        val legacy = getLastPlayedTrack() ?: return null
        return SavedPlaybackSession(
            queue = listOf(legacy.first),
            startIndex = 0,
            positionMs = legacy.second
        )
    }

    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSearchHistory(query: String): List<String> {
        val clean = query.trim()
        if (clean.length < 2) return getSearchHistory()
        val updated = buildList {
            add(clean)
            for (existing in getSearchHistory()) {
                if (!existing.equals(clean, ignoreCase = true)) {
                    add(existing)
                    if (size >= MAX_SEARCH_HISTORY) break
                }
            }
        }
        saveSearchHistoryList(updated)
        return updated
    }

    fun removeSearchHistoryItem(query: String): List<String> {
        val clean = query.trim()
        val updated = getSearchHistory().filterNot { it.equals(clean, ignoreCase = true) }
        saveSearchHistoryList(updated)
        return updated
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    private fun saveSearchHistoryList(list: List<String>) {
        val arr = JSONArray()
        list.take(MAX_SEARCH_HISTORY).forEach { arr.put(it) }
        prefs.edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }
}
