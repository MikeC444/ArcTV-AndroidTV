package com.mangotv.app.ui.browse

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.ui.components.ContentCard
import com.mangotv.app.ui.components.ServiceCardStyle
import com.mangotv.app.ui.theme.MangoTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Draws Netflix's page pieces (the Top 10 row and its cards) with the app's own composables. A desktop render, not a picture from a TV. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi", sdk = [34])
class TopTenRowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun title(id: String, name: String) =
        Content(id = id, type = ContentType.MOVIE, title = name, description = "", posterUrl = null, backdropUrl = null, providerId = "p")

    @Test
    fun `the row has its heading and a poster for each title, and pressing one opens it`() {
        val opened = mutableListOf<String>()
        rule.setContent {
            MangoTvTheme {
                TopTenRow(
                    titles = listOf(title("a", "Alpha"), title("b", "Beta"), title("c", "Gamma")),
                    onItemClick = { opened += it.id },
                    focusRequester = FocusRequester(),
                    onNavigateUp = {}
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Top 10 Today").assertExists()
        // each rank is drawn twice (its outline and its fill)
        rule.onAllNodesWithText("1").assertCountEquals(2)
        rule.onAllNodesWithText("3").assertCountEquals(2)
        rule.onNodeWithContentDescription("Beta").performClick()
        assertEquals(listOf("b"), opened)
    }

    @Test
    fun `while the list is on its way it shows the heading and ten grey posters with their numbers`() {
        rule.setContent {
            MangoTvTheme {
                TopTenRow(titles = null, onItemClick = {}, focusRequester = FocusRequester(), onNavigateUp = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Top 10 Today").assertExists()
        rule.onAllNodesWithText("1").assertCountEquals(2)
    }

    @Test
    fun `a wide card on a service page has no caption, a poster card there has none either`() {
        rule.setContent {
            MangoTvTheme {
                Row {
                    ContentCard(content = title("a", "Alpha"), onClick = {}, serviceStyle = ServiceCardStyle(width = 160.dp, wide = true))
                    ContentCard(content = title("b", "Beta"), onClick = {}, serviceStyle = ServiceCardStyle(width = 112.dp, wide = false))
                    ContentCard(content = title("c", "Gamma"), onClick = {})
                }
            }
        }
        rule.waitForIdle()
        // only the ordinary card has its title as text under it
        rule.onNodeWithText("Alpha").assertDoesNotExist()
        rule.onNodeWithText("Beta").assertDoesNotExist()
        rule.onNodeWithText("Gamma").assertExists()
    }
}
