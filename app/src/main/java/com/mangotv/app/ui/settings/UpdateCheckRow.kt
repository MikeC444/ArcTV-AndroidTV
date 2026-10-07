package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Column
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
import com.mangotv.app.ui.theme.TextTertiary
import com.mangotv.app.ui.update.ManualUpdateCheck
import com.mangotv.app.ui.update.UpdateViewModel

/** The foot of Settings' side panel: the app's version in small text, a "Check for updates" button and what the last check found. */
@Composable
fun UpdateCheckRow(viewModel: UpdateViewModel, focusRight: FocusRequester?) {
    val check by viewModel.manualCheck.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Arc TV ${BuildConfig.VERSION_NAME}", color = TextTertiary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 10.dp))
        Spacer(Modifier.height(6.dp))
        TvFocusSurface(
            onClick = viewModel::checkNow,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            focusedScale = 1.02f,
            borderColor = TextPrimary,
            focusRight = focusRight
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = null, tint = ArcAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text = "Check for updates", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            }
        }
        val (text, color) = when (val c = check) {
            ManualUpdateCheck.Idle -> null to TextSecondary
            ManualUpdateCheck.Checking -> "Checking\u2026" to TextSecondary
            ManualUpdateCheck.UpToDate -> "You're on the latest version." to ArcAccent
            is ManualUpdateCheck.Available -> "${c.tag} is available." to ArcAccent
            ManualUpdateCheck.Failed -> "Couldn't check. Check your connection." to ArcWarn
        }
        if (text != null) {
            Text(text = text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
        }
    }
}
