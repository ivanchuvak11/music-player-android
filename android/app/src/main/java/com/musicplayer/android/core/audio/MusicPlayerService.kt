package com.musicplayer.android.core.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musicplayer.android.MainActivity
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
class MusicPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    // Bound to the service lifecycle — cancelled in onDestroy()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // C1 FIX: Pass cache DataSource DIRECTLY to constructor so caching actually works
        val dataSourceFactory = AudioCacheManager.buildCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Optimized LoadControl: starts playback almost immediately (<1s buffer)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

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

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
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
}
