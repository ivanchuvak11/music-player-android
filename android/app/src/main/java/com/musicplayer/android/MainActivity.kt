package com.musicplayer.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.calculateSearchRelevanceScore
import com.musicplayer.android.core.audio.searchDeduplicationKey
import com.musicplayer.android.core.audio.toAudioTrack
import com.musicplayer.android.core.network.SoundCloudTrackDto
import com.musicplayer.android.core.network.YouTubeTrackDto
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.components.*
import com.musicplayer.android.ui.screens.*
import com.musicplayer.android.ui.theme.DarkRefTheme

enum class SearchSourceFilter(val label: String) {
    ALL("Загально"),
    LOCAL("На пристрої"),
    YOUTUBE("YouTube Music"),
    SOUNDCLOUD("SoundCloud")
}

enum class LibrarySubTab {
    ON_DEVICE,
    FAVORITES,
    PLAYLISTS
}

enum class TrackSortOrder(val labelResId: Int) {
    DEFAULT(R.string.sort_default),
    TITLE_ASC(R.string.sort_title_asc),
    TITLE_DESC(R.string.sort_title_desc),
    ARTIST_ASC(R.string.sort_artist_asc),
    DURATION_DESC(R.string.sort_duration_desc),
    DURATION_ASC(R.string.sort_duration_asc)
}

fun List<AudioTrack>.sortedByOrder(order: TrackSortOrder): List<AudioTrack> = when (order) {
    TrackSortOrder.DEFAULT -> this
    TrackSortOrder.TITLE_ASC -> this.sortedBy { it.title.lowercase() }
    TrackSortOrder.TITLE_DESC -> this.sortedByDescending { it.title.lowercase() }
    TrackSortOrder.ARTIST_ASC -> this.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    TrackSortOrder.DURATION_DESC -> this.sortedByDescending { it.durationMs }
    TrackSortOrder.DURATION_ASC -> this.sortedBy { it.durationMs }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkRefTheme.BackgroundDark
                ) {
                    PlayerCoreScreen()
                }
            }
        }
    }
}

