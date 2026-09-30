package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.android.R
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.database.LocalPlaylistEntity
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun AddToPlaylistDialog(
    tracks: List<AudioTrack>,
    localPlaylists: List<LocalPlaylistEntity>,
    viewModel: MainPlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var dialogNewPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (tracks.size == 1) stringResource(R.string.btn_add_to_playlist) else "Додати ${tracks.size} пісень",
                color = DarkRefTheme.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tracks.size == 1) {
                    Text(
                        text = "Пісня: ${tracks.first().title}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.create_new_playlist),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkRefTheme.TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = dialogNewPlaylistName,
                        onValueChange = { dialogNewPlaylistName = it },
                        placeholder = { Text(stringResource(R.string.new_playlist_hint), fontSize = 12.sp, color = DarkRefTheme.TextSecondary) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkRefTheme.TextPrimary,
                            unfocusedTextColor = DarkRefTheme.TextPrimary
                        )
                    )
                    Button(
                        onClick = {
                            if (dialogNewPlaylistName.isNotBlank()) {
                                val name = dialogNewPlaylistName.trim()
                                viewModel.createLocalPlaylistWithTracks(name, tracks) {
                                    Toast.makeText(context, "Створено «$name» і додано ${tracks.size} пісень!", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Введіть назву", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(stringResource(R.string.btn_create), fontSize = 11.sp, color = DarkRefTheme.BackgroundDark, fontWeight = FontWeight.Bold)
                    }
                }

                if (localPlaylists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Або обрати існуючий плейліст:",
                        fontSize = 12.sp,
                        color = DarkRefTheme.TextSecondary
                    )
                    localPlaylists.forEach { pl ->
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkRefTheme.SurfaceCardElevated),
                            onClick = {
                                viewModel.addTracksToLocalPlaylist(pl.id, tracks)
                                val msg = if (tracks.size == 1) "Додано до «${pl.name}»" else "Додано ${tracks.size} пісень до «${pl.name}»"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        ) {
                            Text("📁 ${pl.name}", color = DarkRefTheme.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close), color = DarkRefTheme.TextSecondary)
            }
        },
        containerColor = DarkRefTheme.SurfaceCard
    )
}
