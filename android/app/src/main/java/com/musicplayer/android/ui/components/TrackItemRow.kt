package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun TrackItemRow(
    track: AudioTrack,
    onPlay: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isFavorite: Boolean = false,
    isCurrentTrack: Boolean = false,
    isPlayingThisTrack: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlaySimilar: (() -> Unit)? = null,
    onTrackCardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isRowActive = isSelected || isCurrentTrack || isPlayingThisTrack
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isRowActive) DarkRefTheme.SurfaceCardElevated
                else DarkRefTheme.SurfaceCard
            )
            .clickable {
                if (isSelectionMode && onToggleSelect != null) {
                    onToggleSelect()
                } else if (onTrackCardClick != null) {
                    onTrackCardClick()
                } else {
                    onPlay()
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect?.invoke() },
                colors = CheckboxDefaults.colors(
                    checkedColor = DarkRefTheme.AccentMint,
                    checkmarkColor = DarkRefTheme.BackgroundDark,
                    uncheckedColor = DarkRefTheme.TextSecondary.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Cover thumbnail
        TrackArtwork(
            artworkUrl = track.artworkUrl,
            isLiveStream = track.isLiveStream,
            size = 46.dp,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontWeight = if (isCurrentTrack || isPlayingThisTrack) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (isPlayingThisTrack) DarkRefTheme.AccentMint else if (isCurrentTrack) Color.White else DarkRefTheme.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val sourceLabel = when {
                track.id.startsWith("youtube_") -> "YouTube"
                track.id.startsWith("soundcloud_") -> "SoundCloud"
                else -> null
            }
            val subtitle = if (sourceLabel != null) {
                "${track.artist} • $sourceLabel • ${formatTrackDuration(track.durationMs)}"
            } else {
                "${track.artist} • ${formatTrackDuration(track.durationMs)}"
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = DarkRefTheme.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isSelectionMode) {
            // Favorite Button
            if (onToggleFavorite != null) {
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
            }

            // Similar Tracks (Song Radio) Button
            if (onPlaySimilar != null) {
                IconButton(
                    onClick = onPlaySimilar,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Схожі пісні",
                        tint = DarkRefTheme.AccentMint.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Add to Playlist Button
            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Quick Play Circle Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlayingThisTrack) DarkRefTheme.AccentMint.copy(alpha = 0.25f)
                        else if (isCurrentTrack) DarkRefTheme.AccentMint.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.12f)
                    )
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlayingThisTrack) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlayingThisTrack) "Pause" else "Play",
                    tint = if (isPlayingThisTrack || isCurrentTrack) DarkRefTheme.AccentMint else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
