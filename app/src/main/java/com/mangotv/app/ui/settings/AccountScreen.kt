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
import androidx.compose.runtime.remember
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
    val container = remember { (LocalContext.current.applicationContext as MangoTvApplication).container }
    val profiles by container.profileRepository.state.collectAsStateWithLifecycle()
    val plus by container.plusRepository.status.collectAsStateWithLifecycle()
    val activeProfile = if (plus.active && profiles.supported) profiles.active else null
    val session by viewModel.session.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()

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
