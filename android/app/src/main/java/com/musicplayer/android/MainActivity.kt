package com.musicplayer.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlayerCoreScreen()
                }
            }
        }
    }
}

@Composable
fun PlayerCoreScreen(viewModel: MainPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val localTracks by viewModel.localTracks.collectAsState()
    val trendingAudius by viewModel.trendingAudiusTracks.collectAsState()
    val radioStations by viewModel.radioStations.collectAsState()
    val cachedTracks by viewModel.cachedTracks.collectAsState()

    // Permission launcher for scanning local audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadLocalTracks()
            Toast.makeText(context, "Сканування запущено...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Дозвіл на читання аудіо відхилено", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header
        item {
            Text(
                text = "🎵 Music Player Core",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Функціональна панель тестування Core & Audio Engine",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Active Player Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Зараз грає:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = playbackState.currentTrack?.title ?: "Трек не вибрано",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playbackState.currentTrack?.artist ?: "Оберіть пісню зі списку нижче",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status and Timing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when {
                                playbackState.isBuffering -> "⏳ Буферизація..."
                                playbackState.isPlaying -> "▶ Відтворення"
                                playbackState.currentTrack != null -> "⏸ На паузі"
                                else -> "⏹ Зупинено"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Player Buttons: Previous, Play/Pause, Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { viewModel.playPrevious() }) {
                            Text("⏮ Назад")
                        }
                        Button(
                            onClick = {
                                if (playbackState.isPlaying) {
                                    viewModel.pause()
                                } else {
                                    viewModel.play()
                                }
                            }
                        ) {
                            Text(if (playbackState.isPlaying) "⏸ Пауза" else "▶ Грати")
                        }
                        Button(onClick = { viewModel.playNext() }) {
                            Text("Вперед ⏭")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Modes: Shuffle & Repeat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = { viewModel.toggleShuffle() }) {
                            Text(if (playbackState.shuffleModeEnabled) "🔀 Shuffle: ON" else "🔀 Shuffle: OFF")
                        }
                        OutlinedButton(onClick = { viewModel.cycleRepeatMode() }) {
                            val repeatText = when (playbackState.repeatMode) {
                                PlaybackState.REPEAT_MODE_ONE -> "🔂 Повтор: 1"
                                PlaybackState.REPEAT_MODE_ALL -> "🔁 Повтор: Всі"
                                else -> "🔁 Повтор: Вимк"
                            }
                            Text(repeatText)
                        }
                    }
                }
            }
        }

        // Quick Audio Engine Test Streams
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⚡ Прямий тест онлайн-потоків (перевірка звуку):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val testRadio = AudioTrack(
                                    id = "test_radio_1",
                                    title = "Hit Radio Live",
                                    artist = "Internet Radio Stream",
                                    audioUrl = "https://stream.zeno.fm/f3wvbbqmdg8uv",
                                    durationMs = 0L,
                                    isLocal = false,
                                    isLiveStream = true
                                )
                                viewModel.playTrack(testRadio)
                            }
                        ) {
                            Text("📻 Онлайн Радіо")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val testSong = AudioTrack(
                                    id = "test_song_1",
                                    title = "SoundHelix Song 1",
                                    artist = "SoundHelix (MP3 Stream)",
                                    audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                                    durationMs = 372000L,
                                    isLocal = false,
                                    isLiveStream = false
                                )
                                viewModel.playTrack(testSong)
                            }
                        ) {
                            Text("🎵 Тестовий MP3")
                        }
                    }
                }
            }
        }

        // Section: Local Tracks Scanner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📱 Локальні пісні з пам'яті телефону (MediaStore):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            permissionLauncher.launch(perm)
                        }
                    ) {
                        Text("🔍 Сканувати пісні на телефоні")
                    }
                }
            }
        }

        // List of found local tracks
        if (localTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Знайдено ${localTracks.size} пісень:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(localTracks.take(15)) { track ->
                TrackItemRow(
                    track = track,
                    onPlay = { viewModel.playTrack(track) },
                    onCache = {
                        viewModel.cacheTrack(track)
                        Toast.makeText(context, "Збережено в Room офлайн-кеш!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Section: Room Offline Cache
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "💾 Офлайн-кеш Room DB (${cachedTracks.size} треків збережено):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (cachedTracks.isEmpty()) {
                        Text(
                            text = "Поки порожньо. Натисніть кнопку «+ Кеш» біля будь-якої пісні.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(cachedTracks) { cached ->
            CachedTrackRow(
                cached = cached,
                onPlay = {
                    val track = AudioTrack(
                        id = cached.id,
                        title = cached.title,
                        artist = cached.artist,
                        audioUrl = cached.localFilePath ?: cached.originalUrl,
                        durationMs = cached.durationMs,
                        isLocal = cached.localFilePath != null
                    )
                    viewModel.playTrack(track)
                },
                onDelete = {
                    viewModel.removeCachedTrack(cached.id)
                }
            )
        }

        // Section: Backend Data (Audius & Radio)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🌐 Онлайн бекенд (Audius & Радіо):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.loadBackendData()
                            Toast.makeText(context, "Завантаження даних із бекенду...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("🔄 Завантажити дані з бекенду")
                    }
                }
            }
        }

        // List of Audius tracks if loaded
        if (trendingAudius.isNotEmpty()) {
            item {
                Text(
                    text = "Тренди Audius (${trendingAudius.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(trendingAudius.take(10)) { audiusTrack ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playAudiusTrack(audiusTrack) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = audiusTrack.title, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(text = audiusTrack.artist, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { viewModel.playAudiusTrack(audiusTrack) }) {
                            Text("▶")
                        }
                    }
                }
            }
        }

        // List of Radio stations if loaded
        if (radioStations.isNotEmpty()) {
            item {
                Text(
                    text = "Радіостанції (${radioStations.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(radioStations.take(5)) { station ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playRadioStation(station) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = station.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(text = station.genre ?: "Online Radio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { viewModel.playRadioStation(station) }) {
                            Text("📻")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun TrackItemRow(
    track: AudioTrack,
    onPlay: () -> Unit,
    onCache: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${formatDuration(track.durationMs)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onCache) {
                Text("+ Кеш")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Button(onClick = onPlay) {
                Text("▶")
            }
        }
    }
}

@Composable
fun CachedTrackRow(
    cached: CachedTrackEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cached.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${cached.artist} • В базі Room",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            Button(onClick = onPlay) {
                Text("▶")
            }
            Spacer(modifier = Modifier.width(6.dp))
            OutlinedButton(onClick = onDelete) {
                Text("🗑")
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
