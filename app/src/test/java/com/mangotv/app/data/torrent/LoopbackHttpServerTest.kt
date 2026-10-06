package com.mangotv.app.data.torrent

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Socket
import java.net.URL

class LoopbackHttpServerTest {
    private val data = ByteArray(10_000) { (it * 31 % 251).toByte() }
    private lateinit var server: LoopbackHttpServer
    private var openedClosed = 0

    private val good = object : RangedContent {
        override val length = data.size.toLong()
        override val mimeType = "video/mp4"
        override fun open(start: Long, endInclusive: Long): InputStream =
            object : ByteArrayInputStream(data, start.toInt(), (endInclusive - start + 1).toInt()) {
                override fun close() { openedClosed++ }
            }
    }
    private val failing = object : RangedContent {
        override val length = 100L
        override val mimeType = "video/mp4"
        override fun open(start: Long, endInclusive: Long): InputStream = object : InputStream() {
            override fun read(): Int = throw IOException("No one is sharing this torrent")
        }
    }

    @Before fun setUp() {
        server = LoopbackHttpServer { path -> when (path) { "/v.mp4" -> good; "/bad.mp4" -> failing; else -> null } }
    }

    @After fun tearDown() = server.close()

    private fun get(path: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val c = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        val headers = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.first() }
        val body = (if (code >= 400) c.errorStream else c.inputStream)?.readBytes() ?: ByteArray(0)
        return Triple(code, headers, body)
    }

    @Test fun fullResponseAdvertisesRanges() {
        val (code, h, body) = get("/v.mp4")
        assertEquals(200, code)
        assertEquals("bytes", h["accept-ranges"])
        assertEquals("video/mp4", h["content-type"])
        assertEquals("10000", h["content-length"])
        assertArrayEquals(data, body)
    }

    @Test fun partialResponses() {
        var (code, h, body) = get("/v.mp4", "bytes=100-199")
        assertEquals(206, code)
        assertEquals("bytes 100-199/10000", h["content-range"])
        assertEquals("100", h["content-length"])
        assertArrayEquals(data.copyOfRange(100, 200), body)

        get("/v.mp4", "bytes=9990-").let { (c, hh, b) ->
            assertEquals(206, c); assertEquals("bytes 9990-9999/10000", hh["content-range"]); assertArrayEquals(data.copyOfRange(9990, 10000), b)
        }
        get("/v.mp4", "bytes=-50").let { (c, hh, b) ->
            assertEquals(206, c); assertEquals("bytes 9950-9999/10000", hh["content-range"]); assertArrayEquals(data.copyOfRange(9950, 10000), b)
        }
    }

    @Test fun unsatisfiableRange() {
        val (code, h, _) = get("/v.mp4", "bytes=10000-")
        assertEquals(416, code)
        assertEquals("bytes */10000", h["content-range"])
    }

    @Test fun headHasNoBodyButTheLength() {
        val (code, h, body) = get("/v.mp4", method = "HEAD")
        assertEquals(200, code)
        assertEquals("10000", h["content-length"])
        assertEquals(0, body.size)
        val (c2, h2, b2) = get("/v.mp4", "bytes=0-9", "HEAD")
        assertEquals(206, c2); assertEquals("10", h2["content-length"]); assertEquals(0, b2.size)
    }

    @Test fun unknownPathAndMethod() {
        assertEquals(404, get("/nope").first)
        assertEquals(405, get("/v.mp4", method = "POST").first)
    }

    @Test fun sourceThatCannotDeliverAnswers503WithTheReason() {
        val (code, _, body) = get("/bad.mp4")
        assertEquals(503, code)
        assertEquals("No one is sharing this torrent", String(body))
    }

    @Test fun sourceStreamsAreClosedAfterTheResponse() {
        get("/v.mp4", "bytes=0-9")
        repeat(50) { if (openedClosed == 0) Thread.sleep(20) }
        assertEquals(1, openedClosed)
    }

    @Test fun listensOnLoopbackOnly() {
        // A non-loopback address of this machine must not accept connections.
        val others = java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .filter { !it.isLoopbackAddress && it is java.net.Inet4Address }
        for (addr in others) {
            val refused = try { Socket(addr, server.port).close(); false } catch (_: IOException) { true }
            assertTrue("reachable on $addr", refused)
        }
        Socket(InetAddress.getLoopbackAddress(), server.port).close()
    }

    @Test fun closeEndsOpenConnections() {
        val s = Socket(InetAddress.getLoopbackAddress(), server.port)
        server.close()
        s.soTimeout = 2000
        val r = try { s.getInputStream().read() } catch (_: IOException) { -1 }
        assertEquals(-1, r)
        s.close()
    }
}
