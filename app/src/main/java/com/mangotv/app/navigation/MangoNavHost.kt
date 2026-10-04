package com.mangotv.app.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.audio.LocalUiSoundPlayer
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.profile.ActiveProfile
import com.mangotv.app.data.profile.needsProfilePicker
import com.mangotv.app.data.profile.visibleProfiles
import com.mangotv.app.ui.profiles.ProfilesScreen
import com.mangotv.app.ui.auth.AuthGateScreen
import com.mangotv.app.ui.auth.AuthMethodScreen
import com.mangotv.app.ui.auth.AuthStartScreen
import com.mangotv.app.ui.auth.GateDestination
import com.mangotv.app.ui.auth.PasswordSignInScreen
import com.mangotv.app.ui.auth.QrSignInScreen
import com.mangotv.app.ui.browse.MoviesScreen
import com.mangotv.app.ui.browse.TvShowsScreen
import com.mangotv.app.ui.detail.DetailScreen
import com.mangotv.app.ui.search.SearchScreen
import com.mangotv.app.ui.mylist.MyListScreen
import com.mangotv.app.ui.home.HomeScreen
import com.mangotv.app.ui.home.HomeViewModel
import com.mangotv.app.ui.player.PlayerScreen
import com.mangotv.app.ui.settings.AddAddonScreen
import com.mangotv.app.ui.settings.SettingsScreen
import com.mangotv.app.ui.sources.SourcesScreen
import com.mangotv.app.ui.components.CardActionsMenuOverlay
import com.mangotv.app.ui.components.CardActionsMenuState
import com.mangotv.app.ui.components.LocalCardActionsMenu
import com.mangotv.app.ui.plus.PlusPromoHost
import com.mangotv.app.ui.settings.PendingSettingsTab
import com.mangotv.app.ui.update.UpdatePromptHost
import com.mangotv.app.ui.update.UpdateViewModel
import java.net.URLDecoder
import kotlinx.coroutines.launch

// Static, argument-less top-level destinations reached from the top nav bar.
// Navigating to one of these reuses/restores its existing back-stack entry
// (and therefore its ViewModelStoreOwner) instead of always pushing a fresh
// one -- without this, every tab switch tore down and rebuilt
// HomeViewModel/MoviesViewModel/etc. from scratch, discarding all
// already-fetched data and re-running every network fetch on every visit.
private val TAB_ROOT_ROUTES = setOf(
    MangoRoutes.HOME, MangoRoutes.MOVIES, MangoRoutes.TV_SHOWS,
    MangoRoutes.SEARCH, MangoRoutes.MY_LIST, MangoRoutes.SETTINGS
)

/** A guest sent to sign in: the route they were heading for once signed in, or null to just return to where they were. */
private data class SignInReturn(val target: String?)

