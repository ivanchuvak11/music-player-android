package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.android.R
import com.musicplayer.android.core.audio.toAudioTrack
import com.musicplayer.android.core.database.LocalPlaylistEntity
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun LocalPlaylistCard(
    playlist: LocalPlaylistEntity,
    viewModel: MainPlayerViewModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList())
    val playbackState by viewModel.playbackState.collectAsState()
    val currentTrackId = playbackState.currentTrack?.id
    val isPlaybackPlaying = playbackState.isPlaying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkRefTheme.SurfaceCard)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = DarkRefTheme.AccentMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = playlist.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkRefTheme.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${tracks.size} ${stringResource(R.string.nav_tracks).lowercase()}",
                    fontSize = 12.sp,
                    color = DarkRefTheme.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tracks.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onPlayAll,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.btn_play_all), fontSize = 11.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete",
                        tint = DarkRefTheme.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = DarkRefTheme.TextSecondary
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = DarkRefTheme.SurfaceCardElevated)
            Spacer(modifier = Modifier.height(8.dp))

            if (tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_playlist_message),
                    fontSize = 12.sp,
                    color = DarkRefTheme.TextSecondary
                )
            } else {
                tracks.forEach { trackEntity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trackEntity.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkRefTheme.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = trackEntity.artist,
                                fontSize = 11.sp,
                                color = DarkRefTheme.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val isTrackCurrent = currentTrackId == trackEntity.trackId
                        val isTrackPlaying = isTrackCurrent && isPlaybackPlaying
                        IconButton(
                            onClick = {
                                if (isTrackCurrent) {
                                    if (isPlaybackPlaying) viewModel.pause() else viewModel.play()
                                } else {
                                    viewModel.playTrack(trackEntity.toAudioTrack())
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isTrackPlaying) "Pause" else "Play",
                                tint = if (isTrackPlaying || isTrackCurrent) DarkRefTheme.AccentMint else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.removeTrackFromLocalPlaylist(playlist.id, trackEntity.trackId, trackEntity.id)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove",
                                tint = DarkRefTheme.AccentPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
