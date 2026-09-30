package com.musicplayer.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun RadioBar(
    stations: List<RadioStationDto>,
    currentRadioIndex: Int,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    viewModel: MainPlayerViewModel,
    onRadioIndexChange: (Int) -> Unit,
    playOnlineSafely: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeIndex = currentRadioIndex.coerceIn(0, (stations.size - 1).coerceAtLeast(0))
    val currentStation = stations.getOrNull(safeIndex)

    val isCurrentStationPlaying = currentStation != null &&
        currentTrack?.title == currentStation.name &&
        isPlaying

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DarkRefTheme.SurfaceCard)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = DarkRefTheme.AccentMint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.radio_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = DarkRefTheme.TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.radio_live_badge),
                        fontSize = 10.sp,
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (stations.isNotEmpty()) {
                Text(
                    text = "${safeIndex + 1}/${stations.size}",
                    fontSize = 12.sp,
                    color = DarkRefTheme.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (currentStation != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Prev button
                IconButton(
                    onClick = {
                        val newIdx = if (safeIndex > 0) safeIndex - 1 else stations.size - 1
                        onRadioIndexChange(newIdx)
                        val newStation = stations[newIdx]
                        if (isPlaying && currentTrack?.isLiveStream == true) {
                            playOnlineSafely { viewModel.playRadioStation(newStation) }
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkRefTheme.SurfaceCardElevated)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Station Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (isCurrentStationPlaying) {
                                viewModel.pause()
                            } else {
                                playOnlineSafely { viewModel.playRadioStation(currentStation) }
                            }
                        }
                ) {
                    Text(
                        text = currentStation.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = DarkRefTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentStation.genre ?: "Online Radio"} • ${currentStation.codec ?: "MP3"}",
                        fontSize = 11.sp,
                        color = DarkRefTheme.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentStationPlaying) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f))
                        .clickable {
                            if (isCurrentStationPlaying) {
                                viewModel.pause()
                            } else if (currentTrack?.title == currentStation.name) {
                                viewModel.play()
                            } else {
                                playOnlineSafely { viewModel.playRadioStation(currentStation) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentStationPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isCurrentStationPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = {
                        val newIdx = (safeIndex + 1) % stations.size
                        onRadioIndexChange(newIdx)
                        val newStation = stations[newIdx]
                        if (isPlaying && currentTrack?.isLiveStream == true) {
                            playOnlineSafely { viewModel.playRadioStation(newStation) }
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkRefTheme.SurfaceCardElevated)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = DarkRefTheme.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
