package com.musicplayer.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musicplayer.android.R
import com.musicplayer.android.core.viewmodel.MainPlayerViewModel
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun StatsDialog(
    viewModel: MainPlayerViewModel,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit
) {
    val selectedPeriod by viewModel.statsPeriod.collectAsState()
    val userStats by viewModel.userStatsSummary.collectAsState()
    val systemReport by viewModel.systemReport.collectAsState()
    val publicOverview by viewModel.publicOverview.collectAsState()
    val isLoading by viewModel.statsLoading.collectAsState()
    val errorMsg by viewModel.statsError.collectAsState()

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadStats(selectedPeriod)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkRefTheme.SurfaceCard)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            // Header
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
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = DarkRefTheme.AccentMint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.stats_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DarkRefTheme.TextPrimary
                        )
                        Text(
                            text = publicOverview?.status?.let { "Сервер: $it" }
                                ?: stringResource(R.string.stats_subtitle),
                            fontSize = 11.sp,
                            color = DarkRefTheme.AccentMint
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.loadStats(selectedPeriod) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.stats_refresh),
                            tint = DarkRefTheme.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = DarkRefTheme.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Period selector
            val periods = remember {
                listOf(
                    "day" to R.string.stats_period_day,
                    "week" to R.string.stats_period_week,
                    "month" to R.string.stats_period_month,
                    "all" to R.string.stats_period_all
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                periods.forEach { (key, labelRes) ->
                    val active = selectedPeriod == key
                    Surface(
                        onClick = { viewModel.loadStats(key) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) Color.White else DarkRefTheme.SurfaceCardElevated,
                        border = BorderStroke(
                            1.dp,
                            if (active) Color.White else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(labelRes),
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) DarkRefTheme.BackgroundDark else DarkRefTheme.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading && systemReport == null && userStats == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DarkRefTheme.AccentMint)
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!errorMsg.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkRefTheme.AccentPink.copy(alpha = 0.14f))
                                .border(1.dp, DarkRefTheme.AccentPink.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(text = errorMsg.orEmpty(), fontSize = 12.sp, color = DarkRefTheme.TextPrimary)
                        }
                    }

                    // User Stats Summary Section
                    Text(
                        text = stringResource(R.string.stats_user_section),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkRefTheme.TextPrimary
                    )

                    if (!isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkRefTheme.SurfaceCardElevated)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_login_prompt),
                                fontSize = 12.sp,
                                color = DarkRefTheme.TextSecondary
                            )
                        }
                    } else if (userStats != null) {
                        val u = userStats!!
                        StatsMetricGrid(
                            items = listOf(
                                "Збережено треків" to "${u.totalSavedItemsCount}",
                                "Плейлісти" to "${u.playlistsCreatedCount}",
                                "Улюблені треки" to "${u.favoritesAddedCount}",
                                "Хвилин аудіо" to "${u.totalDurationMinutes}"
                            )
                        )

                        if (u.topArtists.isNotEmpty()) {
                            StatsSectionCard(title = stringResource(R.string.stats_top_artists)) {
                                u.topArtists.take(5).forEach { artist ->
                                    StatsKeyValueRow(
                                        label = artist.artist.ifBlank { "Невідомий" },
                                        value = "${artist.trackCount} тр."
                                    )
                                }
                            }
                        }

                        if (u.sourceBreakdown.isNotEmpty()) {
                            StatsSectionCard(title = stringResource(R.string.stats_sources)) {
                                u.sourceBreakdown.forEach { src ->
                                    StatsKeyValueRow(
                                        label = src.source.uppercase(),
                                        value = "${src.count} (${src.percentage}%)"
                                    )
                                }
                            }
                        }

                        if (u.recentActivity.isNotEmpty()) {
                            StatsSectionCard(title = stringResource(R.string.stats_recent_activity)) {
                                u.recentActivity.take(5).forEach { item ->
                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkRefTheme.TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.artistOrDetails} • ${item.activityType}",
                                            fontSize = 10.5.sp,
                                            color = DarkRefTheme.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // System Report Section
                    if (systemReport != null) {
                        val r = systemReport!!
                        Text(
                            text = stringResource(R.string.stats_system_section),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkRefTheme.TextPrimary
                        )

                        StatsMetricGrid(
                            items = listOf(
                                "Користувачі" to "${r.totalUsers} (+${r.newUsersInPeriod})",
                                "Плейлісти" to "${r.totalPlaylists} (+${r.newPlaylistsInPeriod})",
                                "У плейлістах" to "${r.totalPlaylistTracks} (+${r.playlistTracksAddedInPeriod})",
                                "В обраному" to "${r.totalFavoriteTracks} (+${r.favoriteTracksAddedInPeriod})"
                            )
                        )

                        if (r.platformTopArtists.isNotEmpty()) {
                            StatsSectionCard(title = "Топ виконавці платформи") {
                                r.platformTopArtists.take(5).forEach { artist ->
                                    StatsKeyValueRow(
                                        label = artist.artist.ifBlank { "Невідомий" },
                                        value = "${artist.trackCount} тр."
                                    )
                                }
                            }
                        }

                        if (r.platformSourceDistribution.isNotEmpty()) {
                            StatsSectionCard(title = "Джерела на платформі") {
                                r.platformSourceDistribution.forEach { src ->
                                    StatsKeyValueRow(
                                        label = src.source.uppercase(),
                                        value = "${src.count} (${src.percentage}%)"
                                    )
                                }
                            }
                        }
                    }

                    // Public Overview Section
                    if (publicOverview != null) {
                        val o = publicOverview!!
                        StatsSectionCard(title = stringResource(R.string.stats_overview_section)) {
                            StatsKeyValueRow("Статус API", o.status)
                            StatsKeyValueRow("Публічні плейлісти", "${o.totalPublicPlaylistsCount}")
                            StatsKeyValueRow("Публічні треки", "${o.totalPublicTracksCount}")
                            if (o.supportedSources.isNotEmpty()) {
                                Text(
                                    text = "Джерела: ${o.supportedSources.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    color = DarkRefTheme.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = stringResource(R.string.btn_close),
                    color = DarkRefTheme.BackgroundDark,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatsMetricGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkRefTheme.SurfaceCardElevated)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = value,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkRefTheme.AccentMint,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = DarkRefTheme.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkRefTheme.SurfaceCardElevated)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkRefTheme.TextPrimary
        )
        content()
    }
}

@Composable
private fun StatsKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = DarkRefTheme.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkRefTheme.TextPrimary
        )
    }
}
