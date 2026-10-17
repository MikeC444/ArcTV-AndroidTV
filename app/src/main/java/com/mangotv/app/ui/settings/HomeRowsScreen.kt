package com.mangotv.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
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
private data class ListEntry(val id: String, val title: String, val on: Boolean)

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
 * for every installed addon that has rows (a new addon the person installs gets its own page by itself). A page lists everything it has in one grid, in order,
 * each with a switch and an up and a down arrow to move it. Hosted by SettingsScreen's detail pane -- see AccountSettingsContent's kdoc for why this isn't its own screen.
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
            val on = services.filter { isServiceVisible(it, hidden) }.toSet()
            ListPage(
                title = "Streaming tiles",
                subtitle = "The order here is the order of the tiles on Home",
                entries = services.map { ListEntry(it, streamingServiceById(it)?.name ?: it, it in on) },
                onToggle = { id, visible -> viewModel.setServiceVisible(id, visible) },
                onMove = { id, delta -> viewModel.moveServiceWithin(services, services, id, delta) },
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
            ListPage(
                title = addon.name,
                subtitle = "The order here is the order of these rows on Home",
                entries = ordered.map { ListEntry(it, rowTitles[it] ?: it, it !in hidden) },
                onToggle = { id, visible -> viewModel.setRowVisible(id, visible) },
                onMove = { id, delta -> viewModel.moveRowWithin(allOrdered, ordered, id, delta) },
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

/**
 * A list page (the streaming tiles, or one addon's rows): everything it has in one list, in its order. Each line has its place number, a switch (OK on the
 * name turns it on or off; one that is off is dimmed) and two arrow buttons: OK on the up arrow moves it one place up, OK on the down arrow one place down.
 * The cursor stays on the arrow that was pressed, so pressing OK again moves it on.
 */
@Composable
private fun ColumnScope.ListPage(
    title: String,
    subtitle: String,
    entries: List<ListEntry>,
    onToggle: (id: String, visible: Boolean) -> Unit,
    onMove: (id: String, delta: Int) -> Unit,
    onBack: () -> Unit,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester
) {
    // The cursor starts on the first item, not on the top bar the page was opened from
    val firstCell = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstCell.requestFocus() }
    }

    Text(text = title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
    Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        FilterPill(label = "‹ Back", selected = false, onClick = onBack, focusRequester = contentFocusRequester, focusUp = navFocusRequester, focusLeft = sidebarFocusRequester)
        Text(
            text = "${entries.count { it.on }} of ${entries.size} on Home. OK on a name turns it on or off; OK on ▲ or ▼ moves it up or down.",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (entries.isEmpty()) {
        Text(text = "Nothing to show here.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = SettingsListPadding,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
            ItemLine(
                position = index + 1,
                entry = entry,
                canMoveUp = index > 0,
                canMoveDown = index < entries.lastIndex,
                focusRequester = if (index == 0) firstCell else null,
                onToggle = { onToggle(entry.id, !entry.on) },
                onMove = { delta -> onMove(entry.id, delta) }
            )
        }
    }
}

@Composable
private fun RowSwitch(checked: Boolean) {
    // a small pill (the stock Switch is 48dp tall, which would make every line tall)
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 18.dp)
            .background(if (checked) TextPrimary else MangoBackground, RoundedCornerShape(9.dp))
            .border(1.5.dp, if (checked) TextPrimary else TextTertiary, RoundedCornerShape(9.dp))
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(12.dp).background(if (checked) MangoBackground else TextTertiary, CircleShape))
    }
}

/** One line: its place in the order, a switch (OK on the name turns it on or off; dimmed when off) and the two arrows that move it. */
@Composable
private fun ItemLine(
    position: Int,
    entry: ListEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    focusRequester: FocusRequester?,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvFocusSurface(
            onClick = onToggle,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            focusedScale = 1.01f,
            backgroundColor = MangoSurface,
            borderColor = TextPrimary,
            focusRequester = focusRequester
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = position.toString(), color = TextTertiary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                Text(
                    text = entry.title,
                    color = if (entry.on) TextPrimary else TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                RowSwitch(checked = entry.on)
            }
        }
        ArrowButton(up = true, enabled = canMoveUp, label = entry.title, onClick = { onMove(-1) })
        ArrowButton(up = false, enabled = canMoveDown, label = entry.title, onClick = { onMove(1) })
    }
}

/** A button that moves a line one place up or down; dimmed (and doing nothing) at the top or bottom of the list. */
@Composable
private fun ArrowButton(up: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(width = 44.dp, height = 32.dp),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        borderColor = TextPrimary
    ) {
        Icon(
            imageVector = if (up) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (up) "Move $label up" else "Move $label down",
            tint = if (enabled) TextPrimary else TextTertiary.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(22.dp)
        )
    }
}