@Composable
fun PlayerCoreScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: MainPlayerViewModel = viewModel(
        factory = remember {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainPlayerViewModel(app) as T
                }
            }
        }
    )

    // Isolated low-frequency playback states (prevents whole-screen recomposition on position ticks!)
    val currentPlayingTrack by remember(viewModel) {
        viewModel.playbackState.map { it.currentTrack }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.playbackState.value.currentTrack)

    val isPlaybackPlaying by remember(viewModel) {
        viewModel.playbackState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = viewModel.playbackState.value.isPlaying)

    val playbackErrorMessage by remember(viewModel) {
        viewModel.playbackState.map { it.errorMessage }.distinctUntilChanged()
    }.collectAsState(initial = null)

    val localTracks by viewModel.localTracks.collectAsState()
    val trendingOnlineTracks by viewModel.trendingOnlineTracks.collectAsState()
    val searchUiResults by viewModel.searchUiResults.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authStatus by viewModel.authStatusMessage.collectAsState()
    val onlineSearchError by viewModel.onlineSearchError.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val audioEffectsState by viewModel.audioEffectsState.collectAsState()
    val isSleepTimerActive by viewModel.isSleepTimerActive.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val playHistory by viewModel.playHistory.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val isLoadingAutoplay by viewModel.isLoadingAutoplay.collectAsState()

    // Navigation and Dialog States
    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var similarTracksSeedTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var tracksToAddToPlaylist by remember { mutableStateOf<List<AudioTrack>?>(null) }

    // Search and Selection States
    var unifiedSearchQuery by remember { mutableStateOf("") }
    var searchSourceFilter by remember { mutableStateOf(SearchSourceFilter.ALL) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTracks by remember { mutableStateOf(setOf<AudioTrack>()) }
    var currentRadioIndex by remember { mutableStateOf(0) }
    var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var librarySubTab by remember { mutableStateOf(LibrarySubTab.ON_DEVICE) }
    var trackSortOrder by remember { mutableStateOf(TrackSortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFavSortMenu by remember { mutableStateOf(false) }

    val sortedLocalTracks = remember(localTracks, trackSortOrder) {
        localTracks.sortedByOrder(trackSortOrder)
    }
    val sortedFavorites = remember(localFavorites, trackSortOrder) {
        localFavorites.map { it.toAudioTrack() }.sortedByOrder(trackSortOrder)
    }
    val favoriteTrackIds = remember(localFavorites) {
        localFavorites.map { it.id }.toHashSet()
    }

    // Sync radio index
    LaunchedEffect(currentPlayingTrack) {
        val track = currentPlayingTrack
        if (track != null && track.isLiveStream) {
            val stations = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS
            val idx = stations.indexOfFirst { it.name == track.title }
            if (idx >= 0) {
                currentRadioIndex = idx
            }
        }
    }

    // High-performance background search pipeline (computed on Dispatchers.Default, zero UI freeze)
    LaunchedEffect(unifiedSearchQuery, isOnline) {
        viewModel.updateSearchQuery(unifiedSearchQuery)
    }

    // Permission launcher
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val audioGranted = permissionsMap.entries.firstOrNull { it.key.contains("AUDIO") || it.key.contains("STORAGE") }?.value ?: false
        if (audioGranted) {
            viewModel.loadLocalTracks()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        val audioPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, audioPerm) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(audioPerm)
        } else {
            viewModel.loadLocalTracks()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(playbackErrorMessage) {
        val error = playbackErrorMessage
        if (!error.isNullOrBlank()) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    val playOnlineSafely: (() -> Unit) -> Unit = { action ->
        if (isOnline) {
            action()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.offline_error_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val handleTrackPlayPause: (AudioTrack, () -> Unit) -> Unit = { track, defaultStartQueue ->
        if (currentPlayingTrack?.id == track.id) {
            if (isPlaybackPlaying) viewModel.pause() else viewModel.play()
        } else {
            defaultStartQueue()
        }
    }

    val focusManager = LocalFocusManager.current

    BackHandler(enabled = unifiedSearchQuery.isNotEmpty()) {
        unifiedSearchQuery = ""
        searchSourceFilter = SearchSourceFilter.ALL
        focusManager.clearFocus()
    }

    val lastSession = remember { viewModel.getLastPlayedTrack() }
    val rememberedTrack = lastSession?.first
    val rememberedPosition = lastSession?.second ?: 0L
    val currentOrRememberedTrack = currentPlayingTrack ?: rememberedTrack

    val listContentPadding = PaddingValues(
        top = 146.dp,
        bottom = if (currentOrRememberedTrack != null) 154.dp else 84.dp,
        start = 16.dp,
        end = 16.dp
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkRefTheme.BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // LAYER 0: SCROLLABLE LIST CONTENT (Full screen, scrolls under top & bottom bars!)
        Box(modifier = Modifier.fillMaxSize()) {
            // MAIN CONTENT BY SELECTED TAB OR SEARCH
            if (unifiedSearchQuery.isNotBlank()) {
                val filteredLocal = searchUiResults.localTracks
                val filteredCached = searchUiResults.cachedTracks
                val onlineResults = searchUiResults.onlineResults

                val allLocalResults = remember(filteredLocal, filteredCached) {
                    (filteredLocal + filteredCached).distinctBy { it.id }
                }
                val youtubeResults = remember(onlineResults) {
                    onlineResults.filter { it.id.startsWith("youtube_") }
                }
                val soundCloudResults = remember(onlineResults) {
                    onlineResults.filter { it.id.startsWith("soundcloud_") }
                }

                val currentDisplayCount = when (searchSourceFilter) {
                    SearchSourceFilter.ALL -> filteredLocal.size + filteredCached.size + onlineResults.size
                    SearchSourceFilter.LOCAL -> allLocalResults.size
                    SearchSourceFilter.YOUTUBE -> youtubeResults.size
                    SearchSourceFilter.SOUNDCLOUD -> soundCloudResults.size
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = listContentPadding
                ) {
                    if (searchSuggestions.isNotEmpty()) {
                        item(key = "search_suggestions_card") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = DarkRefTheme.SurfaceCard.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    searchSuggestions.take(5).forEach { suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    unifiedSearchQuery = suggestion
                                                    viewModel.updateSearchQuery(suggestion)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Search,
                                                contentDescription = null,
                                                tint = DarkRefTheme.TextSecondary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = suggestion,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = {
                                                    unifiedSearchQuery = suggestion
                                                    viewModel.updateSearchQuery(suggestion)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.NorthWest,
                                                    contentDescription = "Apply suggestion",
                                                    tint = DarkRefTheme.TextSecondary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "search_summary_and_filters") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Знайдено: $currentDisplayCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SearchFilterChip(
                                    label = "Загально",
                                    isSelected = searchSourceFilter == SearchSourceFilter.ALL,
                                    selectedBgColor = DarkRefTheme.AccentMint,
                                    selectedTextColor = DarkRefTheme.BackgroundDark,
                                    unselectedBorderColor = Color.White.copy(alpha = 0.18f),
                                    unselectedTextColor = DarkRefTheme.TextSecondary,
                                    onClick = { searchSourceFilter = SearchSourceFilter.ALL }
                                )

                                SearchFilterChip(
                                    label = "На пристрої",
                                    isSelected = searchSourceFilter == SearchSourceFilter.LOCAL,
                                    selectedBgColor = Color(0xFFFFD54F),
                                    selectedTextColor = Color(0xFF261D00),
                                    unselectedBorderColor = Color(0xFFFFD54F).copy(alpha = 0.45f),
                                    unselectedTextColor = Color(0xFFFFD54F),
                                    onClick = { searchSourceFilter = SearchSourceFilter.LOCAL }
                                )

                                SearchFilterChip(
                                    label = "YouTube Music",
                                    isSelected = searchSourceFilter == SearchSourceFilter.YOUTUBE,
                                    selectedBgColor = Color(0xFFFF4E4E),
                                    selectedTextColor = Color.White,
                                    unselectedBorderColor = Color(0xFFFF4E4E).copy(alpha = 0.45f),
                                    unselectedTextColor = Color(0xFFFF6B6B),
                                    onClick = { searchSourceFilter = SearchSourceFilter.YOUTUBE }
                                )

                                SearchFilterChip(
                                    label = "SoundCloud",
                                    isSelected = searchSourceFilter == SearchSourceFilter.SOUNDCLOUD,
                                    selectedBgColor = Color(0xFFFF7700),
                                    selectedTextColor = Color.White,
                                    unselectedBorderColor = Color(0xFFFF7700).copy(alpha = 0.45f),
                                    unselectedTextColor = Color(0xFFFF9436),
                                    onClick = { searchSourceFilter = SearchSourceFilter.SOUNDCLOUD }
                                )
                            }
                        }
                    }

                    when (searchSourceFilter) {
                        SearchSourceFilter.ALL -> {
                            if (filteredLocal.isNotEmpty()) {
                                item(key = "search_header_local") {
                                    Text(
                                        text = "${stringResource(R.string.header_on_device)} (${filteredLocal.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkRefTheme.TextSecondary
                                    )
                                }
                                items(
                                    items = filteredLocal,
                                    key = { "s_loc_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                viewModel.playLocalTrack(track)
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onPlaySimilar = {
                                            similarTracksSeedTrack = track
                                            viewModel.loadSimilarTracks(track)
                                        },
                                        onTrackCardClick = {
                                            if (!isCurrent) {
                                                viewModel.playLocalTrack(track)
                                            }
                                            showNowPlayingSheet = true
                                        }
                                    )
                                }
                            }

                            if (filteredCached.isNotEmpty()) {
                                item(key = "search_header_cached") {
                                    Text(
                                        text = "💾 Кешовані треки (${filteredCached.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkRefTheme.TextSecondary
                                    )
                                }
                                items(
                                    items = filteredCached,
                                    key = { "s_cac_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                viewModel.playLocalTrack(track)
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onTrackCardClick = {
                                            if (!isCurrent) {
                                                viewModel.playLocalTrack(track)
                                            }
                                            showNowPlayingSheet = true
                                        }
                                    )
                                }
                            }

                            if (onlineResults.isNotEmpty()) {
                                item(key = "search_header_online") {
                                    Text(
                                        text = "🌐 ${stringResource(R.string.tab_online)} (${onlineResults.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkRefTheme.TextSecondary
                                    )
                                }
                                items(
                                    items = onlineResults,
                                    key = { "s_onl_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                playOnlineSafely {
                                                    val idx = onlineResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(onlineResults, idx)
                                                }
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onTrackCardClick = {
                                            playOnlineSafely {
                                                if (!isCurrent) {
                                                    val idx = onlineResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(onlineResults, idx)
                                                }
                                                showNowPlayingSheet = true
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        SearchSourceFilter.LOCAL -> {
                            if (allLocalResults.isNotEmpty()) {
                                item(key = "search_header_local_filtered") {
                                    Text(
                                        text = "${stringResource(R.string.header_on_device)} (${allLocalResults.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                                items(
                                    items = allLocalResults,
                                    key = { "s_loc_f_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                viewModel.playQueue(allLocalResults, allLocalResults.indexOf(track).coerceAtLeast(0))
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onTrackCardClick = {
                                            if (!isCurrent) {
                                                viewModel.playQueue(allLocalResults, allLocalResults.indexOf(track).coerceAtLeast(0))
                                            }
                                            showNowPlayingSheet = true
                                        }
                                    )
                                }
                            } else {
                                item(key = "search_empty_local") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "На пристрої не знайдено треків за запитом «$unifiedSearchQuery»",
                                            color = DarkRefTheme.TextSecondary,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        SearchSourceFilter.YOUTUBE -> {
                            if (youtubeResults.isNotEmpty()) {
                                item(key = "search_header_yt_filtered") {
                                    Text(
                                        text = "YouTube Music (${youtubeResults.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF6B6B)
                                    )
                                }
                                items(
                                    items = youtubeResults,
                                    key = { "s_yt_f_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                playOnlineSafely {
                                                    val idx = youtubeResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(youtubeResults, idx)
                                                }
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onTrackCardClick = {
                                            playOnlineSafely {
                                                if (!isCurrent) {
                                                    val idx = youtubeResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(youtubeResults, idx)
                                                }
                                                showNowPlayingSheet = true
                                            }
                                        }
                                    )
                                }
                            } else {
                                item(key = "search_empty_yt") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "В YouTube Music не знайдено треків за запитом «$unifiedSearchQuery»",
                                            color = DarkRefTheme.TextSecondary,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        SearchSourceFilter.SOUNDCLOUD -> {
                            if (soundCloudResults.isNotEmpty()) {
                                item(key = "search_header_sc_filtered") {
                                    Text(
                                        text = "SoundCloud (${soundCloudResults.size}):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9436)
                                    )
                                }
                                items(
                                    items = soundCloudResults,
                                    key = { "s_sc_f_${it.id}" },
                                    contentType = { "track" }
                                ) { track ->
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                playOnlineSafely {
                                                    val idx = soundCloudResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(soundCloudResults, idx)
                                                }
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                        onTrackCardClick = {
                                            playOnlineSafely {
                                                if (!isCurrent) {
                                                    val idx = soundCloudResults.indexOf(track).coerceAtLeast(0)
                                                    viewModel.playQueue(soundCloudResults, idx)
                                                }
                                                showNowPlayingSheet = true
                                            }
                                        }
                                    )
                                }
                            } else {
                                item(key = "search_empty_sc") {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "В SoundCloud не знайдено треків за запитом «$unifiedSearchQuery»",
                                            color = DarkRefTheme.TextSecondary,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (onlineSearchError != null && onlineResults.isEmpty()) {
                        item(key = "search_online_error_banner") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF2A1616),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Онлайн-пошук недоступний",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF6B6B),
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = onlineSearchError ?: "Не вдалося з'єднатися з сервером.",
                                        color = DarkRefTheme.TextSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { showSettingsDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkRefTheme.SurfaceCardElevated),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Налаштування сервера ⚙️", color = DarkRefTheme.AccentMint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                when (currentTab) {
                    NavigationTab.HOME -> {
                        // TAB 1: HOME (Featured releases with big frosted cards like reference photo)
                        val featuredTracks = remember(trendingOnlineTracks, localTracks) {
                            if (trendingOnlineTracks.isNotEmpty()) {
                                (trendingOnlineTracks.take(12) + localTracks.take(4))
                            } else {
                                localTracks.take(6)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = listContentPadding
                        ) {
                            // Section: New Releases / Featured Cards
                            if (featuredTracks.isNotEmpty()) {
                                item(key = "home_featured_header") {
                                    Text(
                                        text = "Популярне зараз",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = DarkRefTheme.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = featuredTracks,
                                            key = { "feat_${it.id}" },
                                            contentType = { "featured" }
                                        ) { track ->
                                            val isCurrent = currentPlayingTrack?.id == track.id
                                            val isPlaying = isCurrent && isPlaybackPlaying
                                            FeaturedTrackCard(
                                                track = track,
                                                onPlay = {
                                                    handleTrackPlayPause(track) {
                                                        playOnlineSafely {
                                                            val idx = featuredTracks.indexOf(track).coerceAtLeast(0)
                                                            viewModel.playQueue(featuredTracks, idx)
                                                            showNowPlayingSheet = true
                                                        }
                                                    }
                                                },
                                                isCurrentTrack = isCurrent,
                                                isPlayingThisTrack = isPlaying
                                            )
                                        }
                                    }
                                }
                            }

                            // Live Radio preview bar
                            item(key = "home_radio_bar") {
                                RadioBar(
                                    stations = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS,
                                    currentRadioIndex = currentRadioIndex,
                                    currentTrack = currentPlayingTrack,
                                    isPlaying = isPlaybackPlaying,
                                    viewModel = viewModel,
                                    onRadioIndexChange = { currentRadioIndex = it },
                                    playOnlineSafely = playOnlineSafely
                                )
                            }

                            // Recently Played
                            if (playHistory.isNotEmpty()) {
                                item(key = "home_history_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.header_history),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = DarkRefTheme.TextPrimary
                                        )
                                        TextButton(onClick = { viewModel.clearPlayHistory() }) {
                                            Text(stringResource(R.string.btn_clear_history), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                        }
                                    }
                                }
                                items(
                                    items = playHistory.take(4),
                                    key = { "hist_${it.trackId}" },
                                    contentType = { "track" }
                                ) { histItem ->
                                    val track = histItem.toAudioTrack()
                                    val isCurrent = currentPlayingTrack?.id == track.id
                                    val isPlaying = isCurrent && isPlaybackPlaying
                                    TrackItemRow(
                                        track = track,
                                        onPlay = {
                                            handleTrackPlayPause(track) {
                                                viewModel.playTrack(track)
                                            }
                                        },
                                        isFavorite = track.id in favoriteTrackIds,
                                        isCurrentTrack = isCurrent,
                                        isPlayingThisTrack = isPlaying,
                                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                        onTrackCardClick = {
                                            if (!isCurrent) {
                                                viewModel.playTrack(track)
                                            }
                                            showNowPlayingSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    NavigationTab.RADIO -> {
                        // TAB 2: RADIO
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = listContentPadding
                        ) {
                            item(key = "radio_title_header") {
                                Text(
                                    text = "Онлайн-Радіостанції",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = DarkRefTheme.TextPrimary
                                )
                            }
                            item(key = "radio_featured_bar") {
                                RadioBar(
                                    stations = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS,
                                    currentRadioIndex = currentRadioIndex,
                                    currentTrack = currentPlayingTrack,
                                    isPlaying = isPlaybackPlaying,
                                    viewModel = viewModel,
                                    onRadioIndexChange = { currentRadioIndex = it },
                                    playOnlineSafely = playOnlineSafely
                                )
                            }

                            items(
                                items = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS,
                                key = { "radio_${it.name}" },
                                contentType = { "station" }
                            ) { station ->
                                val isStationCurrent = currentPlayingTrack?.title == station.name
                                val isStationPlaying = isStationCurrent && isPlaybackPlaying
                                TrackItemRow(
                                    track = AudioTrack.fromRadio(station),
                                    onPlay = {
                                        playOnlineSafely {
                                            if (isStationCurrent) {
                                                if (isPlaybackPlaying) viewModel.pause() else viewModel.play()
                                            } else {
                                                viewModel.playRadioStation(station)
                                            }
                                        }
                                    },
                                    isCurrentTrack = isStationCurrent,
                                    isPlayingThisTrack = isStationPlaying
                                )
                            }
                        }
                    }

                    NavigationTab.LIBRARY -> {
                        // TAB 3: LIBRARY (3 Sub-categories: На пристрої, Улюблені, Плейлісти)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = listContentPadding
                        ) {
                            // Sub-category selector chips (default: "На пристрої" per user request)
                            item(key = "library_subtabs_selector") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val subTabs = listOf(
                                        Triple(LibrarySubTab.ON_DEVICE, "На пристрої (${localTracks.size})", Icons.Rounded.PhoneAndroid),
                                        Triple(LibrarySubTab.FAVORITES, "Улюблені (${localFavorites.size})", Icons.Rounded.Favorite),
                                        Triple(LibrarySubTab.PLAYLISTS, "Плейлісти (${localPlaylists.size})", Icons.AutoMirrored.Rounded.PlaylistPlay)
                                    )
                                    subTabs.forEach { (subTab, title, icon) ->
                                        val isSelected = librarySubTab == subTab
                                        Surface(
                                            onClick = { librarySubTab = subTab },
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isSelected) DarkRefTheme.AccentMint.copy(alpha = 0.20f) else DarkRefTheme.SurfaceCardElevated,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) DarkRefTheme.AccentMint.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.08f)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = title,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            when (librarySubTab) {
                                LibrarySubTab.ON_DEVICE -> {
                                    // Section: Local tracks on device
                                    item(key = "library_on_device_header") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${stringResource(R.string.header_on_device)} (${localTracks.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = DarkRefTheme.TextPrimary
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Sort Button with DropdownMenu
                                            Box {
                                                Surface(
                                                    onClick = { showSortMenu = true },
                                                    shape = RoundedCornerShape(20.dp),
                                                    color = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint.copy(alpha = 0.18f) else DarkRefTheme.SurfaceCardElevated,
                                                    border = BorderStroke(1.dp, if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                                                            contentDescription = stringResource(R.string.sort_button),
                                                            tint = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = stringResource(trackSortOrder.labelResId),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary
                                                        )
                                                    }
                                                }

                                                DropdownMenu(
                                                    expanded = showSortMenu,
                                                    onDismissRequest = { showSortMenu = false },
                                                    modifier = Modifier.background(DarkRefTheme.SurfaceCardElevated)
                                                ) {
                                                    TrackSortOrder.values().forEach { order ->
                                                        val isCurrent = trackSortOrder == order
                                                        DropdownMenuItem(
                                                            text = {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                ) {
                                                                    if (isCurrent) {
                                                                        Icon(
                                                                            imageVector = Icons.Rounded.Check,
                                                                            contentDescription = null,
                                                                            tint = DarkRefTheme.AccentMint,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    } else {
                                                                        Spacer(modifier = Modifier.size(16.dp))
                                                                    }
                                                                    Text(
                                                                        text = stringResource(order.labelResId),
                                                                        color = if (isCurrent) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary,
                                                                        fontSize = 13.sp,
                                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                                    )
                                                                }
                                                            },
                                                            onClick = {
                                                                trackSortOrder = order
                                                                showSortMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Multi-select toggle button
                                            Surface(
                                                onClick = {
                                                    isMultiSelectMode = !isMultiSelectMode
                                                    if (!isMultiSelectMode) selectedTracks = emptySet()
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (isMultiSelectMode) DarkRefTheme.AccentMint.copy(alpha = 0.18f) else DarkRefTheme.SurfaceCardElevated,
                                                border = BorderStroke(1.dp, if (isMultiSelectMode) DarkRefTheme.AccentMint.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isMultiSelectMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                                                        contentDescription = null,
                                                        tint = if (isMultiSelectMode) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isMultiSelectMode) stringResource(R.string.btn_close_selection) else stringResource(R.string.btn_select_multiple),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isMultiSelectMode) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (isMultiSelectMode) {
                                        item(key = "library_multiselect_bar") {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp),
                                                color = DarkRefTheme.SurfaceCardElevated,
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = DarkRefTheme.AccentMint.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Обрано: ${selectedTracks.size}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = DarkRefTheme.AccentMint,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        TextButton(
                                                            onClick = {
                                                                selectedTracks = if (selectedTracks.size == localTracks.size) emptySet() else localTracks.toSet()
                                                            },
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (selectedTracks.size == localTracks.size) stringResource(R.string.btn_deselect_all) else stringResource(R.string.btn_select_all),
                                                                fontSize = 12.sp,
                                                                color = DarkRefTheme.TextPrimary
                                                            )
                                                        }

                                                        Button(
                                                            enabled = selectedTracks.isNotEmpty(),
                                                            onClick = { tracksToAddToPlaylist = selectedTracks.toList() },
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = DarkRefTheme.AccentMint,
                                                                disabledContainerColor = DarkRefTheme.SurfaceCard
                                                            ),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                        ) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                                    contentDescription = null,
                                                                    tint = if (selectedTracks.isNotEmpty()) DarkRefTheme.BackgroundDark else DarkRefTheme.TextSecondary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "${stringResource(R.string.btn_add_to_playlist)} (${selectedTracks.size})",
                                                                    fontSize = 12.sp,
                                                                    color = if (selectedTracks.isNotEmpty()) DarkRefTheme.BackgroundDark else DarkRefTheme.TextSecondary,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (localTracks.isEmpty()) {
                                        item(key = "empty_local_tracks") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "На пристрої не знайдено аудіофайлів.\nПеревірте дозволи на доступ до сховища.",
                                                    color = DarkRefTheme.TextSecondary,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(
                                            items = sortedLocalTracks,
                                            key = { "loc_${it.id}" },
                                            contentType = { "track" }
                                        ) { track ->
                                            val isSelected = selectedTracks.contains(track)
                                            val isCurrent = currentPlayingTrack?.id == track.id
                                            val isPlaying = isCurrent && isPlaybackPlaying
                                            TrackItemRow(
                                                track = track,
                                                onPlay = {
                                                    if (isMultiSelectMode) {
                                                        selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                                    } else {
                                                        handleTrackPlayPause(track) {
                                                            viewModel.playQueue(sortedLocalTracks, sortedLocalTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                                        }
                                                    }
                                                },
                                                isSelectionMode = isMultiSelectMode,
                                                isSelected = isSelected,
                                                isFavorite = track.id in favoriteTrackIds,
                                                isCurrentTrack = isCurrent,
                                                isPlayingThisTrack = isPlaying,
                                                onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                                onToggleSelect = {
                                                    selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                                                },
                                                onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                                onPlaySimilar = {
                                                    similarTracksSeedTrack = track
                                                    viewModel.loadSimilarTracks(track)
                                                },
                                                onTrackCardClick = {
                                                    if (!isCurrent) {
                                                        viewModel.playQueue(sortedLocalTracks, sortedLocalTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                                    }
                                                    showNowPlayingSheet = true
                                                }
                                            )
                                        }
                                    }
                                }

                                LibrarySubTab.FAVORITES -> {
                                    // Section: Favorites
                                    item(key = "library_favorites_header") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${stringResource(R.string.header_favorites)} (${localFavorites.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = DarkRefTheme.TextPrimary
                                        )

                                        if (localFavorites.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Sort Button with DropdownMenu for Favorites
                                                Box {
                                                    Surface(
                                                        onClick = { showFavSortMenu = true },
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint.copy(alpha = 0.18f) else DarkRefTheme.SurfaceCardElevated,
                                                        border = BorderStroke(1.dp, if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                                                contentDescription = stringResource(R.string.sort_button),
                                                                tint = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Text(
                                                                text = stringResource(trackSortOrder.labelResId),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                color = if (trackSortOrder != TrackSortOrder.DEFAULT) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary
                                                            )
                                                        }
                                                    }

                                                    DropdownMenu(
                                                        expanded = showFavSortMenu,
                                                        onDismissRequest = { showFavSortMenu = false },
                                                        modifier = Modifier.background(DarkRefTheme.SurfaceCardElevated)
                                                    ) {
                                                        TrackSortOrder.values().forEach { order ->
                                                            val isCurrent = trackSortOrder == order
                                                            DropdownMenuItem(
                                                                text = {
                                                                    Row(
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        if (isCurrent) {
                                                                            Icon(
                                                                                imageVector = Icons.Rounded.Check,
                                                                                contentDescription = null,
                                                                                tint = DarkRefTheme.AccentMint,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                        } else {
                                                                            Spacer(modifier = Modifier.size(16.dp))
                                                                        }
                                                                        Text(
                                                                            text = stringResource(order.labelResId),
                                                                            color = if (isCurrent) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary,
                                                                            fontSize = 13.sp,
                                                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                                                        )
                                                                    }
                                                                },
                                                                onClick = {
                                                                    trackSortOrder = order
                                                                    showFavSortMenu = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                TextButton(onClick = {
                                                    viewModel.playQueue(sortedFavorites, 0)
                                                }) {
                                                    Text(stringResource(R.string.btn_play_all), fontSize = 12.sp, color = DarkRefTheme.AccentMint, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    if (localFavorites.isEmpty()) {
                                        item(key = "empty_favorites") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Немає улюблених треків.\nНатисніть ❤️ біля будь-якої пісні!",
                                                    color = DarkRefTheme.TextSecondary,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(
                                            items = sortedFavorites,
                                            key = { "fav_${it.id}" },
                                            contentType = { "track" }
                                        ) { track ->
                                            val isCurrent = currentPlayingTrack?.id == track.id
                                            val isPlaying = isCurrent && isPlaybackPlaying
                                            TrackItemRow(
                                                track = track,
                                                onPlay = {
                                                    handleTrackPlayPause(track) {
                                                        viewModel.playQueue(sortedFavorites, sortedFavorites.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                                    }
                                                },
                                                isFavorite = true,
                                                isCurrentTrack = isCurrent,
                                                isPlayingThisTrack = isPlaying,
                                                onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                                onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                                                onPlaySimilar = {
                                                    similarTracksSeedTrack = track
                                                    viewModel.loadSimilarTracks(track)
                                                },
                                                onTrackCardClick = {
                                                    if (!isCurrent) {
                                                        viewModel.playQueue(sortedFavorites, sortedFavorites.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                                                    }
                                                    showNowPlayingSheet = true
                                                }
                                            )
                                        }
                                    }
                                }

                                LibrarySubTab.PLAYLISTS -> {
                                    // Section: Playlists
                                    item(key = "library_playlists_header") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${stringResource(R.string.header_playlists)} (${localPlaylists.size})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = DarkRefTheme.TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = newPlaylistNameInput,
                                                onValueChange = { newPlaylistNameInput = it },
                                                placeholder = { Text(stringResource(R.string.new_playlist_hint), fontSize = 12.sp, color = DarkRefTheme.TextSecondary) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = DarkRefTheme.TextPrimary,
                                                    unfocusedTextColor = DarkRefTheme.TextPrimary
                                                )
                                            )
                                            Button(
                                                onClick = {
                                                    if (newPlaylistNameInput.isNotBlank()) {
                                                        val name = newPlaylistNameInput.trim()
                                                        viewModel.createPlaylist(name)
                                                        newPlaylistNameInput = ""
                                                        Toast.makeText(context, "Плейліст «$name» створено!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                                            ) {
                                                Text(stringResource(R.string.btn_create), fontSize = 11.sp, color = DarkRefTheme.BackgroundDark, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    if (localPlaylists.isEmpty()) {
                                        item(key = "empty_playlists") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 40.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "Немає створених плейлістів.\nВведіть назву вище та натисніть «Створити»!",
                                                    color = DarkRefTheme.TextSecondary,
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    } else {
                                        items(
                                            items = localPlaylists,
                                            key = { "pl_${it.id}" },
                                            contentType = { "playlist" }
                                        ) { pl ->
                                            LocalPlaylistCard(
                                                playlist = pl,
                                                viewModel = viewModel,
                                                isExpanded = expandedPlaylistId == pl.id,
                                                onToggleExpand = {
                                                    expandedPlaylistId = if (expandedPlaylistId == pl.id) null else pl.id
                                                },
                                                onDelete = {
                                                    viewModel.deleteLocalPlaylist(pl.id)
                                                    Toast.makeText(context, "Плейліст видалено", Toast.LENGTH_SHORT).show()
                                                },
                                                onPlayAll = { viewModel.playLocalPlaylist(pl.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NavigationTab.PROFILE -> {
                        // TAB 4: PROFILE & QUICK SETTINGS
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = listContentPadding
                        ) {
                            item {
                                Text(
                                    text = "Мій Профіль та Налаштування",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = DarkRefTheme.TextPrimary
                                )
                            }

                            // Account card
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DarkRefTheme.SurfaceCard)
                                        .padding(16.dp)
                                    ) {
                                    Text(
                                        text = stringResource(R.string.account_title),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = DarkRefTheme.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (currentUser != null) "${stringResource(R.string.account_logged_in_as)} $currentUser" else "Ви увійшли як гість",
                                        fontSize = 13.sp,
                                        color = DarkRefTheme.TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { showAccountDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                                    ) {
                                        Text(
                                            text = if (currentUser != null) "Керувати акаунтом" else "Увійти в акаунт",
                                            color = DarkRefTheme.BackgroundDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Quick Settings & Equalizer launch cards
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        onClick = { showEqualizerDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = DarkRefTheme.SurfaceCardElevated,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.weight(1f).height(52.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Equalizer,
                                                contentDescription = null,
                                                tint = DarkRefTheme.AccentMint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Еквалайзер",
                                                color = DarkRefTheme.TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = { showSettingsDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        color = DarkRefTheme.SurfaceCardElevated,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                        modifier = Modifier.weight(1f).height(52.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bedtime,
                                                contentDescription = null,
                                                tint = DarkRefTheme.AccentMint,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Таймер сну і Кеш",
                                                color = DarkRefTheme.TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Autoplay toggle card
                            item {
                                Surface(
                                    onClick = { viewModel.toggleAutoplay() },
                                    shape = RoundedCornerShape(16.dp),
                                    color = DarkRefTheme.SurfaceCardElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (autoplayEnabled) DarkRefTheme.AccentMint.copy(alpha = 0.5f)
                                        else Color.White.copy(alpha = 0.08f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                            contentDescription = null,
                                            tint = if (autoplayEnabled) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Автопрогравання схожих треків",
                                                color = DarkRefTheme.TextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (autoplayEnabled)
                                                    "Увімкнено — коли черга закінчиться, грають схожі пісні"
                                                else
                                                    "Вимкнено — плеєр зупиниться після останньої пісні",
                                                color = DarkRefTheme.TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (isLoadingAutoplay) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = DarkRefTheme.AccentMint
                                            )
                                        } else {
                                            androidx.compose.material3.Switch(
                                                checked = autoplayEnabled,
                                                onCheckedChange = { viewModel.setAutoplayEnabled(it) },
                                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = DarkRefTheme.AccentMint,
                                                    uncheckedThumbColor = DarkRefTheme.TextSecondary,
                                                    uncheckedTrackColor = DarkRefTheme.SurfaceCard
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // LAYER 1: TOP HEADER OVERLAY (AppTopBar + OutlinedTextField + downwards gradient fade)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to DarkRefTheme.BackgroundDark,
                        0.52f to DarkRefTheme.BackgroundDark,
                        0.76f to DarkRefTheme.BackgroundDark.copy(alpha = 0.60f),
                        0.92f to Color.Transparent,
                        1.0f to Color.Transparent
                    )
                )
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppTopBar(
                    currentUser = currentUser,
                    isOnline = isOnline,
                    onAccountClick = { showAccountDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = unifiedSearchQuery,
                    onValueChange = { unifiedSearchQuery = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.search_placeholder),
                            fontSize = 13.sp,
                            color = DarkRefTheme.TextSecondary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (unifiedSearchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                unifiedSearchQuery = ""
                                searchSourceFilter = SearchSourceFilter.ALL
                                viewModel.searchYouTube("")
                                viewModel.searchSoundCloud("")
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = DarkRefTheme.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkRefTheme.TextPrimary,
                        unfocusedTextColor = DarkRefTheme.TextPrimary,
                        focusedContainerColor = DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.94f),
                        unfocusedContainerColor = DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.94f),
                        focusedBorderColor = DarkRefTheme.AccentMint,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = DarkRefTheme.AccentMint
                    )
                )
            }
        }

        // LAYER 2: BOTTOM CONTAINER OVERLAY (MiniPlayer + BottomNavBar + upwards gradient fade)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.22f to Color.Transparent,
                        0.42f to DarkRefTheme.BackgroundDark.copy(alpha = 0.68f),
                        0.60f to DarkRefTheme.BackgroundDark.copy(alpha = 0.95f),
                        0.75f to DarkRefTheme.BackgroundDark,
                        1.0f to DarkRefTheme.BackgroundDark
                    )
                )
                .padding(top = 19.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mini Player anchored right above the navigation bar
                if (currentOrRememberedTrack != null) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        IsolatedMiniPlayer(
                            viewModel = viewModel,
                            currentOrRememberedTrack = currentOrRememberedTrack,
                            rememberedPosition = rememberedPosition,
                            isSleepTimerActive = isSleepTimerActive,
                            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                            isFavorite = currentOrRememberedTrack.id in favoriteTrackIds,
                            onExpandNowPlaying = { showNowPlayingSheet = true }
                        )
                    }
                }

                // Bottom Navigation Bar with 4 tabs
                BottomNavBar(
                    selectedTab = currentTab,
                    onTabSelected = {
                        currentTab = it
                        unifiedSearchQuery = ""
                        searchSourceFilter = SearchSourceFilter.ALL
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }

    // MODAL DIALOGS
    if (showAccountDialog) {
        AuthDialog(
            viewModel = viewModel,
            currentUser = currentUser,
            authStatus = authStatus,
            onDismiss = { showAccountDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            viewModel = viewModel,
            audioEffectsState = audioEffectsState,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            isSleepTimerActive = isSleepTimerActive,
            sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showNowPlayingSheet) {
        IsolatedNowPlayingSheet(
            viewModel = viewModel,
            localFavorites = localFavorites.map { it.toAudioTrack() },
            onDismiss = { showNowPlayingSheet = false },
            onAddToPlaylist = { track -> tracksToAddToPlaylist = listOf(track) }
        )
    }

    if (tracksToAddToPlaylist != null) {
        AddToPlaylistDialog(
            tracks = tracksToAddToPlaylist!!,
            localPlaylists = localPlaylists,
            viewModel = viewModel,
            onDismiss = { tracksToAddToPlaylist = null }
        )
    }

    if (similarTracksSeedTrack != null) {
        SimilarTracksDialog(
            seedTrack = similarTracksSeedTrack!!,
            viewModel = viewModel,
            onDismiss = { similarTracksSeedTrack = null }
        )
    }
}

@Composable
private fun IsolatedMiniPlayer(
    viewModel: MainPlayerViewModel,
    currentOrRememberedTrack: AudioTrack,
    rememberedPosition: Long,
    isSleepTimerActive: Boolean,
    sleepTimerRemainingSeconds: Long,
    isFavorite: Boolean,
    onExpandNowPlaying: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    MiniPlayer(
        playbackState = playbackState,
        currentOrRememberedTrack = currentOrRememberedTrack,
        rememberedPosition = rememberedPosition,
        isSleepTimerActive = isSleepTimerActive,
        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
        onExpandNowPlaying = onExpandNowPlaying,
        onTogglePlayPause = {
            if (playbackState.isPlaying) viewModel.pause() else viewModel.play()
        },
        onPrevious = { viewModel.playPrevious() },
        onNext = { viewModel.playNext() },
        onToggleFavorite = { viewModel.toggleLocalFavorite(currentOrRememberedTrack) },
        isFavorite = isFavorite
    )
}

@Composable
private fun IsolatedNowPlayingSheet(
    viewModel: MainPlayerViewModel,
    localFavorites: List<AudioTrack>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    NowPlayingSheet(
        playbackState = playbackState,
        viewModel = viewModel,
        localFavorites = localFavorites,
        onDismiss = onDismiss,
        onAddToPlaylist = onAddToPlaylist
    )
}

@Composable
private fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    selectedBgColor: Color,
    selectedTextColor: Color,
    unselectedBorderColor: Color,
    unselectedTextColor: Color,
    count: Int? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) selectedBgColor else DarkRefTheme.SurfaceCardElevated,
        border = BorderStroke(1.dp, if (isSelected) selectedBgColor else unselectedBorderColor),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val text = if (count != null && count > 0) "$label ($count)" else label
            Text(
                text = text,
                color = if (isSelected) selectedTextColor else unselectedTextColor,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

