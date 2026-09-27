package com.musicplayer.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
fun PlayerCoreScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as android.app.Application
    val viewModel: MainPlayerViewModel = viewModel(
        factory = remember {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return MainPlayerViewModel(app) as T
                }
            }
        }
    )
    val playbackState by viewModel.playbackState.collectAsState()
    val localTracks by viewModel.localTracks.collectAsState()
    val trendingAudius by viewModel.trendingAudiusTracks.collectAsState()
    val searchedAudius by viewModel.searchedAudiusTracks.collectAsState()
    val searchedJamendo by viewModel.searchedJamendoTracks.collectAsState()
    val radioStations by viewModel.radioStations.collectAsState()
    val radioStationsByCountry by viewModel.radioStationsByCountry.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val cachedTracks by viewModel.searchedCachedTracks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authStatus by viewModel.authStatusMessage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var audioCacheSizeBytes by remember { mutableStateOf(viewModel.getAudioCacheSizeBytes()) }
    var serverUrlInput by remember { mutableStateOf(viewModel.getBaseUrl()) }
    var isServerConfigExpanded by remember { mutableStateOf(false) }
    var cachedFilterQuery by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var jamendoQuery by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }

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

    LaunchedEffect(Unit) {
        viewModel.loadLocalTracks()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎵 Music Player Core",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Повна функціональна панель: плеєр, авторизація, радіо, база Room",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(if (isOnline) "🟢 Онлайн" else "🔴 Офлайн", fontSize = 11.sp) }
                )
            }
        }

        // Server URL Configuration (collapsible for cleaner UI)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isServerConfigExpanded = !isServerConfigExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌐 Сервер: ${serverUrlInput.trimEnd('/')}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isServerConfigExpanded) "▲ Приховати" else "▼ Змінити",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isServerConfigExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("Base URL сервера") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                viewModel.setBaseUrl(serverUrlInput)
                                isServerConfigExpanded = false
                                Toast.makeText(context, "Сервер оновлено: $serverUrlInput", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("ОК")
                            }
                        }
                    }
                }
            }
        }

        // Active Player Card with Seek Bar
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Interactive Seek Slider
                    if (playbackState.durationMs > 0L) {
                        Slider(
                            value = playbackState.currentPositionMs.toFloat().coerceIn(0f, playbackState.durationMs.toFloat()),
                            valueRange = 0f..playbackState.durationMs.toFloat(),
                            onValueChange = { newPos ->
                                viewModel.seekTo(newPos.toLong())
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Playback Speed control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Швидкість:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val speedLabel = if (speed == 1.0f) "1x" else if (speed == 2.0f) "2x" else "${speed}x"
                            FilterChip(
                                selected = playbackState.playbackSpeed == speed,
                                onClick = { viewModel.setPlaybackSpeed(speed) },
                                label = { Text(speedLabel, fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Authentication & Account (Вхід / Реєстрація)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔐 Акаунт та Авторизація (JWT)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (currentUser != null) {
                        Text(
                            text = "✅ Ви увійшли як: $currentUser",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = { viewModel.logout() }) {
                            Text("🚪 Вийти з акаунту")
                        }
                    } else {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email або Username") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Пароль") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                        viewModel.login(emailInput.trim(), passwordInput.trim())
                                    } else {
                                        Toast.makeText(context, "Введіть email та пароль", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("🔑 Увійти")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                        val uname = emailInput.substringBefore("@")
                                        viewModel.register(uname, emailInput.trim(), passwordInput.trim())
                                    } else {
                                        Toast.makeText(context, "Введіть email та пароль", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("📝 Реєстрація")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                emailInput = "ivan@example.com"
                                passwordInput = "Test12345"
                            }
                        ) {
                            Text("⚡ Вставити демо-дані з API.md")
                        }
                    }

                    if (!authStatus.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = authStatus.orEmpty(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
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
                        text = "⚡ Прямий тест звуку (радіо та mp3):",
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

        // Section: Local Tracks Scanner (with voice note filter)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📱 Локальні пісні з пам'яті (фільтр голосових увімкнено):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Голосові з Telegram, WhatsApp, диктофона та звуки <20с автоматично відфільтровано",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.loadLocalTracks()
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            permissionLauncher.launch(perm)
                        }
                    ) {
                        Text("🔍 Сканувати музику на телефоні")
                    }
                }
            }
        }

        // List of found local tracks (All items)
        if (localTracks.isNotEmpty()) {
            item {
                Text(
                    text = "Знайдено музичних треків: ${localTracks.size}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(localTracks) { track ->
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
                        text = "💾 Офлайн-кеш Room DB (${cachedTracks.size} знайдено):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cachedFilterQuery,
                        onValueChange = {
                            cachedFilterQuery = it
                            viewModel.searchCachedTracks(it)
                        },
                        label = { Text("Швидкий пошук у базі Room...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cacheMb = audioCacheSizeBytes / (1024 * 1024)
                        Text(
                            text = "Дисковий кеш: $cacheMb МБ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = {
                            viewModel.clearAudioCache()
                            audioCacheSizeBytes = viewModel.getAudioCacheSizeBytes()
                            Toast.makeText(context, "Кеш очищено", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("🧹 Очистити кеш", fontSize = 11.sp)
                        }
                    }
                    if (cachedTracks.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (cachedFilterQuery.isBlank()) "Поки порожньо. Натисніть кнопку «+ Кеш» біля будь-якої пісні." else "Нічого не знайдено за запитом «$cachedFilterQuery»",
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

        // Section: Audius Search (Пошук онлайн-музики)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔍 Пошук треків в Audius:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Пошук пісні/артиста...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    viewModel.searchAudius(searchQuery.trim())
                                }
                            }
                        ) {
                            Text("Пошук")
                        }
                    }
                }
            }
        }

        if (searchedAudius.isNotEmpty()) {
            item {
                Text(
                    text = "Результати пошуку (${searchedAudius.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(searchedAudius) { audiusTrack ->
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

        // Section: Jamendo Music Search (Основне джерело онлайн-треків)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🎸 Пошук треків у Jamendo (Playable Online MP3):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = jamendoQuery,
                            onValueChange = { jamendoQuery = it },
                            placeholder = { Text("Наприклад: rock, electronic...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (jamendoQuery.isNotBlank()) {
                                    viewModel.searchJamendo(jamendoQuery.trim())
                                }
                            }
                        ) {
                            Text("Пошук")
                        }
                    }
                }
            }
        }

        if (searchedJamendo.isNotEmpty()) {
            item {
                Text(
                    text = "Знайдено в Jamendo (${searchedJamendo.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(searchedJamendo) { jamendoTrack ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playJamendoTrack(jamendoTrack) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = jamendoTrack.title, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(
                                text = "${jamendoTrack.artist} • ${jamendoTrack.album ?: "Jamendo"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { viewModel.playJamendoTrack(jamendoTrack) }) {
                            Text("▶")
                        }
                    }
                }
            }
        }

        // Section: Radio by Country (UA)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🇺🇦 Українські радіостанції (Radio Browser + Redis):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.loadRadioByCountry("UA")
                        }
                    ) {
                        Text("📻 Оновити станції України (UA)")
                    }
                }
            }
        }

        if (radioStationsByCountry.isNotEmpty()) {
            item {
                Text(
                    text = "Радіостанції України (${radioStationsByCountry.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(radioStationsByCountry) { station ->
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
                            Text(
                                text = "${station.genre ?: "Music"} • ${station.codec ?: "MP3"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { viewModel.playRadioStation(station) }) {
                            Text("▶")
                        }
                    }
                }
            }
        }

        // Section: Backend Data & Playlists
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🌐 Бекенд плейлісти та онлайн-радіо:",
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
                        Text("🔄 Оновити дані з бекенду")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Назва нового плейліста") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.createPlaylist(newPlaylistName.trim())
                                    newPlaylistName = ""
                                    Toast.makeText(context, "Плейліст створюється...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("➕")
                        }
                    }
                }
            }
        }

        // Playlists list
        if (playlists.isNotEmpty()) {
            item {
                Text(
                    text = "Мої плейлісти (${playlists.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(playlists) { pl ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📁 ${pl.name} (${pl.trackCount} треків)",
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Audius Trending
        if (trendingAudius.isNotEmpty()) {
            item {
                Text(
                    text = "Тренди Audius (${trendingAudius.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(trendingAudius) { audiusTrack ->
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

        // Radio stations list
        if (radioStations.isNotEmpty()) {
            item {
                Text(
                    text = "Радіостанції (${radioStations.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            items(radioStations) { station ->
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
            Spacer(modifier = Modifier.height(32.dp))
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
