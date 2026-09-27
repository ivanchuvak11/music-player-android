package com.musicplayer.android.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.android.core.audio.AudioCacheManager
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.LocalAudioScanner
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.audio.PlayerController
import com.musicplayer.android.core.audio.PlayerControllerImpl
import com.musicplayer.android.core.database.AppDatabase
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.network.AddFavoriteRadioRequestDto
import com.musicplayer.android.core.network.AddFavoriteTrackRequestDto
import com.musicplayer.android.core.network.AddTrackToPlaylistRequestDto
import com.musicplayer.android.core.network.AudiusTrackDto
import com.musicplayer.android.core.network.CreatePlaylistRequestDto
import com.musicplayer.android.core.network.FavoriteTrackDto
import com.musicplayer.android.core.network.JamendoTrackDto
import com.musicplayer.android.core.network.LoginRequestDto
import com.musicplayer.android.core.network.MusicApiService
import com.musicplayer.android.core.network.NetworkClient
import com.musicplayer.android.core.network.NetworkConnectivityObserver
import com.musicplayer.android.core.network.PlaylistDetailDto
import com.musicplayer.android.core.network.PlaylistSummaryDto
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.network.RegisterRequestDto
import com.musicplayer.android.core.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Main ViewModel exposing player state, local tracks, playlists, radio stations,
 * Jamendo/Audius streaming, Room DB caching, and persistent session to the UI layer.
 */
