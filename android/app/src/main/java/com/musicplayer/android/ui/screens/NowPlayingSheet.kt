package com.musicplayer.android.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import coil.compose.SubcomposeAsyncImage
import com.musicplayer.android.R
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.audio.areTrackIdsEqual
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.components.TrackArtwork
import com.musicplayer.android.ui.components.formatTrackDuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PageSize
import androidx.compose.ui.platform.LocalConfiguration
import com.musicplayer.android.ui.theme.DarkRefTheme
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    viewModel: MainPlayerViewModel,
    localFavorites: List<AudioTrack>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Responsive card size dynamically tuned for Pixel 8 & all resolutions
    val cardSize = (screenWidth * 0.60f).coerceIn(210.dp, 252.dp)
    val horizontalPadding = (screenWidth - cardSize) / 2

    val lastSession = remember { viewModel.getLastPlayedTrack() }
    val activeTrack = playbackState.currentTrack ?: lastSession?.first ?: return
    val totalDuration = if (playbackState.durationMs > 0L) playbackState.durationMs else activeTrack.durationMs

    var userSeekingPosition by remember { mutableStateOf<Float?>(null) }
    val currentPos = userSeekingPosition ?: (
        if (playbackState.currentTrack != null) playbackState.currentPositionMs.toFloat()
        else (lastSession?.second ?: 0L).toFloat()
    )
    val isFav = localFavorites.any { areTrackIdsEqual(it.id, activeTrack.id) }

    // Queue for carousel navigation
    val currentQueue = remember(playbackState.queue, activeTrack) {
        if (playbackState.queue.isNotEmpty()) playbackState.queue else listOf(activeTrack)
    }
    val activeTrackIndex = currentQueue.indexOfFirst { areTrackIdsEqual(it.id, activeTrack.id) }.coerceAtLeast(0)
    val upcomingQueueTracks = remember(currentQueue, activeTrackIndex) {
        if (activeTrackIndex in currentQueue.indices && activeTrackIndex < currentQueue.lastIndex) {
            currentQueue.drop(activeTrackIndex + 1)
        } else {
            emptyList()
        }
    }

    val pagerState = key(currentQueue) {
        rememberPagerState(
            initialPage = activeTrackIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0)),
            pageCount = { currentQueue.size }
        )
    }

    val density = LocalDensity.current
    val isShuffle = playbackState.shuffleModeEnabled
    // Smooth animated exit and entry for left/right peek covers when shuffle button is toggled
    val shuffleExitProgress by animateFloatAsState(
        targetValue = if (isShuffle) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "ShuffleExitProgress"
    )
    var lastTrackId by remember { mutableStateOf(activeTrack.id) }
    var isShufflingAnimation by remember { mutableStateOf(false) }
    var shuffleRollCovers by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    val wheelProgress = remember { Animatable(0f) }
    val extraSideOffset = remember { Animatable(0f) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    val audioEffectsState by viewModel.audioEffectsState.collectAsState()
    val cachedTracksList by viewModel.cachedTracks.collectAsState()
    val downloadProgressMap by viewModel.downloadProgress.collectAsState()
    val isTrackCached = remember(activeTrack.id, cachedTracksList) {
        cachedTracksList.any { areTrackIdsEqual(it.id, activeTrack.id) }
    }
    val currentDownloadPercent = downloadProgressMap[activeTrack.id]
    val sheetScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Smooth 3-phase circular arc roll when track changes under shuffle:
    // Phase 1: Side covers quickly slide in from behind screen edges
    // Phase 2: Carousel rolls along circular arc to target track
    // Phase 3: Remaining side covers slide out past screen edges, leaving active track centered
    LaunchedEffect(activeTrack.id) {
        if (lastTrackId != activeTrack.id) {
            val prevId = lastTrackId
            lastTrackId = activeTrack.id
            if (isShuffle && currentQueue.size > 1 && prevId.isNotBlank()) {
                val prevTrack = currentQueue.firstOrNull { areTrackIdsEqual(it.id, prevId) } ?: activeTrack
                val pool = currentQueue.filter { !areTrackIdsEqual(it.id, activeTrack.id) && !areTrackIdsEqual(it.id, prevTrack.id) }
                val intermediate = if (pool.size >= 2) {
                    pool.shuffled().take(2)
                } else if (pool.isNotEmpty()) {
                    listOf(pool.random(), pool.random())
                } else {
                    listOf(activeTrack, prevTrack)
                }
                val rollList = listOf(prevTrack) + intermediate + listOf(activeTrack)
                shuffleRollCovers = rollList

                val maxShift = with(density) { screenWidth.toPx() * 0.85f }

                // 1. Initial state: prevTrack centered (rel=0), side covers placed completely off-screen
                wheelProgress.snapTo(0f)
                extraSideOffset.snapTo(maxShift)
                isShufflingAnimation = true

                // 2. Entrance: side covers quickly emerge/slide in from behind screen edges
                extraSideOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )

                // 3. Roll: smoothly spin across the circular arc (center below screen) to target track
                wheelProgress.animateTo(
                    targetValue = (rollList.size - 1).toFloat(),
                    animationSpec = tween(
                        durationMillis = 620,
                        easing = CubicBezierEasing(0.14f, 0.92f, 0.22f, 1.0f)
                    )
                )

                // 4. Exit: remaining side covers smoothly slide out past screen edges, leaving active track centered
                extraSideOffset.animateTo(
                    targetValue = maxShift,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )

                // 5. Complete and sync Pager
                if (activeTrackIndex in currentQueue.indices) {
                    pagerState.scrollToPage(activeTrackIndex)
                }
                isShufflingAnimation = false
                extraSideOffset.snapTo(0f)
            } else {
                if (activeTrackIndex in currentQueue.indices && pagerState.currentPage != activeTrackIndex) {
                    pagerState.animateScrollToPage(activeTrackIndex)
                }
            }
        }
    }

    // Sync playback when user swipes to a different track in carousel (only when shuffle is off)
    LaunchedEffect(pagerState.settledPage) {
        if (!isShuffle && !isShufflingAnimation) {
            val settledIndex = pagerState.settledPage
            if (settledIndex in currentQueue.indices && settledIndex != activeTrackIndex) {
                viewModel.skipToQueueItem(settledIndex)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkRefTheme.BackgroundDark,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Expansive Ambient Atmospheric Glow covering the full sheet without any stripe cutoffs
            Crossfade(
                targetState = activeTrack.artworkUrl,
                animationSpec = tween(durationMillis = 650),
                label = "AtmosphericArtworkGlow",
                modifier = Modifier.matchParentSize()
            ) { artworkUrl ->
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(620.dp)
                                .blur(110.dp)
                                .alpha(0.40f),
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    DarkRefTheme.AccentPrimary.copy(alpha = 0.22f),
                                                    DarkRefTheme.AccentMint.copy(alpha = 0.12f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            },
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    DarkRefTheme.AccentPrimary.copy(alpha = 0.22f),
                                                    DarkRefTheme.AccentMint.copy(alpha = 0.12f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(620.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            DarkRefTheme.AccentPrimary.copy(alpha = 0.25f),
                                            DarkRefTheme.AccentMint.copy(alpha = 0.14f),
                                            DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.20f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // Seamless vertical fade into BackgroundDark spanning the whole sheet
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.30f to DarkRefTheme.BackgroundDark.copy(alpha = 0.15f),
                                    0.50f to DarkRefTheme.BackgroundDark.copy(alpha = 0.45f),
                                    0.70f to DarkRefTheme.BackgroundDark.copy(alpha = 0.85f),
                                    0.90f to DarkRefTheme.BackgroundDark,
                                    1.0f to DarkRefTheme.BackgroundDark
                                )
                            )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScrollState)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle pill placed floating on top of glow at top of sheet
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(48.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                )

                // Album Art Carousel (Curved wheel arc roll during shuffle, and smooth side-covers slide out/in)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardSize + 28.dp)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isShuffle && isShufflingAnimation && shuffleRollCovers.isNotEmpty()) {
                        val maxShift = with(density) { screenWidth.toPx() * 0.85f }
                        val shiftRatio = (extraSideOffset.value / maxShift.coerceAtLeast(1f)).coerceIn(0f, 1f)

                        // Smooth Circular Arc Wheel Roll (center of circle is below the screen)
                        shuffleRollCovers.forEachIndexed { index, trackItem ->
                            val rel = index - wheelProgress.value
                            // Render covers within angular visible window
                            if (rel.absoluteValue < 2.5f) {
                                val cardStepPx = with(density) { (cardSize * 0.92f).toPx() }
                                val transX = rel * cardStepPx
                                // Center of circle is below screen -> apex is at rel = 0, downward dip as |rel| increases
                                val transY = with(density) { (rel * rel * 24.dp.toPx()) }
                                val rotationDeg = rel * 14f
                                val scale = (1.0f - rel.absoluteValue * 0.10f).coerceIn(0.70f, 1.0f)

                                // Smooth entrance from edges before roll, and exit off-screen after roll
                                val sideDir = (rel / 0.15f).coerceIn(-1f, 1f)
                                val sideShiftX = sideDir * extraSideOffset.value

                                val baseAlpha = (1.0f - (rel.absoluteValue - 0.70f).coerceAtLeast(0f) * 1.6f).coerceIn(0f, 1.0f)
                                val alpha = if (rel.absoluteValue < 0.15f) 1.0f else (baseAlpha * (1f - shiftRatio)).coerceIn(0f, 1.0f)

                                Box(
                                    modifier = Modifier
                                        .size(cardSize)
                                        .graphicsLayer {
                                            translationX = transX + sideShiftX
                                            translationY = transY
                                            rotationZ = rotationDeg
                                            scaleX = scale
                                            scaleY = scale
                                            this.alpha = alpha
                                        }
                                        .clip(RoundedCornerShape(26.dp))
                                        .border(
                                            width = 1.dp,
                                            color = if (index == shuffleRollCovers.lastIndex) DarkRefTheme.AccentMint.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(26.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TrackArtwork(
                                        artworkUrl = trackItem.artworkUrl,
                                        isLiveStream = trackItem.isLiveStream,
                                        size = cardSize,
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            key = { page -> currentQueue.getOrNull(page)?.id ?: page.toString() },
                            pageSize = PageSize.Fixed(cardSize),
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            pageSpacing = 16.dp,
                            userScrollEnabled = !isShuffle && shuffleExitProgress == 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardSize + 28.dp)
                        ) { page ->
                            val track = currentQueue.getOrNull(page) ?: return@HorizontalPager
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                            val scale = lerp(0.85f, 1f, (1f - pageOffset).coerceIn(0f, 1f))

                            val isCurrentPage = page == pagerState.currentPage
                            val isLeftPage = page < pagerState.currentPage
                            val isRightPage = page > pagerState.currentPage
                            val screenWidthPx = with(density) { screenWidth.toPx() }

                            // Smooth slide out to left / right edges when shuffle is turned on; slide back in when turned off
                            val sideSlideX = if (isLeftPage) {
                                -shuffleExitProgress * (screenWidthPx * 0.75f)
                            } else if (isRightPage) {
                                shuffleExitProgress * (screenWidthPx * 0.75f)
                            } else {
                                0f
                            }

                            val baseAlpha = lerp(0.50f, 1f, (1f - pageOffset).coerceIn(0f, 1f))
                            val itemAlpha = if (isCurrentPage) 1f else (baseAlpha * (1f - shuffleExitProgress)).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .size(cardSize)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = sideSlideX
                                        alpha = itemAlpha
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                TrackArtwork(
                                    artworkUrl = track.artworkUrl,
                                    isLiveStream = track.isLiveStream,
                                    size = cardSize,
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(26.dp))
                                        .border(
                                            width = 1.dp,
                                            color = Color.White.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(26.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                // Controls container with standard margins
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

            // Track Title (Centered, bold)
            Text(
                text = activeTrack.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DarkRefTheme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist (Centered, gray)
            Text(
                text = activeTrack.artist,
                fontSize = 15.sp,
                color = DarkRefTheme.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Favorite Button (Centered directly below artist with vector heart)
            IconButton(
                onClick = { viewModel.toggleLocalFavorite(activeTrack) },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFav) DarkRefTheme.AccentPink else DarkRefTheme.TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Straight horizontal progress bar with timestamps
            if (totalDuration > 0L) {
                Slider(
                    value = currentPos.coerceIn(0f, totalDuration.toFloat()),
                    valueRange = 0f..totalDuration.toFloat(),
                    onValueChange = { userSeekingPosition = it },
                    onValueChangeFinished = {
                        userSeekingPosition?.let { viewModel.seekTo(it.toLong()) }
                        userSeekingPosition = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = DarkRefTheme.AccentMint,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = userSeekingPosition?.toLong() ?: (
                        if (playbackState.currentTrack != null) playbackState.currentPositionMs
                        else (lastSession?.second ?: 0L)
                    )
                    val remainingMs = (totalDuration - displayedPos).coerceAtLeast(0L)

                    Text(
                        text = formatTrackDuration(displayedPos),
                        fontSize = 12.sp,
                        color = DarkRefTheme.TextSecondary
                    )
                    Text(
                        text = "-${formatTrackDuration(remainingMs)}",
                        fontSize = 12.sp,
                        color = DarkRefTheme.TextSecondary
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(28.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls (Prev, Big Clean Circular Play/Pause, Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Big circular Play Button without offset shadow artifacts
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2C303E),
                                    Color(0xFF1E212D)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (playbackState.isPlaying) DarkRefTheme.AccentMint.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.22f),
                            shape = CircleShape
                        )
                        .clickable {
                            if (playbackState.isPlaying) {
                                viewModel.pause()
                            } else {
                                viewModel.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Additional tools: Shuffle, Repeat, Equalizer, Add to Playlist with mint glowing highlights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isShuffle) DarkRefTheme.AccentMint.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val isRepeat = playbackState.repeatMode != PlaybackState.REPEAT_MODE_OFF
                val repeatIcon = if (playbackState.repeatMode == PlaybackState.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isRepeat) DarkRefTheme.AccentMint.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Equalizer quick tool button
                IconButton(
                    onClick = { showEqualizerDialog = true },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (audioEffectsState.isEnabled) DarkRefTheme.AccentMint.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Equalizer",
                        tint = if (audioEffectsState.isEnabled) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Offline download button with state indicator
                IconButton(
                    onClick = {
                        if (!isTrackCached && currentDownloadPercent == null && !activeTrack.isLocal && !activeTrack.isLiveStream) {
                            viewModel.cacheTrack(activeTrack)
                        }
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTrackCached) DarkRefTheme.AccentMint.copy(alpha = 0.16f) else Color.Transparent)
                        .padding(2.dp)
                ) {
                    if (currentDownloadPercent != null) {
                        CircularProgressIndicator(
                            progress = { currentDownloadPercent / 100f },
                            modifier = Modifier.size(22.dp),
                            color = DarkRefTheme.AccentMint,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isTrackCached) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            contentDescription = if (isTrackCached) "Збережено офлайн" else "Завантажити трек",
                            tint = if (isTrackCached) DarkRefTheme.AccentMint else if (activeTrack.isLocal) DarkRefTheme.TextSecondary.copy(alpha = 0.4f) else DarkRefTheme.TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { onAddToPlaylist(activeTrack) }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.btn_add_to_playlist),
                        tint = DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Spotify-style "Up Next" preview glance card & smooth scroll cue
            Surface(
                onClick = {
                    coroutineScope.launch {
                        sheetScrollState.animateScrollTo(sheetScrollState.maxValue)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                color = DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val nextTrack = upcomingQueueTracks.firstOrNull()

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(DarkRefTheme.AccentMint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (nextTrack != null) "Далі: ${nextTrack.title}" else "Черга відтворення",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (nextTrack != null) "${nextTrack.artist} • ${upcomingQueueTracks.size} в черзі" else "Кінець поточної черги",
                            color = DarkRefTheme.TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Показати чергу",
                        tint = DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1. Upcoming in Queue (Далі у черзі)
            if (upcomingQueueTracks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = DarkRefTheme.AccentMint,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Далі у черзі",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkRefTheme.TextPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkRefTheme.SurfaceCardElevated,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                text = "${upcomingQueueTracks.size} треків",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkRefTheme.TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        upcomingQueueTracks.take(12).forEachIndexed { index, track ->
                            val queueIndex = activeTrackIndex + 1 + index
                            Surface(
                                onClick = { viewModel.skipToQueueItem(queueIndex) },
                                shape = RoundedCornerShape(14.dp),
                                color = DarkRefTheme.SurfaceCard.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkRefTheme.TextSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.width(22.dp)
                                    )
                                    TrackArtwork(
                                        artworkUrl = track.artworkUrl,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            color = DarkRefTheme.TextSecondary,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (track.durationMs > 0L) {
                                        Text(
                                            text = formatTrackDuration(track.durationMs),
                                            color = DarkRefTheme.TextSecondary.copy(alpha = 0.7f),
                                            fontSize = 11.5.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(queueIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Видалити з черги",
                                            tint = DarkRefTheme.TextSecondary.copy(alpha = 0.65f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.skipToQueueItem(queueIndex) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            tint = DarkRefTheme.AccentMint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkRefTheme.SurfaceCard.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Кінець вашої черги",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Додайте більше пісень для безперервного відтворення",
                                color = DarkRefTheme.TextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (showEqualizerDialog) {
            EqualizerDialog(
                viewModel = viewModel,
                audioEffectsState = audioEffectsState,
                onDismiss = { showEqualizerDialog = false }
            )
        }
    }
}
