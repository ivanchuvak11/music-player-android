package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.equalizer_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = DarkRefTheme.TextPrimary
                    )
                    Text(
                        text = if (audioEffectsState.isHeadphonesConnected)
                            stringResource(R.string.headphones_connected)
                        else
                            stringResource(R.string.headphones_required),
                        fontSize = 11.sp,
                        color = if (audioEffectsState.isHeadphonesConnected)
                            Color(0xFF00E676)
                        else
                            DarkRefTheme.AccentPink
                    )
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
                        checkedThumbColor = DarkRefTheme.TextPrimary,
                        checkedTrackColor = Color.White.copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!audioEffectsState.isHeadphonesConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkRefTheme.AccentPink.copy(alpha = 0.15f))
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(stringResource(R.string.equalizer_reset), fontSize = 11.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Presets
                    Text(
                        text = stringResource(R.string.equalizer_presets_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkRefTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AudioEffectsState.AVAILABLE_PRESETS.take(4).forEach { preset ->
                            FilterChip(
                                selected = audioEffectsState.currentPreset == preset,
                                onClick = { viewModel.setEqualizerPreset(preset) },
                                label = { Text(preset, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AudioEffectsState.AVAILABLE_PRESETS.drop(4).forEach { preset ->
                            FilterChip(
                                selected = audioEffectsState.currentPreset == preset,
                                onClick = { viewModel.setEqualizerPreset(preset) },
                                label = { Text(preset, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.White.copy(alpha = 0.25f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bass Boost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_bass_boost),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkRefTheme.TextPrimary
                        )
                        Text(
                            text = "${audioEffectsState.bassBoostStrength / 10}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Slider(
                        value = audioEffectsState.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoostStrength(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Frequency Bands
                    Text(
                        text = stringResource(R.string.equalizer_bands_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkRefTheme.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    for (i in 0 until audioEffectsState.numberOfBands) {
                        val band = i.toShort()
                        val level = audioEffectsState.bandLevels[band] ?: 0.toShort()
                        val label = AudioEffectsState.BAND_LABELS[band] ?: "Band ${i + 1}"
                        val dbValue = level.toFloat() / 100f
                        val dbText = if (dbValue >= 0f) "+${"%.1f".format(dbValue)} dB" else "${"%.1f".format(dbValue)} dB"

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, fontSize = 11.sp, color = DarkRefTheme.TextSecondary)
                                Text(dbText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Slider(
                                value = level.toFloat().coerceIn(audioEffectsState.minBandLevel.toFloat(), audioEffectsState.maxBandLevel.toFloat()),
                                onValueChange = { newLevel ->
                                    viewModel.setEqualizerBandLevel(band, newLevel.toInt().toShort())
                                },
                                valueRange = audioEffectsState.minBandLevel.toFloat()..audioEffectsState.maxBandLevel.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White
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

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.btn_close), color = DarkRefTheme.TextSecondary)
            }
        }
    }
}
