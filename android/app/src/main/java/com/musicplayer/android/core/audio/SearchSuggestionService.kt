package com.musicplayer.android.core.audio

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Provides real-time intelligent search suggestions (autocomplete) like Spotify / YouTube Music.
 * Uses Google/YouTube Suggest API with ultra-fast ~50ms response time.
 */
object SearchSuggestionService {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank() || q.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = Uri.encode(q)
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext emptyList()

            val rootArray = JSONArray(body)
            if (rootArray.length() > 1) {
                val suggestionsArray = rootArray.optJSONArray(1) ?: return@withContext emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    val item = suggestionsArray.optString(i)
                    if (item.isNotBlank()) {
                        list.add(item)
                    }
                }
                list.take(7)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
