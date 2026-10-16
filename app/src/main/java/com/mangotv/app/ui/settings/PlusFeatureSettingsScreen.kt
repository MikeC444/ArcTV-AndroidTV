package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.player.DevicePlayerPrefs
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
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
        contentPadding = SettingsListPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "smart_picking") {
            SettingsSwitchRow(
                title = "Smart source picking",
                description = "Skips Select a Source and starts the best source for this device. If none surely plays, you still get the list.",
                checked = smartPicking,
                onToggle = {
                    smartPicking = !smartPicking
                    DevicePlayerPrefs.setSmartSourcePicking(context, smartPicking)
                },
                focusRequester = contentFocusRequester,
                focusLeft = sidebarFocusRequester
            )
        }
    }
}

/** A Settings row with a title, a line of explanation and an on/off switch; the whole row is the focus target and pressing it flips the switch. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusLeft: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // A full-width row: the default poster-card scale would push it past the screen's safe margin.
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusLeft = focusLeft
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = if (checked) TextPrimary else TextSecondary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(text = description, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(16.dp))
            Text(text = if (checked) "On" else "Off", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            // Display only: the row around it takes the press.
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
    }
}
