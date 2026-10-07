package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.BuildConfig
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcWarn
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.update.ManualUpdateCheck
import com.mangotv.app.ui.update.UpdateViewModel

/** Settings > Updates: the app's version, a "Check for updates" button and what the last check found. */
@Composable
fun ColumnScope.UpdatesSettingsContent(viewModel: UpdateViewModel, contentFocusRequester: FocusRequester, sidebarFocusRequester: FocusRequester) {
    val check by viewModel.manualCheck.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(text = "Arc TV ${BuildConfig.VERSION_NAME}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        TvFocusSurface(
            onClick = viewModel::checkNow,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.02f,
            borderColor = TextPrimary,
            focusRequester = contentFocusRequester,
            focusLeft = sidebarFocusRequester
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(text = "Check for updates", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
        val (text, color) = when (val c = check) {
            ManualUpdateCheck.Idle -> null to TextSecondary
            ManualUpdateCheck.Checking -> "Checking…" to TextSecondary
            ManualUpdateCheck.UpToDate -> "You're on the latest version." to ArcAccent
            is ManualUpdateCheck.Available -> "${c.tag} is available." to ArcAccent
            ManualUpdateCheck.Failed -> "Couldn't check. Check your connection." to ArcWarn
        }
        if (text != null) {
            Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        }
    }
}
