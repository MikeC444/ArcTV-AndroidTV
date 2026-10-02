package com.mangotv.app.ui.profiles

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.profile.AVATARS
import com.mangotv.app.data.profile.KIND_ADULT
import com.mangotv.app.data.profile.KIND_KIDS
import com.mangotv.app.data.profile.PIN_LENGTH
import com.mangotv.app.data.profile.PROFILE_NAME_MAX
import com.mangotv.app.data.profile.PinChange
import com.mangotv.app.data.profile.Profile
import com.mangotv.app.data.profile.ProfileException
import com.mangotv.app.ui.components.ArcLogo
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ErrorCoral
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * "Who's watching?" (ArcTV Plus profiles): pick a profile to open its library, or manage profiles (add, rename, change the picture,
 * Adult / Kids, PIN, remove). Shown at launch for an account with Plus and more than one profile, and from the nav bar's profile item.
 *
 * [onFinished] is called once a profile is open (true when it is a different one, so Home has to reload); [onBack] is only offered when
 * the screen was opened on purpose (something was already picked since launch) so launch can't skip the question.
 */
@Composable
fun ProfilesScreen(
    onFinished: (switched: Boolean) -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfilesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var managing by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf<Profile?>(null) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val firstTile = remember { FocusRequester() }
    val canGoBack = state.chosen || !state.plus || !state.supported

    BackHandler(enabled = asking == null && editing == null) {
        if (managing) managing = false else if (canGoBack) onBack()
    }
    LaunchedEffect(state.ready, state.profiles.size) {
        if (state.ready && state.profiles.isNotEmpty()) runCatching { firstTile.requestFocus() }
    }

    fun openProfile(profile: Profile, pin: String?) {
        error = null
        viewModel.open(
            profile = profile,
            pin = pin,
            onResult = { result ->
                when (result) {
                    ProfileResult.Ok -> {
                        asking = null
                        pinError = null
                    }
                    is ProfileResult.Failed -> if (profile.hasPin && asking != null) pinError = result.message else error = result.message
                }
            },
            onDone = onFinished
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(MangoBackground)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ArcLogo()
                Spacer(Modifier.weight(1f))
                MangoButton(text = "Switch account", icon = Icons.Filled.Close, onClick = { viewModel.signOut(onSignOut) }, compact = true)
            }
            Spacer(Modifier.height(20.dp))
            Column(modifier = Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (managing) "Manage profiles." else "Welcome back to ArcTV.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (managing) "Choose a profile to change." else "Who's watching today?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (managing) "Rename it, change its picture, lock it with a PIN or remove it." else "Choose your profile to pick up where you left off.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = error.orEmpty(),
                    color = if (error != null) ErrorCoral else Color.Transparent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.height(20.dp)
                )
                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    state.profiles.forEachIndexed { index, profile ->
                        ProfileTile(
                            profile = profile,
                            current = profile.id == state.activeId,
                            managing = managing,
                            focusRequester = if (index == 0) firstTile else null,
                            onClick = {
                                when {
                                    managing -> editing = EditTarget.Existing(profile)
                                    profile.hasPin -> {
                                        pinError = null
                                        asking = profile
                                    }
                                    else -> openProfile(profile, null)
                                }
                            }
                        )
                    }
                    if (state.supported && state.plus && state.profiles.size < state.limit) {
                        AddTile(onClick = { editing = EditTarget.New })
                    }
                }

                Spacer(Modifier.height(30.dp))
                if (state.supported && state.plus) {
                    MangoButton(
                        text = if (managing) "Done" else "Manage profiles",
                        icon = if (managing) Icons.Filled.Check else Icons.Filled.Edit,
                        onClick = { managing = !managing },
                        compact = true
                    )
                    if (state.profiles.size >= state.limit) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = "An account can have up to ${state.limit} profiles.", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (state.ready && state.supported && !state.plus) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "More profiles come with ArcTV Plus: a separate My List, Continue Watching and recommendations for everyone at home, kids profiles, and a PIN on any profile.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
                if (state.ready && !state.supported) {
                    Text(text = "Profiles aren't available yet. Everything you watch stays in your account's one library for now.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                if (!managing && state.ready && canGoBack) {
                    Spacer(Modifier.height(10.dp))
                    MangoButton(text = "Back", icon = Icons.Filled.ArrowBack, onClick = onBack, compact = true)
                }
            }
        }
    }

    asking?.let { profile ->
        FullScreenDialog(onDismiss = { asking = null; pinError = null }) {
            PinPad(
                title = "Enter PIN for ${profile.name}",
                message = pinError,
                resetKey = pinError,
                onComplete = { pin -> openProfile(profile, pin) },
                onCancel = { asking = null; pinError = null }
            )
        }
    }
    editing?.let { target ->
        FullScreenDialog(onDismiss = { editing = null }) {
            ProfileEditor(target = target, viewModel = viewModel, onClose = { editing = null })
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    current: Boolean,
    managing: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        TvFocusSurface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            focusRequester = focusRequester,
            alwaysShowBorder = current && !managing,
            modifier = Modifier.size(140.dp)
        ) {
            ProfileAvatarTile(avatar = profile.avatar, size = 140.dp)
            if (profile.hasPin) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(28.dp).background(Color(0x99000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Lock, contentDescription = "Locked with a PIN", tint = TextPrimary, modifier = Modifier.size(16.dp)) }
            }
            if (managing) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0x80000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(text = profile.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        if (profile.isKids) SmallCaption("KIDS")
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
        TvFocusSurface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            backgroundColor = MangoBackgroundElevated,
            modifier = Modifier.size(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "Add profile", tint = TextSecondary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(text = "Add profile", color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xE608080A)), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .background(MangoBackgroundElevated, RoundedCornerShape(20.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { content() }
        }
    }
}

sealed interface EditTarget {
    data object New : EditTarget
    data class Existing(val profile: Profile) : EditTarget
}

/** Where the editor is: filling in the profile, or asking for a PIN (the current one before saving / removing a locked profile; a new one, twice). */
private sealed interface EditorStep {
    data object Form : EditorStep
    data class CurrentPin(val then: PendingAction) : EditorStep
    data object NewPin : EditorStep
    data class ConfirmPin(val first: String) : EditorStep
}

private enum class PendingAction { SAVE, REMOVE }

@Composable
private fun ProfileEditor(target: EditTarget, viewModel: ProfilesViewModel, onClose: () -> Unit) {
    val existing = (target as? EditTarget.Existing)?.profile
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(existing?.avatar ?: AVATARS.first().id) }
    var kind by remember { mutableStateOf(existing?.kind ?: KIND_ADULT) }
    var pin by remember { mutableStateOf<PinChange>(PinChange.Keep) }
    var step by remember { mutableStateOf<EditorStep>(EditorStep.Form) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val locked = existing?.hasPin == true
    val willHavePin = when (pin) {
        PinChange.Remove -> false
        is PinChange.Set -> true
        PinChange.Keep -> locked
    }

    LaunchedEffect(step) { if (step == EditorStep.Form && existing == null) runCatching { nameFocus.requestFocus() } }

    fun finish(result: ProfileResult) {
        when (result) {
            ProfileResult.Ok -> onClose()
            is ProfileResult.Failed -> {
                if (result.code == ProfileException.Code.WRONG_PIN || result.code == ProfileException.Code.LOCKED) {
                    pinMessage = result.message
                } else {
                    error = result.message
                    step = EditorStep.Form
                }
            }
        }
    }

    fun perform(action: PendingAction, currentPin: String?) {
        when (action) {
            PendingAction.REMOVE -> viewModel.remove(requireNotNull(existing), currentPin, ::finish)
            PendingAction.SAVE -> if (existing == null) {
                viewModel.create(name.trim(), avatar, kind, (pin as? PinChange.Set)?.pin, ::finish)
            } else {
                viewModel.update(existing, name.trim(), avatar, if (existing.isDefault) null else kind, pin, currentPin, ::finish)
            }
        }
    }

    fun request(action: PendingAction) {
        error = null
        if (action == PendingAction.SAVE && name.isBlank()) {
            error = "Give the profile a name."
            return
        }
        if (locked) {
            pinMessage = null
            step = EditorStep.CurrentPin(action)
        } else {
            perform(action, null)
        }
    }

    when (val current = step) {
        EditorStep.Form -> Column(modifier = Modifier.width(620.dp), horizontalAlignment = Alignment.Start) {
            Text(text = if (existing == null) "Add profile" else "Edit ${existing.name}", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarTile(avatar = avatar, size = 84.dp)
                Spacer(Modifier.width(16.dp))
                TextField(
                    value = name,
                    onValueChange = { if (it.length <= PROFILE_NAME_MAX) name = it },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                    colors = profileTextFieldColors()
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Picture", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            AVATARS.chunked(6).forEach { rowAvatars ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowAvatars.forEach { a ->
                        TvFocusSurface(
                            onClick = { avatar = a.id },
                            shape = RoundedCornerShape(12.dp),
                            alwaysShowBorder = a.id == avatar,
                            borderColor = TextPrimary,
                            modifier = Modifier.size(64.dp)
                        ) { ProfileAvatarTile(avatar = a.id, size = 64.dp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("Who is it for?", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KindChoice("Adult", selected = kind == KIND_ADULT) { kind = KIND_ADULT }
                if (existing?.isDefault != true) KindChoice("Kids", selected = kind == KIND_KIDS) { kind = KIND_KIDS }
            }
            Text(
                text = if (existing?.isDefault == true) "The account's own profile is always an adult profile." else "A kids profile hides horror, thriller, crime, war and mystery titles, and has no Settings.",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = if (willHavePin) "Locked with a PIN" else "No PIN", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
                MangoButton(
                    text = if (willHavePin) "Change PIN" else "Set a PIN",
                    icon = Icons.Filled.Lock,
                    onClick = { pinMessage = null; step = EditorStep.NewPin },
                    compact = true
                )
                if (willHavePin) {
                    MangoButton(text = "Remove PIN", icon = Icons.Filled.Close, onClick = { pin = PinChange.Remove }, compact = true)
                }
            }
            Text(
                text = error.orEmpty(),
                color = if (error != null) ErrorCoral else Color.Transparent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).height(22.dp)
            )
            if (confirmRemove && existing != null) {
                Text("Remove ${existing.name}? Its My List, Continue Watching, settings and recommendations are deleted for good.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangoButton(text = "Keep it", icon = Icons.Filled.Check, onClick = { confirmRemove = false }, compact = true)
                    MangoButton(text = "Remove", icon = Icons.Filled.Delete, onClick = { confirmRemove = false; request(PendingAction.REMOVE) }, compact = true, style = MangoButtonStyle.FILLED)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MangoButton(text = if (existing == null) "Create" else "Save", icon = Icons.Filled.Check, onClick = { request(PendingAction.SAVE) }, compact = true, style = MangoButtonStyle.FILLED)
                    MangoButton(text = "Cancel", icon = Icons.Filled.Close, onClick = onClose, compact = true)
                    if (existing != null && !existing.isDefault) {
                        MangoButton(text = "Remove", icon = Icons.Filled.Delete, onClick = { confirmRemove = true }, compact = true)
                    }
                }
            }
            if (busy) Text("Working…", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        }
        is EditorStep.CurrentPin -> PinPad(
            title = "Enter the current PIN for ${existing?.name.orEmpty()}",
            message = pinMessage,
            resetKey = pinMessage,
            onComplete = { entered -> perform(current.then, entered) },
            onCancel = { step = EditorStep.Form }
        )
        EditorStep.NewPin -> PinPad(
            title = "Choose a $PIN_LENGTH-digit PIN",
            message = pinMessage,
            resetKey = step,
            onComplete = { first -> pinMessage = null; step = EditorStep.ConfirmPin(first) },
            onCancel = { step = EditorStep.Form }
        )
        is EditorStep.ConfirmPin -> PinPad(
            title = "Enter it again",
            message = pinMessage,
            resetKey = pinMessage ?: current,
            onComplete = { again ->
                if (again == current.first) {
                    pin = PinChange.Set(again)
                    pinMessage = null
                    step = EditorStep.Form
                } else {
                    pinMessage = "The two PINs don't match."
                    step = EditorStep.NewPin
                }
            },
            onCancel = { step = EditorStep.Form }
        )
    }
}

@Composable
private fun KindChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        backgroundColor = if (selected) ArcAccent else MangoSurface,
        modifier = Modifier.height(38.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 22.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = label, color = if (selected) MangoBackground else TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun profileTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MangoSurface,
    unfocusedContainerColor = MangoSurface,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = ArcAccent,
    focusedIndicatorColor = ArcAccent,
    unfocusedIndicatorColor = TextTertiary
)
