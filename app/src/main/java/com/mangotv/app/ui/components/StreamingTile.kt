package com.mangotv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.data.model.Content
import com.mangotv.app.ui.home.STREAMING_LOGOS
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** How many tiles are across the screen, whatever its size (as on the web app). */
private const val TILES_ACROSS = 5

/** A logo drawn all white or all black on its service's own tile colour. */
private enum class LogoTint { White, Black }

/**
 * One service's own tile look: its colour (what its app icon sits on), an optional gradient over it, how its logo is drawn, and the colour of the light
 * beneath the tile (a quiet [rest] one, the full [hover] one when focused). The values are the web app's (`StreamingTiles.tsx`, `BRAND`). A service
 * with no entry here is a black tile with its name in white.
 */
private class Brand(
    val bg: Color,
    val gradientAngle: Float? = null,
    val gradient: List<Pair<Float, Color>> = emptyList(),
    val tint: LogoTint? = null,
    val glow: Color,
    val hover: Float,
    val rest: Float
)

private fun rgb(hex: Long) = Color(0xFF000000 or hex)

private val BRANDS: Map<String, Brand> = mapOf(
    "netflix" to Brand(bg = rgb(0x000000), glow = Color(229, 9, 20), hover = 0.55f, rest = 0.12f),
    "disney" to Brand(
        bg = rgb(0x003f4f), gradientAngle = 175f,
        gradient = listOf(0f to rgb(0x011828), 0.35f to rgb(0x002f3d), 0.70f to rgb(0x015e70), 0.88f to rgb(0x0098a5), 1f to rgb(0x2bbcc1)),
        glow = Color(30, 190, 200), hover = 0.55f, rest = 0.14f
    ),
    "prime" to Brand(
        bg = rgb(0x1d2547), gradientAngle = 80f,
        gradient = listOf(0f to rgb(0x141721), 0.30f to rgb(0x1d2547), 0.52f to rgb(0x294071), 0.80f to rgb(0x346b9f), 1f to rgb(0x4089c0)),
        tint = LogoTint.White, glow = Color(64, 137, 192), hover = 0.6f, rest = 0.24f
    ),
    "max" to Brand(bg = rgb(0xffffff), glow = Color(140, 90, 255), hover = 0.5f, rest = 0.26f),
    "apple" to Brand(bg = rgb(0x000000), glow = Color(190, 190, 205), hover = 0.4f, rest = 0.16f),
    "paramount" to Brand(bg = rgb(0x0064ff), tint = LogoTint.White, glow = Color(0, 100, 255), hover = 0.6f, rest = 0.28f),
    "hulu" to Brand(bg = rgb(0x1ce783), tint = LogoTint.White, glow = Color(28, 231, 131), hover = 0.5f, rest = 0.12f),
    "peacock" to Brand(bg = rgb(0x000000), glow = Color(255, 190, 40), hover = 0.4f, rest = 0.12f),
    "crunchyroll" to Brand(bg = rgb(0xf47521), tint = LogoTint.White, glow = Color(244, 117, 33), hover = 0.6f, rest = 0.16f),
    "skyshowtime" to Brand(
        bg = rgb(0x4a38e6), gradientAngle = 135f, gradient = listOf(0f to rgb(0x6a3df0), 1f to rgb(0x2b2fe0)),
        glow = Color(106, 61, 240), hover = 0.6f, rest = 0.3f
    ),
    "britbox" to Brand(bg = rgb(0x0d273d), glow = Color(120, 190, 235), hover = 0.5f, rest = 0.16f),
    "itvx" to Brand(bg = rgb(0xdfeb55), tint = LogoTint.Black, glow = Color(223, 235, 85), hover = 0.5f, rest = 0.12f),
    "iplayer" to Brand(bg = rgb(0x000000), glow = Color(255, 70, 150), hover = 0.5f, rest = 0.16f),
    "sky" to Brand(bg = rgb(0xffffff), glow = Color(160, 90, 255), hover = 0.5f, rest = 0.24f),
    "discovery" to Brand(bg = rgb(0x000000), glow = Color(255, 150, 60), hover = 0.5f, rest = 0.14f),
    "shudder" to Brand(bg = rgb(0x000000), glow = Color(220, 20, 30), hover = 0.55f, rest = 0.12f),
    "channel4" to Brand(bg = rgb(0x000000), glow = Color(170, 255, 140), hover = 0.45f, rest = 0.10f)
)

/** A CSS `linear-gradient(<angle>deg, stops)` over a box of [size] (0deg is toward the top, 90deg toward the right). */
private fun cssLinearGradient(angleDeg: Float, stops: List<Pair<Float, Color>>, size: Size): Brush {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = sin(a).toFloat()
    val dy = (-cos(a)).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(center.x - dx * length / 2f, center.y - dy * length / 2f)
    val end = Offset(center.x + dx * length / 2f, center.y + dy * length / 2f)
    return Brush.linearGradient(colorStops = stops.toTypedArray(), start = start, end = end)
}

