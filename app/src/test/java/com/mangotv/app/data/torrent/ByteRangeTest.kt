package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteRangeTest {
    private fun p(header: String?, len: Long = 1000) = parseRangeHeader(header, len)

    @Test fun absentOrUnknownUnitMeansWholeResource() {
        assertEquals(RangeRequest.Full, p(null))
        assertEquals(RangeRequest.Full, p(""))
        assertEquals(RangeRequest.Full, p("items=0-5"))
    }

    @Test fun closedRange() {
        assertEquals(RangeRequest.Partial(0, 99), p("bytes=0-99"))
        assertEquals(RangeRequest.Partial(100, 199), p("BYTES= 100-199 "))
        assertEquals(100L, (p("bytes=0-99") as RangeRequest.Partial).length)
    }

    @Test fun openEndedAndClampedToTheEnd() {
        assertEquals(RangeRequest.Partial(900, 999), p("bytes=900-"))
        assertEquals(RangeRequest.Partial(900, 999), p("bytes=900-5000"))
        assertEquals(RangeRequest.Partial(999, 999), p("bytes=999-"))
    }

    @Test fun suffixRange() {
        assertEquals(RangeRequest.Partial(500, 999), p("bytes=-500"))
        assertEquals(RangeRequest.Partial(0, 999), p("bytes=-5000"))
        assertEquals(RangeRequest.Unsatisfiable, p("bytes=-0"))
    }

    @Test fun startPastTheEndIsUnsatisfiable() {
        assertEquals(RangeRequest.Unsatisfiable, p("bytes=1000-"))
        assertEquals(RangeRequest.Unsatisfiable, p("bytes=1000-1100"))
        assertEquals(RangeRequest.Unsatisfiable, p("bytes=99999999999999999999-"))
    }

    @Test fun malformedOrMultiRangeIsIgnored() {
        assertEquals(RangeRequest.Full, p("bytes=abc-def"))
        assertEquals(RangeRequest.Full, p("bytes=50-10"))
        assertEquals(RangeRequest.Full, p("bytes=0-10,20-30"))
        assertEquals(RangeRequest.Full, p("bytes=5"))
        assertEquals(RangeRequest.Full, p("bytes=-x"))
    }

    @Test fun emptyResourceHasNoRanges() {
        assertEquals(RangeRequest.Full, p("bytes=0-10", 0))
    }
}
