package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicplayer.android.ui.theme.GlassTheme

/**
 * Creates an ambient glowing background that reflects through Liquid Glass panels.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassTheme.BackgroundDark,
                        GlassTheme.BackgroundDarkEnd,
                        GlassTheme.BackgroundDark
                    )
                )
            )
    ) {
        // Ambient fluid light orbs
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-40).dp)
                .blur(80.dp)
                .clip(CircleShape)
                .background(GlassTheme.AmbientGlow1)
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .offset(x = 220.dp, y = 240.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(GlassTheme.AmbientGlow2)
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .offset(x = (-60).dp, y = 560.dp)
                .blur(85.dp)
                .clip(CircleShape)
                .background(GlassTheme.AmbientGlow3)
        )

        content()
    }
}
