package com.musicplayer.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.ui.theme.DarkRefTheme

/**
 * SimilarTrackCard — Variant 2: Contextual Horizontal Track Card.
 *
 * Displays:
 *  - High-resolution square artwork
 *  - Distinguishable source badge ("YouTube" in Red, "SoundCloud" in Orange)
 *  - Circular quick play/pause action button
 *  - Bold title & secondary artist
 *  - Direct 1-tap download & favorite actions
 */
@Composable
fun SimilarTrackCard(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    isDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isYouTube = track.id.startsWith("youtube_")
    val isSoundCloud = track.id.startsWith("soundcloud_")

    Card(
        modifier = modifier
            .width(154.dp)
            .clickable { onPlay() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E212D)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isCurrentTrack) DarkRefTheme.AccentMint.copy(alpha = 0.65f) else Color(0x2B4B5568)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Artwork Container
            Box(
                modifier = Modifier
                    .size(134.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF262A38))
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    val sizePx = with(LocalDensity.current) { 134.dp.roundToPx() }
                    val imageRequest = remember(track.artworkUrl, sizePx) {
                        ImageRequest.Builder(context)
                            .data(track.artworkUrl)
                            .size(sizePx, sizePx)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Source Badge
                val badgeColor = when {
                    isYouTube -> Color(0xD9CC0000)      // YouTube Red
                    isSoundCloud -> Color(0xD9FF5500)   // SoundCloud Orange
                    else -> Color(0xD92C303E)
                }
                val badgeText = when {
                    isYouTube -> "YouTube"
                    isSoundCloud -> "SoundCloud"
                    else -> "Audio"
                }

                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Quick Play/Pause Button
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(32.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(
                            if (isCurrentTrack && isPlaying) DarkRefTheme.AccentMint else Color(0xCC111319)
                        )
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentTrack && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrentTrack && isPlaying) "Pause" else "Play",
                        tint = if (isCurrentTrack && isPlaying) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = track.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrentTrack) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Artist
            Text(
                text = track.artist,
                fontSize = 11.sp,
                color = DarkRefTheme.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Actions: Download to Device & Favorite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = if (isDownloaded) "Завантажено на пристрій" else "Завантажити на пристрій",
                        tint = if (isDownloaded) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Улюблене",
                        tint = if (isFavorite) DarkRefTheme.AccentPink else DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
