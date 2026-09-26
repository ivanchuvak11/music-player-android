package com.musicplayer.android.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.LocalAudioScanner
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.audio.PlayerController
import com.musicplayer.android.core.audio.PlayerControllerImpl
import com.musicplayer.android.core.database.AppDatabase
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.network.MusicApiService
import com.musicplayer.android.core.network.NetworkClient
import com.musicplayer.android.core.network.PlaylistSummaryDto
import com.musicplayer.android.core.network.RadioStationDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Main ViewModel exposing player state, local tracks, playlists, and radio stations to the UI layer.
 */
class MainPlayerViewModel(
    application: Application,
    private val playerController: PlayerController = PlayerControllerImpl(application),
    private val apiService: MusicApiService = NetworkClient.createService()
) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val localScanner = LocalAudioScanner(application)

    // Playback state directly from Audio Engine
    val playbackState: StateFlow<PlaybackState> = playerController.playbackState

    // Local device tracks
    private val _localTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val localTracks: StateFlow<List<AudioTrack>> = _localTracks.asStateFlow()

    // Radio stations from backend
    private val _radioStations = MutableStateFlow<List<RadioStationDto>>(emptyList())
    val radioStations: StateFlow<List<RadioStationDto>> = _radioStations.asStateFlow()

    // Playlists from backend
    private val _playlists = MutableStateFlow<List<PlaylistSummaryDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummaryDto>> = _playlists.asStateFlow()

    // Offline / Cached tracks from Room DB
    val cachedTracks: StateFlow<List<CachedTrackEntity>> = db.cachedTrackDao()
        .getAllCachedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Player Actions
    fun play() = playerController.play()
    fun pause() = playerController.pause()
    fun playNext() = playerController.playNext()
    fun playPrevious() = playerController.playPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun playTrack(track: AudioTrack) {
        playerController.playTrack(track)
    }

    fun playQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playerController.setQueue(tracks, startIndex, autoPlay = true)
    }

    fun playRadioStation(station: RadioStationDto) {
        val radioTrack = AudioTrack(
            id = "radio_${station.stationId}",
            title = station.name,
            artist = station.genre ?: "Online Radio",
            audioUrl = station.streamUrl,
            artworkUrl = station.logoUrl,
            durationMs = 0L,
            isLocal = false,
            isLiveStream = true
        )
        playTrack(radioTrack)
    }

    // Data Loading
    fun loadLocalTracks() {
        viewModelScope.launch {
            try {
                _localTracks.value = localScanner.getLocalAudioTracks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadBackendData() {
        viewModelScope.launch {
            try {
                val playlistsResp = apiService.getPlaylists()
                if (playlistsResp.isSuccessful) {
                    _playlists.value = playlistsResp.body().orEmpty()
                }

                val radioResp = apiService.getFavoriteRadioStations()
                if (radioResp.isSuccessful) {
                    _radioStations.value = radioResp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
