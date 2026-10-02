package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Settings > Blocked Genres: titles in a blocked genre are hidden from Home, Movies, TV Shows, Search, Genres and "More
 * like this". The choice is stored on the account, so it follows the person to every device (see
 * BlockedGenresRepository). One LazyColumn so the whole tab scrolls as a unit, like Home Rows.
 */
@Composable
fun ColumnScope.BlockedGenresSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: BlockedGenresViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()
    val blockedKeys = remember(blocked) { blocked.map { it.trim().lowercase() }.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "header") {
            Text(
                text = "Titles in a blocked genre are hidden from Home, Movies, TV Shows, Search and Genres. " +
                    "Titles an addon gives no genres for can't be filtered. Saved to your account, so it applies on every device.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // The focus target for the pane is the Clear all button when there is one, otherwise the first genre row.
        val clearAllIsFirst = blocked.isNotEmpty()
        item(key = "summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (blocked.isEmpty()) "Nothing blocked" else "${blocked.size} blocked",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                if (clearAllIsFirst) {
                    MangoButton(
                        text = "Clear all",
                        icon = Icons.Filled.Delete,
                        onClick = viewModel::clearAll,
                        compact = true,
                        focusRequester = contentFocusRequester,
                        focusUp = navFocusRequester,
                        focusLeft = sidebarFocusRequester
                    )
                }
            }
        }

        when (val state = uiState) {
            is BlockedGenresUiState.Loading -> item(key = "loading") { CircularProgressIndicator(color = ArcAccent) }
            is BlockedGenresUiState.NoAddons -> item(key = "no_addons") {
                Text(
                    text = "Install an addon first — its genres will show up here.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is BlockedGenresUiState.Loaded -> {
                itemsIndexed(state.genres, key = { _, genre -> genre.lowercase() }) { index, genre ->
                    val isBlocked = genre.trim().lowercase() in blockedKeys
                    BlockedGenreRow(
                        genre = genre,
                        blocked = isBlocked,
                        onToggle = { viewModel.toggle(genre) },
                        focusRequester = if (index == 0 && !clearAllIsFirst) contentFocusRequester else null,
                        focusUp = if (index == 0 && !clearAllIsFirst) navFocusRequester else null,
                        focusLeft = sidebarFocusRequester
                    )
                }
                item(key = "footer") {
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

@Composable
private fun BlockedGenreRow(
    genre: String,
    blocked: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusLeft: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // Same wide-row reasoning as HomeRowToggleRow: the default scale is tuned for small poster cards.
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = genre,
                color = if (blocked) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (blocked) "Blocked" else "Shown",
                color = if (blocked) ArcAccent else TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = blocked,
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
    }
}
