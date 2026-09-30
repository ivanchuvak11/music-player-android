package com.musicplayer.android.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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

    // Sync pager when track changes externally (Next/Prev buttons, queue auto-advance)
    LaunchedEffect(activeTrackIndex) {
        if (pagerState.currentPage != activeTrackIndex && activeTrackIndex in currentQueue.indices) {
            pagerState.animateScrollToPage(activeTrackIndex)
        }
    }

    // Sync playback when user swipes to a different track in carousel
    LaunchedEffect(pagerState.currentPage) {
        val selectedTrack = currentQueue.getOrNull(pagerState.currentPage)
        if (selectedTrack != null && selectedTrack.id != activeTrack.id) {
            viewModel.playTrack(selectedTrack)
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

                // Horizontal Pager with peeking adjacent album covers dynamically sized to screen resolution
                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fixed(cardSize),
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    pageSpacing = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cardSize + 16.dp)
                ) { page ->
                    val track = currentQueue[page]
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                    val scale = lerp(0.85f, 1f, (1f - pageOffset).coerceIn(0f, 1f))
                    val itemAlpha = lerp(0.50f, 1f, (1f - pageOffset).coerceIn(0f, 1f))

                    Box(
                        modifier = Modifier
                            .size(cardSize)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
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
                val isShuffle = playbackState.shuffleModeEnabled
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
    }
}
