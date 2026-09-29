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
import com.musicplayer.android.core.audio.AudioEffectsManager
import com.musicplayer.android.core.audio.AudioEffectsState
import com.musicplayer.android.core.audio.AudioTrack
import com.musicplayer.android.core.audio.PlaybackState
import com.musicplayer.android.core.database.CachedTrackEntity
import com.musicplayer.android.core.database.LocalPlaylistEntity
import com.musicplayer.android.core.database.LocalPlaylistTrackEntity
import com.musicplayer.android.core.network.RadioStationDto
import com.musicplayer.android.core.audio.calculateSearchRelevanceScore
import com.musicplayer.android.core.audio.toAudioTrack
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val cachedTracks by viewModel.searchedCachedTracks.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val authStatus by viewModel.authStatusMessage.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    val audioEffectsState by viewModel.audioEffectsState.collectAsState()
    val isSleepTimerActive by viewModel.isSleepTimerActive.collectAsState()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsState()
    val localFavorites by viewModel.localFavorites.collectAsState()
    val playHistory by viewModel.playHistory.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var tracksToAddToPlaylist by remember { mutableStateOf<List<AudioTrack>?>(null) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedTracks by remember { mutableStateOf(setOf<AudioTrack>()) }
    var dialogNewPlaylistName by remember { mutableStateOf("") }
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
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isFavoritesExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(playbackState.currentTrack) {
        val track = playbackState.currentTrack
        if (track != null && track.isLiveStream) {
            val stations = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS
            val idx = stations.indexOfFirst { it.name == track.title }
            if (idx >= 0) {
                currentRadioIndex = idx
            }
        }
    }

    LaunchedEffect(isServerConfigExpanded) {
        if (isServerConfigExpanded) {
            audioCacheSizeBytes = viewModel.getAudioCacheSizeBytes()
        }
    }

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
                "📡 Немає зв'язку з інтернетом. Ви можете слухати музику з пам'яті телефону офлайн!",
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
                            text = "Ви зараз офлайн. Треки з пам'яті телефону доступні для прослуховування!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (currentOrRememberedTrack != null) {
                                        showNowPlayingSheet = true
                                    }
                                }
                        ) {
                            Text(
                                text = if (playbackState.isPlaying) "Зараз грає (натисніть ↗):" else if (playbackState.currentTrack != null) "На паузі (натисніть ↗):" else "Остання пісня:",
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

                    // Modes: Shuffle & Repeat & Sleep Timer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleShuffle() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(if (playbackState.shuffleModeEnabled) "🔀 ON" else "🔀 OFF", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.cycleRepeatMode() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val repeatText = when (playbackState.repeatMode) {
                                PlaybackState.REPEAT_MODE_ONE -> "🔂 1"
                                PlaybackState.REPEAT_MODE_ALL -> "🔁 Всі"
                                else -> "🔁 Вимк"
                            }
                            Text(repeatText, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val timerText = if (isSleepTimerActive) "🌙 ${sleepTimerRemainingSeconds / 60}хв" else "🌙 Сон"
                            Text(timerText, fontSize = 11.sp)
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
                        Column {
                            Text(
                                text = "🎚 Еквалайзер та Ефекти",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (audioEffectsState.isHeadphonesConnected) "🎧 Навушники підключено" else "🎧 Тільки в навушниках",
                                fontSize = 10.sp,
                                color = if (audioEffectsState.isHeadphonesConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
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

                        if (!audioEffectsState.isHeadphonesConnected) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🎧", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Підключіть навушники (дротові, Bluetooth або Type-C), щоб увімкнути еквалайзер.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Увімкнути еквалайзер:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (audioEffectsState.isEnabled) {
                                    OutlinedButton(
                                        onClick = { viewModel.resetEqualizer() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("🔄 0 dB", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Switch(
                                    checked = audioEffectsState.isEnabled,
                                    onCheckedChange = { desired ->
                                        if (desired) {
                                            val success = viewModel.setEqualizerEnabled(true)
                                            if (!success) {
                                                Toast.makeText(
                                                    context,
                                                    "🎧 Еквалайзер можна увімкнути лише з навушниками!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                Toast.makeText(context, "🎚 Еквалайзер увімкнено", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            viewModel.setEqualizerEnabled(false)
                                            Toast.makeText(context, "Еквалайзер вимкнено (чистий звук)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
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
                                        viewModel.login(emailInput.trim(), passwordInput)
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
                                        viewModel.register(uname, emailInput.trim(), passwordInput)
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
            val stations = MainPlayerViewModel.DEFAULT_UA_RADIO_STATIONS
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
            val filteredLocal = localTracks
                .filter { it.calculateSearchRelevanceScore(q) >= 0 }
                .sortedByDescending { it.calculateSearchRelevanceScore(q) }
            val filteredCached = cachedTracks
                .filter { it.toAudioTrack().calculateSearchRelevanceScore(q) >= 0 }
                .sortedByDescending { it.toAudioTrack().calculateSearchRelevanceScore(q) }
            val rawOnline = searchedJamendo.map { AudioTrack.fromJamendo(it, viewModel.getBaseUrl()) } +
                searchedAudius.map { AudioTrack.fromAudius(it, viewModel.getBaseUrl()) }
            val onlineResults = rawOnline
                .filter { it.calculateSearchRelevanceScore(q) >= 0 }
                .distinctBy { "${it.title.trim().lowercase()}_${it.artist.trim().lowercase()}" }
                .sortedByDescending { it.calculateSearchRelevanceScore(q) }

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
                    val isFav = localFavorites.any { it.id == track.id }
                    TrackItemRow(
                        track = track,
                        onPlay = { viewModel.playLocalTrack(track) },
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                        onTrackCardClick = {
                            if (playbackState.currentTrack?.id != track.id) {
                                viewModel.playLocalTrack(track)
                            }
                            showNowPlayingSheet = true
                        }
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
                        onPlay = { viewModel.playTrack(cached.toAudioTrack()) },
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
                    val isFav = localFavorites.any { it.id == track.id }
                    TrackItemRow(
                        track = track,
                        onPlay = { playOnlineSafely { viewModel.playTrack(track) } },
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                        onTrackCardClick = {
                            playOnlineSafely {
                                if (playbackState.currentTrack?.id != track.id) {
                                    viewModel.playTrack(track)
                                }
                                showNowPlayingSheet = true
                            }
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📱 Музика на пристрої (${localTracks.size}):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedTracks = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (isMultiSelectMode) "✕ Закрити" else "☑ Вибрати кілька", fontSize = 11.sp)
                        }
                    }
                }

                if (isMultiSelectMode) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Обрано: ${selectedTracks.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = {
                                        selectedTracks = if (selectedTracks.size == localTracks.size) emptySet() else localTracks.toSet()
                                    }) {
                                        Text(if (selectedTracks.size == localTracks.size) "Зняти всі" else "Всі", fontSize = 11.sp)
                                    }
                                    Button(
                                        enabled = selectedTracks.isNotEmpty(),
                                        onClick = {
                                            tracksToAddToPlaylist = selectedTracks.toList()
                                        }
                                    ) {
                                        Text("📁+ Додати (${selectedTracks.size})", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                items(localTracks) { track ->
                    val isSelected = selectedTracks.contains(track)
                    val isFav = localFavorites.any { it.id == track.id }
                    TrackItemRow(
                        track = track,
                        onPlay = {
                            if (isMultiSelectMode) {
                                selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                            } else {
                                viewModel.playLocalTrack(track)
                            }
                        },
                        isSelectionMode = isMultiSelectMode,
                        isSelected = isSelected,
                        isFavorite = isFav,
                        onToggleFavorite = { viewModel.toggleLocalFavorite(track) },
                        onToggleSelect = {
                            selectedTracks = if (isSelected) selectedTracks - track else selectedTracks + track
                        },
                        onAddToPlaylist = { tracksToAddToPlaylist = listOf(track) },
                        onTrackCardClick = {
                            if (playbackState.currentTrack?.id != track.id) {
                                viewModel.playLocalTrack(track)
                            }
                            showNowPlayingSheet = true
                        }
                    )
                }
            }
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
                            viewModel.playTrack(histItem.toAudioTrack())
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



        // Section: Favorite Tracks (❤️ Улюблені пісні з Room DB)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFavoritesExpanded = !isFavoritesExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "❤️ Улюблені треки (${localFavorites.size}):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (localFavorites.isNotEmpty()) "Пісні, які ви відзначили сердечком" else "Тут з'являтимуться треки з позначкою ❤️",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (localFavorites.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val favTracks = localFavorites.map { it.toAudioTrack() }
                                        viewModel.playQueue(favTracks, 0)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("▶ Грати всі", fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(text = if (isFavoritesExpanded) "▲" else "▼", fontSize = 12.sp)
                        }
                    }

                    if (isFavoritesExpanded && localFavorites.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Натисніть ❤️ на будь-якій пісні під час прослуховування або в плеєрі, щоб додати її сюди!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (isFavoritesExpanded && localFavorites.isNotEmpty()) {
            items(localFavorites) { favItem ->
                val track = favItem.toAudioTrack()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playTrack(track) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (playbackState.currentTrack?.id != track.id) {
                                        viewModel.playTrack(track)
                                    }
                                    showNowPlayingSheet = true
                                }
                        ) {
                            Text(favItem.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(favItem.artist, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(favItem.durationMs), fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { tracksToAddToPlaylist = listOf(track) }) {
                                Text("📁+", fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.toggleLocalFavorite(track) }) {
                                Text("❤️", fontSize = 16.sp)
                            }
                        }
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

        // Section: System Settings & Storage Cache
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚙️ Налаштування та Пам'ять",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val cacheMb = audioCacheSizeBytes / (1024 * 1024)
                            val timerStatus = if (isSleepTimerActive) "активний (${sleepTimerRemainingSeconds / 60} хв)" else "вимкнено"
                            Text(
                                text = "Кеш: $cacheMb МБ • Таймер сну: $timerStatus",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (isServerConfigExpanded) "▲ Згорнути" else "▼ Відкрити",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (isServerConfigExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Sleep Timer Setting (prominently at top of settings)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🌙 Таймер сну (Sleep Timer):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSleepTimerActive) {
                                        val mins = sleepTimerRemainingSeconds / 60
                                        val secs = sleepTimerRemainingSeconds % 60
                                        "⏳ Музика зупиниться через: %02d:%02d".format(mins, secs)
                                    } else {
                                        "Автоматично зупиняє відтворення через обраний час"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSleepTimerActive) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.cancelSleepTimer()
                                        Toast.makeText(context, "Таймер сну скасовано", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Вимкнути", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = isSleepTimerActive && (sleepTimerRemainingSeconds <= mins * 60L && sleepTimerRemainingSeconds > (mins - 15) * 60L),
                                    onClick = {
                                        viewModel.startSleepTimer(mins)
                                        Toast.makeText(context, "Таймер сну встановлено на $mins хв", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("$mins хв", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Cache Cleaner Row
                        val cacheMb = audioCacheSizeBytes / (1024 * 1024)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "💾 Тимчасовий кеш аудіо:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$cacheMb МБ (ExoPlayer авто-кешування треків)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearAudioCache()
                                    audioCacheSizeBytes = viewModel.getAudioCacheSizeBytes()
                                    Toast.makeText(context, "Кеш аудіо успішно очищено!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("🧹 Очистити", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // Server URL Row
                        Text(
                            text = "🌐 Адреса бекенд-сервера (API):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("Base URL сервера", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                viewModel.setBaseUrl(serverUrlInput)
                                Toast.makeText(context, "Сервер оновлено: $serverUrlInput", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("ОК")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🌙", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Таймер сну (Sleep Timer)")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isSleepTimerActive) {
                        val mins = sleepTimerRemainingSeconds / 60
                        val secs = sleepTimerRemainingSeconds % 60
                        Text(
                            text = "⏳ Відтворення зупиниться через: %02d:%02d".format(mins, secs),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                viewModel.cancelSleepTimer()
                                Toast.makeText(context, "Таймер сну вимкнено", Toast.LENGTH_SHORT).show()
                                showSleepTimerDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("❌ Вимкнути таймер")
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Або змінити тривалість:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Оберіть час, через який музика автоматично зупиниться:")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.startSleepTimer(mins)
                                    Toast.makeText(context, "Таймер сну встановлено на $mins хв", Toast.LENGTH_SHORT).show()
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                            ) {
                                Text("$mins хв", fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Закрити")
                }
            }
        )
    }

    if (tracksToAddToPlaylist != null) {
        val tracks = tracksToAddToPlaylist!!
        AlertDialog(
            onDismissRequest = {
                tracksToAddToPlaylist = null
                dialogNewPlaylistName = ""
            },
            title = {
                Text(if (tracks.size == 1) "📁 Додати до плейліста" else "📁 Додати ${tracks.size} пісень до плейліста")
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Обрано ${tracks.size} пісень для додавання",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text("Створити новий плейліст:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dialogNewPlaylistName,
                            onValueChange = { dialogNewPlaylistName = it },
                            placeholder = { Text("Назва нового...", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (dialogNewPlaylistName.isNotBlank()) {
                                    val name = dialogNewPlaylistName.trim()
                                    viewModel.createLocalPlaylistWithTracks(name, tracks) {
                                        Toast.makeText(context, "Створено «$name» і додано ${tracks.size} пісень!", Toast.LENGTH_SHORT).show()
                                    }
                                    dialogNewPlaylistName = ""
                                    tracksToAddToPlaylist = null
                                    isMultiSelectMode = false
                                    selectedTracks = emptySet()
                                } else {
                                    Toast.makeText(context, "Введіть назву плейліста", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("➕ Додати", fontSize = 11.sp)
                        }
                    }

                    if (localPlaylists.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Або обрати існуючий плейліст:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        localPlaylists.forEach { pl ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.addTracksToLocalPlaylist(pl.id, tracks)
                                    val msg = if (tracks.size == 1) "Додано до «${pl.name}»" else "Додано ${tracks.size} пісень до «${pl.name}»"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    tracksToAddToPlaylist = null
                                    isMultiSelectMode = false
                                    selectedTracks = emptySet()
                                }
                            ) {
                                Text("📁 ${pl.name}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    tracksToAddToPlaylist = null
                    dialogNewPlaylistName = ""
                }) {
                    Text("Закрити")
                }
            }
        )
    }

    // 🎵 Now Playing / Track Detail Bottom Sheet
    if (showNowPlayingSheet) {
        val lastSession = remember { viewModel.getLastPlayedTrack() }
        val activeTrack = playbackState.currentTrack ?: lastSession?.first
        if (activeTrack != null) {
            val totalDuration = if (playbackState.durationMs > 0L) {
                playbackState.durationMs
            } else {
                activeTrack.durationMs
            }
            var sheetSeekingPosition by remember { mutableStateOf<Float?>(null) }
            val currentPos = sheetSeekingPosition ?: (
                if (playbackState.currentTrack != null) playbackState.currentPositionMs.toFloat()
                else (lastSession?.second ?: 0L).toFloat()
            )
            val isFav = localFavorites.any { it.id == activeTrack.id }

            ModalBottomSheet(
                onDismissRequest = { showNowPlayingSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Art Placeholder Card
                    Card(
                        modifier = Modifier
                            .size(160.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (activeTrack.isLiveStream) "📻" else "🎵",
                                fontSize = 64.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Track Title & Favorite Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTrack.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeTrack.artist,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.toggleLocalFavorite(activeTrack) }) {
                            Text(if (isFav) "❤️" else "🤍", fontSize = 26.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Seek Slider
                    if (totalDuration > 0L) {
                        Slider(
                            value = currentPos.coerceIn(0f, totalDuration.toFloat()),
                            valueRange = 0f..totalDuration.toFloat(),
                            onValueChange = { sheetSeekingPosition = it },
                            onValueChangeFinished = {
                                sheetSeekingPosition?.let { viewModel.seekTo(it.toLong()) }
                                sheetSeekingPosition = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val displayedPos = sheetSeekingPosition?.toLong() ?: (
                                if (playbackState.currentTrack != null) playbackState.currentPositionMs
                                else (lastSession?.second ?: 0L)
                            )
                            Text(formatDuration(displayedPos), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatDuration(totalDuration), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Playback Controls (Prev, Play/Pause, Next)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Text("⏮", fontSize = 28.sp)
                        }

                        FilledIconButton(
                            onClick = {
                                if (playbackState.isPlaying) {
                                    viewModel.pause()
                                } else {
                                    viewModel.play()
                                }
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(if (playbackState.isPlaying) "⏸" else "▶", fontSize = 30.sp)
                        }

                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Text("⏭", fontSize = 28.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Modes: Shuffle, Repeat & Add to Playlist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.toggleShuffle() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (playbackState.shuffleModeEnabled) "🔀 Shuffle: ON" else "🔀 Shuffle: OFF", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { viewModel.cycleRepeatMode() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            val rep = when (playbackState.repeatMode) {
                                PlaybackState.REPEAT_MODE_ONE -> "🔂 Повтор: 1"
                                PlaybackState.REPEAT_MODE_ALL -> "🔁 Повтор: Всі"
                                else -> "🔁 Повтор: Вимк"
                            }
                            Text(rep, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                tracksToAddToPlaylist = listOf(activeTrack)
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("📁+ Додати", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun TrackItemRow(
    track: AudioTrack,
    onPlay: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onToggleSelect: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onTrackCardClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSelectionMode && onToggleSelect != null) {
                    onToggleSelect()
                } else if (onTrackCardClick != null) {
                    onTrackCardClick()
                } else {
                    onPlay()
                }
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect?.invoke() }
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
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
            if (!isSelectionMode) {
                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(if (isFavorite) "❤️" else "🤍", fontSize = 16.sp)
                    }
                }
                if (onAddToPlaylist != null) {
                    OutlinedButton(
                        onClick = onAddToPlaylist,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("📁+", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Button(
                    onClick = onPlay,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("▶", fontSize = 12.sp)
                }
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
                                viewModel.playTrack(trackEntity.toAudioTrack())
                            }) {
                                Text("▶", fontSize = 14.sp)
                            }
                            IconButton(onClick = {
                                viewModel.removeTrackFromLocalPlaylist(playlist.id, trackEntity.trackId, trackEntity.id)
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
