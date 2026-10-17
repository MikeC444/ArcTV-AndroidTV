package com.mangotv.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.provider.isServiceVisible
import com.mangotv.app.data.provider.streamingServiceById
import com.mangotv.app.ui.components.FilterPill
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

private const val HUB = "hub"
private const val STREAMING = "streaming"
private const val ADDON_PREFIX = "addon:"

/** One line of a list page: a streaming service or a row. */
private data class ListEntry(val id: String, val title: String)

/** One card on the hub page: the streaming tiles, or one installed addon's rows. */
private class HubCard(val key: String, val title: String, val subtitle: String)

/** "10 of 36 on · Netflix, Prime Video, Shudder ..." */
private fun summary(on: List<String>, total: Int): String {
    val counts = "${on.size} of $total on"
    if (on.isEmpty()) return counts
    val more = if (on.size > 4) " …" else ""
    return "$counts · ${on.take(4).joinToString(", ")}$more"
}

/**
 * Settings > Home Rows, as a short hub plus a page for each thing that can be switched and ordered, so no list is long: "Streaming tiles", and one page
 * for every installed addon that has rows (a new addon the person installs gets its own page by itself). Each page has two tabs, "On Home" (the ones that
 * are shown, in their order, two across; press OK on the handle to pick one up and move it with the arrows) and "Off" (the rest, three across; switching one
 * on adds it back). Hosted by SettingsScreen's detail pane -- see AccountSettingsContent's kdoc for why this isn't its own screen.
 */
