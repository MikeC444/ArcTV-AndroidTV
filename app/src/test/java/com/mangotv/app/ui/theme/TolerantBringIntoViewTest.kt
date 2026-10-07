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
}
