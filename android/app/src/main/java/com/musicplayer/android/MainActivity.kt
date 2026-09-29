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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicplayer.android.core.audio.AudioEffectsState
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.database.LocalPlaylistEntity
import com.musicplayer.android.core.database.LocalPlaylistTrackEntity
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
    val radioStationsByCountry by viewModel.radioStationsByCountry.collectAsState()
    val cachedTracks by viewModel.searchedCachedTracks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authStatus by viewModel.authStatusMessage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val audioEffectsState by viewModel.audioEffectsState.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val playHistory by viewModel.playHistory.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }
    var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var currentRadioIndex by remember { mutableStateOf(0) }
    var audioCacheSizeBytes by remember { mutableStateOf(viewModel.getAudioCacheSizeBytes()) }
    var serverUrlInput by remember { mutableStateOf(viewModel.getBaseUrl()) }
    var isServerConfigExpanded by remember { mutableStateOf(false) }
    var isEqualizerExpanded by remember { mutableStateOf(false) }
    var unifiedSearchQuery by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }

    // Permission launcher for scanning local audio
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadLocalTracks()
        } else {
            Toast.makeText(context, "Дозвіл на читання аудіо відхилено", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            perm
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            viewModel.loadLocalTracks()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        val error = playbackState.errorMessage
        if (!error.isNullOrBlank()) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        }
    }

    val playOnlineSafely: (() -> Unit) -> Unit = { action ->
        if (isOnline) {
            action()
        } else {
            Toast.makeText(
                context,
                "📡 Немає зв'язку з інтернетом. Ви можете слухати музику з пристрою чи збережений Room-кеш офлайн!",
                Toast.LENGTH_LONG
            ).show()
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

        // Status Banner: Network Error or Offline Mode
        if (playbackState.errorMessage != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playbackState.errorMessage ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        } else if (!isOnline) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📡", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ви зараз офлайн. Треки з пам'яті телефону та кеш Room доступні без інтернету!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
        // Active Player Card with Seek Bar & Memory Resume
        item {
            val lastSession = remember { viewModel.getLastPlayedTrack() }
            val rememberedTrack = lastSession?.first
            val rememberedPosition = lastSession?.second ?: 0L
            val currentOrRememberedTrack = playbackState.currentTrack ?: rememberedTrack

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (playbackState.isPlaying) "Зараз грає:" else if (playbackState.currentTrack != null) "На паузі:" else "Остання пісня:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentOrRememberedTrack?.title ?: "Трек не вибрано",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentOrRememberedTrack?.artist ?: "Натисніть «▶ Грати» щоб почати",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        currentOrRememberedTrack?.let { track ->
                            val isFav = localFavorites.any { it.id == track.id }
                            IconButton(onClick = { viewModel.toggleLocalFavorite(track) }) {
                                Text(if (isFav) "❤️" else "🤍", fontSize = 22.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Interactive Seek Slider with smooth preview to avoid audio stuttering
                    var userSeekingPosition by remember { mutableStateOf<Float?>(null) }
                    val totalDuration = if (playbackState.durationMs > 0L) {
                        playbackState.durationMs
                    } else {
                        currentOrRememberedTrack?.durationMs ?: 0L
                    }
                    val currentPos = userSeekingPosition ?: (
                        if (playbackState.currentTrack != null) playbackState.currentPositionMs.toFloat()
                        else rememberedPosition.toFloat()
                    )

                    if (totalDuration > 0L) {
                        Slider(
                            value = currentPos.coerceIn(0f, totalDuration.toFloat()),
                            valueRange = 0f..totalDuration.toFloat(),
                            onValueChange = { newPos ->
                                userSeekingPosition = newPos
                            },
                            onValueChangeFinished = {
                                userSeekingPosition?.let { viewModel.seekTo(it.toLong()) }
                                userSeekingPosition = null
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
                                playbackState.errorMessage != null -> "⚠️ Збій мережі"
                                playbackState.isBuffering -> "⏳ Буферизація..."
                                playbackState.isPlaying -> "▶ Відтворення"
                                playbackState.currentTrack != null -> "⏸ На паузі"
                                rememberedTrack != null -> "⏸ Готово до відтворення"
                                else -> "⏹ Зупинено"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        val displayedPos = userSeekingPosition?.toLong() ?: (
                            if (playbackState.currentTrack != null) playbackState.currentPositionMs
                            else rememberedPosition
                        )
                        Text(
                            text = "${formatDuration(displayedPos)} / ${formatDuration(totalDuration)}",
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

        // Section: Hardware Equalizer & Sound Effects
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isEqualizerExpanded = !isEqualizerExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎚 Еквалайзер та Ефекти",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (audioEffectsState.isEnabled) "Увімк: ${audioEffectsState.currentPreset}" else "Вимкнено",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isEqualizerExpanded) "▲" else "▼", fontSize = 12.sp)
                        }
                    }

                    if (isEqualizerExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Увімкнути еквалайзер:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = audioEffectsState.isEnabled,
                                onCheckedChange = { viewModel.setEqualizerEnabled(it) }
                            )
                        }

                        if (audioEffectsState.isEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Пресети звучання:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AudioEffectsState.AVAILABLE_PRESETS.take(3).forEach { preset ->
                                    FilterChip(
                                        selected = audioEffectsState.currentPreset == preset,
                                        onClick = { viewModel.setEqualizerPreset(preset) },
                                        label = { Text(preset, fontSize = 10.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AudioEffectsState.AVAILABLE_PRESETS.drop(3).forEach { preset ->
                                    FilterChip(
                                        selected = audioEffectsState.currentPreset == preset,
                                        onClick = { viewModel.setEqualizerPreset(preset) },
                                        label = { Text(preset, fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Підсилення басу (Bass Boost):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${audioEffectsState.bassBoostStrength / 10}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = audioEffectsState.bassBoostStrength.toFloat(),
                                onValueChange = { viewModel.setBassBoostStrength(it.toInt().toShort()) },
                                valueRange = 0f..1000f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Смуги частот (5-смуговий Еквалайзер):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))

                            for (i in 0 until audioEffectsState.numberOfBands) {
                                val band = i.toShort()
                                val level = audioEffectsState.bandLevels[band] ?: 0.toShort()
                                val label = AudioEffectsState.BAND_LABELS[band] ?: "Смуга ${i + 1}"
                                val dbValue = level.toFloat() / 100f
                                val dbText = if (dbValue >= 0f) "+${"%.1f".format(dbValue)} dB" else "${"%.1f".format(dbValue)} dB"

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(dbText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Slider(
                                        value = level.toFloat().coerceIn(audioEffectsState.minBandLevel.toFloat(), audioEffectsState.maxBandLevel.toFloat()),
                                        onValueChange = { newLevel ->
                                            viewModel.setEqualizerBandLevel(band, newLevel.toInt().toShort())
                                        },
                                        valueRange = audioEffectsState.minBandLevel.toFloat()..audioEffectsState.maxBandLevel.toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
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

        // 📻 Compact Live Radio Bar with Station Switcher
        item {
            val stations = radioStationsByCountry.ifEmpty { MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS }
            val safeIndex = currentRadioIndex.coerceIn(0, (stations.size - 1).coerceAtLeast(0))
            val currentStation = stations.getOrNull(safeIndex)

            val isCurrentStationPlaying = currentStation != null &&
                playbackState.currentTrack?.title == currentStation.name &&
                playbackState.isPlaying

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📻 Онлайн-радіо",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("LIVE 🟢", fontSize = 9.sp) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        if (stations.isNotEmpty()) {
                            Text(
                                text = "${safeIndex + 1}/${stations.size}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentStation != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Prev station button
                            OutlinedButton(
                                onClick = {
                                    val newIdx = if (safeIndex > 0) safeIndex - 1 else stations.size - 1
                                    currentRadioIndex = newIdx
                                    val newStation = stations[newIdx]
                                    if (playbackState.isPlaying && playbackState.currentTrack?.isLiveStream == true) {
                                        playOnlineSafely { viewModel.playRadioStation(newStation) }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.width(44.dp)
                            ) {
                                Text("⏮")
                            }

                            // Station info (clickable to play)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${currentStation.genre ?: "Online Radio"} • ${currentStation.codec ?: "MP3"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Play / Pause button
                            Button(
                                onClick = {
                                    if (isCurrentStationPlaying) {
                                        viewModel.pause()
                                    } else if (playbackState.currentTrack?.title == currentStation.name) {
                                        viewModel.play()
                                    } else {
                                        playOnlineSafely { viewModel.playRadioStation(currentStation) }
                                    }
                                }
                            ) {
                                Text(if (isCurrentStationPlaying) "⏸" else "▶")
                            }

                            // Next station button
                            OutlinedButton(
                                onClick = {
                                    val newIdx = (safeIndex + 1) % stations.size
                                    currentRadioIndex = newIdx
                                    val newStation = stations[newIdx]
                                    if (playbackState.isPlaying && playbackState.currentTrack?.isLiveStream == true) {
                                        playOnlineSafely { viewModel.playRadioStation(newStation) }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.width(44.dp)
                            ) {
                                Text("⏭")
                            }
                        }
                    }
                }
            }
        }

        // 🔍 Unified Global Search Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🔍 Швидкий пошук:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = unifiedSearchQuery,
                        onValueChange = {
                            unifiedSearchQuery = it
                            if (it.isNotBlank() && isOnline) {
                                viewModel.searchJamendo(it.trim())
                                viewModel.searchAudius(it.trim())
                            }
                        },
                        placeholder = { Text("Введіть пісню або артиста...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Text("🔍", fontSize = 16.sp)
                        },
                        trailingIcon = {
                            if (unifiedSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { unifiedSearchQuery = "" }) {
                                    Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }
        }

        // Unified Search Results or Default Local Library
        if (unifiedSearchQuery.isNotBlank()) {
            val q = unifiedSearchQuery.trim()
            val filteredLocal = localTracks.filter {
                it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
            }
            val filteredCached = cachedTracks.filter {
                it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
            }
            val onlineResults = searchedJamendo.map { AudioTrack.fromJamendo(it) } +
                searchedAudius.map { AudioTrack.fromAudius(it, viewModel.getBaseUrl()) }

            val totalFound = filteredLocal.size + filteredCached.size + onlineResults.size

            item {
                Text(
                    text = "Знайдено результатів: $totalFound",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Sub-header 1: On device
            if (filteredLocal.isNotEmpty()) {
                item {
                    Text(
                        text = "📱 На пристрої (${filteredLocal.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(filteredLocal) { track ->
                    TrackItemRow(
                        track = track,
                        onPlay = { viewModel.playLocalTrack(track) },
                        onAddToPlaylist = { trackToAddToPlaylist = track }
                    )
                }
            }

            // Sub-header 2: In Room cache
            if (filteredCached.isNotEmpty()) {
                item {
                    Text(
                        text = "💾 У збереженому кеші (${filteredCached.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(filteredCached) { cached ->
                    CachedTrackRow(
                        cached = cached,
                        onPlay = {
                            viewModel.playTrack(
                                AudioTrack(
                                    id = cached.id,
                                    title = cached.title,
                                    artist = cached.artist,
                                    audioUrl = cached.localFilePath ?: cached.originalUrl,
                                    durationMs = cached.durationMs,
                                    isLocal = cached.localFilePath != null
                                )
                            )
                        },
                        onDelete = { viewModel.removeCachedTrack(cached.id) }
                    )
                }
            }

            // Sub-header 3: Online
            if (onlineResults.isNotEmpty()) {
                item {
                    Text(
                        text = "🌐 Онлайн (${onlineResults.size}):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(onlineResults) { track ->
                    TrackItemRow(
                        track = track,
                        onPlay = { playOnlineSafely { viewModel.playTrack(track) } },
                        onAddToPlaylist = { trackToAddToPlaylist = track }
                    )
                }
            }

            if (totalFound == 0) {
                item {
                    Text(
                        text = "Нічого не знайдено за запитом «$q»",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Default on-device music list when not searching
            if (localTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "📱 Музика на пристрої (${localTracks.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(localTracks) { track ->
                    TrackItemRow(
                        track = track,
                        onPlay = { viewModel.playLocalTrack(track) },
                        onAddToPlaylist = { trackToAddToPlaylist = track }
                    )
                }
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
                        text = "💾 Офлайн-кеш Room DB (${cachedTracks.size} збережено):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
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
                    val tracks = cachedTracks.map { c ->
                        AudioTrack(
                            id = c.id,
                            title = c.title,
                            artist = c.artist,
                            audioUrl = c.localFilePath ?: c.originalUrl,
                            durationMs = c.durationMs,
                            isLocal = c.localFilePath != null
                        )
                    }
                    val index = cachedTracks.indexOfFirst { it.id == cached.id }.coerceAtLeast(0)
                    viewModel.playQueue(tracks, index)
                },
                onDelete = {
                    viewModel.removeCachedTrack(cached.id)
                }
            )
        }

        // Section: Recently Played History (Історія прослуховувань)
        if (playHistory.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📜 Нещодавно прослухані (${playHistory.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = { viewModel.clearPlayHistory() }) {
                                Text("Очистити", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            items(playHistory.take(5)) { histItem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val track = AudioTrack(
                                id = histItem.trackId,
                                title = histItem.title,
                                artist = histItem.artist,
                                audioUrl = histItem.audioUrl,
                                artworkUrl = histItem.artworkUrl,
                                durationMs = histItem.durationMs
                            )
                            viewModel.playTrack(track)
                        },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(histItem.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(histItem.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(formatDuration(histItem.durationMs), fontSize = 11.sp)
                    }
                }
            }
        }



        // Section: Local Room Playlists (Офлайн-плейлісти на пристрої)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📁 Мої плейлісти (${localPlaylists.size}):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Створюйте добірки та додавайте треки кнопкою «📁+» біля будь-якої пісні",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Назва нового плейліста...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    val name = newPlaylistName.trim()
                                    viewModel.createPlaylist(name)
                                    newPlaylistName = ""
                                    Toast.makeText(context, "Плейліст «$name» створено!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Введіть назву плейліста", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("➕ Створити")
                        }
                    }

                    if (localPlaylists.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "У вас ще немає створених плейлістів. Введіть назву вище та натисніть «➕ Створити».",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(localPlaylists) { pl ->
            LocalPlaylistCard(
                playlist = pl,
                viewModel = viewModel,
                isExpanded = expandedPlaylistId == pl.id,
                onToggleExpand = {
                    expandedPlaylistId = if (expandedPlaylistId == pl.id) null else pl.id
                },
                onDelete = {
                    viewModel.deleteLocalPlaylist(pl.id)
                    Toast.makeText(context, "Плейліст «${pl.name}» видалено", Toast.LENGTH_SHORT).show()
                },
                onPlayAll = {
                    viewModel.playLocalPlaylist(pl.id)
                }
            )
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
                        .clickable { playOnlineSafely { viewModel.playAudiusTrack(audiusTrack) } },
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
                        Button(onClick = { playOnlineSafely { viewModel.playAudiusTrack(audiusTrack) } }) {
                            Text("▶")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (trackToAddToPlaylist != null) {
        val track = trackToAddToPlaylist!!
        AlertDialog(
            onDismissRequest = { trackToAddToPlaylist = null },
            title = { Text("📁 Додати в плейліст") },
            text = {
                if (localPlaylists.isEmpty()) {
                    Text("У вас ще немає створених плейлістів. Створіть новий плейліст у розділі «Мої плейлісти» нижче.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Пісня: ${track.title}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Оберіть плейліст:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        localPlaylists.forEach { pl ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.addTrackToLocalPlaylist(pl.id, track)
                                    Toast.makeText(context, "Додано до «${pl.name}»", Toast.LENGTH_SHORT).show()
                                    trackToAddToPlaylist = null
                                }
                            ) {
                                Text("📁 ${pl.name}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackToAddToPlaylist = null }) {
                    Text("Закрити")
                }
            }
        )
    }
}

@Composable
fun TrackItemRow(
    track: AudioTrack,
    onPlay: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
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
            if (onAddToPlaylist != null) {
                OutlinedButton(onClick = onAddToPlaylist) {
                    Text("📁+", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Button(onClick = onPlay) {
                Text("▶")
            }
        }
    }
}

@Composable
fun LocalPlaylistCard(
    playlist: LocalPlaylistEntity,
    viewModel: MainPlayerViewModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit,
    onPlayAll: () -> Unit
) {
    val tracks by viewModel.getPlaylistTracks(playlist.id).collectAsState(initial = emptyList())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📁 ${playlist.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${tracks.size} треків",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tracks.isNotEmpty()) {
                        Button(onClick = onPlayAll) {
                            Text("▶ Грати", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Text("🗑", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isExpanded) "▲" else "▼", fontSize = 12.sp)
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (tracks.isEmpty()) {
                    Text(
                        text = "Плейліст порожній. Натисніть «📁+» біля будь-якої пісні у списку вище, щоб додати її сюди.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = trackEntity.artist,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = {
                                val audioTrack = AudioTrack(
                                    id = trackEntity.trackId,
                                    title = trackEntity.title,
                                    artist = trackEntity.artist,
                                    audioUrl = trackEntity.audioUrl,
                                    durationMs = trackEntity.durationMs,
                                    isLocal = trackEntity.isLocal
                                )
                                viewModel.playTrack(audioTrack)
                            }) {
                                Text("▶", fontSize = 14.sp)
                            }
                            IconButton(onClick = {
                                viewModel.removeTrackFromLocalPlaylist(playlist.id, trackEntity.trackId)
                            }) {
                                Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CachedTrackRow(
    cached: CachedTrackEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
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
            if (onAddToPlaylist != null) {
                OutlinedButton(onClick = onAddToPlaylist) {
                    Text("📁+", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
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
