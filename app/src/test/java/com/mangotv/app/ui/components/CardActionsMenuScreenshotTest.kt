package com.mangotv.app.ui.components

import androidx.activity.ComponentActivity
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

    private fun movie(progress: WatchProgress? = null, picked: Boolean = false, title: String = "Insidious: Out of the Further") = Content(
        id = "tt32393988", type = ContentType.MOVIE, title = title, description = "", providerId = "com.linvo.cinemeta",
        posterUrl = System.getProperty("poster.dir")?.let { "file://$it/tt32393988.jpg" }, backdropUrl = null,
        watchProgress = progress, pickedForYou = picked
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
            repeat(8) { Thread.sleep(150); rule.waitForIdle() }
            val out = base.removeSuffix(".png") + name + ".png"
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun plusMemberOnAPickedTitleWithProgress() {
        draw(movie(WatchProgress(positionMs = 25 * 60_000L, durationMs = 6_000_000L), picked = true), plus = true, inList = true, feedback = Feedback.LIKE, withCw = true)
        rule.onNodeWithText("Resume from 25m").assertExists()
        rule.onNodeWithText("Remove from My List").assertExists()
        rule.onNodeWithText("Remove like").assertExists()
        rule.onNodeWithText("Remove from Picked for you").assertExists()
        rule.onNodeWithText("Remove from Continue Watching").assertExists()
        save("-plus")
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
