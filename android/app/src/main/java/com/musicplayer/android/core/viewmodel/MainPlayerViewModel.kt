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
import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.LoginRequestDto
import com.musicplayer.android.core.network.RegisterRequestDto
import com.musicplayer.android.core.network.CreatePlaylistRequestDto
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

    constructor(application: Application) : this(
        application,
        PlayerControllerImpl(application),
        NetworkClient.createService()
    )

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
    fun toggleShuffle() = playerController.toggleShuffle()
    fun setRepeatMode(repeatMode: Int) = playerController.setRepeatMode(repeatMode)
    fun cycleRepeatMode() = playerController.cycleRepeatMode()

    fun playTrack(track: AudioTrack) {
        playerController.playTrack(track)
    }

    fun playQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playerController.setQueue(tracks, startIndex, autoPlay = true)
    }

    // Audius online music tracks
    private val _trendingAudiusTracks = MutableStateFlow<List<AudiusTrackDto>>(emptyList())
    val trendingAudiusTracks: StateFlow<List<AudiusTrackDto>> = _trendingAudiusTracks.asStateFlow()

    private val _searchedAudiusTracks = MutableStateFlow<List<AudiusTrackDto>>(emptyList())
    val searchedAudiusTracks: StateFlow<List<AudiusTrackDto>> = _searchedAudiusTracks.asStateFlow()

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

    fun playAudiusTrack(track: AudiusTrackDto, baseUrl: String = "http://10.0.2.2:5000") {
        val cleanBase = baseUrl.trimEnd('/')
        val audiusAudioTrack = AudioTrack(
            id = "audius_${track.externalId}",
            title = track.title,
            artist = track.artist,
            audioUrl = "$cleanBase/api/audius/tracks/${track.externalId}/stream",
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs ?: 0L,
            isLocal = false,
            isLiveStream = false
        )
        playTrack(audiusAudioTrack)
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

                val trendingResp = apiService.getTrendingAudiusTracks(20)
                if (trendingResp.isSuccessful) {
                    _trendingAudiusTracks.value = trendingResp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchAudius(query: String, limit: Int = 20) {
        viewModelScope.launch {
            try {
                val resp = apiService.searchAudiusTracks(query, limit)
                if (resp.isSuccessful) {
                    _searchedAudiusTracks.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Offline caching & Room DB operations
    fun cacheTrack(track: AudioTrack) {
        viewModelScope.launch {
            try {
                db.cachedTrackDao().insertTrack(
                    CachedTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        localFilePath = null,
                        originalUrl = track.audioUrl,
                        durationMs = track.durationMs,
                        cachedAtTimestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeCachedTrack(trackId: String) {
        viewModelScope.launch {
            try {
                db.cachedTrackDao().deleteTrack(trackId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auth & User Management
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            try {
                _authStatusMessage.value = "Вхід..."
                val resp = apiService.login(LoginRequestDto(email = email, password = pass))
                if (resp.isSuccessful && resp.body() != null) {
                    val authData = resp.body()!!
                    NetworkClient.authInterceptor.authToken = authData.token
                    _currentUser.value = authData.username ?: authData.email
                    _authStatusMessage.value = "Авторизовано: ${_currentUser.value}"
                    loadBackendData()
                } else {
                    _authStatusMessage.value = "Помилка входу: HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _authStatusMessage.value = "Сервер недоступний: ${e.message}"
            }
        }
    }

    fun register(username: String, email: String, pass: String) {
        viewModelScope.launch {
            try {
                _authStatusMessage.value = "Реєстрація..."
                val resp = apiService.register(RegisterRequestDto(username = username, email = email, password = pass))
                if (resp.isSuccessful) {
                    _authStatusMessage.value = "Реєстрація успішна! Входимо..."
                    login(email, pass)
                } else {
                    _authStatusMessage.value = "Помилка реєстрації: HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _authStatusMessage.value = "Сервер недоступний: ${e.message}"
            }
        }
    }

    fun logout() {
        NetworkClient.authInterceptor.authToken = null
        _currentUser.value = null
        _authStatusMessage.value = "Ви вийшли з акаунту"
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            try {
                val resp = apiService.createPlaylist(CreatePlaylistRequestDto(name = name))
                if (resp.isSuccessful) {
                    loadBackendData()
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
