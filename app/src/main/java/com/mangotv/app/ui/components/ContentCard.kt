package com.mangotv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.ProgressFill
import com.mangotv.app.ui.theme.ProgressTrack
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import com.mangotv.app.ui.theme.WatchedGreen

/**
 * How a card looks on a streaming service's page (Netflix's: the Top 10 posters and the wide pictures under them, as on the web app): an exact [width]
 * (a poster is 2:3, a [wide] picture 16:9 with the title's logo at its bottom left), small corners, no caption under it, the service's mark ([badge]) at
 * the top left and the watched tick at the top right, so the two never sit on each other.
 */
class ServiceCardStyle(
    val width: Dp,
    val wide: Boolean,
    val cornerRadius: Dp = 4.dp,
    val badge: (@Composable () -> Unit)? = null
)

private val IMDB_TITLE = Regex("^tt\\d+$")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentCard(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RowStyle = RowStyle.STANDARD,
    focusRequester: FocusRequester? = null,
    // Used by the movie detail page to fit its whole layout on one screen
    // without scrolling — Home and the TV show detail page never pass this,
    // so their card sizing is completely unaffected.
    compact: Boolean = false,
    // Independent of `compact` (a fixed 2/3 ratio tied to that specific
    // layout need) — an additional multiplier a caller can apply on top,
    // e.g. Home shrinking its poster rows. Defaults to 1f so every other
    // existing caller is unaffected.
    posterScale: Float = 1f,
    // The width of the row this card is in, when known: the streaming tiles (five across) are sized from it.
    rowWidth: androidx.compose.ui.unit.Dp? = null,
    // Reports this specific card's own focus state, distinct from a row's
    // aggregate "is any card in me focused" (see ContentRow.onFocusChanged)
    // -- e.g. My List uses this to remember exactly which title the user
    // was last hovering so returning from the nav bar re-lands on it.
    onFocusChanged: (Boolean) -> Unit = {},
    // A streaming service's page (see ServiceCardStyle); null everywhere else, so every other card is unchanged.
    serviceStyle: ServiceCardStyle? = null
) {
    if (style == RowStyle.STREAMING_SERVICES) {
        StreamingTile(content = content, onClick = onClick, modifier = modifier, focusRequester = focusRequester, onFocusChanged = onFocusChanged, posterScale = posterScale, rowWidth = rowWidth)
        return
    }
    val isContinueWatching = style == RowStyle.CONTINUE_WATCHING
    val scale = (if (compact) 2f / 3f else 1f) * posterScale
    val wideCard = serviceStyle?.wide == true
    val cornerRadius = serviceStyle?.cornerRadius ?: MangoDimens.CardCornerRadius
    val width = serviceStyle?.width ?: ((if (isContinueWatching) MangoDimens.ContinueWatchingWidth else MangoDimens.PosterWidth) * scale)
    val height = when {
        serviceStyle == null -> (if (isContinueWatching) MangoDimens.ContinueWatchingHeight else MangoDimens.PosterHeight) * scale
        wideCard -> width * 9f / 16f
        else -> width * 1.5f
    }
    var focused by remember { mutableStateOf(false) }
    val lift = animateFloatAsState(targetValue = if (focused) 1f else 0f, animationSpec = MangoMotion.focusTween, label = "posterLift")
    val imageUrl = when {
        isContinueWatching -> content.backdropUrl
        wideCard -> content.backdropUrl ?: content.posterUrl
        else -> content.posterUrl
    }
    val titleStyle = if (scale < 0.85f) {
        androidx.compose.material3.MaterialTheme.typography.labelMedium
    } else {
        androidx.compose.material3.MaterialTheme.typography.titleMedium
    }

    // Reads the app-wide menu state directly rather than taking an
    // onLongClick param from the caller -- every ContentCard everywhere
    // (Home's rows, My List, Movies/TV Shows/Genre grids, Detail's Similar
    // row) should offer the same quick-actions menu with zero extra
    // plumbing at each of those call sites. See CardActionsMenu.kt's own
    // doc for why this lives on a CompositionLocal.
    val cardActionsMenu = LocalCardActionsMenu.current

    Column(modifier = modifier.width(width)) {
        TvFocusSurface(
            onClick = onClick,
            onLongClick = { cardActionsMenu.open(content) },
            onLongClickKeyReleased = { requester -> cardActionsMenu.armFocus(requester) },
            modifier = Modifier
                .width(width)
                .height(height)
                // A focused poster lifts a little as well as growing (like the web app's posters); Continue Watching keeps its own look
                // (only the drawing moves, not the card's place, so the remote's focus search is not thrown off by a card that has really moved)
                .drawWithContent {
                    translate(top = if (isContinueWatching) 0f else -4.dp.toPx() * lift.value) { this@drawWithContent.drawContent() }
                },
            shape = RoundedCornerShape(cornerRadius),
            backgroundColor = MangoSurface,
            focusRequester = focusRequester,
            onFocusChanged = { isFocused -> focused = isFocused; onFocusChanged(isFocused) },
            // The enclosing LazyRow already has its own built-in
            // scroll-into-view behavior that runs as focus moves from card
            // to card. Leaving this surface's own explicit bringIntoView
            // call enabled meant two slightly-independent scroll animations
            // running at once for the same focus change, which showed up as
            // a very small shimmer/jitter on the posters while scrolling
            // through a row. The row's own scrolling is sufficient on its
            // own, so this one is redundant — same fix already applied to
            // the hero's buttons for the equivalent double-scroll issue.
            bringIntoViewOnFocus = false
        ) {
            AsyncImage(
                model = rememberOpaqueImageRequest(imageUrl),
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // A faint light rim at rest, so a light poster sits on the dark page instead of glaring (the web app's poster rim); gone while focused, when the
            // focus ring takes over. Continue Watching cards are 16:9 backdrops with their own look.
            if (!isContinueWatching && !focused) {
                // (not faded with a layer of its own per card: a grid has dozens of cards on screen, and a layer each made scrolling heavy)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.08f))),
                            RoundedCornerShape(cornerRadius)
                        )
                )
            }

            // Always visible (not gated on focus like the rating/progress
            // overlays below) -- a solid-filled circle rather than a
            // translucent one so the tick reads clearly against poster art
            // of any color, at any focus state.
            if (content.watched) {
                Box(
                    modifier = Modifier
                        // on a streaming service's page the service's mark holds the top left corner
                        .align(if (serviceStyle != null) Alignment.TopEnd else Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(WatchedGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            serviceStyle?.badge?.let { badge ->
                Box(Modifier.align(Alignment.TopStart).padding(top = 5.dp, start = 6.dp)) { badge() }
            }
            if (wideCard) {
                // The title's logo at the bottom left, on a soft shade. A title with an IMDb id goes straight to the logo picture on Metahub (Cinemeta's logos
                // live there) instead of waiting for a details lookup; the lookup is the fallback when Metahub has none.
                val direct = if (IMDB_TITLE.matches(content.id) && content.logoUrl == null) "https://images.metahub.space/logo/medium/${content.id}/img" else null
                var directFailed by remember(content.id) { mutableStateOf(false) }
                val looked by rememberTitleLogo(content, enabled = direct == null || directFailed)
                val logo = if (direct != null && !directFailed) direct else looked
                if (logo != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(height * 0.5f)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    )
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        onError = { if (direct != null && !directFailed) directFailed = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 6.dp)
                            .widthIn(max = width * 0.52f)
                            .heightIn(max = height * 0.34f)
                    )
                }
            }

            val ratingOverlayAlpha = animateFloatAsState(
                targetValue = if (focused && !isContinueWatching && !wideCard) 1f else 0f,
                label = "ratingOverlayAlpha"
            )
            val ratingOverlayGradient = remember {
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = ratingOverlayAlpha.value }
                    .background(ratingOverlayGradient)
                    .padding(10.dp)
            ) {
                content.rating?.let {
                    Text(
                        text = "★ ${"%.1f".format(it)}",
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (isContinueWatching) {
                // The title's logo, small, in the top left corner over a soft darkening so a white logo reads on a pale picture (as the web app's Continue Watching poster)
                val logoUrl by rememberTitleLogo(content, enabled = true)
                logoUrl?.let { url ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .height(height * 0.36f)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    )
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.TopStart,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp * scale.coerceAtLeast(0.7f), top = 6.dp * scale.coerceAtLeast(0.7f))
                            .widthIn(max = width * 0.34f)
                            .heightIn(max = height * 0.18f)
                    )
                }
                val progressGradient = remember {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(progressGradient)
                )
                content.watchProgress?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(bottom = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(ProgressTrack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.fraction)
                                    .height(4.dp)
                                    .background(ProgressFill)
                            )
                        }
                    }
                }
            }
        }

        if (serviceStyle == null) CardCaptions(content, focused, isContinueWatching, scale, titleStyle)
    }
}

