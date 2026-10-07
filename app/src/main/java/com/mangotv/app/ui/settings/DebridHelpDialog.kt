package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/** The page on the web app that explains debrid services, opened from a phone through a QR code. */
const val DEBRID_GUIDE_URL = "https://web.arctv.org/guides/debrid"

/** "Click here for help" on the Addons tab: a QR code for a phone, since the TV has no browser. BACK or Close dismisses it. */
@Composable
fun DebridHelpDialog(onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        // Inside the dialog's own window, where the button exists: asking before that finds nothing to focus.
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .background(MangoBackgroundElevated, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Scan with your phone", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Open your phone's camera and point it at the code to read the debrid guide.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                QrCodeImage(content = DEBRID_GUIDE_URL, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(12.dp))
                Text(text = "web.arctv.org/guides/debrid", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(18.dp))
                MangoButton(
                    text = "Close",
                    icon = Icons.Filled.Close,
                    onClick = onClose,
                    style = MangoButtonStyle.FILLED,
                    focusRequester = focus,
                    compact = true,
                    borderColor = TextPrimary
                )
            }
        }
    }
}
