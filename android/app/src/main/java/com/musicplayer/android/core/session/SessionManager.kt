package com.musicplayer.android.core.session

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent user session, JWT authentication token, and credentials
 * stored in SharedPreferences.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "music_player_session_prefs"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_EMAIL = "key_email"
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
        prefs.edit().clear().apply()
    }
}
