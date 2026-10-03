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
import com.musicplayer.android.core.audio.mergeYouTubeSearchResults
import com.musicplayer.android.core.audio.shouldSupplementYouTubeSearch
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
import com.musicplayer.android.core.network.SoundCloudTrackDto
import com.musicplayer.android.core.network.YouTubeTrackDto
import com.musicplayer.android.core.session.SessionManager
import com.musicplayer.android.core.audio.calculateSearchRelevanceScore
import com.musicplayer.android.core.audio.searchDeduplicationKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Encapsulates fully ranked and deduplicated search results computed on background Dispatchers.Default.
 * Zero scoring or filtering work occurs on the Main UI thread.
 */
data class SearchUiResults(
    val query: String = "",
    val localTracks: List<AudioTrack> = emptyList(),
    val cachedTracks: List<AudioTrack> = emptyList(),
    val onlineResults: List<AudioTrack> = emptyList()
)

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

    // YouTube Music online tracks
    private val _searchedYouTubeTracks = MutableStateFlow<List<YouTubeTrackDto>>(emptyList())
    val searchedYouTubeTracks: StateFlow<List<YouTubeTrackDto>> = _searchedYouTubeTracks.asStateFlow()

    // SoundCloud online tracks
    private val _searchedSoundCloudTracks = MutableStateFlow<List<SoundCloudTrackDto>>(emptyList())
    val searchedSoundCloudTracks: StateFlow<List<SoundCloudTrackDto>> = _searchedSoundCloudTracks.asStateFlow()

    // Unified Trending / Popular online tracks (YouTube, Audius, SoundCloud)
    private val _trendingOnlineTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val trendingOnlineTracks: StateFlow<List<AudioTrack>> = _trendingOnlineTracks.asStateFlow()

    private val _onlineSearchError = MutableStateFlow<String?>(null)
    val onlineSearchError: StateFlow<String?> = _onlineSearchError.asStateFlow()

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
    private var youtubeSearchJob: Job? = null
    private var soundCloudSearchJob: Job? = null
    private var lastPositionSaveTimeMs = 0L

    // ─── Unified multi-source search state (ТЗ Section 3) ─────────────────────
    private val _unifiedSearchResults = MutableStateFlow<List<com.musicplayer.android.core.audio.UnifiedTrack>>(emptyList())
    val unifiedSearchResults: StateFlow<List<com.musicplayer.android.core.audio.UnifiedTrack>> = _unifiedSearchResults.asStateFlow()

    private val _unifiedSearchErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val unifiedSearchErrors: StateFlow<Map<String, String>> = _unifiedSearchErrors.asStateFlow()

    private val _isUnifiedSearchOffline = MutableStateFlow(false)
    val isUnifiedSearchOffline: StateFlow<Boolean> = _isUnifiedSearchOffline.asStateFlow()

    private var unifiedSearchJob: Job? = null

    private val musicSearchUseCase: com.musicplayer.android.core.audio.MusicSearchUseCase by lazy {
        com.musicplayer.android.core.audio.MusicSearchUseCase(
            apiService = apiService,
            cachedTrackDao = db.cachedTrackDao(),
            isOnline = { isOnline.value }
        )
    }

    // ─── High-performance Background Search Pipeline ──────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchUiResults = MutableStateFlow(SearchUiResults())
    val searchUiResults: StateFlow<SearchUiResults> = _searchUiResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private var searchPipelineJob: Job? = null
    private var suggestionJob: Job? = null
    // ──────────────────────────────────────────────────────────────────────────

    // ─── Autoplay (Smart Queue Extension) ─────────────────────────────────────
    private val _autoplayEnabled = MutableStateFlow(sessionManager.getAutoplayEnabled())
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _isLoadingAutoplay = MutableStateFlow(false)
    val isLoadingAutoplay: StateFlow<Boolean> = _isLoadingAutoplay.asStateFlow()

    private var autoplayJob: Job? = null
    // ──────────────────────────────────────────────────────────────────────────

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

        // Clean up any incomplete .tmp download files from interrupted sessions
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.musicplayer.android.core.audio.TrackDownloadManager.cleanupOrphanTempFiles(application)
        }

        // Fix #10: Debounce media changes by 1500ms to avoid 30 redundant scans per download
        loadLocalTracks()
        loadTrendingOnlineTracks()
        viewModelScope.launch {
            try {
                localScanner.observeMediaChanges()
                    .debounce(1500L)
                    .collect {
                        com.musicplayer.android.core.audio.AudioArtworkFetcher.clearCache()
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
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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

        // ─── Autoplay Observer: extend queue when last track finishes ─────────
        // Fires when:
        //   • Autoplay is ON
        //   • Repeat mode is OFF (repeat-all/one loops by itself, no need to extend)
        //   • hasNext == false (we are at the end of the queue)
        //   • The track is playing (not just paused at end)
        //   • The track is NOT a radio live stream
        var lastAutoplayTriggeredForTrackId: String? = null
        viewModelScope.launch {
            playerController.playbackState.collect { state ->
                val track = state.currentTrack ?: return@collect
                val repeatOff = state.repeatMode == PlaybackState.REPEAT_MODE_OFF
                val autoplayOn = _autoplayEnabled.value
                val isNearEnd = state.durationMs > 0L &&
                    state.currentPositionMs >= (state.durationMs - 8_000L)

                // Trigger fetch 8 seconds before the last track ends (like Spotify/YT Music)
                if (autoplayOn && repeatOff && !state.hasNext &&
                    !track.isLiveStream && !track.isLocal &&
                    state.isPlaying && isNearEnd &&
                    track.id != lastAutoplayTriggeredForTrackId &&
                    autoplayJob?.isActive != true
                ) {
                    lastAutoplayTriggeredForTrackId = track.id
                    triggerAutoplay(track, state.queue)
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────
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

    // ─── Autoplay: extend queue with similar tracks ───────────────────────────

    /**
     * Fetches similar tracks and appends them to the current queue.
     * Called automatically when the last track in the queue is 8 seconds from ending.
     */
    private fun triggerAutoplay(currentTrack: AudioTrack, currentQueue: List<AudioTrack>) {
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            _isLoadingAutoplay.value = true
            try {
                val existingIds = currentQueue.map { it.id }.toSet()
                val similar = com.musicplayer.android.core.audio.AutoplayService.fetchSimilarTracks(
                    currentTrack = currentTrack,
                    existingIds = existingIds,
                    limit = 12
                )
                if (similar.isNotEmpty()) {
                    similar.forEach { track -> playerController.addToQueue(track) }
                    android.util.Log.i("Autoplay", "Added ${similar.size} similar tracks to queue")
                    // If player is ended or reached the end while fetching, advance to the new track
                    val state = playbackState.value
                    if (!state.isPlaying && (state.currentPositionMs >= (currentTrack.durationMs - 1500L).coerceAtLeast(0L) || currentTrack.durationMs <= 0L)) {
                        delay(200)
                        playerController.playNext()
                    }
                } else {
                    android.util.Log.w("Autoplay", "No similar tracks found for ${currentTrack.title}")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("Autoplay", "Failed to fetch autoplay tracks: ${e.message}")
                }
            } finally {
                _isLoadingAutoplay.value = false
            }
        }
    }

    /**
     * Triggered when the user explicitly taps "Next" on the last song in the queue.
     * Fetches similar tracks, appends them to the queue, and immediately advances playback.
     */
    fun playNextWithAutoplay(currentTrack: AudioTrack, currentQueue: List<AudioTrack>) {
        if (currentTrack.isLiveStream) return
        autoplayJob?.cancel()
        autoplayJob = viewModelScope.launch {
            _isLoadingAutoplay.value = true
            try {
                val existingIds = currentQueue.map { it.id }.toSet()
                val similar = com.musicplayer.android.core.audio.AutoplayService.fetchSimilarTracks(
                    currentTrack = currentTrack,
                    existingIds = existingIds,
                    limit = 15
                )
                if (similar.isNotEmpty()) {
                    similar.forEach { track -> playerController.addToQueue(track) }
                    android.util.Log.i("Autoplay", "Explicit Next: appended ${similar.size} similar tracks")
                    delay(200)
                    playerController.playNext()
                } else if (currentQueue.size > 1) {
                    // Loop to start if no similar tracks found
                    playerController.setQueue(currentQueue, startIndex = 0, autoPlay = true)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("Autoplay", "Failed to fetch autoplay on Next: ${e.message}")
                }
            } finally {
                _isLoadingAutoplay.value = false
            }
        }
    }

    // ─── Similar Tracks for Any Song (Song Radio / Схожі треки) ────────────────
    private val _similarTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val similarTracks: StateFlow<List<AudioTrack>> = _similarTracks.asStateFlow()

    private val _isLoadingSimilarTracks = MutableStateFlow(false)
    val isLoadingSimilarTracks: StateFlow<Boolean> = _isLoadingSimilarTracks.asStateFlow()

    private val _selectedSimilarSeedTrack = MutableStateFlow<AudioTrack?>(null)
    val selectedSimilarSeedTrack: StateFlow<AudioTrack?> = _selectedSimilarSeedTrack.asStateFlow()

    private var similarTracksJob: Job? = null

    /**
     * Loads a list of similar tracks for the given seed track to display in UI.
     */
    fun loadSimilarTracks(seedTrack: AudioTrack) {
        _selectedSimilarSeedTrack.value = seedTrack
        _similarTracks.value = emptyList()
        similarTracksJob?.cancel()
        similarTracksJob = viewModelScope.launch {
            _isLoadingSimilarTracks.value = true
            try {
                val results = com.musicplayer.android.core.audio.AutoplayService.fetchSimilarTracks(
                    currentTrack = seedTrack,
                    existingIds = setOf(seedTrack.id),
                    limit = 20
                )
                _similarTracks.value = results
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("Autoplay", "Failed to load similar tracks: ${e.message}")
                }
            } finally {
                _isLoadingSimilarTracks.value = false
            }
        }
    }

    /**
     * Creates an instant radio queue based on the seed track (seed + 20 similar songs)
     * and starts playback immediately.
     */
    fun playSimilarTracks(seedTrack: AudioTrack) {
        viewModelScope.launch {
            _isLoadingSimilarTracks.value = true
            try {
                val results = com.musicplayer.android.core.audio.AutoplayService.fetchSimilarTracks(
                    currentTrack = seedTrack,
                    existingIds = setOf(seedTrack.id),
                    limit = 20
                )
                if (results.isNotEmpty()) {
                    val fullQueue = listOf(seedTrack) + results
                    playQueue(fullQueue, startIndex = 0)
                } else {
                    playTrack(seedTrack)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("Autoplay", "Failed to play similar mix: ${e.message}")
                }
                playTrack(seedTrack)
            } finally {
                _isLoadingSimilarTracks.value = false
            }
        }
    }

    /**
     * Appends all currently loaded similar tracks to the current playback queue.
     */
    fun addLoadedSimilarTracksToQueue() {
        val tracks = _similarTracks.value
        if (tracks.isNotEmpty()) {
            tracks.forEach { playerController.addToQueue(it) }
        }
    }

    fun clearSimilarTracks() {
        _selectedSimilarSeedTrack.value = null
        _similarTracks.value = emptyList()
    }

    /** Toggle the Autoplay setting and persist it. */
    fun toggleAutoplay() {
        val next = !_autoplayEnabled.value
        _autoplayEnabled.value = next
        sessionManager.saveAutoplayEnabled(next)
    }

    /** Set Autoplay explicitly and persist it. */
    fun setAutoplayEnabled(enabled: Boolean) {
        _autoplayEnabled.value = enabled
        sessionManager.saveAutoplayEnabled(enabled)
    }

    // ─────────────────────────────────────────────────────────────────────────

    // Player Actions
    fun play() {
        if (playbackState.value.currentTrack != null) {
            playerController.play()
        } else {
            // Memory: Resume track and position that was playing before app was closed!
            val lastPlayed = sessionManager.getLastPlayedTrack()
            if (lastPlayed != null) {
                val (track, pos) = lastPlayed
                val startPos = if (pos > 1000L && !track.isLiveStream) pos else 0L
                playerController.playTrack(track, startPos)
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

    fun pause() {
        // Immediately persist current position upon pausing to never lose playback state
        val state = playbackState.value
        val track = state.currentTrack
        if (track != null && state.currentPositionMs > 0L) {
            sessionManager.saveLastPlayedTrack(track, state.currentPositionMs)
        }
        playerController.pause()
    }

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
        val state = playbackState.value
        // When user taps "Next" on the last song in queue, trigger instant Autoplay extension
        if (!state.hasNext) {
            if (state.repeatMode == PlaybackState.REPEAT_MODE_ALL && state.queue.isNotEmpty()) {
                playerController.setQueue(state.queue, startIndex = 0, autoPlay = true)
                return
            }
            if (current != null && !current.isLiveStream) {
                playNextWithAutoplay(current, state.queue)
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
        loadTrendingOnlineTracks()
        if (sessionManager.isLoggedIn()) {
            validateSession()
            loadBackendData()
        }
    }

    fun getBaseUrl(): String = sessionManager.getBaseUrl()

    fun playTrack(track: AudioTrack) {
        // If the track belongs to local favorites and current queue doesn't contain it, play the favorites queue
        val favs = localFavorites.value
        val favIndex = favs.indexOfFirst { it.id == track.id }
        if (favIndex >= 0 && (playbackState.value.queue.isEmpty() || playbackState.value.queue.none { it.id == track.id })) {
            val favTracks = favs.map { it.toAudioTrack() }
            playQueue(favTracks, favIndex)
            return
        }

        // If the track is among local tracks and queue is empty, play local tracks queue
        val locals = _localTracks.value
        val localIndex = locals.indexOfFirst { it.id == track.id }
        if (localIndex >= 0 && (playbackState.value.queue.isEmpty() || playbackState.value.queue.none { it.id == track.id })) {
            playQueue(locals, localIndex)
            return
        }

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

    fun playYouTubeTrack(track: com.musicplayer.android.core.network.YouTubeTrackDto) {
        val baseUrl = sessionManager.getBaseUrl()
        val allYouTube = _searchedYouTubeTracks.value
        if (allYouTube.isNotEmpty()) {
            val audioTracks = allYouTube.map { AudioTrack.fromYouTube(it, baseUrl) }
            val index = allYouTube.indexOfFirst { it.externalId == track.externalId }.coerceAtLeast(0)
            playQueue(audioTracks, index)
        } else {
            playTrack(AudioTrack.fromYouTube(track, baseUrl))
        }
    }

    fun playSoundCloudTrack(track: com.musicplayer.android.core.network.SoundCloudTrackDto) {
        val baseUrl = sessionManager.getBaseUrl()
        val allSoundCloud = _searchedSoundCloudTracks.value
        if (allSoundCloud.isNotEmpty()) {
            val audioTracks = allSoundCloud.map { AudioTrack.fromSoundCloud(it, baseUrl) }
            val index = allSoundCloud.indexOfFirst { it.externalId == track.externalId }.coerceAtLeast(0)
            playQueue(audioTracks, index)
        } else {
            playTrack(AudioTrack.fromSoundCloud(track, baseUrl))
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
                loadTrendingOnlineTracks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Fix #25: Debounced Jamendo search (400ms delay) to prevent hammering the network.
     */
    private fun notifyOnlineSearchError(e: Throwable) {
        if (e is CancellationException) return
        val url = sessionManager.getBaseUrl()
        _onlineSearchError.value = "Сервер недоступний ($url). Перевірте підключення до бекенду або адресу в Налаштуваннях."
    }

    fun searchJamendo(query: String, limit: Int = 20) {
        // Disabled: Jamendo source is turned off
        _searchedJamendoTracks.value = emptyList()
    }

    /**
     * Fix #25: Debounced Audius search (400ms delay).
     */
    fun searchAudius(query: String, limit: Int = 20) {
        // Disabled: Audius source is turned off
        _searchedAudiusTracks.value = emptyList()
    }

    fun searchYouTube(query: String, limit: Int = 20) {
        youtubeSearchJob?.cancel()
        _onlineSearchError.value = null
        if (query.isBlank()) {
            _searchedYouTubeTracks.value = emptyList()
            refreshOnlineSearchResults("")
            return
        }
        youtubeSearchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(350L)
            try {
                // 1. Client-side NewPipe extractor runs concurrently on device network (~0.8s)
                val newPipeDeferred = async(Dispatchers.IO) {
                    com.musicplayer.android.core.audio.YouTubeExtractorService.search(query.trim(), limit)
                }

                // 2. Fast-timeout backend probe (fails in 2.5s if server unreachable, never hangs!)
                val backendDeferred = async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(2500L) {
                            val response = apiService.searchYouTubeTracks(query.trim(), limit)
                            if (response.isSuccessful) response.body().orEmpty() else emptyList()
                        } ?: emptyList()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                }

                val supplementalTracks = newPipeDeferred.await()
                val primaryTracks = backendDeferred.await()
                currentCoroutineContext().ensureActive()

                val tracks = mergeYouTubeSearchResults(query, primaryTracks, supplementalTracks, limit)
                currentCoroutineContext().ensureActive()

                _searchedYouTubeTracks.value = tracks
                if (tracks.isNotEmpty()) {
                    _onlineSearchError.value = null
                }
                refreshOnlineSearchResults(query.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _searchedYouTubeTracks.value = emptyList()
                notifyOnlineSearchError(e)
            }
        }
    }

    fun searchSoundCloud(query: String, limit: Int = 20) {
        if (query.isBlank()) {
            _searchedSoundCloudTracks.value = emptyList()
            _onlineSearchError.value = null
            return
        }
        soundCloudSearchJob?.cancel()
        soundCloudSearchJob = viewModelScope.launch {
            delay(400L)
            try {
                val resp = apiService.searchSoundCloudTracks(query.trim(), limit)
                if (resp.isSuccessful) {
                    _searchedSoundCloudTracks.value = resp.body().orEmpty()
                    _onlineSearchError.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                notifyOnlineSearchError(e)
            }
        }
    }

    fun loadTrendingOnlineTracks() {
        viewModelScope.launch {
            try {
                val baseUrl = getBaseUrl()
                val tracks = mutableListOf<AudioTrack>()

                // 1. YouTube Music top hits
                try {
                    var ytTracks: List<YouTubeTrackDto>? = null
                    try {
                        val ytResp = apiService.searchYouTubeTracks("Ukraine Top Hits", 15)
                        if (ytResp.isSuccessful && !ytResp.body().isNullOrEmpty()) {
                            ytTracks = ytResp.body()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (ytTracks.isNullOrEmpty()) {
                        ytTracks = com.musicplayer.android.core.audio.YouTubeExtractorService.search("Ukraine Top Hits", 15)
                    }

                    if (!ytTracks.isNullOrEmpty()) {
                        tracks.addAll(ytTracks.map { AudioTrack.fromYouTube(it, baseUrl) })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. SoundCloud popular
                try {
                    val scResp = apiService.searchSoundCloudTracks("Top Hits", 10)
                    if (scResp.isSuccessful && !scResp.body().isNullOrEmpty()) {
                        tracks.addAll(scResp.body()!!.map { AudioTrack.fromSoundCloud(it, baseUrl) })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (tracks.isNotEmpty()) {
                    _trendingOnlineTracks.value = tracks
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Unified multi-source search (ТЗ Section 3).
     * Queries SoundCloud, Audius and Jamendo in parallel with 400ms debounce.
     * Merges results in order SC → Audius → Jamendo, deduplicates by source+externalId.
     * Per-source errors do NOT block other sources.
     * On offline: returns Room-cached tracks instead.
     */
    fun searchAllSources(query: String, limit: Int = 20) {
        if (query.isBlank()) {
            _unifiedSearchResults.value = emptyList()
            _unifiedSearchErrors.value = emptyMap()
            _isUnifiedSearchOffline.value = false
            refreshOnlineSearchResults("")
            return
        }
        unifiedSearchJob?.cancel()
        _unifiedSearchErrors.value = emptyMap()
        _isUnifiedSearchOffline.value = false
        unifiedSearchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(350L)
            _isLoading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    musicSearchUseCase.search(
                        query = query,
                        limit = limit,
                        backendBaseUrl = sessionManager.getBaseUrl()
                    )
                }
                _unifiedSearchResults.value = result.tracks
                _unifiedSearchErrors.value = result.errors.mapValues { it.value.message.orEmpty() }
                _isUnifiedSearchOffline.value = result.isOfflineFallback
                refreshOnlineSearchResults(query.trim())

                if (result.allSourcesFailed) {
                    android.util.Log.w("MainPlayerViewModel", "All 3 sources failed for query: '$query'")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "searchAllSources error: ${e.message}", e)
                _unifiedSearchErrors.value = mapOf("all" to (e.message ?: "Unknown error"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Clear unified search results and reset state */
    fun clearUnifiedSearch() {
        unifiedSearchJob?.cancel()
        _unifiedSearchResults.value = emptyList()
        _unifiedSearchErrors.value = emptyMap()
        _isUnifiedSearchOffline.value = false
        refreshOnlineSearchResults(_searchQuery.value)
    }

    /**
     * Updates unified search query and performs lightning-fast background filtering
     * on Dispatchers.Default. Zero UI thread work ensures 120 FPS typing.
     */
    fun updateSearchQuery(query: String) {
        val q = query.trim()
        _searchQuery.value = q
        if (q.isBlank()) {
            searchPipelineJob?.cancel()
            suggestionJob?.cancel()
            youtubeSearchJob?.cancel()
            unifiedSearchJob?.cancel()
            _searchSuggestions.value = emptyList()
            _searchedYouTubeTracks.value = emptyList()
            _unifiedSearchResults.value = emptyList()
            _searchUiResults.value = SearchUiResults()
            return
        }

        // Real-time suggestions from Google/YouTube Suggest API (~50ms)
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch(Dispatchers.IO) {
            val suggestions = com.musicplayer.android.core.audio.SearchSuggestionService.getSuggestions(q)
            _searchSuggestions.value = suggestions
        }

        searchPipelineJob?.cancel()
        searchPipelineJob = viewModelScope.launch(Dispatchers.Default) {
            // Instant local and cached tracks relevance filtering on Dispatchers.Default
            val loc = _localTracks.value
                .map { it to it.calculateSearchRelevanceScore(q) }
                .filter { it.second >= 0 }
                .sortedByDescending { it.second }
                .map { it.first }

            val cac = cachedTracks.value
                .map { it.toAudioTrack() }
                .map { it to it.calculateSearchRelevanceScore(q) }
                .filter { it.second >= 0 }
                .sortedByDescending { it.second }
                .map { it.first }

            _searchUiResults.value = _searchUiResults.value.copy(
                query = q,
                localTracks = loc,
                cachedTracks = cac
            )

            // Trigger debounced online searches concurrently
            if (isOnline.value) {
                searchYouTube(q)
                searchAllSources(q)
            } else {
                searchCachedTracks(q)
            }
        }
    }

    private fun refreshOnlineSearchResults(query: String) {
        if (query.isBlank()) {
            _searchUiResults.value = _searchUiResults.value.copy(onlineResults = emptyList())
            return
        }
        val currentYt = _searchedYouTubeTracks.value
        val currentUnified = _unifiedSearchResults.value
        val baseUrl = getBaseUrl()

        viewModelScope.launch(Dispatchers.Default) {
            val rawOnline = currentYt.map { AudioTrack.fromYouTube(it, baseUrl) } +
                currentUnified.map { it.toAudioTrack(baseUrl) }

            val onl = rawOnline.withIndex()
                .map { indexed ->
                    val rawScore = indexed.value.calculateSearchRelevanceScore(query)
                    // If exact/strong title/artist match, give high score.
                    // Otherwise, preserve natural YouTube AI streaming order so thematic/genre songs remain at top.
                    val effectiveScore = if (rawScore >= 250) rawScore else (150 - indexed.index.coerceAtMost(100))
                    Triple(
                        indexed.value,
                        effectiveScore,
                        indexed.index
                    )
                }
                .sortedWith(
                    compareByDescending<Triple<AudioTrack, Int, Int>> { it.second }
                        .thenBy { it.third }
                )
                .distinctBy { result ->
                    result.first.searchDeduplicationKey().ifBlank { "id:${result.first.id}" }
                }
                .map { it.first }

            _searchUiResults.value = _searchUiResults.value.copy(
                onlineResults = onl
            )
        }
    }

    /**
     * Plays a UnifiedTrack by resolving the stream URL and delegating to PlayerController.
     * Handles all 4 sources per ТЗ Section 4.
     */
    fun playUnifiedTrack(track: com.musicplayer.android.core.audio.UnifiedTrack) {
        if (track.source == com.musicplayer.android.core.audio.TrackSource.LOCAL) {
            val audioTrack = track.toAudioTrack(sessionManager.getBaseUrl())
            playerController.playTrack(audioTrack)
            return
        }
        viewModelScope.launch {
            try {
                val audioTrack = track.toAudioTrack(sessionManager.getBaseUrl())
                playerController.playTrack(audioTrack)
                // Add to play history
                try {
                    db.playHistoryDao().deleteTrackHistory(audioTrack.id)
                    db.playHistoryDao().insertHistory(
                        com.musicplayer.android.core.database.PlayHistoryEntity(
                            trackId = audioTrack.id,
                            title = audioTrack.title,
                            artist = audioTrack.artist,
                            audioUrl = audioTrack.audioUrl,
                            artworkUrl = audioTrack.artworkUrl,
                            durationMs = audioTrack.durationMs,
                            lastPositionMs = 0L,
                            playedAtTimestamp = System.currentTimeMillis()
                        )
                    )
                    db.playHistoryDao().trimOldHistory()
                } catch (e: Exception) {
                    android.util.Log.e("MainPlayerViewModel", "playUnifiedTrack history error: ${e.message}", e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "playUnifiedTrack error: ${e.message}", e)
                _authStatusMessage.value = "Помилка відтворення: ${e.message}"
            }
        }
    }

    /**
     * Add unified track to favorites (backend + local Room).
     * Correctly sends source and externalId per ТЗ Section 5.
     */
    fun addUnifiedTrackToFavorites(track: com.musicplayer.android.core.audio.UnifiedTrack) {
        // Add to local Room favorites (always works offline)
        viewModelScope.launch {
            try {
                db.favoriteTrackDao().insertFavorite(
                    com.musicplayer.android.core.database.FavoriteTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        audioUrl = track.resolveStreamUrl(sessionManager.getBaseUrl()),
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "addUnifiedTrackToFavorites Room error: ${e.message}", e)
            }
        }
        // Sync to backend if logged in
        if (sessionManager.isLoggedIn() && track.source != com.musicplayer.android.core.audio.TrackSource.LOCAL) {
            viewModelScope.launch {
                try {
                    val resp = apiService.addFavoriteTrack(
                        com.musicplayer.android.core.network.AddFavoriteTrackRequestDto(
                            source = track.source.value,
                            externalId = track.externalId,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs
                        )
                    )
                    if (resp.isSuccessful) {
                        loadFavoriteTracks()
                    } else if (resp.code() == 401) {
                        handleSessionExpired()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MainPlayerViewModel", "addUnifiedTrackToFavorites backend error: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Add unified track to a playlist (backend + local Room).
     * Correctly sends source and externalId per ТЗ Section 5.
     */
    fun addUnifiedTrackToPlaylist(playlistId: Long, backendPlaylistId: Int? = null, track: com.musicplayer.android.core.audio.UnifiedTrack) {
        // Add to local Room playlist
        viewModelScope.launch {
            try {
                db.localPlaylistDao().insertTracks(
                    listOf(
                        com.musicplayer.android.core.database.LocalPlaylistTrackEntity(
                            playlistId = playlistId,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            audioUrl = track.resolveStreamUrl(sessionManager.getBaseUrl()),
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            isLocal = track.source == com.musicplayer.android.core.audio.TrackSource.LOCAL
                        )
                    )
                )
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "addUnifiedTrackToPlaylist Room error: ${e.message}", e)
            }
        }
        // Sync to backend if logged in and backend playlist ID is provided
        if (sessionManager.isLoggedIn() && backendPlaylistId != null &&
            track.source != com.musicplayer.android.core.audio.TrackSource.LOCAL) {
            viewModelScope.launch {
                try {
                    val resp = apiService.addTrackToPlaylist(
                        playlistId = backendPlaylistId,
                        request = com.musicplayer.android.core.network.AddTrackToPlaylistRequestDto(
                            source = track.source.value,
                            externalId = track.externalId,
                            title = track.title,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs
                        )
                    )
                    if (resp.isSuccessful) {
                        loadPlaylists()
                    } else if (resp.code() == 401) {
                        handleSessionExpired()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MainPlayerViewModel", "addUnifiedTrackToPlaylist backend error: ${e.message}", e)
                }
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

    fun removeTrackFromLocalPlaylist(playlistId: Long, trackId: String, rowId: Long = 0L) {
        viewModelScope.launch {
            try {
                if (rowId > 0L) {
                    db.localPlaylistDao().removeTrackById(rowId)
                } else {
                    db.localPlaylistDao().removeTrackFromPlaylist(playlistId, trackId)
                }
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

    // Active download jobs & progress
    private val activeDownloadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Offline caching & Room DB operations
    fun cacheTrack(track: AudioTrack) {
        // Rule 1: Never download live streams
        if (track.isLiveStream) {
            _authStatusMessage.value = "Неможливо завантажити прямий радіоефір"
            return
        }

        // Cancel previous job for the same track if any
        activeDownloadJobs[track.id]?.cancel()

        val job = viewModelScope.launch {
            try {
                // If it's a remote URL, download it to private app storage first
                val localFile = if (!track.isLocal && track.audioUrl.startsWith("http")) {
                    com.musicplayer.android.core.audio.TrackDownloadManager.downloadTrackToPrivateStorage(
                        context = getApplication(),
                        trackId = track.id,
                        audioUrl = track.audioUrl,
                        isLiveStream = track.isLiveStream,
                        onProgress = { progress ->
                            val current = _downloadProgress.value.toMutableMap()
                            current[track.id] = progress
                            _downloadProgress.value = current
                        }
                    )
                } else null

                // Rule 2: If download failed or returned null, DO NOT create a corrupt Room record!
                if (!track.isLocal && track.audioUrl.startsWith("http") && localFile == null) {
                    _authStatusMessage.value = "Не вдалося завантажити трек: помилка мережі"
                    return@launch
                }

                val localPath = localFile?.absolutePath

                db.cachedTrackDao().insertTrack(
                    CachedTrackEntity(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        localFilePath = localPath,
                        originalUrl = track.audioUrl,
                        artworkUrl = track.artworkUrl, // Fix #14: save artworkUrl
                        durationMs = track.durationMs,
                        cachedAtTimestamp = System.currentTimeMillis()
                    )
                )
                _authStatusMessage.value = "Трек збережено офлайн"
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Clean up on cancel
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "Error caching track: ${e.message}", e)
                _authStatusMessage.value = "Помилка завантаження: ${e.message}"
            } finally {
                activeDownloadJobs.remove(track.id)
                val current = _downloadProgress.value.toMutableMap()
                current.remove(track.id)
                _downloadProgress.value = current
            }
        }
        activeDownloadJobs[track.id] = job
    }

    fun cancelDownload(trackId: String) {
        activeDownloadJobs[trackId]?.cancel()
        activeDownloadJobs.remove(trackId)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(trackId)
        _downloadProgress.value = current
    }

    fun removeCachedTrack(trackId: String) {
        viewModelScope.launch {
            try {
                // Step 1: Remove physical file first
                val existing = db.cachedTrackDao().getTrackById(trackId)
                if (existing?.localFilePath != null) {
                    com.musicplayer.android.core.audio.TrackDownloadManager.deleteDownloadedTrack(
                        localFilePath = existing.localFilePath
                    )
                }
                // Step 2: Remove DB record
                db.cachedTrackDao().deleteTrack(trackId)
            } catch (e: Exception) {
                android.util.Log.e("MainPlayerViewModel", "Error removing cached track: ${e.message}", e)
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
        val state = playbackState.value
        val track = state.currentTrack
        if (track != null && state.currentPositionMs > 0L) {
            sessionManager.saveLastPlayedTrack(track, state.currentPositionMs)
        }
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
