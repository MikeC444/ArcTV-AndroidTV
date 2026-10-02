package com.mangotv.app.ui.sources

import com.mangotv.app.data.model.DebridState
import com.mangotv.app.data.model.StreamLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesHintsTest {

    private fun row(name: String, lookup: StreamLookup?) = AddonLookupRow(name, lookup)

    @Test
    fun `each answer reads plainly`() {
        assertEquals("Checking…", lookupText(null))
        assertEquals("1 source", lookupText(StreamLookup.Ok(1)))
        assertEquals("12 sources", lookupText(StreamLookup.Ok(12)))
        assertEquals("No streams for this title", lookupText(StreamLookup.None))
        assertEquals("Doesn't provide streams", lookupText(StreamLookup.Unsupported))
        assertEquals("took too long to answer", lookupText(StreamLookup.Failed("took too long to answer")))
    }

    @Test
    fun `the empty-state hint names the actual cause`() {
        assertTrue(noSourcesHint(emptyList()).contains("don't have any addons"))
        assertTrue(noSourcesHint(listOf(row("A", StreamLookup.None), row("B", StreamLookup.Failed("x")))).contains("didn't answer"))
        assertTrue(noSourcesHint(listOf(row("A", StreamLookup.Unsupported), row("B", StreamLookup.Unsupported))).contains("None of your installed addons provide streams"))
        assertTrue(noSourcesHint(listOf(row("A", StreamLookup.None), row("B", StreamLookup.Unsupported))).contains("none of them has a stream for this title"))
    }

    @Test
    fun `a failure outranks the other causes`() {
        val rows = listOf(row("A", StreamLookup.Unsupported), row("B", StreamLookup.Failed("took too long to answer")))
        assertTrue(anyAddonFailed(rows))
        assertEquals(listOf("B took too long to answer"), failedAddonLines(rows))
        assertFalse(anyAddonFailed(listOf(row("A", StreamLookup.Ok(2)), row("B", null))))
    }

    @Test
    fun `the debrid label says whether it starts at once`() {
        assertEquals("Cached on Real-Debrid", debridLabel(DebridState("RD", true)))
        assertEquals("Not cached on Real-Debrid — may take minutes", debridLabel(DebridState("RD", false)))
    }
}
