package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musicplayer.android.R
import com.musicplayer.android.core.audio.AudioEffectsState
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerDialog(
    viewModel: MainPlayerViewModel,
    audioEffectsState: AudioEffectsState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkRefTheme.SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            // Header with Equalizer icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkRefTheme.AccentMint.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = stringResource(R.string.equalizer_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DarkRefTheme.TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (audioEffectsState.isHeadphonesConnected)
                                    Icons.Rounded.Headphones
                                else
                                    Icons.Rounded.HeadphonesBattery,
                                contentDescription = null,
                                tint = if (audioEffectsState.isHeadphonesConnected)
                                    DarkRefTheme.AccentMint
                                else
                                    DarkRefTheme.AccentPink,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (audioEffectsState.isHeadphonesConnected)
                                    stringResource(R.string.headphones_connected)
                                else
                                    stringResource(R.string.headphones_required),
                                fontSize = 11.sp,
                                color = if (audioEffectsState.isHeadphonesConnected)
                                    DarkRefTheme.AccentMint
                                else
                                    DarkRefTheme.AccentPink
                            )
                        }
                    }
                }

                Switch(
                    checked = audioEffectsState.isEnabled,
                    onCheckedChange = { desired ->
                        if (desired) {
                            val success = viewModel.setEqualizerEnabled(true)
                            if (!success) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.equalizer_headphones_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.equalizer_enabled_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            viewModel.setEqualizerEnabled(false)
                            Toast.makeText(
                                context,
                                context.getString(R.string.equalizer_disabled_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkRefTheme.BackgroundDark,
                        checkedTrackColor = DarkRefTheme.AccentMint,
                        uncheckedThumbColor = DarkRefTheme.TextSecondary,
                        uncheckedTrackColor = DarkRefTheme.SurfaceCardElevated
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!audioEffectsState.isHeadphonesConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkRefTheme.AccentPink.copy(alpha = 0.14f))
                        .border(1.dp, DarkRefTheme.AccentPink.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.headphones_warning),
                        fontSize = 11.sp,
                        color = DarkRefTheme.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Scrollable EQ controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
            ) {
                if (audioEffectsState.isEnabled) {
                    // Reset Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.resetEqualizer() },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkRefTheme.SurfaceCardElevated
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = DarkRefTheme.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.equalizer_reset),
                                fontSize = 11.sp,
                                color = DarkRefTheme.TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Presets
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.equalizer_presets_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkRefTheme.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // FlowRow ensures chips wrap properly across any screen width with zero cutoffs
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3
                    ) {
                        AudioEffectsState.AVAILABLE_PRESETS.forEach { preset ->
                            val isSelected = audioEffectsState.currentPreset == preset
                            Surface(
                                onClick = { viewModel.setEqualizerPreset(preset) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) DarkRefTheme.AccentMint.copy(alpha = 0.20f) else DarkRefTheme.SurfaceCardElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) DarkRefTheme.AccentMint.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = when (preset) {
                                            AudioEffectsState.PRESET_FLAT -> Icons.Rounded.GraphicEq
                                            AudioEffectsState.PRESET_BASS -> Icons.Rounded.Speaker
                                            AudioEffectsState.PRESET_ROCK -> Icons.Rounded.ElectricBolt
                                            AudioEffectsState.PRESET_POP -> Icons.Rounded.Audiotrack
                                            AudioEffectsState.PRESET_JAZZ -> Icons.Rounded.Piano
                                            AudioEffectsState.PRESET_CLASSICAL -> Icons.Rounded.LibraryMusic
                                            else -> Icons.Rounded.Tune
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = preset,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) DarkRefTheme.AccentMint else DarkRefTheme.TextPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bass Boost Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = DarkRefTheme.AccentMint,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.equalizer_bass_boost),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkRefTheme.TextPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkRefTheme.AccentMint.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${audioEffectsState.bassBoostStrength / 10}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkRefTheme.AccentMint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Slider(
                        value = audioEffectsState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoostStrength(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = DarkRefTheme.AccentMint,
                            activeTrackColor = DarkRefTheme.AccentMint,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Frequency Bands Section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Equalizer,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.equalizer_bands_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkRefTheme.TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    for (i in 0 until audioEffectsState.numberOfBands) {
                        val band = i.toShort()
                        val level = audioEffectsState.bandLevels[band] ?: 0.toShort()
                        val label = AudioEffectsState.BAND_LABELS[band] ?: "Band ${i + 1}"
                        val dbValue = level.toFloat() / 100f
                        val dbText = if (dbValue >= 0f) "+${"%.1f".format(dbValue)} dB" else "${"%.1f".format(dbValue)} dB"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, fontSize = 11.sp, color = DarkRefTheme.TextSecondary)
                                Text(
                                    text = dbText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (level != 0.toShort()) DarkRefTheme.AccentMint else Color.White
                                )
                            }
                            Slider(
                                value = level.toFloat().coerceIn(audioEffectsState.minBandLevel.toFloat(), audioEffectsState.maxBandLevel.toFloat()),
                                onValueChange = { newLevel ->
                                    viewModel.setEqualizerBandLevel(band, newLevel.toInt().toShort())
                                },
                                valueRange = audioEffectsState.minBandLevel.toFloat()..audioEffectsState.maxBandLevel.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = DarkRefTheme.AccentMint,
                                    activeTrackColor = DarkRefTheme.AccentMint,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_enable),
                            fontSize = 13.sp,
                            color = DarkRefTheme.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                color = DarkRefTheme.SurfaceCardElevated,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.btn_close),
                        color = DarkRefTheme.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
