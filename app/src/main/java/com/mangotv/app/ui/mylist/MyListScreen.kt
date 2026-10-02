package com.mangotv.app.ui.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.browse.BrowseHeaderFocus
import com.mangotv.app.ui.browse.RowsBrowseContent
import com.mangotv.app.ui.browse.RowsBrowseUiState
import com.mangotv.app.ui.components.DropdownPicker
import com.mangotv.app.ui.browse.RowsBrowseLayout

@Composable
fun MyListScreen(
    onNavigate: (String) -> Unit,
    viewModel: MyListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val filters = MyListFilter.entries
    val sorts = MyListSort.entries
    val hasItems = (uiState as? RowsBrowseUiState.Loaded)?.sections?.isNotEmpty() == true
    // "Sort by: Recently Added" drop-down beside the title, as on the web. Left out while the list is empty, since there
    // is nothing to sort. Built as a value first: a lambda written straight after `if`/`else` would be read as a block.
    val sortHeader: (@Composable (BrowseHeaderFocus) -> Unit)? = if (hasItems) {
        { focus ->
            DropdownPicker(
                buttonLabel = "Sort by: ${selectedSort.label}",
                options = sorts.map { it.label },
                selectedIndex = sorts.indexOf(selectedSort),
                onSelect = { index -> viewModel.selectSort(sorts[index]) },
                focusRequester = focus.requester,
                focusUp = focus.up,
                focusDown = focus.down
            )
        }
    } else {
        null
    }
    RowsBrowseContent(
        screenTitle = "My List",
        navLabel = "My List",
        uiState = uiState,
        onNavigate = onNavigate,
        onRetry = {},
        layout = RowsBrowseLayout.GRID,
        emptyMessage = if (selectedFilter == MyListFilter.WATCHED) {
            "Nothing watched yet. Titles you finish will show up here."
        } else {
            "Your list is empty. Add titles from a Detail page or Home's featured title to see them here."
        },
        filterOptions = filters.map { it.label },
        selectedFilterIndex = filters.indexOf(selectedFilter),
        onFilterSelected = { index -> viewModel.selectFilter(filters[index]) },
        headerAction = sortHeader
    )
}
