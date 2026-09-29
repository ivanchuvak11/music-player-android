package com.musicplayer.android.core.session

import android.content.Context
import android.content.SharedPreferences
import com.musicplayer.android.core.audio.AudioTrack

/**
 * Manages persistent user session, authentication token, playback preferences,
 * and equalizer state stored safely in SharedPreferences.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "music_player_session_prefs"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_EMAIL = "key_email"
        private const val KEY_BASE_URL = "key_base_url"
        private const val KEY_SHUFFLE_MODE = "key_shuffle_mode"
        private const val KEY_REPEAT_MODE = "key_repeat_mode"

        // Equalizer persistence keys
        private const val KEY_EQ_ENABLED = "key_eq_enabled"
        private const val KEY_EQ_PRESET = "key_eq_preset"
        private const val KEY_EQ_BASS_BOOST = "key_eq_bass_boost"
        private const val KEY_EQ_BAND_PREFIX = "key_eq_band_"

        // Resume position persistence
        private const val KEY_LAST_TRACK_ID = "key_last_track_id"
        private const val KEY_LAST_POSITION_MS = "key_last_position_ms"

        const val DEFAULT_BASE_URL = "http://10.0.2.2:5116/"

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    fun saveSession(
        token: String,
        userId: Int? = null,
        username: String? = null,
        email: String? = null
    ) {
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            if (userId != null) putInt(KEY_USER_ID, userId) else remove(KEY_USER_ID)
            putString(KEY_USERNAME, username)
            putString(KEY_EMAIL, email)
            apply()
        }
    }

    fun getToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
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
        prefs.edit().apply {
            remove(KEY_AUTH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USERNAME)
            remove(KEY_EMAIL)
            apply()
        }
    }

    // Server Configuration
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
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

    // Equalizer State Persistence
    fun saveEqualizerState(enabled: Boolean, preset: String, bassBoost: Short, bandLevels: Map<Short, Short>) {
        prefs.edit().apply {
            putBoolean(KEY_EQ_ENABLED, enabled)
            putString(KEY_EQ_PRESET, preset)
            putInt(KEY_EQ_BASS_BOOST, bassBoost.toInt())
            bandLevels.forEach { (band, level) ->
                putInt("${KEY_EQ_BAND_PREFIX}$band", level.toInt())
            }
            apply()
        }
    }

    fun getEqualizerEnabled(): Boolean = prefs.getBoolean(KEY_EQ_ENABLED, false)
    fun getEqualizerPreset(): String = prefs.getString(KEY_EQ_PRESET, "Звичайний") ?: "Звичайний"
    fun getEqualizerBassBoost(): Short = prefs.getInt(KEY_EQ_BASS_BOOST, 0).toShort()
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
}
