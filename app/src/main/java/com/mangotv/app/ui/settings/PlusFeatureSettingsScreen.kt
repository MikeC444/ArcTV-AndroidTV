package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.ui.player.DevicePlayerPrefs
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Settings > Plus settings (Arc TV Plus): the switches for Plus features, kept on this device. The tab can't be opened without Plus (its row is
 * locked), so the locked message only guards a stale selection.
 */
@Composable
fun ColumnScope.PlusFeatureSettingsContent(contentFocusRequester: FocusRequester, sidebarFocusRequester: FocusRequester) {
    val context = LocalContext.current
    val plus by (context.applicationContext as MangoTvApplication).container.plusRepository.status.collectAsStateWithLifecycle()
    if (!plus.active) {
        Column(Modifier.weight(1f)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextTertiary)
            Spacer(Modifier.height(8.dp))
            Text("Plus settings are only for Arc TV Plus.", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    var smartPicking by remember { mutableStateOf(DevicePlayerPrefs.smartSourcePicking(context)) }
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "smart_header") {
            Column {
                Text(text = "Smart source picking", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Skips Select a Source and starts the best source for this device. If none surely plays, you still get the list.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        listOf(false to "Smart source picking: Off", true to "Smart source picking: On").forEachIndexed { index, (on, label) ->
            item(key = "smart_$on") {
                LanguageOptionRow(
                    label = label,
                    selected = smartPicking == on,
                    onClick = {
                        smartPicking = on
                        DevicePlayerPrefs.setSmartSourcePicking(context, on)
                    },
                    focusRequester = if (index == 0) contentFocusRequester else null,
                    focusLeft = sidebarFocusRequester
                )
            }
        }
    }
}
