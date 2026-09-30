package com.musicplayer.android.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object DarkRefTheme {
    // Pure deep modern background from reference
    val BackgroundDark = Color(0xFF0C0D12)
    val SurfaceDark = Color(0xFF14151C)
    val SurfaceCard = Color(0xFF191B24)
    val SurfaceCardElevated = Color(0xFF202330)

    // Text hierarchy
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E92A4)
    val TextMuted = Color(0xFF5E6273)

    // Accents & Bloom
    val AccentPrimary = Color(0xFF6C5CE7)
    val AccentPink = Color(0xFFFF477E)
    val AccentCyan = Color(0xFF00CEC9)
    val AccentMint = Color(0xFF00E676)
    val AccentMintGlow = Color(0xFF69F0AE)
    val PlayButtonDark = Color(0x991E202B)

    // Gradients for cards (bottom blurred overlay)
    val CardOverlayGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color(0x770C0D12),
            Color(0xDD0C0D12),
            Color(0xF50C0D12)
        )
    )

    val TopBarFadeGradient = Brush.verticalGradient(
        colors = listOf(
            BackgroundDark,
            BackgroundDark.copy(alpha = 0.85f),
            Color.Transparent
        )
    )

    val BottomNavGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            BackgroundDark.copy(alpha = 0.92f),
            BackgroundDark
        )
    )
}
