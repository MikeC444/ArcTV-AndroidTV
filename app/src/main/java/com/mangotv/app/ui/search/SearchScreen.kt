package com.mangotv.app.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.Content
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.browse.GRID_COLUMNS
import com.mangotv.app.ui.components.ContentCard
import com.mangotv.app.ui.components.EmptyState
import com.mangotv.app.ui.components.FilterPill
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.detail.PendingDetailCache
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onNavigate: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val navFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var fieldFocused by remember { mutableStateOf(false) }
    // Which result row holds focus (by its list key), so it can be scrolled to the middle of the screen as focus moves down the grid.
    var focusedRowKey by remember { mutableStateOf<String?>(null) }
    // Gates the keyboard behind an explicit SELECT press on the field --
    // TextField shows the IME on any focus gain by default (D-pad DOWN from
    // the nav bar included, via TopNavBar's contentFocusRequester below),
    // which pops the keyboard just from navigating past it. readOnly blocks
    // that text-input session while still letting the field take D-pad
    // focus normally; only DirectionCenter/Enter flips it into edit mode.
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) keyboardController?.show()
    }

    LaunchedEffect(Unit) {
        runCatching { navFocusRequester.requestFocus() }
    }

    // Focus moving down the results: keep that row in the middle of the screen (the same explicit centering the Movies / TV Shows grids use,
    // because automatic scroll-into-view fights the focus zoom).
    LaunchedEffect(focusedRowKey) {
        val key = focusedRowKey ?: return@LaunchedEffect
        val info = listState.layoutInfo.visibleItemsInfo.find { it.key == key } ?: return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        listState.animateScrollBy(info.offset + info.size / 2f - viewportHeight / 2f)
    }

    fun backToTop() {
        focusedRowKey = null
        scope.launch { listState.animateScrollToItem(0) }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        // Opening a result makes this a search worth remembering.
        viewModel.rememberSearch(query)
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The scale that makes exactly GRID_COLUMNS posters fill the width within the screen margins (same sum the other grids use).
            val availableWidth = maxWidth - MangoDimens.ScreenPaddingHorizontal * 2
            val cardWidth = (availableWidth - MangoDimens.CardSpacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS
            val posterScale = (cardWidth / MangoDimens.PosterWidth).coerceIn(0.3f, 1f)

            CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = MangoDimens.NavBarHeight + 24.dp)
                ) {
                    item(key = "title") {
                        Text(
                            text = "Search",
                            color = TextPrimary,
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 4.dp)
                        )
                    }
                    item(key = "field") {
                        val fieldShape = RoundedCornerShape(50)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // The rounded search bar: icon inside, the typing area, and (once there is text) a clear button beside it.
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .weight(1f, fill = false)
                                    .height(56.dp)
                                    .clip(fieldShape)
                                    .background(MangoSurface)
                                    .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) FocusBorder else DividerSubtle, fieldShape),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.padding(start = 20.dp).size(22.dp)
                                )
                                TextField(
                                    value = query,
                                    onValueChange = {
                                        query = it
                                        viewModel.onQueryChanged(it)
                                    },
                                    readOnly = !isEditing,
                                    placeholder = { Text("Search movies and TV shows") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        viewModel.submit(query)
                                        isEditing = false
                                        keyboardController?.hide()
                                    }),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(fieldFocusRequester)
                                        .focusProperties {
                                            up = navFocusRequester
                                            if (uiState is SearchUiState.Results) down = firstResultFocusRequester
                                        }
                                        .onFocusChanged { state ->
                                            fieldFocused = state.isFocused
                                            if (state.isFocused) backToTop() else isEditing = false
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (!isEditing &&
                                                (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                                                event.type == KeyEventType.KeyDown
                                            ) {
                                                isEditing = true
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = ArcAccent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                                if (query.isNotEmpty()) {
                                    HeroIconButton(
                                        icon = Icons.Filled.Close,
                                        contentDescription = "Clear search",
                                        onClick = {
                                            query = ""
                                            viewModel.onQueryChanged("")
                                            runCatching { fieldFocusRequester.requestFocus() }
                                        },
                                        modifier = Modifier.padding(end = 8.dp),
                                        compact = true,
                                        focusLeft = fieldFocusRequester,
                                        focusUp = navFocusRequester
                                    )
                                }
                            }
                        }
                    }
                    when (val state = uiState) {
                        is SearchUiState.Idle -> item(key = "idle") {
                            Column {
                                if (recents.isNotEmpty()) {
                                    RecentSearches(
                                        terms = recents,
                                        onPick = { viewModel.submit(it); query = it },
                                        onRemove = viewModel::removeRecent,
                                        onClear = viewModel::clearRecents,
                                        onFocus = ::backToTop,
                                        fieldFocusRequester = fieldFocusRequester
                                    )
                                }
                                Text(
                                    text = "Search for movies and TV shows across your installed addons.",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 8.dp)
                                )
                            }
                        }
                        is SearchUiState.Searching -> item(key = "searching") {
                            Row(
                                modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ArcAccent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(text = "Searching…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        is SearchUiState.Results -> {
                            val hasMovies = state.movies.isNotEmpty()
                            if (hasMovies) {
                                resultGrid(
                                    keyPrefix = "movies",
                                    heading = "Movies",
                                    items = state.movies,
                                    posterScale = posterScale,
                                    firstGridRowOfScreen = true,
                                    firstResultFocusRequester = firstResultFocusRequester,
                                    onRowFocused = { focusedRowKey = it },
                                    onUpFromFirstRow = { runCatching { fieldFocusRequester.requestFocus() } },
                                    onOpen = ::navigateToContent
                                )
                            }
                            if (state.tvShows.isNotEmpty()) {
                                resultGrid(
                                    keyPrefix = "tv",
                                    heading = "TV Shows",
                                    items = state.tvShows,
                                    posterScale = posterScale,
                                    firstGridRowOfScreen = !hasMovies,
                                    firstResultFocusRequester = firstResultFocusRequester,
                                    onRowFocused = { focusedRowKey = it },
                                    onUpFromFirstRow = { runCatching { fieldFocusRequester.requestFocus() } },
                                    onOpen = ::navigateToContent
                                )
                            }
                            if (state.loading) {
                                item(key = "still_checking") {
                                    Row(
                                        modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ArcAccent, strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(text = "Still checking other addons…", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            item(key = "bottom_spacer") { Spacer(Modifier.height(48.dp)) }
                        }
                        is SearchUiState.NoResults -> item(key = "none") {
                            EmptyState(
                                icon = Icons.Filled.SearchOff,
                                title = "No results",
                                message = "Nothing found for \"${state.query}\". Try a different search.",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is SearchUiState.Error -> item(key = "error") {
                            Text(
                                text = state.message,
                                color = ErrorCoral,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                            )
                        }
                    }
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Search"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = fieldFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
    }
}

/** A titled, wrapping poster grid (Movies / TV Shows): GRID_COLUMNS posters per row, no sideways scrolling to see every match. */
private fun androidx.compose.foundation.lazy.LazyListScope.resultGrid(
    keyPrefix: String,
    heading: String,
    items: List<Content>,
    posterScale: Float,
    firstGridRowOfScreen: Boolean,
    firstResultFocusRequester: FocusRequester,
    onRowFocused: (String) -> Unit,
    onUpFromFirstRow: () -> Unit,
    onOpen: (Content) -> Unit
) {
    item(key = "${keyPrefix}_heading") {
        Row(
            modifier = Modifier.padding(start = MangoDimens.ScreenPaddingHorizontal, end = MangoDimens.ScreenPaddingHorizontal, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text = heading, color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (items.size == 1) "1 result" else "${items.size} results",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    itemsIndexed(items.chunked(GRID_COLUMNS), key = { index, _ -> "${keyPrefix}_row_$index" }) { rowIndex, rowItems ->
        val rowKey = "${keyPrefix}_row_$rowIndex"
        val isFirstRowOnScreen = firstGridRowOfScreen && rowIndex == 0
        Row(
            modifier = Modifier
                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = MangoDimens.RowSpacing / 2)
                .onFocusChanged { state -> if (state.hasFocus) onRowFocused(rowKey) }
                .let { base ->
                    if (isFirstRowOnScreen) {
                        // UP from the first row goes back to the search bar (every other row leaves UP to the default focus search).
                        base.onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionUp) {
                                if (event.type == KeyEventType.KeyDown) onUpFromFirstRow()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        base
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
        ) {
            rowItems.forEachIndexed { colIndex, content ->
                ContentCard(
                    content = content,
                    onClick = { onOpen(content) },
                    focusRequester = if (isFirstRowOnScreen && colIndex == 0) firstResultFocusRequester else null,
                    posterScale = posterScale
                )
            }
        }
    }
}

/** "Recent searches" under the bar while nothing is being searched: the last few, one press to search again, hold OK to remove one. */
@Composable
private fun RecentSearches(
    terms: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onFocus: () -> Unit,
    fieldFocusRequester: FocusRequester
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Recent searches", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            FilterPill(label = "Clear", selected = false, onClick = onClear, focusUp = fieldFocusRequester)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(terms, key = { it }) { term ->
                TvFocusSurface(
                    onClick = { onPick(term) },
                    onLongClick = { onRemove(term) },
                    shape = RoundedCornerShape(50),
                    backgroundColor = MangoSurfaceHigh,
                    onFocusChanged = { focused -> if (focused) onFocus() },
                    focusUp = fieldFocusRequester,
                    bringIntoViewOnFocus = false
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = term,
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = "Hold OK on a search to remove it",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
        )
    }
}
