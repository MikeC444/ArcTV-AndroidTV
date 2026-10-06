package com.mangotv.app.data.torrent

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** A resource the loopback server can serve with byte ranges. */
interface RangedContent {
    val length: Long
    val mimeType: String

    /**
     * A stream over bytes [start]..[endInclusive]. Reading it may block while data arrives (a torrent piece); it throws [IOException] when
     * the data can no longer arrive, and is closed by the server when the response ends or the client goes away.
     */
    fun open(start: Long, endInclusive: Long): InputStream
}

/**
 * A minimal HTTP/1.1 server bound to the loopback interface only, serving [RangedContent] to the app's own players (ExoPlayer, LibVLC, an
 * external player on the device). Supports GET and HEAD, `Range` (single range, `a-b`, `a-` and `-n`) with the correct 200 / 206 / 416
 * responses, and always closes the connection after a response, so no idle sockets are left behind. Written by hand rather than on a
 * library so range handling and the blocking-read behaviour are fully under control and testable on the plain JVM.
 */
class LoopbackHttpServer(
    private val resolve: (path: String) -> RangedContent?,
    private val log: (String) -> Unit = {}
) : Closeable {
    private val serverSocket = ServerSocket(0, BACKLOG, IPV4_LOOPBACK)
    private val clients: MutableSet<Socket> = Collections.synchronizedSet(HashSet())
    private val threadCount = AtomicInteger()
    private val pool: ExecutorService = Executors.newCachedThreadPool(ThreadFactory { r ->
        Thread(r, "torrent-http-${threadCount.incrementAndGet()}").apply { isDaemon = true }
    })
    @Volatile private var closed = false

    val port: Int get() = serverSocket.localPort

    init {
        pool.execute {
            while (!closed) {
                val socket = try { serverSocket.accept() } catch (_: IOException) { break }
                clients += socket
                try { pool.execute { handle(socket) } } catch (_: Exception) { closeQuietly(socket) }
            }
        }
    }

    override fun close() {
        closed = true
        closeQuietly(serverSocket)
        synchronized(clients) { clients.toList() }.forEach { closeQuietly(it) }
        clients.clear()
        pool.shutdownNow()
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = REQUEST_READ_TIMEOUT_MS
            val request = readRequest(socket.getInputStream()) ?: return
            socket.soTimeout = 0
            respond(request, socket.getOutputStream())
        } catch (_: IOException) {
            // Client went away (a seek, or the player closing); nothing to report.
        } finally {
            clients -= socket
            closeQuietly(socket)
        }
    }

    private class Request(val method: String, val path: String, val range: String?)

    private fun readRequest(input: InputStream): Request? {
        val head = StringBuilder()
        var matched = 0
        while (head.length < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            head.append(b.toChar())
            matched = if (b == "\r\n\r\n"[matched].code) matched + 1 else if (b == '\r'.code) 1 else 0
            if (matched == 4) break
        }
        if (matched != 4) return null
        val lines = head.toString().split("\r\n")
        val parts = lines[0].split(' ')
        if (parts.size < 2) return null
        val range = lines.drop(1).firstOrNull { it.startsWith("range:", ignoreCase = true) }?.substringAfter(':')?.trim()
        return Request(parts[0].uppercase(), parts[1].substringBefore('?'), range)
    }

    private fun respond(request: Request, out: OutputStream) {
        if (request.method != "GET" && request.method != "HEAD") {
            return writeSimple(out, 405, "Method Not Allowed", "Allow: GET, HEAD\r\n")
        }
        val content = try { resolve(request.path) } catch (_: Exception) { null }
            ?: return writeSimple(out, 404, "Not Found")
        val length = content.length
        log("http ${request.method} range=${request.range ?: "-"} length=$length")
        val body = when (val range = parseRangeHeader(request.range, length)) {
            RangeRequest.Unsatisfiable -> return writeSimple(out, 416, "Range Not Satisfiable", "Content-Range: bytes */$length\r\n")
            RangeRequest.Full -> Triple(200, 0L, length - 1)
            is RangeRequest.Partial -> Triple(206, range.start, range.endInclusive)
        }
        val (status, start, end) = body
        val count = if (length == 0L) 0 else end - start + 1
        val head = StringBuilder()
            .append(if (status == 206) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            .append("Content-Type: ").append(content.mimeType).append("\r\n")
            .append("Content-Length: ").append(count).append("\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Cache-Control: no-store\r\n")
            .append("Connection: close\r\n")
        if (status == 206) head.append("Content-Range: bytes $start-$end/$length\r\n")
        head.append("\r\n")

        if (request.method == "HEAD" || count == 0L) {
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            out.flush()
            return
        }
        // The first chunk is read before any header goes out, so a source that cannot deliver answers 503 with the reason instead of
        // a 200 followed by a body that stops.
        val buffer = ByteArray(CHUNK)
        val source = try {
            content.open(start, end)
        } catch (e: IOException) {
            return writeSimple(out, 503, "Service Unavailable", "", e.message)
        }
        source.use { input ->
            var remaining = count
            var n = try { input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()) } catch (e: IOException) {
                return writeSimple(out, 503, "Service Unavailable", "", e.message)
            }
            if (n <= 0) return writeSimple(out, 503, "Service Unavailable", "", "no data")
            out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
            while (true) {
                out.write(buffer, 0, n)
                remaining -= n
                if (remaining <= 0) break
                n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n <= 0) break // the source ended early: close, so the client sees a short body rather than waiting for ever
            }
            out.flush()
        }
    }

    private fun writeSimple(out: OutputStream, code: Int, reason: String, extraHeaders: String = "", message: String? = null) {
        val body = (message ?: reason).toByteArray(Charsets.UTF_8)
        val head = "HTTP/1.1 $code $reason\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\n$extraHeaders" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun closeQuietly(c: Closeable) { try { c.close() } catch (_: Exception) {} }

    companion object {
        // Explicitly IPv4: on Android getLoopbackAddress() can be ::1, which would leave nothing listening on the 127.0.0.1 address the URL names.
        private val IPV4_LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        private const val BACKLOG = 16
        private const val REQUEST_READ_TIMEOUT_MS = 10_000
        private const val MAX_HEAD_BYTES = 16 * 1024
        private const val CHUNK = 64 * 1024
    }
}