class MainPlayerViewModel(
    application: Application,
    private val playerController: PlayerController = PlayerControllerImpl(application),
    private var apiService: MusicApiService = NetworkClient.createService(
        baseUrl = SessionManager(application).getBaseUrl(),
        context = application
    ),
    private val sessionManager: SessionManager = SessionManager(application)
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        PlayerControllerImpl(application),
        NetworkClient.createService(
            baseUrl = SessionManager(application).getBaseUrl(),
            context = application
        ),
        SessionManager(application)
    )

    private val db = AppDatabase.getDatabase(application)
    private val localScanner = LocalAudioScanner(application)
    private val connectivityObserver = NetworkConnectivityObserver(application)

    // Real-time network connectivity
    val isOnline: StateFlow<Boolean> = connectivityObserver.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, connectivityObserver.isCurrentlyConnected())

    // Playback state directly from Audio Engine
    val playbackState: StateFlow<PlaybackState> = playerController.playbackState

    // Local device tracks
    private val _localTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val localTracks: StateFlow<List<AudioTrack>> = _localTracks.asStateFlow()

    // Radio stations from backend
    private val _radioStations = MutableStateFlow<List<RadioStationDto>>(emptyList())
    val radioStations: StateFlow<List<RadioStationDto>> = _radioStations.asStateFlow()

    private val _radioStationsByCountry = MutableStateFlow<List<RadioStationDto>>(emptyList())
    val radioStationsByCountry: StateFlow<List<RadioStationDto>> = _radioStationsByCountry.asStateFlow()

    // Jamendo tracks
    private val _searchedJamendoTracks = MutableStateFlow<List<JamendoTrackDto>>(emptyList())
    val searchedJamendoTracks: StateFlow<List<JamendoTrackDto>> = _searchedJamendoTracks.asStateFlow()

    // Audius online music tracks
    private val _trendingAudiusTracks = MutableStateFlow<List<AudiusTrackDto>>(emptyList())
    val trendingAudiusTracks: StateFlow<List<AudiusTrackDto>> = _trendingAudiusTracks.asStateFlow()

    private val _searchedAudiusTracks = MutableStateFlow<List<AudiusTrackDto>>(emptyList())
    val searchedAudiusTracks: StateFlow<List<AudiusTrackDto>> = _searchedAudiusTracks.asStateFlow()

    // Playlists from backend
    private val _playlists = MutableStateFlow<List<PlaylistSummaryDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummaryDto>> = _playlists.asStateFlow()

    // Favorite tracks from backend
    private val _favoriteTracks = MutableStateFlow<List<FavoriteTrackDto>>(emptyList())
    val favoriteTracks: StateFlow<List<FavoriteTrackDto>> = _favoriteTracks.asStateFlow()

    // Offline / Cached tracks from Room DB
    val cachedTracks: StateFlow<List<CachedTrackEntity>> = db.cachedTrackDao()
        .getAllCachedTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _cachedSearchQuery = MutableStateFlow("")
    val cachedSearchQuery: StateFlow<String> = _cachedSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchedCachedTracks: StateFlow<List<CachedTrackEntity>> = _cachedSearchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                db.cachedTrackDao().getAllCachedTracks()
            } else {
                db.cachedTrackDao().searchCachedTracks(query.trim())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun searchCachedTracks(query: String) {
        _cachedSearchQuery.value = query
    }

    // Auth & User Management
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Wire automatic 401 Unauthorized handling
        NetworkClient.authInterceptor.onUnauthorizedListener = {
            handleSessionExpired()
        }

        // Restore saved player preferences
        val savedShuffle = sessionManager.getShuffleMode()
        val savedRepeat = sessionManager.getRepeatMode()
        val savedSpeed = sessionManager.getPlaybackSpeed()
        if (savedShuffle) {
            playerController.setShuffleMode(savedShuffle)
        }
        if (savedRepeat != PlaybackState.REPEAT_MODE_OFF) {
            playerController.setRepeatMode(savedRepeat)
        }
        if (savedSpeed != 1.0f) {
            playerController.setPlaybackSpeed(savedSpeed)
        }

        // Restore saved session on launch
        if (sessionManager.isLoggedIn()) {
            val savedToken = sessionManager.getToken()
            NetworkClient.authInterceptor.authToken = savedToken
            _currentUser.value = sessionManager.getUsername() ?: sessionManager.getEmail()
            _authStatusMessage.value = "Авторизовано: ${_currentUser.value}"
            validateSession()
            loadBackendData()
        }
    }

    private fun handleSessionExpired() {
        viewModelScope.launch {
            sessionManager.clearSession()
            NetworkClient.authInterceptor.authToken = null
            _currentUser.value = null
            _authStatusMessage.value = "Сесія закінчилась (401). Будь ласка, увійдіть знову"
        }
    }

    private fun validateSession() {
        viewModelScope.launch {
            try {
                val resp = apiService.getCurrentUser()
                if (resp.isSuccessful && resp.body() != null) {
                    val user = resp.body()!!
                    _currentUser.value = user.username
                    sessionManager.saveSession(
                        token = sessionManager.getToken().orEmpty(),
                        userId = user.id,
                        username = user.username,
                        email = user.email
                    )
                } else if (resp.code() == 401) {
                    handleSessionExpired()
                }
            } catch (e: Exception) {
                // If offline, keep local session cached
            }
        }
    }

    // Player Actions
    fun play() = playerController.play()
    fun pause() = playerController.pause()
    fun playNext() = playerController.playNext()
    fun playPrevious() = playerController.playPrevious()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun toggleShuffle() {
        val nextMode = !playbackState.value.shuffleModeEnabled
        playerController.setShuffleMode(nextMode)
        sessionManager.saveShuffleMode(nextMode)
    }

    fun setShuffleMode(enabled: Boolean) {
        playerController.setShuffleMode(enabled)
        sessionManager.saveShuffleMode(enabled)
    }

    fun setRepeatMode(repeatMode: Int) {
        playerController.setRepeatMode(repeatMode)
        sessionManager.saveRepeatMode(repeatMode)
    }

    fun cycleRepeatMode() {
        val nextMode = when (playbackState.value.repeatMode) {
            PlaybackState.REPEAT_MODE_OFF -> PlaybackState.REPEAT_MODE_ALL
            PlaybackState.REPEAT_MODE_ALL -> PlaybackState.REPEAT_MODE_ONE
            else -> PlaybackState.REPEAT_MODE_OFF
        }
        setRepeatMode(nextMode)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
        sessionManager.savePlaybackSpeed(speed)
    }

    // Audio Cache Management
    fun getAudioCacheSizeBytes(): Long {
        return AudioCacheManager.getCacheSizeBytes(getApplication())
    }

    fun clearAudioCache() {
        AudioCacheManager.clearCache(getApplication())
    }

    // Dynamic Server Base URL Configuration
    fun setBaseUrl(newUrl: String) {
        val normalized = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        sessionManager.saveBaseUrl(normalized)
        apiService = NetworkClient.createService(normalized, getApplication())
        if (sessionManager.isLoggedIn()) {
            validateSession()
            loadBackendData()
        }
    }

    fun getBaseUrl(): String = sessionManager.getBaseUrl()

    fun playTrack(track: AudioTrack) {
        playerController.playTrack(track)
    }

    fun playQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playerController.setQueue(tracks, startIndex, autoPlay = true)
    }

    fun playRadioStation(station: RadioStationDto) {
        playTrack(AudioTrack.fromRadio(station))
    }

    fun playJamendoTrack(track: JamendoTrackDto) {
        playTrack(AudioTrack.fromJamendo(track))
    }

    fun playJamendoQueue(tracks: List<JamendoTrackDto>, startIndex: Int = 0) {
        val audioTracks = tracks.map { AudioTrack.fromJamendo(it) }
        playQueue(audioTracks, startIndex)
    }

    fun playAudiusTrack(track: AudiusTrackDto, baseUrl: String = sessionManager.getBaseUrl()) {
        playTrack(AudioTrack.fromAudius(track, baseUrl))
    }

    // Data Loading & API Calls
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
                loadPlaylists()
                loadFavoriteRadio()
                loadFavoriteTracks()
                loadRadioByCountry("UA")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchJamendo(query: String, limit: Int = 20) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = apiService.searchJamendoTracks(query.trim(), limit)
                if (resp.isSuccessful) {
                    _searchedJamendoTracks.value = resp.body().orEmpty()
                } else {
                    _authStatusMessage.value = "Помилка Jamendo: HTTP ${resp.code()}"
                }
            } catch (e: Exception) {
                _authStatusMessage.value = "Jamendo недоступний: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchAudius(query: String, limit: Int = 20) {
        if (query.isBlank()) return
        viewModelScope.launch {
            try {
                val resp = apiService.searchAudiusTracks(query.trim(), limit)
                if (resp.isSuccessful) {
                    _searchedAudiusTracks.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadRadioByCountry(countryCode: String = "UA", limit: Int = 20) {
        viewModelScope.launch {
            try {
                val resp = apiService.getRadioStationsByCountry(countryCode, limit)
                if (resp.isSuccessful) {
                    _radioStationsByCountry.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadRadioByGenre(genre: String, limit: Int = 20) {
        viewModelScope.launch {
            try {
                val resp = apiService.getRadioStationsByGenre(genre, limit)
                if (resp.isSuccessful) {
                    _radioStationsByCountry.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchRadio(query: String? = null, countryCode: String? = null, genre: String? = null, limit: Int = 20) {
        viewModelScope.launch {
            try {
                val resp = apiService.searchRadioStations(
                    query = query?.takeIf { it.isNotBlank() },
                    countryCode = countryCode?.takeIf { it.isNotBlank() },
                    genre = genre?.takeIf { it.isNotBlank() },
                    limit = limit
                )
                if (resp.isSuccessful) {
                    _radioStations.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadFavoriteRadio() {
        viewModelScope.launch {
            try {
                val resp = apiService.getFavoriteRadioStations()
                if (resp.isSuccessful) {
                    _radioStations.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addRadioToFavorites(station: RadioStationDto) {
        viewModelScope.launch {
            try {
                val resp = apiService.addFavoriteRadioStation(AddFavoriteRadioRequestDto(stationId = station.stationId))
                if (resp.isSuccessful) {
                    loadFavoriteRadio()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeRadioFromFavorites(id: Int) {
        viewModelScope.launch {
            try {
                val resp = apiService.removeFavoriteRadioStation(id)
                if (resp.isSuccessful) {
                    loadFavoriteRadio()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            try {
                val resp = apiService.getPlaylists()
                if (resp.isSuccessful) {
                    _playlists.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val resp = apiService.createPlaylist(CreatePlaylistRequestDto(name = name.trim()))
                if (resp.isSuccessful) {
                    loadPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            try {
                val resp = apiService.deletePlaylist(playlistId)
                if (resp.isSuccessful) {
                    loadPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTrackToPlaylist(playlistId: Int, track: AudioTrack, source: String = "jamendo") {
        viewModelScope.launch {
            try {
                val externalId = track.id.substringAfter('_', track.id)
                val resp = apiService.addTrackToPlaylist(
                    playlistId = playlistId,
                    request = AddTrackToPlaylistRequestDto(
                        source = source,
                        externalId = externalId,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs
                    )
                )
                if (resp.isSuccessful) {
                    loadPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch {
            try {
                val resp = apiService.removeTrackFromPlaylist(playlistId, trackId)
                if (resp.isSuccessful) {
                    loadPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadFavoriteTracks() {
        viewModelScope.launch {
            try {
                val resp = apiService.getFavoriteTracks()
                if (resp.isSuccessful) {
                    _favoriteTracks.value = resp.body().orEmpty()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTrackToFavorites(track: AudioTrack, source: String = "jamendo") {
        viewModelScope.launch {
            try {
                val externalId = track.id.substringAfter('_', track.id)
                val resp = apiService.addFavoriteTrack(
                    AddFavoriteTrackRequestDto(
                        source = source,
                        externalId = externalId,
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs
                    )
                )
                if (resp.isSuccessful) {
                    loadFavoriteTracks()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeFavoriteTrack(id: Int) {
        viewModelScope.launch {
            try {
                val resp = apiService.removeFavoriteTrack(id)
                if (resp.isSuccessful) {
                    loadFavoriteTracks()
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

    // Auth actions
    fun login(email: String, pass: String) {
        viewModelScope.launch {
            try {
                _authStatusMessage.value = "Вхід..."
                val resp = apiService.login(LoginRequestDto(email = email.trim(), password = pass))
                if (resp.isSuccessful && resp.body() != null) {
                    val authData = resp.body()!!
                    val token = authData.token
                    if (!token.isNullOrBlank()) {
                        NetworkClient.authInterceptor.authToken = token
                        sessionManager.saveSession(
                            token = token,
                            userId = authData.userId,
                            username = authData.username,
                            email = authData.email ?: email
                        )
                        _currentUser.value = authData.username ?: authData.email ?: email
                        _authStatusMessage.value = "Авторизовано: ${_currentUser.value}"
                        loadBackendData()
                    } else {
                        _authStatusMessage.value = "Помилка: відсутній токен авторизації"
                    }
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
                val resp = apiService.register(RegisterRequestDto(username = username.trim(), email = email.trim(), password = pass))
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
        sessionManager.clearSession()
        NetworkClient.authInterceptor.authToken = null
        _currentUser.value = null
        _authStatusMessage.value = "Ви вийшли з акаунту"
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
