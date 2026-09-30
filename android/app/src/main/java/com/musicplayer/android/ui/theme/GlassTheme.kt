package com.musicplayer.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object GlassTheme {
    // Deep fluid palette
    val BackgroundDark = Color(0xFF0B0E17)
    val BackgroundDarkEnd = Color(0xFF141929)
    val AmbientGlow1 = Color(0x356C5CE7)
    val AmbientGlow2 = Color(0x3000CEC9)
    val AmbientGlow3 = Color(0x28FD79A8)

    // Glass surfaces
    val GlassSurface = Color(0x2EFFFFFF)
    val GlassSurfaceDark = Color(0x40161B2C)
    val GlassSurfaceCard = Color(0x331D243B)
    val GlassSurfaceLight = Color(0x45FFFFFF)

    // Neon & Accent Colors
    val PrimaryAccent = Color(0xFF7C5CFF)
    val SecondaryAccent = Color(0xFF00D2D3)
    val PinkAccent = Color(0xFFFF5E7E)
    val TextPrimary = Color(0xFFF1F2F6)
    val TextSecondary = Color(0xFFA4B0BE)

    // Gradients
    val AccentGradient = Brush.horizontalGradient(
        listOf(PrimaryAccent, SecondaryAccent)
    )

    val GlassBorderGradient = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.38f),
            Color.White.copy(alpha = 0.12f),
            Color.White.copy(alpha = 0.04f)
        )
    )

    val GlassCardBorder = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.06f)
        )
    )

    val VinylGradient = Brush.radialGradient(
        listOf(
            Color(0xFF2D3436),
            Color(0xFF1E272E),
            Color(0xFF0F141C)
        )
    )
}

/**
 * Applies a luxurious liquid glass effect with soft border reflection, inner transparency, and optional shadow.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = GlassTheme.GlassSurfaceDark,
    borderColor: Brush = GlassTheme.GlassCardBorder,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 0.dp
): Modifier = this
    .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
    .clip(shape)
    .background(backgroundColor)
    .border(width = borderWidth, brush = borderColor, shape = shape)
