package com.mangotv.app.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * "Add a torrent" on Select a Source: a magnet link (or its info hash) or the address of a .torrent file typed or pasted in, or a .torrent
 * file picked from the device where the system offers a file picker. The source then appears in the list like any other. [error] is the
 * reason the last attempt was refused; the dialog closes by itself ([onAdded] ran) when it worked.
 */
@Composable
fun AddTorrentDialog(
    error: String?,
    onSubmitText: (String) -> Unit,
    onPickFile: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPickFile(uri) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Inside the dialog's own window, where the field exists: asking before that finds nothing to focus.
        LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
        Box(modifier = Modifier.fillMaxSize().background(Color(0xE608080A)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(720.dp)
                    .background(MangoBackgroundElevated, RoundedCornerShape(20.dp))
                    .padding(28.dp)
            ) {
                Text(text = "Add a torrent", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Paste a magnet link or the address of a .torrent file. It plays inside Arc TV, with nothing else to install.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("magnet:?xt=urn:btih:…  or  https://…/file.torrent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MangoSurface,
                        unfocusedContainerColor = MangoSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = ArcAccent,
                        focusedIndicatorColor = ArcAccent,
                        unfocusedIndicatorColor = TextTertiary
                    )
                )
                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(text = error, color = ErrorCoral, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangoButton(
                        text = "Add",
                        icon = Icons.Filled.Add,
                        onClick = { onSubmitText(text) },
                        style = MangoButtonStyle.FILLED,
                        borderColor = TextPrimary
                    )
                    MangoButton(
                        text = "Choose .torrent file",
                        icon = Icons.Filled.FolderOpen,
                        // A TV box without a file picker has nothing to open; the request then simply does nothing.
                        onClick = { runCatching { picker.launch(arrayOf("application/x-bittorrent", "application/octet-stream", "*/*")) } },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Only add torrents you have the right to watch.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
