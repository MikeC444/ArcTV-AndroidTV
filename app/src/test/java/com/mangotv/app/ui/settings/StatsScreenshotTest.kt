package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mangotv.app.data.stats.HistoryItem
import com.mangotv.app.data.stats.computeStats
import com.mangotv.app.ui.theme.MangoTvTheme
import java.io.File
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the real Settings screen on "Your stats" at 1920x1080 and saves it as a PNG when -Dscreenshot.out=<file> is given (otherwise it only
 * checks that the screen composes). A desktop render of the app's own composables, not a picture from a TV.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class StatsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statsScreen() = render("")

    /** The same screen on a tall canvas, so every block of the (scrolling) pane is visible at once. */
    @Test
    @Config(qualifiers = "w960dp-h1000dp-xhdpi", sdk = [34])
    fun statsScreenFullLength() = render("-full")

    private fun render(suffix: String) {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val items = (0 until 70).map { i ->
            val movie = i % 3 == 0
            HistoryItem(
                providerId = "p", contentId = "c$i", isMovie = movie,
                positionMs = if (movie) 6_300_000L else 2_700_000L, durationMs = if (movie) 6_300_000L else 2_700_000L,
                completed = true, watchedAtMs = now - ((i * 7L + (i % 5)) % 80) * day - (i % 4) * 3_600_000L
            )
        }
        val stats = computeStats(items, now, ZoneId.systemDefault())
        val nav = FocusRequester(); val content = FocusRequester()
        rule.setContent {
            MangoTvTheme {
                SettingsLayout(
                    selected = SettingsCategory.STATS, onSelect = {}, plusActive = true, onNavigate = {},
                    navFocusRequester = nav, paneContentFocusRequester = content, rowFocusRequesterFor = { FocusRequester() },
                    footer = {},
                    pane = { _, sidebar -> StatsPanel(stats, false, content, sidebar, Modifier.fillMaxSize()) }
                )
            }
        }
        rule.waitForIdle()
        System.getProperty("screenshot.out")?.let { base ->
            val out = base.removeSuffix(".png") + suffix + ".png"
            val view = rule.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
