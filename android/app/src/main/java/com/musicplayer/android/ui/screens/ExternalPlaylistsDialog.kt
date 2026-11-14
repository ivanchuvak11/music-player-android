package com.musicplayer.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.musicplayer.android.R
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.ExternalPlaylistService
import com.musicplayer.android.core.audio.ExternalPlaylistSource
import com.musicplayer.android.core.audio.areTrackIdsEqual
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.components.TrackItemRow
import com.musicplayer.android.ui.theme.DarkRefTheme

/**
 * Functional external playlist browser & importer (without custom design layer —
 * intended for design team styling pass later).
 */
@Composable
fun ExternalPlaylistsDialog(
    viewModel: MainPlayerViewModel,
    favoriteTrackIds: Set<String>,
    onAddTrackToPlaylist: (AudioTrack) -> Unit,
    onPlaySimilar: (AudioTrack) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val sourceFilter by viewModel.playlistSearchSourceFilter.collectAsState()
    val searchResults by viewModel.searchedExternalPlaylists.collectAsState()
    val isSearching by viewModel.isSearchingExternalPlaylists.collectAsState()
    val searchError by viewModel.externalPlaylistSearchError.collectAsState()

    val openedDetails by viewModel.openedPlaylistDetails.collectAsState()
    val isLoadingOpened by viewModel.isLoadingOpenedPlaylist.collectAsState()
    val openedError by viewModel.openedPlaylistError.collectAsState()
    val curatedPlaylists by viewModel.curatedPlaylists.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    var queryInput by remember { mutableStateOf("") }
    val parsedRef = remember(queryInput) { ExternalPlaylistService.parsePlaylistUrlOrId(queryInput) }

    Dialog(
        onDismissRequest = {
            viewModel.clearOpenedExternalPlaylist()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkRefTheme.SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (openedDetails != null) {
                        IconButton(onClick = { viewModel.clearOpenedExternalPlaylist() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = null,
                                tint = DarkRefTheme.TextPrimary
                            )
                        }
                    }
                    Text(
                        text = openedDetails?.summary?.title ?: stringResource(R.string.playlist_import_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = DarkRefTheme.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.clearOpenedExternalPlaylist()
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = DarkRefTheme.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val details = openedDetails
            if (details != null) {
                Text(
                    text = "${details.summary.source.uppercase()} • ${details.summary.author}",
                    fontSize = 12.sp,
                    color = DarkRefTheme.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (details.tracks.isNotEmpty()) {
                                viewModel.playQueue(details.tracks, 0)
                            }
                        },
                        enabled = details.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_play_all), fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.saveOpenedPlaylistToLibrary { _, savedTitle ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playlist_saved_toast, savedTitle),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = details.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.playlist_save_to_library), fontSize = 12.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isLoadingOpened && details.tracks.isEmpty() -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DarkRefTheme.AccentMint)
                        }
                    }

                    !openedError.isNullOrBlank() && details.tracks.isEmpty() -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(openedError!!, color = DarkRefTheme.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(
                                items = details.tracks,
                                key = { idx, t -> "${details.summary.id}_${idx}_${t.id}" }
                            ) { index, track ->
                                val isCurrent = areTrackIdsEqual(playbackState.currentTrack?.id, track.id)
                                TrackItemRow(
                                    track = track,
                                    onPlay = { viewModel.playQueue(details.tracks, index) },
                                    isFavorite = track.id in favoriteTrackIds,
                                    isCurrentTrack = isCurrent,
                                    isPlayingThisTrack = isCurrent && playbackState.isPlaying,
                                    onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                                    onAddToPlaylist = { onAddTrackToPlaylist(track) },
                                    onPlaySimilar = { onPlaySimilar(track) }
                                )
                            }
                        }
                    }
                }
            } else {
                val runSearchOrUrl = {
                    keyboard?.hide()
                    val q = queryInput.trim()
                    if (q.isNotBlank()) {
                        val ref = ExternalPlaylistService.parsePlaylistUrlOrId(q)
                        if (ref != null) {
                            viewModel.openExternalPlaylist(ref.canonicalUrl, sourceHint = ref.source)
                        } else {
                            viewModel.searchExternalPlaylists(q)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.playlist_search_hint),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearchOrUrl() }),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = DarkRefTheme.TextPrimary,
                            unfocusedTextColor = DarkRefTheme.TextPrimary
                        )
                    )

                    Button(onClick = runSearchOrUrl, enabled = queryInput.isNotBlank()) {
                        Text(
                            text = if (parsedRef != null) {
                                stringResource(R.string.playlist_open_url_btn)
                            } else {
                                stringResource(R.string.nav_search)
                            },
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ExternalPlaylistSource.ALL to stringResource(R.string.playlist_source_all),
                        ExternalPlaylistSource.YOUTUBE to stringResource(R.string.playlist_source_youtube),
                        ExternalPlaylistSource.SOUNDCLOUD to stringResource(R.string.playlist_source_soundcloud)
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = sourceFilter == filter,
                            onClick = { viewModel.setPlaylistSearchSourceFilter(filter) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    isSearching -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DarkRefTheme.AccentMint)
                        }
                    }

                    searchResults.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(items = searchResults, key = { "${it.source}_${it.url}" }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable { viewModel.openExternalPlaylist(item) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkRefTheme.TextPrimary,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.source} • ${item.author}" +
                                                if (item.trackCount > 0) " (${item.trackCount})" else "",
                                            fontSize = 11.5.sp,
                                            color = DarkRefTheme.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = DarkRefTheme.AccentMint)
                                }
                            }
                        }
                    }

                    !searchError.isNullOrBlank() -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(searchError!!, color = DarkRefTheme.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item(key = "curated_lbl") {
                                Text(
                                    text = stringResource(R.string.header_curated_playlists),
                                    fontWeight = FontWeight.Bold,
                                    color = DarkRefTheme.TextPrimary,
                                    fontSize = 14.sp
                                )
                            }
                            items(items = curatedPlaylists, key = { it.id }) { curated ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable { viewModel.openCuratedPlaylistDetails(curated) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(curated.title, fontWeight = FontWeight.SemiBold, color = DarkRefTheme.TextPrimary, fontSize = 13.sp)
                                        Text(curated.description, color = DarkRefTheme.TextSecondary, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = DarkRefTheme.AccentMint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
