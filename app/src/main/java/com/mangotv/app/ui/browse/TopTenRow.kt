package com.mangotv.app.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.model.Content
import com.mangotv.app.ui.components.ContentCard
import com.mangotv.app.ui.components.ServiceCardStyle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/** The row's heading. */
const val TOP_TEN_TITLE = "Top 10 Today"

/** A Top 10 poster's width (a poster is 2:3); the number behind it is 1.2 times as tall. */
private val TOP_TEN_POSTER_WIDTH = 112.dp

/** The service's red N, drawn as three bars (as the web app's `NetflixN`), for a corner of a poster. */
@Composable
fun NetflixN(width: Dp) {
    val red = Color(0xFFE50914)
    val darkRed = Color(0xFFB1060F)
    Canvas(Modifier.size(width = width, height = width * 40f / 24f)) {
        val w = size.width
        val h = size.height
        drawRect(red, Offset(0f, 0f), Size(w / 3f, h))
        drawRect(red, Offset(w * 2f / 3f, 0f), Size(w / 3f, h))
        val diagonal = Path().apply {
            moveTo(0f, 0f)
            lineTo(w / 3f, 0f)
            lineTo(w, h)
            lineTo(w * 2f / 3f, h)
            close()
        }
        drawPath(diagonal, darkRed)
    }
}

/**
 * The big outlined rank behind a poster: a thin light-grey outline with the page's own colour inside, as the web app's `.top10__num`. Two texts, the outline
 * and then the fill over it, so only the outside half of the stroke shows. Its width follows the number of digits.
 */
@Composable
private fun RankNumber(rank: Int, posterWidth: Dp) {
    val density = LocalDensity.current
    val fontSize = with(density) { (posterWidth * 1.5f).toSp() }
    val digits = rank.toString()
    // Every digit of the font has the same width, so a "1" (a narrow shape) left a big gap before the next digit ("10"). Each digit gets a box as wide as
    // its own shape instead (the web app stretches the number to fit, which comes to the same).
    val digitWidths = digits.map { if (it == '1') posterWidth * 0.52f else posterWidth * 0.84f }
    // the poster sits over the right of the number (the web app's negative margin)
    val boxWidth = digitWidths.fold(0.dp) { sum, w -> sum + w } - posterWidth * 0.2f
    val base = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black)
    val outline = base.copy(color = Color(0xFF6F6F7A), drawStyle = Stroke(width = with(density) { 2.5.dp.toPx() }, join = StrokeJoin.Round))
    val fill = base.copy(color = MangoBackground)
    Box(Modifier.width(boxWidth).height(posterWidth * 1.2f)) {
        // wider than the box once the poster's overlap is taken off, so it is laid out unbounded and left to overflow under the poster
        Row(Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)) {
            digits.forEachIndexed { i, digit ->
                Box(Modifier.width(digitWidths[i]).height(posterWidth * 1.2f), contentAlignment = Alignment.BottomCenter) {
                    val textModifier = Modifier
                        .wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true)
                        .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                        .offset(y = posterWidth * 0.27f)
                    Text(text = digit.toString(), style = outline, maxLines = 1, softWrap = false, modifier = textModifier)
                    Text(text = digit.toString(), style = fill, maxLines = 1, softWrap = false, modifier = textModifier)
                }
            }
        }
    }
}

/**
 * Netflix's page opens with its ten most popular titles in the viewer's country: a big outlined number tucked behind each poster and the red N on its
 * corner (the web app's `TopTenRow`). [titles] is null while the list is on its way: the heading and ten grey posters with their numbers.
 *
 * [focusRequester] is on the card that last had focus, so the nav bar's DOWN (and UP from the titles below) lands where the person was. UP leaves for the nav
 * bar through [onNavigateUp]; DOWN is left to the screen's ordinary focus search, into the row of titles below.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopTenRow(
    titles: List<Content>?,
    onItemClick: (Content) -> Unit,
    focusRequester: FocusRequester,
    onNavigateUp: () -> Unit,
    // The row starts this far from the screen's side (the page's own margin)
    sidePadding: Dp = MangoDimens.ScreenPaddingHorizontal,
    modifier: Modifier = Modifier,
    // Told when a poster gains focus, so the page can bring focus back here after a title is opened
    onFocused: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastFocused by rememberSaveable { mutableStateOf(0) }
    Column(modifier) {
        Text(
            text = TOP_TEN_TITLE,
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = sidePadding, vertical = 12.dp)
        )
        val density = LocalDensity.current
        val bringIntoViewSpec = remember(density) {
            with(density) { MangoMotion.edgeSafeBringIntoViewSpec(MangoMotion.CardEdgeSafeBufferDp.dp.toPx()) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
            LazyRow(
                state = listState,
                userScrollEnabled = titles != null,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    // Both KeyDown and KeyUp are consumed, as on the first row of any other page, so an unconsumed KeyUp can't start a scroll of its own.
                    if (event.key == Key.DirectionUp) {
                        if (event.type == KeyEventType.KeyDown) onNavigateUp()
                        true
                    } else {
                        false
                    }
                },
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (titles == null) {
                    items(TOP_TEN_PLACEHOLDERS) { index ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            RankNumber(rank = index + 1, posterWidth = TOP_TEN_POSTER_WIDTH)
                            Box(
                                Modifier
                                    .width(TOP_TEN_POSTER_WIDTH)
                                    .height(TOP_TEN_POSTER_WIDTH * 1.5f)
                                    .background(MangoSurfaceHigh.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                } else {
                    itemsIndexed(titles, key = { _, content -> content.id }) { index, content ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            RankNumber(rank = index + 1, posterWidth = TOP_TEN_POSTER_WIDTH)
                            ContentCard(
                                content = content,
                                onClick = { onItemClick(content) },
                                focusRequester = if (index == lastFocused.coerceIn(0, titles.size - 1)) focusRequester else null,
                                onFocusChanged = { isFocused ->
                                    if (isFocused) {
                                        lastFocused = index
                                        onFocused()
                                        // Scrolling into view only brings the poster on screen, and its big number sits to the poster's left: going back
                                        // to the first poster (or any whose number is cut off) scrolls the whole item, number included, into view.
                                        scope.launch {
                                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                            if (index == 0 || (info != null && info.offset < 0)) listState.animateScrollToItem(index)
                                        }
                                    }
                                },
                                serviceStyle = ServiceCardStyle(
                                    width = TOP_TEN_POSTER_WIDTH,
                                    wide = false,
                                    badge = { NetflixN(width = 11.dp) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val TOP_TEN_PLACEHOLDERS = 10
