package com.musicplayer.android.core.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Provides real-time intelligent search suggestions (autocomplete) like Spotify / YouTube Music.
 * Uses locale-aware Google/YouTube Suggest API and cross-language fallback when results are sparse.
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

        val locale = Locale.getDefault()
        val hl = locale.language.takeIf { it.length in 2..3 }?.lowercase(Locale.ROOT) ?: "uk"
        val gl = locale.country.takeIf { it.length == 2 }?.uppercase(Locale.ROOT) ?: "UA"

        val primary = fetchRawSuggestions(q, hl, gl)
        if (primary.size >= 3) {
            return@withContext primary.take(7)
        }

        val fallbackQuery = MultilingualSearchHelper.buildOnlineFallbackQueries(q).firstOrNull()
        if (fallbackQuery.isNullOrBlank()) {
            return@withContext primary.take(7)
        }

        val extra = fetchRawSuggestions(fallbackQuery, hl, gl)
        return@withContext (primary + extra)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(7)
    }

    private fun fetchRawSuggestions(query: String, hl: String, gl: String): List<String> {
        return try {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=$hl&gl=$gl&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val rootArray = JSONArray(body)
                if (rootArray.length() <= 1) return emptyList()

                val suggestionsArray = rootArray.optJSONArray(1) ?: return emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    val item = suggestionsArray.optString(i)
                    if (item.isNotBlank()) {
                        list.add(item)
                    }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
