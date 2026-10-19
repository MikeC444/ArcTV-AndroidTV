package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.ui.platform.LocalContext
import com.mangotv.app.MangoTvApplication
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Minimal on purpose — device management (Milestone 3's /auth/sessions:
 * viewing or revoking this account's *other* active sessions) is still
 * its own later milestone. "Sync existing data" prompts (Milestone 11)
 * and account switching (Milestone 12: Sign Out here now actually wipes
 * local caches, not just this device's session -- see
 * AccountSwitchCoordinator) are both built. What's here exists so a
 * signed-in build is actually re-testable (create account -> sign out ->
 * sign in again, as the same account or a different one) without
 * clearing app data, which would otherwise be the only way back to the
 * auth screen once past it.
 *
 * A ColumnScope extension, not its own screen -- SettingsScreen's detail
 * pane hosts this (and its 4 siblings) directly, so the shared top nav bar
 * only exists once instead of once per category.
 */
@Composable
fun ColumnScope.AccountSettingsContent(
    onSignedOut: () -> Unit,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    onOpenProfiles: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    // ArcTV Plus profiles: which profile this is, and the way to switch or manage them.
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    // Without Plus the account's own (default) profile is the one in use, as in the nav bar's profile chip.
    val activeProfile = if (!profiles.supported) null else if (plus.active) profiles.active else profiles.profiles.firstOrNull { it.isDefault }
    val session by viewModel.session.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    var showChangePassword by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    val user = session?.user
    if (user != null) {
        Text(text = user.displayName ?: user.email, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        if (user.displayName != null) {
            Text(text = user.email, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(16.dp))
    MangoButton(
        text = if (signingOut) "Signing Out…" else "Sign Out",
        icon = Icons.Filled.Logout,
        onClick = viewModel::signOut,
        style = MangoButtonStyle.GLASS,
        focusRequester = contentFocusRequester,
        focusUp = navFocusRequester,
        focusLeft = sidebarFocusRequester,
        compact = true,
        borderColor = TextPrimary
    )
    Spacer(Modifier.height(8.dp))
    MangoButton(
        text = "Change password",
        icon = Icons.Filled.Lock,
        onClick = { showChangePassword = true },
        style = MangoButtonStyle.GLASS,
        focusLeft = sidebarFocusRequester,
        compact = true,
        borderColor = TextPrimary
    )
    if (showChangePassword) ChangePasswordDialog(onClose = { showChangePassword = false })
    if (activeProfile == null) {
        // Say why, instead of just not showing profiles: an older service, or the list couldn't be read.
        val why = when {
            profiles.problem != null -> profiles.problem
            !profiles.ready -> "still loading"
            else -> "not available"
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "Profiles: $why.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
    }
    if (activeProfile != null) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Watching as ${activeProfile.name}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        MangoButton(
            text = "Switch or manage profiles",
            icon = Icons.Filled.SwitchAccount,
            onClick = onOpenProfiles,
            style = MangoButtonStyle.GLASS,
            focusLeft = sidebarFocusRequester,
            compact = true,
            borderColor = TextPrimary
        )
    }
}
