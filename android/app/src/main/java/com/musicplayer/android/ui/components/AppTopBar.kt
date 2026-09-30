package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicplayer.android.R
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun AppTopBar(
    currentUser: String?,
    isOnline: Boolean,
    onAccountClick: () -> Unit,
    onEqualizerClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TOP-LEFT: Greeting & User Name (per reference design)
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onAccountClick() }
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Text(
                text = if (currentUser != null) "Вітаємо 👋" else "Привіт 👋",
                fontSize = 12.sp,
                color = DarkRefTheme.TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentUser ?: stringResource(R.string.account_guest),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkRefTheme.TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) DarkRefTheme.AccentMint else Color(0xFFFF5252))
                )
            }
        }

        // TOP-RIGHT: Tools (Equalizer, Settings) - only rendered if actions are provided
        if (onEqualizerClick != null || onSettingsClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onEqualizerClick != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkRefTheme.SurfaceCardElevated)
                            .clickable { onEqualizerClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Equalizer",
                            tint = DarkRefTheme.TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (onSettingsClick != null) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkRefTheme.SurfaceCardElevated)
                            .clickable { onSettingsClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = DarkRefTheme.TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}
