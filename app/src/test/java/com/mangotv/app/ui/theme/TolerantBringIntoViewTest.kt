package com.mangotv.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class TolerantBringIntoViewTest {
    private val spec = MangoMotion.tolerantBringIntoViewSpec(slackPx = 3f, marginPx = 10f)

    @Test fun visibleItemDoesNotScroll() = assertEquals(0f, spec.calculateScrollDistance(100f, 60f, 500f), 0f)

    @Test fun scaleUpSliverDoesNotScroll() = assertEquals(0f, spec.calculateScrollDistance(442f, 60f, 500f), 0f)

    @Test fun halfHiddenLastRowScrollsFullyIntoView() {
        val d = spec.calculateScrollDistance(location(), 60f, 500f)
        assertTrue(d >= 30f + 10f - 0.01f) // the 30 hidden dp plus the margin
    }

    private fun location() = 470f

    @Test fun rowAboveTheTopScrollsDown() = assertEquals(-30f, spec.calculateScrollDistance(-20f, 60f, 500f), 0f)

    @Test fun rowRestingOnTheBottomEdgeScrollsUpByTheInsetWhenOneIsSet() {
        val inset = MangoMotion.tolerantBringIntoViewSpec(slackPx = 3f, marginPx = 10f, bottomInsetPx = 28f)
        assertEquals(0f, spec.calculateScrollDistance(440f, 60f, 500f), 0f) // flush with the edge: fine without an inset
        assertEquals(38f, inset.calculateScrollDistance(440f, 60f, 500f), 0.01f) // 60 + 440 - 472 + 10
        assertEquals(0f, inset.calculateScrollDistance(380f, 60f, 500f), 0f) // already clear of the inset
    }

    @Test fun rowRestingOnTheTopEdgeScrollsDownByTheTopInsetWhenOneIsSet() {
        val inset = MangoMotion.tolerantBringIntoViewSpec(slackPx = 3f, marginPx = 10f, topInsetPx = 14f)
        assertEquals(0f, spec.calculateScrollDistance(0f, 60f, 500f), 0f) // flush with the top: fine without an inset
        assertEquals(-14f, inset.calculateScrollDistance(0f, 60f, 500f), 0.01f)
        assertEquals(0f, inset.calculateScrollDistance(12f, 60f, 500f), 0f) // within the slack of the inset
    }
}
