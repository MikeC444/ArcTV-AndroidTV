package com.mangotv.app.data.addon

import com.mangotv.app.data.model.DebridState
import com.mangotv.app.data.model.serviceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DebridAndErrorsTest {

    @Test
    fun `plus means cached and download means the service still has to fetch it`() {
        assertEquals(DebridState("RD", cached = true), parseDebridTag("[RD+] Torrentio"))
        assertEquals(DebridState("RD", cached = false), parseDebridTag("[RD download] Torrentio"))
        assertEquals(DebridState("TB", cached = true), parseDebridTag("  [tb+] Torrentio"))
        assertEquals(DebridState("AD", cached = false), parseDebridTag("[AD DOWNLOAD]"))
    }

    @Test
    fun `no debrid tag means it is not a debrid link`() {
        assertNull(parseDebridTag("Torrentio"))
        assertNull(parseDebridTag(""))
        assertNull(parseDebridTag(null))
        assertNull(parseDebridTag("Movie [RD+] in the middle"))
        assertNull(parseDebridTag("[1080p] Torrentio"))
    }

    @Test
    fun `known services get their full name and unknown ones keep the code`() {
        assertEquals("Real-Debrid", DebridState("RD", true).serviceName())
        assertEquals("TorBox", DebridState("TB", true).serviceName())
        assertEquals("XY", DebridState("XY", true).serviceName())
    }

    @Test
    fun `a stream mapped from an addon answer carries its debrid state`() {
        val stream = StremioStream(url = "https://x/y.mkv", name = "[RD download] Torrentio", title = "Movie.2160p.WEB-DL").toStream("p", "Torrentio")
        assertEquals(DebridState("RD", cached = false), stream.debrid)
        assertNull(StremioStream(url = "https://x/y.mp4", name = "Torrentio", title = "Movie.720p").toStream("p", "Torrentio").debrid)
    }

    @Test
    fun `addon errors are explained without ever showing an address`() {
        assertEquals("is limiting requests right now — try again in a moment", describeAddonError(AddonHttpException(429, "https://secret/key")))
        assertEquals("had a problem answering (HTTP 503)", describeAddonError(AddonHttpException(503, "https://secret/key")))
        assertEquals("refused the request (HTTP 403)", describeAddonError(AddonHttpException(403, "https://secret/key")))
        assertEquals("took too long to answer", describeAddonError(SocketTimeoutException("timeout")))
        assertEquals("couldn't be reached", describeAddonError(UnknownHostException("https://secret/key")))
    }
}