@Composable
fun ColumnScope.HomeRowsSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: HomeRowsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()

    // The page being shown ("hub", "streaming" or "addon:<id>") and the card the person opened last, so Back lands on it again
    var page by rememberSaveable { mutableStateOf(HUB) }
    var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    BackHandler(enabled = page != HUB) { page = HUB }
    LaunchedEffect(page) {
        if (page == HUB) lastOpened?.let { key -> runCatching { cardRequesters[key]?.requestFocus() } }
    }

    val loaded = uiState as? HomeRowsUiState.Loaded
    val allRows = loaded?.rows.orEmpty()
    // every row's id in the order Home uses, hidden ones included (what a move is worked out against)
    val allOrdered = remember(allRows, preferences) { preferences.applyOrder(allRows).map { it.id } }
    val rowTitles = remember(allRows) { allRows.associate { it.id to it.title } }
    val hidden = preferences.hiddenRowIds

    // An addon removed while its page is open: back to the hub
    LaunchedEffect(loaded, page) {
        if (page.startsWith(ADDON_PREFIX) && loaded != null && loaded.addons.none { ADDON_PREFIX + it.providerId == page }) page = HUB
        if (page == STREAMING && services.isEmpty()) page = HUB
    }

    when {
        page == STREAMING && services.isNotEmpty() -> {
            val on = services.filter { isServiceVisible(it, hidden) }
            val off = services.filter { it !in on }
            ListPage(
                title = "Streaming tiles",
                subtitle = "The order here is the order of the tiles on Home",
                on = on.map { ListEntry(it, streamingServiceById(it)?.name ?: it) },
                off = off.map { ListEntry(it, streamingServiceById(it)?.name ?: it) },
                onToggle = { id, visible -> viewModel.setServiceVisible(id, visible) },
                onMove = { id, delta -> viewModel.moveServiceWithin(services, on, id, delta) },
                onBack = { page = HUB },
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
        page.startsWith(ADDON_PREFIX) && loaded != null && loaded.addons.any { ADDON_PREFIX + it.providerId == page } -> {
            val addon = loaded.addons.first { ADDON_PREFIX + it.providerId == page }
            val ids = addon.rows.map { it.id }.toSet()
            val ordered = allOrdered.filter { it in ids }
            val on = ordered.filter { it !in hidden }
            val off = ordered.filter { it in hidden }
            ListPage(
                title = addon.name,
                subtitle = "The order here is the order of these rows on Home",
                on = on.map { ListEntry(it, rowTitles[it] ?: it) },
                off = off.map { ListEntry(it, rowTitles[it] ?: it) },
                onToggle = { id, visible -> viewModel.setRowVisible(id, visible) },
                onMove = { id, delta -> viewModel.moveRowWithin(allOrdered, on, id, delta) },
                onBack = { page = HUB },
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
        else -> {
            val cards = buildList {
                if (services.isNotEmpty()) {
                    val on = services.filter { isServiceVisible(it, hidden) }
                    add(HubCard(STREAMING, "Streaming tiles", summary(on.map { streamingServiceById(it)?.name ?: it }, services.size)))
                }
                loaded?.addons?.forEach { addon ->
                    val onRows = allOrdered.filter { id -> id !in hidden && addon.rows.any { it.id == id } }
                    add(HubCard(ADDON_PREFIX + addon.providerId, addon.name, summary(onRows.map { rowTitles[it] ?: it }, addon.rows.size)))
                }
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = SettingsListPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "header") {
                    Text(
                        text = "Choose what shows on Home, and in what order.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                when (uiState) {
                    is HomeRowsUiState.Loading -> if (cards.isEmpty()) item(key = "loading") { CircularProgressIndicator(color = ArcAccent) }
                    is HomeRowsUiState.NoAddons -> item(key = "no_addons") {
                        Text(
                            text = "Install an addon first — its rows will show up here once it's added.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is HomeRowsUiState.Loaded -> if (cards.isEmpty()) item(key = "no_rows") {
                        Text(
                            text = "Your installed addons aren't reporting any rows right now.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                itemsIndexed(cards, key = { _, card -> card.key }) { index, card ->
                    val requester = if (index == 0) contentFocusRequester else cardRequesters.getOrPut(card.key) { FocusRequester() }
                    if (index == 0) cardRequesters[card.key] = contentFocusRequester
                    HubCardRow(
                        card = card,
                        onClick = {
                            lastOpened = card.key
                            page = card.key
                        },
                        focusRequester = requester,
                        focusUp = if (index == 0) navFocusRequester else null,
                        focusLeft = sidebarFocusRequester
                    )
                }
                if (cards.isNotEmpty()) {
                    item(key = "footer") { HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f)) }
                    item(key = "footer_text") {
                        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Filled.Info, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(text = "Changes are saved automatically", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCardRow(
    card: HubCard,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    focusUp: FocusRequester?,
    focusLeft: FocusRequester?
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // the same small grow as the other wide rows in Settings: a bigger one would run past the screen's safe margin
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusLeft = focusLeft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(text = card.title, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = card.subtitle,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(imageVector = Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

/** A list page (a service list or one addon's rows): a Back pill and the two tabs, then the shown tab's items as a grid. */
@Composable
private fun ColumnScope.ListPage(
    title: String,
    subtitle: String,
    on: List<ListEntry>,
    off: List<ListEntry>,
    onToggle: (id: String, visible: Boolean) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onBack: () -> Unit,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    var tab by remember { mutableIntStateOf(0) } // 0 = On Home, 1 = Off
    // Which item is "picked up" for moving (its handle then takes the arrows); cleared when the tab changes
    var grabbedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) { grabbedId = null }

    val shown = if (tab == 0) on else off
    val columns = if (tab == 0) ON_COLUMNS else OFF_COLUMNS

    // When an item leaves the shown list (switched on or off) focus would be lost with it: the cell at the same place takes it over
    var lastFocusedIndex by remember { mutableIntStateOf(0) }
    val cellFocus = remember { FocusRequester() }
    var refocus by remember { mutableStateOf(false) }
    LaunchedEffect(shown.size, refocus) {
        if (refocus) {
            refocus = false
            runCatching { if (shown.isEmpty()) contentFocusRequester.requestFocus() else cellFocus.requestFocus() }
        }
    }

    Text(text = title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        FilterPill(label = "‹ Back", selected = false, onClick = onBack, focusRequester = contentFocusRequester, focusUp = navFocusRequester, focusLeft = sidebarFocusRequester)
        FilterPill(label = "On Home · ${on.size}", selected = tab == 0, onClick = { tab = 0 }, focusUp = navFocusRequester)
        FilterPill(label = "Off · ${off.size}", selected = tab == 1, onClick = { tab = 1 }, focusUp = navFocusRequester)
    }

    if (shown.isEmpty()) {
        Text(
            text = if (tab == 0) "Nothing is on Home from here. Turn something on in the Off tab." else "Everything here is on Home.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = SettingsListPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(shown, key = { _, entry -> entry.id }) { index, entry ->
            val focusIndex = lastFocusedIndex.coerceAtMost(shown.lastIndex)
            val requester = if (index == focusIndex) cellFocus else null
            if (tab == 0) {
                OnCell(
                    position = index + 1,
                    entry = entry,
                    grabbed = grabbedId == entry.id,
                    focusRequester = requester,
                    onToggleOff = {
                        refocus = true
                        onToggle(entry.id, false)
                    },
                    onToggleGrabbed = { grabbedId = if (grabbedId == entry.id) null else entry.id },
                    onMove = { delta -> onMove(entry.id, delta) },
                    onHandleFocusLost = { if (grabbedId == entry.id) grabbedId = null },
                    onFocused = { lastFocusedIndex = index }
                )
            } else {
                OffCell(
                    entry = entry,
                    focusRequester = requester,
                    onToggleOn = {
                        refocus = true
                        onToggle(entry.id, true)
                    },
                    onFocused = { lastFocusedIndex = index }
                )
            }
        }
    }
}

private const val ON_COLUMNS = 2
private const val OFF_COLUMNS = 3

@Composable
private fun RowSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MangoBackground,
            checkedTrackColor = TextPrimary,
            checkedBorderColor = TextPrimary,
            uncheckedThumbColor = TextTertiary,
            uncheckedTrackColor = MangoBackground,
            uncheckedBorderColor = TextTertiary
        )
    )
}

/** An item that is on Home: its place in the order, a switch (OK turns it off) and a handle (OK picks it up; then Left / Right move it one place, Up / Down one row). */
@Composable
private fun OnCell(
    position: Int,
    entry: ListEntry,
    grabbed: Boolean,
    focusRequester: FocusRequester?,
    onToggleOff: () -> Unit,
    onToggleGrabbed: () -> Unit,
    onMove: (Int) -> Unit,
    onHandleFocusLost: () -> Unit,
    onFocused: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TvFocusSurface(
            onClick = onToggleOff,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            focusedScale = 1.02f,
            backgroundColor = MangoSurface,
            borderColor = TextPrimary,
            focusRequester = focusRequester,
            onFocusChanged = { if (it) onFocused() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = position.toString(), color = TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
                Text(
                    text = entry.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                RowSwitch(checked = true)
            }
        }
        TvFocusSurface(
            onClick = onToggleGrabbed,
            modifier = Modifier
                .size(34.dp)
                .onPreviewKeyEvent { event ->
                    if (grabbed && event.type == KeyEventType.KeyDown) {
                        val delta = when (event.key) {
                            Key.DirectionLeft -> -1
                            Key.DirectionRight -> 1
                            Key.DirectionUp -> -ON_COLUMNS
                            Key.DirectionDown -> ON_COLUMNS
                            else -> 0
                        }
                        if (delta != 0) {
                            onMove(delta)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurface,
            alwaysShowBorder = grabbed,
            borderColor = TextPrimary,
            onFocusChanged = { hasFocus ->
                if (hasFocus) onFocused() else onHandleFocusLost()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = if (grabbed) "Stop moving ${entry.title}" else "Reorder ${entry.title}",
                tint = if (grabbed) TextPrimary else TextSecondary,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
    }
}

/** An item that is not on Home: a switch (OK turns it on). */
@Composable
private fun OffCell(
    entry: ListEntry,
    focusRequester: FocusRequester?,
    onToggleOn: () -> Unit,
    onFocused: () -> Unit
) {
    TvFocusSurface(
        onClick = onToggleOn,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        onFocusChanged = { if (it) onFocused() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.title,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            RowSwitch(checked = false)
        }
    }
}
