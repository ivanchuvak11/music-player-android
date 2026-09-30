package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun SettingsDialog(
    viewModel: MainPlayerViewModel,
    isSleepTimerActive: Boolean,
    sleepTimerRemainingSeconds: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var serverUrlInput by remember { mutableStateOf(viewModel.getBaseUrl()) }
    var audioCacheSizeBytes by remember { mutableStateOf(viewModel.getAudioCacheSizeBytes()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(DarkRefTheme.SurfaceCard)
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = DarkRefTheme.TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sleep Timer section
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sleep_timer_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkRefTheme.TextPrimary
                        )
                        Text(
                            text = if (isSleepTimerActive) {
                                val mins = sleepTimerRemainingSeconds / 60
                                val secs = sleepTimerRemainingSeconds % 60
                                "⏳ %02d:%02d".format(mins, secs)
                            } else {
                                stringResource(R.string.sleep_timer_choose)
                            },
                            fontSize = 11.sp,
                            color = if (isSleepTimerActive) Color.White else DarkRefTheme.TextSecondary
                        )
                    }
                    if (isSleepTimerActive) {
                        OutlinedButton(
                            onClick = {
                                viewModel.cancelSleepTimer()
                                Toast.makeText(context, "Таймер сну вимкнено", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Вимкнути", fontSize = 11.sp, color = DarkRefTheme.AccentPink)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = isSleepTimerActive && (sleepTimerRemainingSeconds <= mins * 60L && sleepTimerRemainingSeconds > (mins - 15) * 60L),
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                Toast.makeText(context, "Встановлено на $mins хв", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("$mins хв", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White.copy(alpha = 0.25f),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkRefTheme.SurfaceCardElevated)
            Spacer(modifier = Modifier.height(14.dp))

            // Cache Cleaner
            val cacheMb = audioCacheSizeBytes / (1024 * 1024)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_cache_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkRefTheme.TextPrimary
                    )
                    Text(
                        text = "$cacheMb MB • ${stringResource(R.string.settings_cache_desc)}",
                        fontSize = 11.sp,
                        color = DarkRefTheme.TextSecondary
                    )
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearAudioCache()
                        audioCacheSizeBytes = viewModel.getAudioCacheSizeBytes()
                        Toast.makeText(context, "Кеш очищено!", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.settings_clean_btn), fontSize = 11.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkRefTheme.SurfaceCardElevated)
            Spacer(modifier = Modifier.height(14.dp))

            // Server URL
            Text(
                text = stringResource(R.string.settings_server_label),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkRefTheme.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serverUrlInput,
                    onValueChange = { serverUrlInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkRefTheme.TextPrimary,
                        unfocusedTextColor = DarkRefTheme.TextPrimary,
                        focusedBorderColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.setBaseUrl(serverUrlInput)
                        Toast.makeText(context, "Сервер оновлено", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text(stringResource(R.string.settings_server_btn), color = DarkRefTheme.BackgroundDark, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.btn_close), color = DarkRefTheme.TextSecondary)
            }
        }
    }
}
