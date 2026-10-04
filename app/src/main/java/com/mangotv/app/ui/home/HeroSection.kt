package com.mangotv.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.imageLoader
import android.graphics.Bitmap
import coil.request.ImageRequest
import com.mangotv.app.data.trailer.TrailerLauncher
import coil.compose.AsyncImagePainter
import com.mangotv.app.data.model.Content
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester

private const val HERO_ROTATE_MILLIS = 9000L

private val HeroTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.85f),
    offset = Offset(0f, 2f),
    blurRadius = 10f
)

private const val HERO_FIRST_IMAGES = 4
private const val HERO_REST_DELAY_MS = 2_500L

@Composable
fun HeroSection(
    items: List<Content>,
    playFocusRequester: FocusRequester,
    onPlay: (Content) -> Unit,
    onAddToList: (Content) -> Unit,
    onMoreInfo: (Content) -> Unit,
    modifier: Modifier = Modifier,
    // Looks up a title's trailer for the Trailer button beside Play; null leaves the button out.
    findTrailer: (suspend (Content) -> String?)? = null,
    navUpFocusRequester: FocusRequester? = null,
    onNavigateUpPastHero: () -> Unit = {},
    onNavigateDownFromHero: () -> Unit = {},
    savedIds: Set<String> = emptySet()
) {
    if (items.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    val current = items[index % items.size]
    val context = LocalContext.current

    // Fetch every picture the hero will show now, in rotation order, so each slide appears instantly instead of
    // loading as it arrives. Only warms Coil's caches; nothing is drawn from here.
    LaunchedEffect(items) {
        val imageLoader = context.imageLoader
        val images = heroImages(items)
        // The first slides straight away; the rest after a pause, so on a cold boot they don't compete with the
        // first slide and the poster rows for the same connection.
        images.take(HERO_FIRST_IMAGES).forEach { image ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(image.url).build())
        }
        delay(HERO_REST_DELAY_MS)
        images.drop(HERO_FIRST_IMAGES).forEach { image ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(image.url).build())
        }
    }

    // Trailer lookups, once per title as its slide comes round: a key present means "looked up", whatever the answer
    // (null = no trailer found). A looked-up title is never asked again while the hero is on screen.
    val trailers = remember { mutableStateMapOf<String, String?>() }
    LaunchedEffect(current.id) {
        if (findTrailer != null && !trailers.containsKey(current.id)) {
            trailers[current.id] = findTrailer(current)
        }
    }

    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HERO_ROTATE_MILLIS)
            index = (index + 1) % items.size
        }
    }

    // The backdrop's minimum cinematic height, as a floor rather than a
    // fixed height: title/description length varies, and a fixed/exact
    // height risks the bottom-aligned text column needing more room than
    // that — since Box doesn't clip by default, it would silently overflow
    // above the box's own top edge, invisible and unreachable by scrolling.
    // matchParentSize() below defers the backdrop's size to whatever the
    // text column actually needs (at least this floor, more if required),
    // rather than the other way around.
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    // How far a slide travels: the whole screen width, so the picture and the text cross together.
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }

    // Decode the next two slides' pictures NOW, into Coil's memory cache, with exactly the request the slide will use (same size and bitmap
    // format, so it is a cache hit): until now only the download was warmed, so every slide change decoded a full-screen picture just as the
    // slide started -- the picture popped in late and the slide stuttered. Two ahead, not all of them, to keep the memory cache for the rows.
    LaunchedEffect(index, items) {
        if (items.size <= 1) return@LaunchedEffect
        val imageLoader = context.imageLoader
        for (ahead in 1..2) {
            val url = items[(index + ahead) % items.size].backdropUrl ?: continue
            imageLoader.enqueue(heroBackdropRequest(context, sharpBackdrop(url) ?: url, screenWidthPx, screenHeightPx))
        }
    }
    val heroMinHeight = screenHeightDp * 0.82f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = heroMinHeight)
            // graphicsLayer's own clip (set on the backdrop's AsyncImage
            // below) only clips content to that layer's own shape, which
            // travels WITH the scaleX/scaleY transform — it does nothing to
            // stop the now-larger scaled layer from visually extending past
            // THIS Box's bounds, since Box doesn't clip its children by
            // default. Clipping has to happen here, at the parent, so the
            // Ken Burns zoom stays contained within the hero regardless of
            // scale.
            .clipToBounds()
    ) {
        // Each title is a slide: the next one slides in from the right as the old one slides out to the left (as on
        // the web). Keyed by id, not by the Content itself: a watched-flag refresh hands the hero an equal-looking copy
        // of the same title, which must not replay the slide.
        AnimatedContent(
            targetState = current.id,
            transitionSpec = { heroSlideTransition(screenWidthPx) },
            label = "heroBackdrop",
            modifier = Modifier.matchParentSize()
        ) { id ->
            // The slow zoom only runs while the slide is settled: not while it slides in or out (two full-screen pictures zooming and
            // sliding together was too much for a Fire TV), and the outgoing one just stays as it was.
            val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
            KenBurnsBackdrop(
                url = (items.firstOrNull { it.id == id } ?: current).backdropUrl,
                zoom = settled,
                widthPx = screenWidthPx,
                heightPx = screenHeightPx
            )
        }

        // A much lighter left-to-right gradient than before — just enough
        // of an assist that text stays readable, without darkening the
        // artwork into "dark spots" across most of the hero. Text itself
        // also carries a drop shadow (HeroTextShadow) as the primary
        // legibility mechanism now, so this can stay subtle.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MangoBackground.copy(alpha = 0.55f),
                            MangoBackground.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom fade so the hero blends into the row content below. This
        // now ramps all the way to a fully opaque MangoBackground at the
        // very bottom edge, matching the solid background the "Popular"
        // row sits on — previously it topped out at 0.4 alpha, so the
        // backdrop was still partly visible right up to the hero's bottom
        // edge and cut off abruptly instead of blending in.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MangoBackground.copy(alpha = 0.15f),
                            MangoBackground.copy(alpha = 0.6f),
                            MangoBackground
                        )
                    )
                )
        )

        // Which title this is, out of how many: one small dot per title, the current one a longer pill, at the bottom right
        // (as on the web). Only shown, not focusable: the hero rotates by itself, and a focus stop here would sit in the
        // way of the remote moving between the buttons and the rows below.
        if (items.size > 1) {
            HeroPageDots(
                count = items.size,
                selected = index % items.size,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MangoDimens.ScreenPaddingHorizontal, bottom = 28.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = MangoDimens.ScreenPaddingHorizontal,
                    end = MangoDimens.ScreenPaddingHorizontal,
                    bottom = 56.dp
                )
                .widthIn(max = 760.dp),
            // The column can be measured taller than its content needs (it's
            // floored to heroMinHeight above) — pack content to the bottom
            // of that space so it stays visually pinned to the hero's
            // bottom edge instead of stranding a gap below the buttons.
            verticalArrangement = Arrangement.Bottom
        ) {
            // The title, details and description slide with the picture; the buttons below stay where they are, so
            // the remote's focus is never disturbed by the rotation. Same slide as the backdrop, in step with it.
            AnimatedContent(
                targetState = current.id,
                transitionSpec = { heroSlideTransition(screenWidthPx) },
                contentAlignment = Alignment.BottomStart,
                label = "heroText"
            ) { id ->
                val item = items.firstOrNull { it.id == id } ?: current
                Column {
                // Prefer the addon-supplied clearlogo (a stylized title graphic,
                // the way Stremio/Nuvio render hero titles) when one's
                // available, falling back to plain text otherwise — catalog
                // preview responses don't always carry a logo the way a full
                // meta fetch does, so this is frequently the fallback path here
                // even when it isn't on the detail page.
                if (item.logoUrl != null) {
                    // A fully fixed box (not height-plus-max-width) so the
                    // rendered logo is always the same footprint regardless of
                    // the source image's own aspect ratio — a height-plus-
                    // widthIn(max) combination let a wide logo render far
                    // beyond the intended cap.
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.size(width = 380.dp, height = 90.dp)
                    )
                } else {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.displayMedium.copy(shadow = HeroTextShadow),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row {
                    val metaParts = buildList {
                        item.year?.let { add(it.toString()) }
                        item.ageRating?.let { add(it) }
                        item.runtimeMinutes?.let { add("${it / 60}h ${it % 60}m") }
                        item.rating?.let { add("★ ${"%.1f".format(it)}") }
                    }
                    // Sized down from the shared titleMedium/bodyMedium/bodyLarge
                    // tokens via .copy() (font family/weight/letter-spacing still
                    // come from them) rather than editing those tokens directly
                    // in Type.kt -- this is scoped to the hero's own meta/genre/
                    // description text specifically, not every other screen that
                    // happens to use the same named styles.
                    Text(
                        text = metaParts.joinToString("   •   "),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            shadow = HeroTextShadow
                        ),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.genres.joinToString("  ·  ") { it.name },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            shadow = HeroTextShadow
                        )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = item.description,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        shadow = HeroTextShadow
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Handling UP here (imperatively) rather than only through
            // focusProperties/focusUp: that mechanism points at a
            // FocusRequester, and if it ever targeted something inside the
            // lazily-composed list that's been scrolled far enough to be
            // disposed, using it throws. This scroll-then-focus path only
            // ever targets the always-composed nav bar overlay, so it can't
            // hit that. focusUp below still points at navUpFocusRequester
            // as a harmless fallback in case this doesn't consume the event.
            Row(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    when {
                        // Consume BOTH the KeyDown and KeyUp phases of UP —
                        // leaving KeyUp unconsumed let it fall through to
                        // Compose's default focus-move handling (which
                        // appears to run its own scroll-into-view
                        // independent of bringIntoViewOnFocus), causing a
                        // second unwanted scroll after the imperative one
                        // below already ran on KeyDown.
                        event.key == Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateUpPastHero()
                            }
                            true
                        }
                        // DOWN is intentionally NOT consumed: moving from
                        // the hero into the first content row is supposed
                        // to scroll (that's the one legitimate case), so
                        // default focus-move handling is left to do it.
                        // This only flags that the hero/nav "must stay
                        // static" region is being left, so HomeContent's
                        // watchdog stops correcting the list back to (0, 0).
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            onNavigateDownFromHero()
                            false
                        }
                        else -> false
                    }
                }
            ) {
                // bringIntoViewOnFocus = false on all three: the hero is
                // sized to always fit within one viewport (see heroMinHeight
                // above), so these buttons are already fully visible
                // whenever the list is at the top — automatic scroll-into-
                // view has nothing legitimate to do here and was instead
                // firing on stale/pre-layout coordinates right after the
                // explicit scrollToItem(0, 0) in onNavigateUpPastHero,
                // causing a second, unwanted scroll. Returning to the hero
                // from a scrolled-down content row is handled explicitly by
                // ContentRow's onNavigateUpPastRow instead.
                MangoButton(
                    text = "Play",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(current) },
                    style = MangoButtonStyle.LIGHT,
                    focusRequester = playFocusRequester,
                    focusUp = navUpFocusRequester,
                    bringIntoViewOnFocus = false
                )
                if (findTrailer != null) {
                    Spacer(Modifier.width(16.dp))
                    val trailerId = trailers[current.id]
                    val lookedUp = trailers.containsKey(current.id)
                    // Dimmed (but still focusable, so the remote can land on it) until a trailer is found; pressing it
                    // then says why nothing opened.
                    MangoButton(
                        text = "Trailer",
                        icon = Icons.Filled.Theaters,
                        onClick = {
                            when {
                                trailerId != null -> TrailerLauncher.launch(context, trailerId)
                                !lookedUp -> Toast.makeText(context, "Looking for a trailer\u2026", Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(context, "No trailer found for this title", Toast.LENGTH_SHORT).show()
                            }
                        },
                        style = MangoButtonStyle.GLASS,
                        focusUp = navUpFocusRequester,
                        bringIntoViewOnFocus = false,
                        dimmed = trailerId == null
                    )
                }
                Spacer(Modifier.width(16.dp))
                val isSaved = current.id in savedIds
                HeroIconButton(
                    icon = if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (isSaved) "Remove from My List" else "Add to My List",
                    onClick = { onAddToList(current) },
                    focusUp = navUpFocusRequester
                )
                Spacer(Modifier.width(16.dp))
                HeroIconButton(
                    icon = Icons.Filled.Info,
                    contentDescription = "More Info",
                    onClick = { onMoreInfo(current) },
                    focusUp = navUpFocusRequester
                )
            }
        }
    }
}

