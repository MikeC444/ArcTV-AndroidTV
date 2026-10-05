package com.mangotv.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

private val ButtonHeight = 36.dp
private val CompactButtonHeight = 28.dp
private val RowHeight = 38.dp

/**
 * The pill-and-panel drop-down the web app uses for "All genres" on Movies / TV Shows and for "Sort by" on My List:
 * a pill showing [buttonLabel] that opens a list of [options] under it. OK on the pill opens it, focus lands on the
 * chosen option, UP/DOWN move through the list, OK picks one and closes it, and BACK closes it without changing
 * anything, returning focus to the pill.
 *
 * The list is a Popup window of its own, so opening it never disturbs the focus handling of the screen behind it.
 * [focusUp]/[focusDown] wire the pill into the surrounding screen the same way every other focusable here is wired.
 */
@Composable
fun DropdownPicker(
    buttonLabel: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    // The smaller pill the Select a Source filter bar uses, and the filled look of a selected FilterPill (a filter is active).
    compact: Boolean = false,
    highlighted: Boolean = false
) {
    val buttonHeight = if (compact) CompactButtonHeight else ButtonHeight
    val contentColor = if (highlighted) MangoBackground else TextPrimary
    var open by remember { mutableStateOf(false) }
    val buttonRequester = focusRequester ?: remember { FocusRequester() }

    // Puts focus back on the pill once the list has closed, however it closed.
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (wasOpen && !open) runCatching { buttonRequester.requestFocus() }
        wasOpen = open
    }

    Box(modifier = modifier) {
        TvFocusSurface(
            onClick = { open = !open },
            modifier = Modifier.heightIn(min = buttonHeight),
            shape = RoundedCornerShape(percent = 50),
            backgroundColor = if (highlighted) ArcAccent else MangoSurfaceHigh,
            focusRequester = buttonRequester,
            focusUp = focusUp,
            focusDown = focusDown,
            bringIntoViewOnFocus = false
        ) {
            Row(
                modifier = if (compact) {
                    Modifier.padding(start = 11.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
                } else {
                    Modifier.padding(start = 16.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buttonLabel,
                    color = contentColor,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = if (highlighted) MangoBackground else TextSecondary,
                    modifier = Modifier.size(if (compact) 16.dp else 20.dp)
                )
            }
        }

        if (open) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(0, (buttonHeight + 10.dp).roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                DropdownPanel(
                    options = options,
                    selectedIndex = selectedIndex,
                    onPick = { index ->
                        open = false
                        if (index != selectedIndex) onSelect(index)
                    }
                )
            }
        }
    }
}

// The popup inherits composition locals from the screen that opened it. The browse grids switch off Compose's automatic
// scroll-to-focus (LocalBringIntoViewSpec = DisabledBringIntoViewSpec) so the page doesn't shake, which would leave this
// list stuck on its first rows while the remote moves focus below them -- so the list puts the normal behaviour back.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DropdownPanel(options: List<String>, selectedIndex: Int, onPick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val selectedRequester = remember { FocusRequester() }
    val safeSelected = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    // Start from the chosen option, so the remote begins where the person is.
    LaunchedEffect(Unit) {
        if (options.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(safeSelected)
        withFrameNanos { }
        runCatching { selectedRequester.requestFocus() }
    }

    // Belt and braces for the automatic scroll-to-focus restored below: whenever focus moves to an option that is not
    // fully on screen, scroll just enough to show it. Whichever mechanism gets there first, the other then finds the
    // option already visible and does nothing.
    var focusedIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusedIndex }
        when {
            item == null || item.offset < info.viewportStartOffset -> listState.animateScrollToItem(focusedIndex)
            item.offset + item.size > info.viewportEndOffset ->
                listState.animateScrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 300.dp)
            .background(MangoBackgroundElevated, shape)
            .border(1.dp, DividerSubtle, shape)
            .padding(8.dp)
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = RowHeight * 7),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(options) { index, label ->
                val selected = index == safeSelected
                TvFocusSurface(
                    onClick = { onPick(index) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = RowHeight),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = Color.Transparent,
                    focusRequester = if (selected) selectedRequester else null,
                    onFocusChanged = { focused -> if (focused) focusedIndex = index },
                    focusedScale = 1f
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selected) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = ArcAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
