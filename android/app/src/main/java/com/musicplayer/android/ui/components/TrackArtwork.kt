package com.musicplayer.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicplayer.android.ui.theme.DarkRefTheme

@Composable
fun TrackArtwork(
    artworkUrl: String?,
    isLiveStream: Boolean = false,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(DarkRefTheme.SurfaceCardElevated),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val sizePx = with(LocalDensity.current) { size.roundToPx() }
            val request = remember(artworkUrl, sizePx) {
                ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(sizePx, sizePx)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }

            ArtworkPlaceholder(isLiveStream = isLiveStream, size = size)

            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        } else {
            ArtworkPlaceholder(isLiveStream = isLiveStream, size = size)
        }
    }
}

@Composable
private fun ArtworkPlaceholder(
    isLiveStream: Boolean,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(DarkRefTheme.SurfaceCardElevated),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiveStream) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = if (isLiveStream) DarkRefTheme.AccentMint else DarkRefTheme.TextSecondary,
            modifier = Modifier.size(size * 0.44f)
        )
    }
}