/** One dot per hero title; the [selected] one is a longer, brighter pill. The change between them animates. */
@Composable
private fun HeroPageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { dotIndex ->
            val isSelected = dotIndex == selected
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = tween(durationMillis = MangoMotion.MediumMillis),
                label = "heroDotWidth"
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}

/**
 * The next title slides in from the right while the old one slides out to the left, [screenWidthPx] each, in step. A gentle ease-in-out over
 * 750 ms: the old curve (very fast start, very long tail) moved the pictures a long way in the first few frames, which on a Fire TV that drops a
 * few frames reads as a jolt; this one starts and ends softly and covers the same distance more evenly.
 */
private fun heroSlideTransition(screenWidthPx: Int): ContentTransform {
    val spec = tween<IntOffset>(durationMillis = HERO_SLIDE_MILLIS, easing = HeroSlideEasing)
    // `using` only exists inside the transition scope, so the size behaviour is passed to the constructor instead.
    return ContentTransform(
        targetContentEnter = slideInHorizontally(animationSpec = spec) { screenWidthPx },
        initialContentExit = slideOutHorizontally(animationSpec = spec) { -screenWidthPx },
        sizeTransform = SizeTransform(clip = false)
    )
}

private const val HERO_SLIDE_MILLIS = 750
private val HeroSlideEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/** The one request both the warm-up and the slide use, so the picture is already decoded (same key) when the slide starts. */
private fun heroBackdropRequest(context: android.content.Context, url: String?, widthPx: Int, heightPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .bitmapConfig(Bitmap.Config.RGB_565)
        .size(widthPx, heightPx)
        .build()

@Composable
private fun KenBurnsBackdrop(url: String?, zoom: Boolean, widthPx: Int, heightPx: Int, modifier: Modifier = Modifier) {
    // The backdrop shows nothing until it loads (no placeholder), so zooming it before then is pointless and, now that Home can paint from
    // cache the instant the app opens, a per-frame transform competing with every other loading row image on a cold boot. It starts from 1x
    // each time a slide settles and zooms slowly over the slide's whole time on screen (it used to bounce back and forth forever, on both
    // the incoming and outgoing picture); the outgoing slide keeps whatever zoom it had reached. The scale is read inside graphicsLayer, so
    // a frame only redraws the layer instead of recomposing anything.
    var isLoaded by remember(url) { mutableStateOf(false) }
    val scale = remember(url) { Animatable(1f) }
    LaunchedEffect(zoom, isLoaded) {
        if (zoom && isLoaded) {
            scale.animateTo(KEN_BURNS_END_SCALE, tween((HERO_ROTATE_MILLIS + HERO_SLIDE_MILLIS).toInt(), easing = LinearEasing))
        }
    }

    // Ask for the sharper size where the address says which sizes exist (see sharpBackdrop), and fall back to the
    // address as given if that one fails to load.
    val sharpUrl = remember(url) { sharpBackdrop(url) }
    var sharpFailed by remember(url) { mutableStateOf(false) }
    val requestUrl = if (sharpFailed || sharpUrl == null) url else sharpUrl
    val context = LocalContext.current
    val request = remember(requestUrl, widthPx, heightPx) { heroBackdropRequest(context, requestUrl, widthPx, heightPx) }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) isLoaded = true
            if (state is AsyncImagePainter.State.Error && sharpUrl != null && sharpUrl != url) sharpFailed = true
        },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val appliedScale = if (isLoaded) scale.value else 1f
                scaleX = appliedScale
                scaleY = appliedScale
            }
    )
}

private const val KEN_BURNS_END_SCALE = 1.06f
