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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
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
    val isFav = localFavorites.any { it.id == activeTrack.id }

    // Queue for carousel navigation
    val currentQueue = remember(playbackState.queue, activeTrack) {
        if (playbackState.queue.isNotEmpty()) playbackState.queue else listOf(activeTrack)
    }
    val activeTrackIndex = currentQueue.indexOfFirst { it.id == activeTrack.id }.coerceAtLeast(0)

    val pagerState = rememberPagerState(
        initialPage = activeTrackIndex,
        pageCount = { currentQueue.size }
    )

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

    // Smooth 3-phase circular arc roll when track changes under shuffle:
    // Phase 1: Side covers quickly slide in from behind screen edges
    // Phase 2: Carousel rolls along circular arc to target track
    // Phase 3: Remaining side covers slide out past screen edges, leaving active track centered
    LaunchedEffect(activeTrack.id) {
        if (lastTrackId != activeTrack.id) {
            val prevId = lastTrackId
            lastTrackId = activeTrack.id
            if (isShuffle && currentQueue.size > 1 && prevId.isNotBlank()) {
                val prevTrack = currentQueue.firstOrNull { it.id == prevId } ?: activeTrack
                val pool = currentQueue.filter { it.id != activeTrack.id && it.id != prevTrack.id }
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
    LaunchedEffect(pagerState.currentPage) {
        if (!isShuffle) {
            val selectedTrack = currentQueue.getOrNull(pagerState.currentPage)
            if (selectedTrack != null && selectedTrack.id != activeTrack.id) {
                viewModel.playTrack(selectedTrack)
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
                    .padding(bottom = 8.dp),
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
                            pageSize = PageSize.Fixed(cardSize),
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            pageSpacing = 16.dp,
                            userScrollEnabled = !isShuffle && shuffleExitProgress == 0f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardSize + 28.dp)
                        ) { page ->
                            val track = currentQueue[page]
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

            // Additional tools: Shuffle, Repeat, Add to Playlist with mint glowing highlights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
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
