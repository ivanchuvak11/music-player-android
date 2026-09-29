package com.musicplayer.android.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.android.core.audio.AudioCacheManager
import com.musicplayer.android.core.audio.AudioEffectsManager
import com.musicplayer.android.core.audio.AudioEffectsState
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.LocalAudioScanner
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.audio.PlayerController
import com.musicplayer.android.core.audio.PlayerControllerImpl
import com.musicplayer.android.core.audio.toAudioTrack
import com.musicplayer.android.core.database.AppDatabase
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.database.FavoriteTrackEntity
import com.musicplayer.android.core.database.LocalPlaylistEntity
import com.musicplayer.android.core.database.LocalPlaylistTrackEntity
import com.musicplayer.android.core.database.PlayHistoryEntity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Main ViewModel exposing player state, local tracks, playlists, radio stations,
 * Jamendo/Audius streaming, Room DB caching, and persistent session to the UI layer.
 */
@OptIn(FlowPreview::class)
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

    private val _radioStationsByCountry = MutableStateFlow<List<RadioStationDto>>(DEFAULT_UA_RADIO_STATIONS)
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

    // Local Room DB Playlists (100% offline & persistent)
    val localPlaylists: StateFlow<List<LocalPlaylistEntity>> = db.localPlaylistDao()
        .getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    // Audio Effects & Hardware Equalizer
    val audioEffectsState: StateFlow<AudioEffectsState> = AudioEffectsManager.effectsState

    // Local Favorites & Play History from Room DB
    val localFavorites: StateFlow<List<FavoriteTrackEntity>> = db.favoriteTrackDao()
        .getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playHistory: StateFlow<List<PlayHistoryEntity>> = db.playHistoryDao()
        .getRecentHistory(50)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Auth & User Management
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var jamendoSearchJob: Job? = null
    private var audiusSearchJob: Job? = null
    private var lastPositionSaveTimeMs = 0L

    init {
        // Wire automatic 401 Unauthorized handling
        NetworkClient.authInterceptor.onUnauthorizedListener = {
            handleSessionExpired()
        }

        // Restore saved player preferences
        val savedShuffle = sessionManager.getShuffleMode()
        val savedRepeat = sessionManager.getRepeatMode()
        if (savedShuffle) {
            playerController.setShuffleMode(savedShuffle)
        }
        if (savedRepeat != PlaybackState.REPEAT_MODE_OFF) {
            playerController.setRepeatMode(savedRepeat)
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

        // Restore persisted Equalizer & Bass Boost preferences
        AudioEffectsManager.restorePersistedState(application)

        // Fix #10: Debounce media changes by 1500ms to avoid 30 redundant scans per download
        loadLocalTracks()
        viewModelScope.launch {
            try {
                localScanner.observeMediaChanges()
                    .debounce(1500L)
                    .collect {
                        loadLocalTracks()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Automatic Play History Recording & Position Bookmarking
        var lastRecordedTrackId: String? = null
        viewModelScope.launch {
            playerController.playbackState.collect { state ->
                val track = state.currentTrack
                if (track != null) {
                    // Fix #7: Throttle disk writes to at most once per 5 seconds (not every 500ms)
                    val now = System.currentTimeMillis()
                    if ((state.currentPositionMs > 500L || state.isPlaying) &&
                        now - lastPositionSaveTimeMs >= 5_000L) {
                        lastPositionSaveTimeMs = now
                        sessionManager.saveLastPlayedTrack(track, state.currentPositionMs)
                    }
                    // Fix #15: Deduplicate history entries by removing any existing row before insert
                    if (state.isPlaying && track.id != lastRecordedTrackId) {
                        lastRecordedTrackId = track.id
                        try {
                            db.playHistoryDao().deleteTrackHistory(track.id)
                            db.playHistoryDao().insertHistory(
                                PlayHistoryEntity(
                                    trackId = track.id,
                                    title = track.title,
                                    artist = track.artist,
                                    audioUrl = track.audioUrl,
                                    artworkUrl = track.artworkUrl,
                                    durationMs = track.durationMs,
                                    lastPositionMs = state.currentPositionMs,
                                    playedAtTimestamp = System.currentTimeMillis()
                                )
                            )
                            db.playHistoryDao().trimOldHistory()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
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
    fun play() {
        if (playbackState.value.currentTrack != null) {
            playerController.play()
        } else {
            // Memory: Resume track and position that was playing before app was closed!
            val lastPlayed = sessionManager.getLastPlayedTrack()
            if (lastPlayed != null) {
                val (track, pos) = lastPlayed
                playerController.playTrack(track)
                if (pos > 1000L && !track.isLiveStream) {
                    playerController.seekTo(pos)
                }
            } else {
                // If nothing in memory, start playing the first local song!
                val firstTrack = _localTracks.value.firstOrNull()
                if (firstTrack != null) {
                    playLocalTrack(firstTrack)
                } else {
                    playerController.play()
                }
            }
        }
    }

    fun getLastPlayedTrack(): Pair<AudioTrack, Long>? = sessionManager.getLastPlayedTrack()

    fun pause() = playerController.pause()

    fun playNext() {
        val current = playbackState.value.currentTrack
        if (current?.isLiveStream == true) {
            val stations = _radioStationsByCountry.value.ifEmpty { DEFAULT_UA_RADIO_STATIONS }
            if (stations.isNotEmpty()) {
                val currentIndex = stations.indexOfFirst { it.name == current.title }
                val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % stations.size else 0
                playRadioStation(stations[nextIndex])
                return
            }
        }
        playerController.playNext()
    }

    fun playPrevious() {
        val current = playbackState.value.currentTrack
        if (current?.isLiveStream == true) {
            val stations = _radioStationsByCountry.value.ifEmpty { DEFAULT_UA_RADIO_STATIONS }
            if (stations.isNotEmpty()) {
                val currentIndex = stations.indexOfFirst { it.name == current.title }
                val prevIndex = if (currentIndex > 0) currentIndex - 1 else stations.size - 1
                playRadioStation(stations[prevIndex])
                return
            }
        }
        playerController.playPrevious()
    }

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

    fun playLocalTrack(track: AudioTrack) {
        val tracks = _localTracks.value
        if (tracks.isNotEmpty()) {
            val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playQueue(tracks, index)
        } else {
            playTrack(track)
        }
    }

    fun playQueue(tracks: List<AudioTrack>, startIndex: Int = 0) {
        playerController.setQueue(tracks, startIndex, autoPlay = true)
    }

    fun playRadioStation(station: RadioStationDto) {
        val stations = _radioStationsByCountry.value.ifEmpty { DEFAULT_UA_RADIO_STATIONS }
        val radioTracks = stations.map { AudioTrack.fromRadio(it) }
        val startIndex = stations.indexOfFirst { it.stationId == station.stationId }.coerceAtLeast(0)
        playQueue(radioTracks, startIndex)
    }

    fun playJamendoTrack(track: JamendoTrackDto) {
        // Fix #1: Always supply configured baseUrl so Jamendo plays on physical phones
        val baseUrl = sessionManager.getBaseUrl()
        val allJamendo = _searchedJamendoTracks.value
        if (allJamendo.isNotEmpty()) {
            val audioTracks = allJamendo.map { AudioTrack.fromJamendo(it, baseUrl) }
            val index = allJamendo.indexOfFirst { it.externalId == track.externalId }.coerceAtLeast(0)
            playQueue(audioTracks, index)
        } else {
            playTrack(AudioTrack.fromJamendo(track, baseUrl))
        }
    }

    fun playJamendoQueue(tracks: List<JamendoTrackDto>, startIndex: Int = 0) {
        // Fix #1: Always supply configured baseUrl
        val baseUrl = sessionManager.getBaseUrl()
        val audioTracks = tracks.map { AudioTrack.fromJamendo(it, baseUrl) }
        playQueue(audioTracks, startIndex)
    }

    fun playAudiusTrack(track: AudiusTrackDto, baseUrl: String = sessionManager.getBaseUrl()) {
        val allAudius = if (_trendingAudiusTracks.value.any { it.externalId == track.externalId }) {
            _trendingAudiusTracks.value
        } else {
            _searchedAudiusTracks.value
        }
        if (allAudius.isNotEmpty()) {
            val audioTracks = allAudius.map { AudioTrack.fromAudius(it, baseUrl) }
            val index = allAudius.indexOfFirst { it.externalId == track.externalId }.coerceAtLeast(0)
            playQueue(audioTracks, index)
        } else {
            playTrack(AudioTrack.fromAudius(track, baseUrl))
        }
    }

    // Audio Effects & Equalizer Controls
    fun setEqualizerEnabled(enabled: Boolean): Boolean {
        return AudioEffectsManager.setEnabled(enabled, getApplication())
    }

    fun setEqualizerPreset(presetName: String) {
        AudioEffectsManager.setPreset(presetName, getApplication())
    }

    fun setBassBoostStrength(strength: Short) {
        AudioEffectsManager.setBassBoost(strength, getApplication())
    }

    fun setEqualizerBandLevel(band: Short, level: Short) {
        AudioEffectsManager.setBandLevel(band, level, getApplication())
    }

    fun setLoudnessEnhancerGain(gainMb: Int) {
        AudioEffectsManager.setLoudnessEnhancerGain(gainMb)
    }

    fun resetEqualizer() {
        AudioEffectsManager.resetToFlat(getApplication())
    }

    // Playback Speed & Queue Management
    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun addToQueue(track: AudioTrack) {
        playerController.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playerController.removeFromQueue(index)
    }

    // Sleep Timer Controls
    val sleepTimerRemainingSeconds: StateFlow<Long> = com.musicplayer.android.core.audio.SleepTimer.remainingSeconds
    val isSleepTimerActive: StateFlow<Boolean> = com.musicplayer.android.core.audio.SleepTimer.isActive

    fun startSleepTimer(minutes: Int) {
        com.musicplayer.android.core.audio.SleepTimer.start(minutes) {
            pause()
        }
    }

    fun cancelSleepTimer() {
        com.musicplayer.android.core.audio.SleepTimer.cancel()
    }

    // Local Favorites & History Management
    fun toggleLocalFavorite(track: AudioTrack) {
        viewModelScope.launch {
            val isFav = localFavorites.value.any { it.id == track.id }
            if (isFav) {
                db.favoriteTrackDao().deleteFavorite(track.id)
            } else {
                db.favoriteTrackDao().insertFavorite(
                    FavoriteTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        audioUrl = track.audioUrl,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs
                    )
                )
            }
        }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            db.playHistoryDao().clearAllHistory()
        }
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Fix #25: Debounced Jamendo search (400ms delay) to prevent hammering the network.
     */
    fun searchJamendo(query: String, limit: Int = 20) {
        if (query.isBlank()) {
            _searchedJamendoTracks.value = emptyList()
            return
        }
        jamendoSearchJob?.cancel()
        jamendoSearchJob = viewModelScope.launch {
            delay(400L)
            _isLoading.value = true
            try {
                val resp = apiService.searchJamendoTracks(query.trim(), limit)
                if (resp.isSuccessful) {
                    _searchedJamendoTracks.value = resp.body().orEmpty()
                    if (_authStatusMessage.value?.startsWith("Jamendo") == true ||
                        _authStatusMessage.value?.startsWith("Помилка Jamendo") == true
                    ) {
                        _authStatusMessage.value = null
                    }
                } else {
                    _authStatusMessage.value = "Помилка Jamendo: HTTP ${resp.code()}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _authStatusMessage.value = "Jamendo недоступний: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fix #25: Debounced Audius search (400ms delay).
     */
    fun searchAudius(query: String, limit: Int = 20) {
        if (query.isBlank()) {
            _searchedAudiusTracks.value = emptyList()
            return
        }
        audiusSearchJob?.cancel()
        audiusSearchJob = viewModelScope.launch {
            delay(400L)
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
                if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                    _radioStationsByCountry.value = resp.body()!!
                }
            } catch (e: Exception) {
                // Keep default stations on network/backend failure
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

    fun getPlaylistTracks(playlistId: Long): Flow<List<LocalPlaylistTrackEntity>> {
        return db.localPlaylistDao().getTracksForPlaylist(playlistId)
    }

    fun createLocalPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                db.localPlaylistDao().insertPlaylist(LocalPlaylistEntity(name = name.trim()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteLocalPlaylist(playlistId: Long) {
        viewModelScope.launch {
            try {
                // Fix #30: ForeignKey CASCADE deletes playlist tracks automatically
                db.localPlaylistDao().deletePlaylist(playlistId)
                db.localPlaylistDao().deleteTracksForPlaylist(playlistId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTrackToLocalPlaylist(playlistId: Long, track: AudioTrack) {
        addTracksToLocalPlaylist(playlistId, listOf(track))
    }

    fun addTracksToLocalPlaylist(playlistId: Long, tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            try {
                val entities = tracks.map { track ->
                    LocalPlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        audioUrl = track.audioUrl,
                        artworkUrl = track.artworkUrl, // Fix #14: preserve artwork
                        durationMs = track.durationMs,
                        isLocal = track.isLocal
                    )
                }
                db.localPlaylistDao().insertTracks(entities)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createLocalPlaylistWithTracks(name: String, tracks: List<AudioTrack>, onCreated: ((Long) -> Unit)? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newPl = LocalPlaylistEntity(name = name.trim())
                val plId = db.localPlaylistDao().insertPlaylist(newPl)
                if (tracks.isNotEmpty()) {
                    val entities = tracks.map { track ->
                        LocalPlaylistTrackEntity(
                            playlistId = plId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            audioUrl = track.audioUrl,
                            artworkUrl = track.artworkUrl, // Fix #14: preserve artwork
                            durationMs = track.durationMs,
                            isLocal = track.isLocal
                        )
                    }
                    db.localPlaylistDao().insertTracks(entities)
                }
                onCreated?.invoke(plId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeTrackFromLocalPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            try {
                db.localPlaylistDao().removeTrackFromPlaylist(playlistId, trackId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playLocalPlaylist(playlistId: Long, startIndex: Int = 0) {
        viewModelScope.launch {
            try {
                val tracks = db.localPlaylistDao().getTracksForPlaylistSync(playlistId).map { it.toAudioTrack() }
                if (tracks.isNotEmpty()) {
                    playerController.setQueue(tracks, startIndex)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        createLocalPlaylist(name)
        if (sessionManager.isLoggedIn()) {
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
    }

    fun deletePlaylist(playlistId: Int) {
        deleteLocalPlaylist(playlistId.toLong())
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
                        artworkUrl = track.artworkUrl, // Fix #14: save artworkUrl
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
        // Fix #20: Cancel SleepTimer when ViewModel is cleared
        com.musicplayer.android.core.audio.SleepTimer.cancel()
    }

    companion object {
        val DEFAULT_UA_RADIO_STATIONS = listOf(
            RadioStationDto(
                stationId = "hitfm_ua",
                name = "Хіт FM",
                streamUrl = "https://online.hitfm.ua/HitFM_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Хіти / Поп",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "kissfm_ua",
                name = "Kiss FM Ukraine",
                streamUrl = "https://online.kissfm.ua/KissFM_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Dance / EDM",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "radioroks_ua",
                name = "Radio ROKS",
                streamUrl = "https://online.radioroks.ua/RadioROKS_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Рок-класика",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "bayraktar_ua",
                name = "Радіо Байрактар",
                streamUrl = "https://online.radiobayraktar.ua/RadioBayraktar_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Українська музика",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "radiorelax_ua",
                name = "Radio Relax",
                streamUrl = "https://online.radiorelax.ua/RadioRelax_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Легка музика / Chill",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "radiojazz_ua",
                name = "Radio Jazz",
                streamUrl = "https://online.radiojazz.ua/RadioJazz_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Джаз та Блюз",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "melodiafm_ua",
                name = "Мелодія FM",
                streamUrl = "https://online.melodiafm.ua/MelodiaFM_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Ретро та Хіти",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "nasheradio_ua",
                name = "Наше Радіо",
                streamUrl = "https://online.nasheradio.ua/NasheRadio_HD",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Український Поп",
                codec = "MP3/AAC"
            ),
            RadioStationDto(
                stationId = "radiotrek_ua",
                name = "Радіо Трек",
                streamUrl = "http://online2.radiotrek.rv.ua:8000/MP3_128",
                country = "Ukraine",
                countryCode = "UA",
                genre = "Поп / Новини",
                codec = "MP3"
            )
        )
    }
}
