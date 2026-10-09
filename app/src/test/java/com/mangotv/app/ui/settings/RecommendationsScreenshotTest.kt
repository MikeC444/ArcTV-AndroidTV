package com.mangotv.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mangotv.app.data.feedback.FeedbackEntry
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.SavedListItem
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
 * Draws the real Settings screen on "Recommendations" and saves it as a PNG when -Dscreenshot.out=<file> is given (otherwise it only checks
 * that the screen composes and that the tabs and Reset work). A desktop render of the app's own composables, not a picture from a TV.
 * -Dposter.dir=<folder> points at downloaded posters named <id>.jpg.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h1100dp-xhdpi", sdk = [34])
class RecommendationsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun entry(id: String, title: String, value: Feedback, at: Int) = FeedbackEntry(
        value = value.wire, title = title, at = "2026-10-1${at}T00:00:00.000Z", providerId = "com.linvo.cinemeta",
        posterUrl = System.getProperty("poster.dir")?.let { "file://$it/$id.jpg" }
    )

    private val feedback = mapOf(
        "tt0816692" to entry("tt0816692", "Interstellar", Feedback.LIKE, 1),
        "tt1160419" to entry("tt1160419", "Dune", Feedback.LIKE, 2),
        "tt2543164" to entry("tt2543164", "Arrival", Feedback.LIKE, 3),
        "tt1856101" to entry("tt1856101", "Blade Runner 2049", Feedback.LIKE, 4),
        "tt1457767" to entry("tt1457767", "The Conjuring", Feedback.DISLIKE, 5),
        "tt0387564" to entry("tt0387564", "Saw", Feedback.DISLIKE, 6),
        "tt5814060" to entry("tt5814060", "The Nun", Feedback.DISLIKE, 7)
    )
    private val list = listOf(
        SavedListItem(id = "w1", type = ContentType.MOVIE, title = "Watched One", posterUrl = null, backdropUrl = null, year = null, rating = null, providerId = "p", watched = true),
        SavedListItem(id = "s1", type = ContentType.TV_SHOW, title = "Saved Show", posterUrl = null, backdropUrl = null, year = null, rating = null, providerId = "p", watched = false)
    )

    private fun draw(data: Map<String, FeedbackEntry>, removed: MutableList<String> = mutableListOf()) {
        val nav = FocusRequester(); val content = FocusRequester()
        rule.setContent {
            MangoTvTheme {
                SettingsLayout(
                    selected = SettingsCategory.RECOMMENDATIONS, onSelect = {}, plusActive = true, onNavigate = {},
                    navFocusRequester = nav, paneContentFocusRequester = content, rowFocusRequesterFor = { FocusRequester() },
                    pane = { _, sidebar ->
                        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.then(androidx.compose.ui.Modifier).fillMaxSize()) {
                            RecommendationsPanel(
                                feedback = data, list = list,
                                onRemove = { id, _ -> removed += id },
                                onReset = { all -> removed += all.map { it.key } },
                                contentFocusRequester = content, sidebarFocusRequester = sidebar,
                                modifier = androidx.compose.ui.Modifier.weight(1f)
                            )
                        }
                    }
                )
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        System.getProperty("screenshot.out")?.let { base ->
            val out = base.removeSuffix(".png") + name + ".png"
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test
    fun likedAndNotForMeTabs() {
        draw(feedback)
        rule.onNodeWithText("Liked (4)").assertExists()
        rule.onNodeWithText("Not for me (3)").assertExists()
        rule.onNodeWithText("Titles you like (4): +20 points").assertExists()
        rule.onNodeWithText("Titles marked Not for me (3): −15 points").assertExists()
        rule.onNodeWithText("Your My List and finished titles (1 finished, 1 saved): +3 points").assertExists()
        save("-liked")
        rule.onNodeWithText("Not for me (3)").performClick()
        rule.waitForIdle()
        save("-notforme")
    }

    @Test
    fun resetAsksFirstThenClearsEveryRating() {
        val removed = mutableListOf<String>()
        draw(feedback, removed)
        rule.onNodeWithText("Yes, reset").assertDoesNotExist() // asks first
        rule.onAllNodesWithText("Reset preferences")[1].performClick() // [0] is the heading, [1] the button
        rule.waitForIdle()
        rule.onNodeWithText("Yes, reset").assertExists()
        assertEquals(emptyList<String>(), removed) // nothing is removed until it is confirmed
        rule.onNodeWithText("Yes, reset").performClick()
        rule.waitForIdle()
        assertEquals(feedback.keys, removed.toSet()) // all seven ratings, movies and shows alike
    }
}
