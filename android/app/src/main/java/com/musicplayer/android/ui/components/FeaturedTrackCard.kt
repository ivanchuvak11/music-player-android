package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun FeaturedTrackCard(
    track: AudioTrack,
    onPlay: () -> Unit,
    isCurrentTrack: Boolean = false,
    isPlayingThisTrack: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkRefTheme.SurfaceCard)
            .clickable { onPlay() }
    ) {
        // Full cover art
        DefaultArtworkPlaceholder()

        if (!track.artworkUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val sizePx = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(track.artworkUrl, sizePx) {
                ImageRequest.Builder(context)
                    .data(track.artworkUrl)
                    .size(sizePx, sizePx)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Frosted bottom gradient overlay with track details (per reference)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(DarkRefTheme.CardOverlayGradient)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkRefTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val sourceLabel = when {
                        track.id.startsWith("youtube_") -> "YouTube"
                        track.id.startsWith("soundcloud_") -> "SoundCloud"
                        else -> null
                    }
                    val subtitle = if (sourceLabel != null) "${track.artist} • $sourceLabel" else track.artist
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = DarkRefTheme.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Floating circular Play Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlayingThisTrack) DarkRefTheme.AccentMint.copy(alpha = 0.35f)
                            else if (isCurrentTrack) DarkRefTheme.AccentMint.copy(alpha = 0.20f)
                            else Color.White.copy(alpha = 0.28f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlayingThisTrack) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlayingThisTrack) "Pause" else "Play",
                        tint = if (isPlayingThisTrack || isCurrentTrack) DarkRefTheme.AccentMint else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultArtworkPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkRefTheme.SurfaceCardElevated),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = DarkRefTheme.TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
    }
}
