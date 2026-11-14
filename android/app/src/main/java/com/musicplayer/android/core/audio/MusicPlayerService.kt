package com.musicplayer.android.core.audio

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.android.MainActivity
import com.musicplayer.android.core.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background MediaSessionService managing ExoPlayer lifecycle, caching, audio focus, WakeLock, and system audio session.
 *
 * Fix #21: Uses a lifecycle-bound serviceScope (SupervisorJob) so all AudioEffects init
 * coroutines are cancelled in onDestroy(), preventing stale-session crashes on Samsung/Huawei.
 *
 * Fix #16: onTaskRemoved only stops the service when there is nothing loaded.
 * Swiping the app from Recents while paused now keeps the service alive with its notification.
 */
/**
 * Checks whether a Media3 PlaybackException was caused by an expired CDN URL (HTTP 403/410).
 */
@OptIn(UnstableApi::class)
internal fun isExpiredStreamPlaybackError(error: PlaybackException): Boolean {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth < 12) {
        if (cause is HttpDataSource.InvalidResponseCodeException &&
            (cause.responseCode == 403 || cause.responseCode == 410)
        ) {
            return true
        }
        cause = cause.cause
        depth++
    }
    return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
        (error.message?.contains("403") == true || error.message?.contains("410") == true)
}

class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // C1 FIX: Pass cache DataSource DIRECTLY to constructor so caching actually works
        val baseCacheDataSourceFactory = AudioCacheManager.buildCacheDataSourceFactory(this)

        var exoPlayerRef: ExoPlayer? = null

        // ResolvingDataSource: intercepts track URIs, resolves direct CDN audio streams on client,
        // and falls back gracefully to YouTube stream search if external/proxy sources fail (e.g. SoundCloud 502).
        val resolvingDataSourceFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            baseCacheDataSourceFactory,
            androidx.media3.datasource.ResolvingDataSource.Resolver { dataSpec ->
                val uri = dataSpec.uri
                // 1. Перевіряємо, чи трек вже збережений офлайн у filesDir/offline_tracks
                val rawKey = dataSpec.key ?: YouTubeExtractorService.extractVideoIdFromUri(uri)?.let { "youtube_$it" }
                if (!rawKey.isNullOrBlank()) {
                    val safeFileName = rawKey.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".mp3"
                    val offlineFile = File(TrackDownloadManager.getOfflineTracksDirectory(this@MusicPlayerService), safeFileName)
                    if (offlineFile.exists() && offlineFile.length() >= 51_200L) {
                        return@Resolver dataSpec.buildUpon()
                            .setUri(Uri.fromFile(offlineFile))
                            .build()
                    }

                    // 1b. Перевіряємо, чи збережено файл на пристрої у Room CachedTrackDao
                    try {
                        val db = AppDatabase.getDatabase(this@MusicPlayerService)
                        val cachedEntity = db.cachedTrackDao().getTrackByIdDirect(rawKey)
                        if (cachedEntity?.localFilePath != null) {
                            val deviceFile = File(cachedEntity.localFilePath)
                            if (deviceFile.exists() && deviceFile.length() >= 51_200L) {
                                return@Resolver dataSpec.buildUpon()
                                    .setUri(Uri.fromFile(deviceFile))
                                    .build()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MusicPlayerService", "Could not check cached track in DB: ${e.message}")
                    }
                }

                // 2. Якщо це YouTube-потік — резолвимо через NewPipeExtractor
                val videoId = YouTubeExtractorService.extractVideoIdFromUri(uri)
                if (videoId != null) {
                    val resolvedUrl = YouTubeExtractorService.resolveAudioStreamUrlSync(videoId)
                    if (!resolvedUrl.isNullOrBlank()) {
                        // Core Optimization: Set immutable custom cache key "youtube_$videoId"
                        // Ensures ExoPlayer's SimpleCache hits local disk cache regardless of expiring CDN URL query tokens!
                        return@Resolver dataSpec.buildUpon()
                            .setUri(Uri.parse(resolvedUrl))
                            .setKey("youtube_$videoId")
                            .build()
                    }
                }

                // 3. Fallback для зовнішніх/проксі-потоків (SoundCloud, backend stream proxy)
                val uriStr = uri.toString()
                // 2b. Direct SoundCloud track URI (soundcloud://url/...) resolved via NewPipe
                if (ExternalPlaylistService.decodeSoundCloudStreamUri(uriStr) != null) {
                    val resolvedScUrl = ExternalPlaylistService.resolveSoundCloudAudioStreamUrlSync(uriStr)
                    if (!resolvedScUrl.isNullOrBlank()) {
                        return@Resolver dataSpec.buildUpon()
                            .setUri(Uri.parse(resolvedScUrl))
                            .setKey(rawKey ?: "soundcloud_${uriStr.hashCode()}")
                            .build()
                    }
                }
                val isProxyStream = uriStr.startsWith("soundcloud://") || uriStr.contains("/api/soundcloud/") || uriStr.contains("/api/") || rawKey?.startsWith("soundcloud_") == true
                if (isProxyStream) {
                    val isPlayable = if (uriStr.startsWith("soundcloud://")) false else isStreamUriPlayable(uriStr)
                    if (!isPlayable) {
                        Log.w("MusicPlayerService", "Stream proxy unreachable: $uriStr. Attempting YouTube fallback...")
                        val cleanKey = rawKey ?: ""
                        val db = try { AppDatabase.getDatabase(this@MusicPlayerService) } catch (e: Exception) { null }
                        val cachedEntity = if (cleanKey.isNotBlank()) db?.cachedTrackDao()?.getTrackByIdDirect(cleanKey) else null
                        val title = cachedEntity?.title ?: exoPlayerRef?.currentMediaItem?.mediaMetadata?.title?.toString()
                        val artist = cachedEntity?.artist ?: exoPlayerRef?.currentMediaItem?.mediaMetadata?.artist?.toString()

                        if (!title.isNullOrBlank()) {
                            val searchQuery = "${artist.orEmpty()} $title".trim()
                            val searchResults = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                                YouTubeExtractorService.search(searchQuery, limit = 1)
                            }
                            val best = searchResults.firstOrNull()
                            if (best != null) {
                                val ytStreamUrl = YouTubeExtractorService.resolveAudioStreamUrlSync(best.externalId)
                                if (!ytStreamUrl.isNullOrBlank()) {
                                    Log.i("MusicPlayerService", "Resolved fallback stream for '$searchQuery': $ytStreamUrl")
                                    return@Resolver dataSpec.buildUpon()
                                        .setUri(Uri.parse(ytStreamUrl))
                                        .setKey("youtube_${best.externalId}")
                                        .build()
                                }
                            }
                        }
                    }
                }

                dataSpec
            }
        )

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Ultra-low latency LoadControl: starts playback almost immediately (~250ms buffer) and reduces RAM usage
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 10_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Core Optimization: Pure Audio Lean RenderersFactory (disables video, text/subtitle, and camera motion overhead)
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setEnableAudioTrackPlaybackParams(true)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exoPlayerRef = player
        var recoveredExpiredMediaId: String? = null
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem?.mediaId != recoveredExpiredMediaId) {
                    recoveredExpiredMediaId = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isExpiredStreamPlaybackError(error)) return
                val currentItem = player.currentMediaItem ?: return
                val uriStr = currentItem.localConfiguration?.uri?.toString().orEmpty()
                if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) return

                val mediaId = currentItem.mediaId.ifBlank { uriStr }
                if (mediaId == recoveredExpiredMediaId) return

                recoveredExpiredMediaId = mediaId
                val resumePos = player.currentPosition.coerceAtLeast(0L)
                val shouldPlay = player.playWhenReady
                val index = player.currentMediaItemIndex.coerceAtLeast(0)

                YouTubeExtractorService.invalidateCachedStreamUrl(mediaId)
                YouTubeExtractorService.extractVideoIdFromUri(Uri.parse(uriStr))?.let {
                    YouTubeExtractorService.invalidateCachedStreamUrl(it)
                }
                ExternalPlaylistService.invalidateSoundCloudStreamCache(uriStr)

                Log.w("MusicPlayerService", "Expired stream (${error.errorCode}) for $mediaId; refreshing URL at ${resumePos}ms")
                player.seekTo(index, resumePos)
                player.prepare()
                if (shouldPlay) {
                    player.play()
                }
            }
        })

        // Fix #21: Use serviceScope so init is cancelled when service is destroyed
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                serviceScope.launch(Dispatchers.IO) {
                    AudioEffectsManager.init(audioSessionId)
                }
            }
        })

        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET) {
            serviceScope.launch(Dispatchers.IO) {
                AudioEffectsManager.init(initialSessionId)
            }
        }

        // H4 FIX: Set session activity so notification tap opens the app
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val callerPackage = controller.packageName
                val isTrusted = callerPackage == packageName ||
                        callerPackage == "com.android.systemui" ||
                        callerPackage == "com.google.android.projection.gearhead" || // Android Auto
                        callerPackage.startsWith("com.android.bluetooth") ||
                        session.isMediaNotificationController(controller)

                return if (isTrusted) {
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(availableSessionCommands.build())
                        .setAvailablePlayerCommands(availablePlayerCommands.build())
                        .build()
                } else {
                    // For third-party untrusted controllers, provide only PLAY, PAUSE, SEEK, NEXT, PREVIOUS
                    val safePlayerCommands = androidx.media3.common.Player.Commands.Builder()
                        .addAll(
                            androidx.media3.common.Player.COMMAND_PLAY_PAUSE,
                            androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT,
                            androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS,
                            androidx.media3.common.Player.COMMAND_SEEK_BACK,
                            androidx.media3.common.Player.COMMAND_SEEK_FORWARD,
                            androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                            androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                            androidx.media3.common.Player.COMMAND_GET_TIMELINE
                        )
                        .build()

                    MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(androidx.media3.session.SessionCommands.EMPTY)
                        .setAvailablePlayerCommands(safePlayerCommands)
                        .build()
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // Cancel all pending AudioEffects coroutines before releasing player
        serviceScope.cancel()
        AudioEffectsManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        // C2 FIX: Release SimpleCache to free SQLite lock and prevent crash on restart
        AudioCacheManager.releaseCache()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Fix #16: Only kill service when there is truly nothing playing/paused.
        // If the user swiped recents while paused, preserve the service + notification.
        if (player == null || player.mediaItemCount == 0) {
            stopSelf()
        }
        // Otherwise: keep alive. The user can resume from the notification.
    }

    private fun isStreamUriPlayable(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return true
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(1200, java.util.concurrent.TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-1024")
                .build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            code in 200..399
        } catch (e: Exception) {
            false
        }
    }
}