@Composable
fun MangoNavHost() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }

    // Constructed here, outside any NavHost destination, so it's scoped to
    // the Activity rather than to Home's own back-stack entry -- the HOME
    // destination below uses this exact instance, so Home keeps its fetched
    // rows across tab switches instead of rebuilding from scratch.
    val homeViewModel: HomeViewModel = viewModel()

    // Scoped to the Activity, same as homeViewModel above -- checks GitHub
    // for a newer release once per process, independent of which tab/screen
    // is showing.
    val updateViewModel: UpdateViewModel = viewModel()

    // One instance for the whole app, provided below so any ContentCard
    // (however deeply nested in Home's rows or a browse grid) can open its
    // long-press quick-actions menu with zero prop-threading -- see
    // CardActionsMenu.kt's own doc.
    val cardActionsMenuState = remember { CardActionsMenuState() }

    // Where a guest was heading when they were sent to sign in (null target: just back to where they were). Null
    // when the sign-in screens were reached any other way, e.g. after signing out.
    var signInReturn by remember { mutableStateOf<SignInReturn?>(null) }

    // Provided here, above the nav graph, so every TvFocusSurface anywhere in the app (cards, buttons, nav
    // items) can play the nav/click sounds without each screen having to
    // thread UiSoundPlayer through its own parameters.
    CompositionLocalProvider(
        LocalUiSoundPlayer provides container.uiSoundPlayer,
        LocalCardActionsMenu provides cardActionsMenuState
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        val navController = rememberNavController()

        // NavHost registers its OWN back-press handling internally (it's
        // what makes plain BACK navigate the back stack, and what drives
        // predictive-back) the moment it composes -- so a BackHandler
        // composed BEFORE the NavHost call below would be registered
        // EARLIER, making it the LESS recently registered callback, which
        // Compose's dispatcher always loses to whatever was registered
        // after it. That's exactly what silently broke this the first time:
        // NavHost's own default handling won, popped the stack correctly,
        // and this BackHandler's sound-playing body just never ran at all.
        // Composing it below, AFTER NavHost, fixes that -- it becomes the
        // most recently registered handler, so it wins by default.
        //
        // PlayerScreen registers its own more specific BackHandler (close
        // menus/scrub-mode/controls before actually leaving) as part of
        // NavHost's own content, i.e. AFTER NavHost's internal handler but
        // BEFORE this one -- so simply being "most recent" would make this
        // one wrongly outrank it too. enabled = !isPlayerActive is what
        // keeps the ordering fix from also swallowing that: disabled here
        // means Compose's dispatcher skips straight past this callback to
        // the next-most-recently-registered enabled one, which is
        // PlayerScreen's.
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val isPlayerActive = currentBackStackEntry?.destination?.route == MangoRoutes.PLAYER_PATTERN
        val onProfilesRoute = currentBackStackEntry?.destination?.route == MangoRoutes.PROFILES

        // ArcTV Plus profiles: an account with Plus and more than one profile starts every launch at "Who's watching?" (the profile list
        // is read by SyncManager.syncAll just after launch, so this fires a moment after Home first appears). Not while a title is
        // playing, and not on the sign-in screens.
        val profilesState by container.profileRepository.state.collectAsStateWithLifecycle()
        val plusStatus by container.plusRepository.status.collectAsStateWithLifecycle()
        val isGuestNow by container.guestGate.isGuest.collectAsStateWithLifecycle()
        val pickerWanted = !isGuestNow && profilesState.ready &&
            needsProfilePicker(
                supported = profilesState.supported,
                plus = plusStatus.active,
                profileCount = visibleProfiles(profilesState.profiles, plusStatus.active).size,
                chosen = profilesState.chosen
            )
        val currentRoute = currentBackStackEntry?.destination?.route
        LaunchedEffect(pickerWanted, currentRoute) {
            val route = currentRoute ?: return@LaunchedEffect
            if (pickerWanted && route != MangoRoutes.PROFILES && route != MangoRoutes.AUTH_GATE && !route.startsWith("auth/") && !route.startsWith("player/")) {
                navController.navigate(MangoRoutes.PROFILES) { launchSingleTop = true }
            }
        }

        // A guest (someone browsing without an account) is asked to sign in for Play, My List, Settings and saving a
        // title. The sign-in screens are pushed on top of where they were, so once signed in they pop straight back to
        // it, and carry on to what they were heading for (see finishSignIn).
        fun askToSignIn(target: String?) {
            if (navController.currentDestination?.route?.startsWith("auth/") == true) return
            signInReturn = SignInReturn(target)
            navController.navigate(MangoRoutes.AUTH_START)
        }

        fun openRoute(route: String) {
            if (route in TAB_ROOT_ROUTES) {
                // Re-tapping the tab you're already on: with the popUpTo +
                // saveState + restoreState combo below (the standard bottom-
                // nav recipe), navigating to a route that's already the
                // current destination still pops it off the back stack
                // (popUpTo's range includes it) and immediately restores a
                // fresh copy of it -- launchSingleTop doesn't prevent this,
                // since the pop happens as part of the same navigate() call.
                // That pop+restore tears down and recreates the whole
                // screen's composition, which is what showed up as a brief
                // black flash (the window's raw background for one frame
                // before the recreated screen has drawn anything) every time
                // a nav item was tapped while already selected. Skipping the
                // call entirely when there's nothing to navigate to is a
                // plain no-op instead.
                if (navController.currentDestination?.route == route) return
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                }
            } else {
                navController.navigate(route)
            }
        }

        fun navigateTo(route: String) {
            // A kids profile has no Settings: addons, signing out and the Plus page are for the adults.
            if (ActiveProfile.kids.value && (route == MangoRoutes.SETTINGS || route == MangoRoutes.SETTINGS_ADD_ADDON)) return
            if (container.guestGate.isGuest.value && (routeNeedsAccount(route) || route == MangoRoutes.AUTH_START)) {
                askToSignIn(target = route.takeIf { it != MangoRoutes.AUTH_START })
                return
            }
            openRoute(route)
        }

        // A signed-out user is never left with anything to navigate back
        // into: both transitions below (auth gate -> a destination, and
        // sign-out -> AuthStart) clear the *entire* back stack via
        // graph.id rather than a specific route, so it doesn't matter what
        // was actually on the stack at the time.
        fun navigateClearingBackStack(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }

        // Signed in: if the sign-in screens were opened from inside the app (a guest pressing Play, say), pop back to
        // where they were and carry on to what they asked for; otherwise start fresh at Home. Goes straight to the
        // target rather than through navigateTo's guest check, which could still see the old state for a moment.
        fun finishSignIn() {
            val pending = signInReturn
            signInReturn = null
            if (pending != null && navController.popBackStack(MangoRoutes.AUTH_START, inclusive = true)) {
                pending.target?.let { openRoute(it) }
            } else {
                navigateClearingBackStack(MangoRoutes.HOME)
            }
        }

        // Saving a title (My List, Watched) from a card menu, the hero or a title page asks a guest to sign in.
        LaunchedEffect(Unit) {
            container.guestGate.signInRequests.collect { askToSignIn(target = null) }
        }

        // Same "skip the picker if a source is already remembered for this
        // exact title/season/episode" logic DetailScreen's own
        // navigateToPlayback uses (see its own doc for why) -- shared here
        // so the card actions menu's Play/Resume item behaves identically
        // no matter where the long-pressed card came from.
        fun resolvePlayRoute(content: Content): String {
            val providerId = content.providerId ?: return MangoRoutes.HOME
            val season = content.watchProgress?.seasonNumber
            val episode = content.watchProgress?.episodeNumber
            val streamId = container.lastSourceRepository.findLastStreamId(providerId, content.id, content.type, season, episode)
            return if (streamId != null) {
                MangoRoutes.player(providerId, content.type, content.id, season, episode, streamId)
            } else {
                MangoRoutes.sources(providerId, content.type, content.id, season, episode)
            }
        }

        // Where the nav ("scrolling"/focus-move) sound actually plays --
        // deliberately NOT tied to any element's focus-gained state (see
        // TvFocusSurface's own doc for why that played extra, unearned
        // ticks on every screen open/return). A real D-pad direction
        // KeyDown is the one signal that's unambiguously "the user
        // physically moved," so this is the single global place that
        // triggers it, for the whole app. Sits above NavHost, not inside
        // any one screen, so it keeps working across every destination
        // without each one wiring it in separately. Always returns false
        // (never consumes) -- this only ever adds a side effect, it must
        // never interfere with any screen's own key handling (seeking,
        // BACK interception, menu navigation, ...).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Silenced while the player is active -- D-pad moves there
                    // are scrubbing the timeline and navigating the player's
                    // own overlay (quality/subtitle/settings menus), not
                    // browsing the rest of the interface, and the same tick
                    // firing on every one of those reads as interface chrome
                    // noise over what should just be video/audio. See
                    // PlayerScreen's own composable below for the matching
                    // LocalUiSoundPlayer override that silences its buttons'
                    // click/back sounds the same way.
                    if (!isPlayerActive &&
                        event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                            event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                    ) {
                        container.uiSoundPlayer.playNav()
                    }
                    false
                }
        ) {
            // Suppressed while the player is active, same reasoning as the
            // nav-sound listener just above: an "Update available" banner
            // over the video is exactly the kind of interface chrome that
            // shouldn't compete with what the user is actually watching.
            UpdatePromptHost(viewModel = updateViewModel, suppressed = isPlayerActive) {
            NavHost(navController = navController, startDestination = MangoRoutes.AUTH_GATE) {
                composable(MangoRoutes.AUTH_GATE) {
                    AuthGateScreen(
                        onNavigate = { destination ->
                            val target = when (destination) {
                                GateDestination.Home -> MangoRoutes.HOME
                            }
                            navigateClearingBackStack(target)
                        }
                    )
                }
                composable(MangoRoutes.AUTH_START) {
                    val scope = rememberCoroutineScope()
                    // Only when this is the first screen (after signing out): a guest sent here from inside the app
                    // can press BACK instead.
                    val browseAsGuest: (() -> Unit)? = if (signInReturn == null) {
                        {
                            scope.launch {
                                container.addonRepository.ensureDefaultAddon()
                                navigateClearingBackStack(MangoRoutes.HOME)
                            }
                            Unit
                        }
                    } else {
                        null
                    }
                    AuthStartScreen(
                        onSignIn = { navController.navigate(MangoRoutes.authMethod("login")) },
                        onCreateAccount = { navController.navigate(MangoRoutes.authMethod("register")) },
                        onBrowseAsGuest = browseAsGuest
                    )
                }
                composable(MangoRoutes.AUTH_METHOD_PATTERN) { backStackEntry ->
                    val intent = backStackEntry.arguments?.getString("intent") ?: "login"
                    AuthMethodScreen(
                        intent = intent,
                        onScanQr = { navController.navigate(MangoRoutes.authQr(intent)) },
                        onUseRemote = { navController.navigate(MangoRoutes.authPassword(intent)) }
                    )
                }
                composable(MangoRoutes.AUTH_QR_PATTERN) {
                    QrSignInScreen(
                        onAuthenticated = { finishSignIn() }
                    )
                }
                composable(MangoRoutes.AUTH_PASSWORD_PATTERN) {
                    PasswordSignInScreen(
                        onAuthenticated = { finishSignIn() }
                    )
                }
                composable(MangoRoutes.HOME) {
                    HomeScreen(
                        onNavigate = ::navigateTo,
                        onResume = { content -> navigateTo(resolvePlayRoute(content)) },
                        viewModel = homeViewModel
                    )
                    // A gentle Arc TV Plus invitation, only here on Home (it decides by itself whether the person should see it).
                    PlusPromoHost(onTakeMeThere = {
                        PendingSettingsTab.openPlus()
                        navigateTo(MangoRoutes.SETTINGS)
                    })
                }
                composable(MangoRoutes.PROFILES) {
                    ProfilesScreen(
                        onFinished = { switched ->
                            // A different profile has other addons, rows and blocked genres: Home refetches for it.
                            if (switched) homeViewModel.load()
                            navigateClearingBackStack(MangoRoutes.HOME)
                        },
                        onBack = { navController.popBackStack() },
                        onSignOut = { navigateClearingBackStack(MangoRoutes.AUTH_START) }
                    )
                }
                composable(MangoRoutes.SETTINGS) {
                    SettingsScreen(
                        onNavigate = ::navigateTo,
                        onSignedOut = { navigateClearingBackStack(MangoRoutes.AUTH_START) },
                        onAddAddon = { navController.navigate(MangoRoutes.SETTINGS_ADD_ADDON) }
                    )
                }
                composable(MangoRoutes.MOVIES) {
                    MoviesScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.TV_SHOWS) {
                    TvShowsScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SEARCH) {
                    SearchScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.MY_LIST) {
                    MyListScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SETTINGS_ADD_ADDON) {
                    AddAddonScreen(
                        onNavigate = ::navigateTo,
                        onInstalled = { navController.popBackStack() }
                    )
                }
                composable(MangoRoutes.DETAIL_PATTERN) {
                    DetailScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(
                    MangoRoutes.SOURCES_PATTERN,
                    arguments = listOf(navArgument("auto") { type = NavType.StringType; defaultValue = "false" })
                ) {
                    SourcesScreen(
                        onNavigate = ::navigateTo,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(MangoRoutes.PLAYER_PATTERN) { backStackEntry ->
                    // Silences every TvFocusSurface's click/back sound (Play/
                    // Pause, the timeline, quality/subtitle/settings menus, ...)
                    // for as long as the player is on screen -- TvFocusSurface
                    // and PlayerScreen's own BackHandler both read this same
                    // composition local, so overriding it here to null covers
                    // all of them at once instead of threading a "silent"
                    // flag through every individual control. Paired with the
                    // isPlayerActive guard above the NavHost, which handles
                    // the one nav-tick sound that plays from outside any
                    // TvFocusSurface at all.
                    CompositionLocalProvider(LocalUiSoundPlayer provides null) {
                        PlayerScreen(
                            onBack = { navController.popBackStack() },
                            // "Next episode": Sources picks the best source by itself, and replaces the player so Back skips the finished episode.
                            onNextEpisode = { nextSeason, nextEpisode ->
                                val args = backStackEntry.arguments
                                val providerId = URLDecoder.decode(args?.getString("providerId").orEmpty(), "UTF-8")
                                val type = if (args?.getString("type") == ContentType.TV_SHOW.name) ContentType.TV_SHOW else ContentType.MOVIE
                                val id = URLDecoder.decode(args?.getString("id").orEmpty(), "UTF-8")
                                navController.navigate(MangoRoutes.sources(providerId, type, id, nextSeason, nextEpisode, autoPlay = true)) {
                                    popUpTo(MangoRoutes.PLAYER_PATTERN) { inclusive = true }
                                }
                            },
                            // Pops the player off the back stack before pushing Sources
                            // rather than stacking Sources on top of a dead player
                            // instance the user could otherwise navigate back into.
                            onChangeSource = {
                                val args = backStackEntry.arguments
                                val providerId = URLDecoder.decode(args?.getString("providerId").orEmpty(), "UTF-8")
                                val type = if (args?.getString("type") == ContentType.TV_SHOW.name) {
                                    ContentType.TV_SHOW
                                } else {
                                    ContentType.MOVIE
                                }
                                val id = URLDecoder.decode(args?.getString("id").orEmpty(), "UTF-8")
                                val season = args?.getString("season")?.toIntOrNull()?.takeIf { it >= 0 }
                                val episode = args?.getString("episode")?.toIntOrNull()?.takeIf { it >= 0 }
                                // Explicit "change source" request -- always show
                                // the picker, even for a title that would
                                // otherwise auto-continue with the very source
                                // being changed away from (see sources()'s own
                                // doc on skipAutoSelect).
                                navController.navigate(MangoRoutes.sources(providerId, type, id, season, episode, skipAutoSelect = true)) {
                                    popUpTo(MangoRoutes.PLAYER_PATTERN) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
            } // UpdatePromptHost
        }

        // Global fallback for the hardware/remote BACK button -- see the
        // doc above (by navController/isPlayerActive) for why this has to
        // be composed here, after NavHost, rather than before it. Falls
        // back to finishing the Activity when there's nothing left to pop
        // (i.e. at Home), matching what BACK would already do with no
        // handler at all -- this replaces that default, so it has to
        // reproduce it itself.
        // The profile screen answers BACK itself (it must not be skipped at launch).
        BackHandler(enabled = !isPlayerActive && !onProfilesRoute) {
            container.uiSoundPlayer.playBack()
            if (!navController.popBackStack()) {
                (context as? Activity)?.finish()
            }
        }

        // Composed last (see the ordering note above the BackHandler right
        // above this) so its own internal BackHandler -- enabled only while
        // a card's menu is actually open -- registers most recently and
        // takes priority: BACK closes the menu instead of leaving the
        // screen behind it.
        CardActionsMenuOverlay(
            state = cardActionsMenuState,
            myListRepository = container.myListRepository,
            continueWatchingSyncRepository = container.continueWatchingSyncRepository,
            guestGate = container.guestGate,
            feedbackRepository = container.feedbackRepository,
            plusRepository = container.plusRepository,
            pickedStateRepository = container.pickedStateRepository,
            onNavigate = ::navigateTo,
            resolvePlayRoute = ::resolvePlayRoute
        )
        } // Box
    }
}
