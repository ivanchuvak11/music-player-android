package com.musicplayer.android.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    currentOrRememberedTrack: AudioTrack?,
    rememberedPosition: Long,
    isSleepTimerActive: Boolean,
    sleepTimerRemainingSeconds: Long,
    onExpandNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
    modifier: Modifier = Modifier
) {
    if (currentOrRememberedTrack == null) return

    val totalDuration = if (playbackState.durationMs > 0L) playbackState.durationMs else currentOrRememberedTrack.durationMs
    val currentPos = if (playbackState.currentTrack != null) playbackState.currentPositionMs else rememberedPosition
    val progressFraction = if (totalDuration > 0L) (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkRefTheme.SurfaceCardElevated)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onExpandNowPlaying() }
    ) {
        // Ambient Cover Blur Backdrop: subtly tints MiniPlayer colors to match current track artwork
        if (!currentOrRememberedTrack.artworkUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(currentOrRememberedTrack.artworkUrl) {
                ImageRequest.Builder(context)
                    .data(currentOrRememberedTrack.artworkUrl)
                    .size(100, 100)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            Crossfade(
                targetState = request,
                animationSpec = tween(durationMillis = 600),
                label = "MiniPlayerArtworkGlow"
            ) { req ->
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(24.dp)
                        .alpha(0.35f)
                )
            }
        }

        // Frosted dark overlay to maintain perfect contrast and readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.88f),
                            DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.65f),
                            DarkRefTheme.SurfaceCardElevated.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // MiniPlayer Contents (Controls and Track Information)
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art
                TrackArtwork(
                    artworkUrl = currentOrRememberedTrack.artworkUrl,
                    isLiveStream = currentOrRememberedTrack.isLiveStream,
                    size = 46.dp,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Title & Artist
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentOrRememberedTrack.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = DarkRefTheme.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSleepTimerActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🌙 ${sleepTimerRemainingSeconds / 60}m",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentOrRememberedTrack.artist,
                        fontSize = 12.sp,
                        color = DarkRefTheme.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Favorite Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) DarkRefTheme.AccentPink else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .clickable { onTogglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Thin straight progress line at bottom with mint active progress
            if (totalDuration > 0L) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = DarkRefTheme.AccentMint,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}
