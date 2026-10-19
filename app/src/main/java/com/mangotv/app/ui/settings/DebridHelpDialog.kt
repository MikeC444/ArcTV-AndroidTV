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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mangotv.app.MangoTvApplication
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
    ScanWithPhoneDialog(
        message = "Open your phone's camera and point it at the code to read the debrid guide.",
        url = DEBRID_GUIDE_URL,
        shownUrl = "web.arctv.org/guides/debrid",
        onClose = onClose
    )
}

/**
 * Account tab's "Change password": the TV has no browser, so a QR code takes the person to the web app's page on their phone. The code holds a
 * one-time link the server makes for the account this TV is signed in to (it works for 15 minutes), so the page changes that account's password
 * whatever the phone is signed in as. Setting it signs every device out, this TV included.
 */
@Composable
fun ChangePasswordDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { (context.applicationContext as MangoTvApplication).container.authRepository }
    var attempt by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        link = null
        failed = false
        auth.passwordChangeLink().onSuccess { link = it }.onFailure { failed = true }
    }
    val url = link
    if (url != null) {
        ScanWithPhoneDialog(
            message = "Scan to choose a new password for this account. The link works once, for 15 minutes. Changing it signs every device out, this TV included.",
            url = url,
            shownUrl = "Opens web.arctv.org",
            onClose = onClose
        )
    } else {
        MessageDialog(
            message = if (failed) "Couldn't get a link right now. Check your connection and try again." else "Getting your link…",
            actionText = if (failed) "Try again" else null,
            onAction = { attempt++ },
            onClose = onClose
        )
    }
}

/** A small dialog with one line, an optional action button and Close; BACK dismisses it. */
@Composable
private fun MessageDialog(message: String, actionText: String?, onAction: () -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(actionText) { runCatching { focus.requestFocus() } }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .background(MangoBackgroundElevated, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Change password", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(text = message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                if (actionText != null) {
                    MangoButton(
                        text = actionText,
                        icon = Icons.Filled.Refresh,
                        onClick = onAction,
                        style = MangoButtonStyle.FILLED,
                        focusRequester = focus,
                        compact = true,
                        borderColor = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                MangoButton(
                    text = "Close",
                    icon = Icons.Filled.Close,
                    onClick = onClose,
                    style = MangoButtonStyle.GLASS,
                    focusRequester = if (actionText == null) focus else null,
                    compact = true,
                    borderColor = TextPrimary
                )
            }
        }
    }
}

/** A QR code for a phone with a line of explanation under the title. BACK or Close dismisses it. */
@Composable
fun ScanWithPhoneDialog(message: String, url: String, shownUrl: String, onClose: () -> Unit) {
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
                    text = message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                QrCodeImage(content = url, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(12.dp))
                Text(text = shownUrl, color = TextTertiary, style = MaterialTheme.typography.labelMedium)
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
