package com.mangotv.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.WatchProgress
import com.mangotv.app.data.recommend.Feedback
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the real card menu at 1920x1080 and saves PNGs when -PscreenshotOut=<file> is given (otherwise it only checks that it composes and
 * that each button reaches its action). A desktop render of the app's own composables, not a picture from a TV.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class CardActionsMenuScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun asset(name: String): String? = System.getProperty("poster.dir")?.let { "file://$it/$name" }

    private fun movie(progress: WatchProgress? = null, picked: Boolean = false, title: String = "Insidious: Out of the Further") = Content(
        id = "tt32393988", type = ContentType.MOVIE, title = title, description = "", providerId = "com.linvo.cinemeta",
        posterUrl = asset("tt32393988.jpg"), backdropUrl = null,
        watchProgress = progress, pickedForYou = picked
    )

    /** With the title's wide backdrop, and its logo unless [logo] is false. */
    private fun withBackdrop(logo: Boolean) = Content(
        id = "tt0816692", type = ContentType.MOVIE, title = if (logo) "Interstellar" else "Midsomer Murders", description = "", providerId = "com.linvo.cinemeta",
        posterUrl = asset("tt0118401.jpg"), backdropUrl = asset(if (logo) "tt0816692-bg.jpg" else "tt0118401-bg.jpg"),
        logoUrl = if (logo) asset("tt0816692-logo.png") else null
    )

    private val calls = mutableListOf<String>()

    private fun draw(content: Content, plus: Boolean, inList: Boolean = false, watched: Boolean = false, feedback: Feedback? = null, withCw: Boolean = false) {
        rule.setContent {
            MangoTvTheme {
                CardActionsMenuPanel(
                    content = content, isInMyList = inList, isWatched = watched, feedback = feedback, plusActive = plus,
                    firstFocusRequester = FocusRequester(),
                    onPlay = { calls += "play" }, onToggleMyList = { calls += "list" }, onToggleWatched = { calls += "watched" },
                    onLike = { calls += "like" }, onDislike = { calls += "dislike" }, onRemoveFromPicked = { calls += "picked" },
                    onViewDetails = { calls += "details" },
                    onRemoveFromContinueWatching = if (withCw) ({ calls += "cw" }) else null,
                    onChooseSource = { calls += "source" }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        System.getProperty("screenshot.out")?.let { base ->
            // The poster loads in the background: give it a moment before drawing.
            // Pictures arrive in the background and need frames to be drawn: let time pass and frames run.
            repeat(30) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100); rule.waitForIdle() }
            val out = base.removeSuffix(".png") + name + ".png"
            val view = rule.activity.window.decorView
            // A first draw pass makes the views re-record what changed since the pictures arrived; the second one is the picture.
            // The pictures fade in: move the (paused) main-thread clock on so the fade finishes.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            repeat(3) {
                rule.runOnUiThread { view.invalidate() }
                rule.waitForIdle()
                view.draw(android.graphics.Canvas(android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)))
                // the fade starts when a picture is first drawn, so the clock must keep moving between the passes
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
            }
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun plusMemberOnAPickedTitleWithProgress() {
        draw(movie(WatchProgress(positionMs = 25 * 60_000L, durationMs = 6_000_000L), picked = true), plus = true, inList = true, feedback = Feedback.LIKE, withCw = true)
        rule.onNodeWithText("Resume from 25m").assertExists()
        rule.onNodeWithText("In My List").assertExists()
        rule.onNodeWithText("Add to My List").assertDoesNotExist()
        rule.onNodeWithText("Like").assertExists()
        rule.onNodeWithText("Remove from Picked for you").assertExists()
        rule.onNodeWithText("Remove from Continue Watching").assertExists()
        save("-plus")
    }

    @Test
    fun backdropWithLogoOverIt() {
        draw(withBackdrop(logo = true), plus = true)
        // The logo stands in for the title text (only checkable when the local logo file is supplied).
        if (asset("tt0816692-logo.png") != null) rule.onNodeWithText("Interstellar").assertDoesNotExist()
        save("-banner-logo")
    }

    @Test
    fun backdropWithoutALogoShowsTheTitleAsText() {
        draw(withBackdrop(logo = false), plus = true, inList = true, watched = true, feedback = Feedback.DISLIKE)
        rule.onNodeWithText("Midsomer Murders").assertExists()
        rule.onNodeWithText("Watched").assertExists()
        rule.onNodeWithText("In My List").assertExists()
        save("-banner-text")
    }

    @Test
    fun pressedButtonsShowTheirStateAndPressingAgainUndoesIt() {
        rule.setContent {
            var inList by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var watched by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            MangoTvTheme {
                CardActionsMenuPanel(
                    content = movie(), isInMyList = inList, isWatched = watched, feedback = null, plusActive = false,
                    firstFocusRequester = FocusRequester(),
                    onPlay = {}, onToggleMyList = { inList = !inList }, onToggleWatched = { watched = !watched },
                    onLike = {}, onDislike = {}, onRemoveFromPicked = {}, onViewDetails = {}, onRemoveFromContinueWatching = null, onChooseSource = {}
                )
            }
        }
        rule.onNodeWithText("Add to My List").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("In My List").assertExists()
        rule.onNodeWithText("Mark as watched").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Watched").assertExists()
        rule.onNodeWithText("In My List").performClick() // again: undone
        rule.waitForIdle()
        rule.onNodeWithText("Add to My List").assertExists()
        rule.onNodeWithText("Watched").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Mark as watched").assertExists()
    }

    @Test
    fun plainMovieWithoutPlus() {
        draw(movie(), plus = false)
        rule.onNodeWithText("Play").assertExists()
        rule.onNodeWithText("Like").assertDoesNotExist() // Like / Not for me are Plus features
        rule.onNodeWithText("Remove from Picked for you").assertDoesNotExist()
        save("-free")
    }

    @Test
    fun everyButtonReachesItsAction() {
        draw(movie(WatchProgress(1_000L, 6_000_000L), picked = true), plus = true, withCw = true)
        listOf(
            "Resume from 0m" to "play", "Add to My List" to "list", "Mark as watched" to "watched", "Like" to "like", "Not for me" to "dislike",
            "Remove from Picked for you" to "picked", "View Details" to "details", "Remove from Continue Watching" to "cw", "Choose Source" to "source"
        ).forEach { (label, call) ->
            calls.clear()
            rule.onNodeWithText(label).performClick()
            assertEquals(label, listOf(call), calls)
        }
    }
}