/** The glass on a tile (the web's `::before`): a soft light from the top left, a faint diagonal streak, a light-to-dark wash, and a bright top rim. */
private fun DrawScope.drawGlass(corner: Float) {
    val white = Color.White
    // radial-gradient(circle at 18% 0%, white .26, transparent 55%) -- the circle reaches the farthest corner
    val cx = size.width * 0.18f
    val reach = hypot(size.width - cx, size.height)
    drawRect(
        Brush.radialGradient(colorStops = arrayOf(0f to white.copy(alpha = 0.26f), 0.55f to white.copy(alpha = 0f), 1f to white.copy(alpha = 0f)), center = Offset(cx, 0f), radius = reach)
    )
    // linear-gradient(115deg, transparent 32%, white .1 44%, transparent 56%)
    drawRect(cssLinearGradient(115f, listOf(0f to white.copy(alpha = 0f), 0.32f to white.copy(alpha = 0f), 0.44f to white.copy(alpha = 0.10f), 0.56f to white.copy(alpha = 0f), 1f to white.copy(alpha = 0f)), size))
    // linear-gradient(180deg, white .06, black .14)
    drawRect(Brush.verticalGradient(listOf(white.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.14f))))
    // a bright rim along the top, fading to a faint ring (inset 0 1px 0 white .45, inset 0 0 0 1px white .08)
    val rim = 1.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(0f to white.copy(alpha = 0.45f), 0.25f to white.copy(alpha = 0.08f), 1f to white.copy(alpha = 0.08f)),
        topLeft = Offset(rim / 2f, rim / 2f),
        size = Size(size.width - rim, size.height - rim),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = rim)
    )
}

/**
 * One streaming-service tile on Home (Netflix, Disney+ ...): Continue Watching's shape, five across, the service's logo on its own colour in a rounded
 * glass slab with a coloured light beneath it (the web app's "glass" look, `StreamingTiles.tsx` and `.row--streaming` in `components.css`). Focused, it
 * lifts, tips back a little and the light beneath it grows. [content] is a stand-in: its id is `streaming:<service>` and its title the service's name.
 */
@Composable
fun StreamingTile(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    // Home shrinks its poster rows with this (see ContentCard); it is only needed here for the gap between tiles
    posterScale: Float = 1f
) {
    val service = content.id.removePrefix("streaming:")
    val brand = BRANDS[service]
    val logo = STREAMING_LOGOS[service]
    var focused by remember { mutableStateOf(false) }
    val focus by animateFloatAsState(targetValue = if (focused) 1f else 0f, animationSpec = MangoMotion.focusTween, label = "tileFocus")

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gap = MangoDimens.CardSpacing * posterScale
    val width = (screenWidth - MangoDimens.ScreenPaddingHorizontal * 2 - gap * (TILES_ACROSS - 1)) / TILES_ACROSS
    val height = width * (MangoDimens.ContinueWatchingHeight.value / MangoDimens.ContinueWatchingWidth.value)
    val corner = 14.dp
    val shape = RoundedCornerShape(corner)
    val density = LocalDensity.current
    val glow = brand?.glow ?: Color.White.copy(alpha = 0.25f)
    val glowAlpha = if (brand != null) brand.rest + (brand.hover - brand.rest) * focus else 0.12f + 0.13f * focus

    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                // the light beneath the tile: a soft ellipse under its lower edge (drawn, not a blurred shadow, which older Android versions do not do)
                .drawBehind {
                    val radius = size.width * 0.5f
                    // worked out here: inside withTransform's own block Dp.toPx() has no receiver to use
                    val center = Offset(size.width / 2f, size.height + 4.dp.toPx())
                    withTransform({ scale(scaleX = 1f, scaleY = 0.28f, pivot = center) }) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(glow.copy(alpha = glowAlpha), glow.copy(alpha = 0f)), center = center, radius = radius),
                            radius = radius,
                            center = center
                        )
                    }
                }
                // focused: lifts and tips back a little (the web's translateY(-6px) rotateX(4deg))
                .graphicsLayer {
                    translationY = -6.dp.toPx() * focus
                    rotationX = 4f * focus
                    cameraDistance = 16f * density.density
                }
        ) {
            TvFocusSurface(
                onClick = onClick,
                modifier = Modifier.width(width).height(height),
                shape = shape,
                focusedScale = 1.05f,
                focusedElevation = 12f,
                backgroundColor = brand?.bg ?: Color.Black,
                focusRequester = focusRequester,
                onFocusChanged = { isFocused -> focused = isFocused; onFocusChanged(isFocused) },
                bringIntoViewOnFocus = false
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().drawBehind {
                        if (brand?.gradientAngle != null && brand.gradient.isNotEmpty()) drawRect(cssLinearGradient(brand.gradientAngle, brand.gradient, size))
                        drawGlass(corner.toPx())
                    }
                )
                if (logo != null) {
                    Image(
                        painter = painterResource(logo),
                        contentDescription = content.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = when (brand?.tint) {
                            LogoTint.White -> ColorFilter.tint(Color.White)
                            LogoTint.Black -> ColorFilter.tint(Color.Black)
                            null -> null
                        },
                        modifier = Modifier.fillMaxSize().padding(horizontal = width * 0.18f, vertical = height * 0.14f)
                    )
                } else {
                    Text(
                        text = content.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (width.value * 0.11f).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 10.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = content.title,
            color = if (focused) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
