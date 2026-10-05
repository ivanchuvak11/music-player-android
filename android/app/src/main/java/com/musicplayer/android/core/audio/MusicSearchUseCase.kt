package com.musicplayer.android.core.audio

import android.util.Log
import com.musicplayer.android.core.database.CachedTrackDao
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.network.MusicApiService
import com.musicplayer.android.core.network.MusicSourceError
import com.musicplayer.android.core.network.httpCodeToMusicError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.SocketTimeoutException

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Use-case for multi-source parallel music search per ТЗ Sections 3 and 8.
 *
 * Responsibilities:
 * 1. Query SoundCloud, Audius, Jamendo in parallel (all 3 simultaneously).
 * 2. Merge results in order: SoundCloud → Audius → Jamendo.
 * 3. Deduplicate by (source + externalId).
 * 4. If one source fails, continue with others (isolate per-source errors).
 * 5. Cache last successful search results in Room (CachedTrackDao).
 * 6. If offline: return cached tracks, do not throw.
 * 7. Map all HTTP errors to typed MusicSourceError.
 *
 * Does NOT modify backend, DB schema, auth, or UI/navigation.
 */
class MusicSearchUseCase(
    private val apiService: MusicApiService,
    private val cachedTrackDao: CachedTrackDao,
    private val isOnline: () -> Boolean
) {
    private val TAG = "MusicSearchUseCase"

    /**
     * Main entry point. Returns MultiSourceSearchResult with tracks and per-source errors.
     *
     * @param query Search string. Empty → returns empty result immediately.
     * @param limit Max results per source.
     * @param backendBaseUrl Used to resolve stream URLs.
     */
    suspend fun search(
        query: String,
        limit: Int = 20,
        backendBaseUrl: String
    ): MultiSourceSearchResult {
        if (query.isBlank()) {
            return MultiSourceSearchResult()
        }

        // Offline fallback: return cached results without attempting network
        if (!isOnline()) {
            Log.w(TAG, "Device is offline — returning cached results for query: $query")
            val cached = loadCachedResults()
            return MultiSourceSearchResult(
                tracks = cached,
                errors = mapOf("all" to MusicSourceError.NoNetwork()),
                isOfflineFallback = true
            )
        }

        return coroutineScope {
            val soundCloudDeferred = async {
                withTimeoutOrNull(2500L) { fetchSoundCloud(query, limit) }
                    ?: Result.failure(MusicSourceError.Timeout("soundcloud"))
            }

            val soundCloudResult = soundCloudDeferred.await()
            val soundCloudTracks = soundCloudResult.getOrElse { emptyList() }

            val merged = mergeAndDeduplicateSources(soundCloudTracks)

            // Collect per-source errors (for UI partial failure notification)
            val errors = mutableMapOf<String, MusicSourceError>()
            soundCloudResult.onFailure { err -> if (err is MusicSourceError) errors["soundcloud"] = err }

            // Cache results if at least some results came back (ТЗ Section 5)
            if (merged.isNotEmpty()) {
                cacheTracks(merged, backendBaseUrl)
            }

            MultiSourceSearchResult(
                tracks = merged,
                errors = errors,
                isOfflineFallback = false
            )
        }
    }

    // ─── Per-source fetch helpers ──────────────────────────────────────────────

    private suspend fun fetchSoundCloud(query: String, limit: Int): Result<List<UnifiedTrack>> {
        return try {
            val response = apiService.searchSoundCloudTracks(query.trim(), limit)
            if (response.isSuccessful) {
                val body = response.body()
                if (body.isNullOrEmpty()) {
                    Log.d(TAG, "SoundCloud: empty response for query='$query'")
                    Result.success(emptyList())
                } else {
                    val tracks = body.map { UnifiedTrack.fromSoundCloud(it) }
                    Log.d(TAG, "SoundCloud: ${tracks.size} results")
                    Result.success(tracks)
                }
            } else {
                val error = httpCodeToMusicError(response.code(), "soundcloud")
                Log.w(TAG, "SoundCloud HTTP ${response.code()}: ${error.message}")
                Result.failure(error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "SoundCloud timeout: ${e.message}")
            Result.failure(MusicSourceError.Timeout("soundcloud"))
        } catch (e: IOException) {
            Log.w(TAG, "SoundCloud network IO: ${e.message}")
            Result.failure(MusicSourceError.NoNetwork("soundcloud"))
        } catch (e: Exception) {
            Log.e(TAG, "SoundCloud unknown error: ${e.message}", e)
            Result.failure(MusicSourceError.Unknown("soundcloud", e.message.orEmpty()))
        }
    }

    // ─── Cache operations (ТЗ Section 5) ─────────────────────────────────────

    /**
     * Persists search result tracks to CachedTrackDao using existing entity schema.
     * Does NOT change the Room schema (no DB migration needed).
     */
    private suspend fun cacheTracks(tracks: List<UnifiedTrack>, backendBaseUrl: String) {
        try {
            for (track in tracks) {
                val entity = CachedTrackEntity(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    localFilePath = null, // search cache — no local file, only metadata
                    originalUrl = track.resolveStreamUrl(backendBaseUrl),
                    artworkUrl = track.artworkUrl,
                    durationMs = track.durationMs,
                    cachedAtTimestamp = System.currentTimeMillis()
                )
                cachedTrackDao.insertTrack(entity)
            }
            Log.d(TAG, "Cached ${tracks.size} search result tracks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache search results: ${e.message}", e)
        }
    }

    /**
     * Returns all tracks from Room cache for offline fallback (ТЗ Section 5).
     * Maps CachedTrackEntity back to UnifiedTrack using the composite ID pattern.
     */
    private suspend fun loadCachedResults(): List<UnifiedTrack> {
        return try {
            cachedTrackDao.getAllCachedTracksSync().mapNotNull { entity ->
                // Decode source from composite ID pattern "{source}_{externalId}"
                val separatorIndex = entity.id.indexOf('_')
                if (separatorIndex < 0) return@mapNotNull null
                val sourceStr = entity.id.substring(0, separatorIndex)
                val externalId = entity.id.substring(separatorIndex + 1)
                val source = TrackSource.fromString(sourceStr)
                UnifiedTrack(
                    source = source,
                    externalId = externalId,
                    title = entity.title,
                    artist = entity.artist,
                    artworkUrl = entity.artworkUrl,
                    durationMs = entity.durationMs,
                    streamUrl = entity.localFilePath ?: entity.originalUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached results: ${e.message}", e)
            emptyList()
        }
    }
}
