package com.mangotv.app.ui.player

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

/**
 * Thin wrapper around Media3's PlayerView with its built-in controller
 * disabled — every control in this app is custom Compose UI drawn on top,
 * PlayerView here only owns the video Surface/TextureView selection and
 * aspect-ratio handling.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    // FIT (letterboxed, never crops) is right for actual video content,
    // where cropping could cut off picture the user actually wants to see.
    // A caller showing decorative video rather than something to watch
    // frame-accurately can pass ZOOM instead to fill the screen edge-to-edge
    // with no letterboxing.
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
                // Subtitle size and colour from Settings > Subtitles; the text keeps a dark outline so it stays readable on any picture.
                val size = DevicePlayerPrefs.subtitleSize(context)
                val color = DevicePlayerPrefs.subtitleColor(context)
                subtitleView?.setFractionalTextSize(size.fraction)
                subtitleView?.setStyle(
                    CaptionStyleCompat(color.argb, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null)
                )
            }
        }
    )
}