/** The title (and year, or why it was picked) under a poster. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardCaptions(
    content: Content,
    focused: Boolean,
    isContinueWatching: Boolean,
    scale: Float,
    titleStyle: androidx.compose.ui.text.TextStyle
) {
    Column {
        Spacer(Modifier.height(if (scale < 0.85f) 6.dp else 8.dp))

        Text(
            text = content.title,
            color = if (focused) TextPrimary else TextSecondary,
            style = titleStyle,
            maxLines = 1,
            // Ellipsis is fine for every unfocused card -- there are
            // usually a dozen+ on screen in a row/grid at once, and
            // scrolling all of their titles simultaneously would be
            // visual noise. Only the focused card's title -- the one the
            // user is actually reading -- marquees, and only clips
            // (rather than truncating with "...") while it does, so a
            // title too long for the card is still fully readable.
            overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = if (focused) Modifier.basicMarquee() else Modifier
        )

        if (isContinueWatching) {
            val progressLabel = content.watchProgress?.let { p ->
                if (p.seasonNumber != null && p.episodeNumber != null) {
                    "S${p.seasonNumber} E${p.episodeNumber}"
                } else null
            }
            if (progressLabel != null) {
                Text(
                    text = progressLabel,
                    color = TextTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else if (content.recommendReason != null) {
            // "Picked for you" says why, in place of the year ("Because you liked X").
            Text(
                text = content.recommendReason,
                color = TextTertiary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            content.year?.let {
                Text(
                    text = it.toString(),
                    color = TextTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
