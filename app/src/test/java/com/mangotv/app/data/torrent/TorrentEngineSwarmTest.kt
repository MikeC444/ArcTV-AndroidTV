package com.mangotv.app.data.torrent

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random

/**
 * End to end on one machine: a real libtorrent seeder serves a small synthetic multi-file torrent (random bytes made here, no third-party
 * content) on the loopback interface, and the engine streams it through its HTTP endpoint exactly as a player would. Needs the desktop
 * libtorrent4j native library; skipped (not failed) where it cannot load, such as on a machine without it.
 */
class TorrentEngineSwarmTest {
    private lateinit var work: File
    private lateinit var seeder: SessionManager
    private lateinit var torrentBytes: ByteArray
    private val files = linkedMapOf<String, ByteArray>()
    private val engines = mutableListOf<TorrentEngine>()
    private val seederPort = 47000 + Random().nextInt(2000)
    private lateinit var infoHash: String

    @Before fun setUp() {
        work = Files.createTempDirectory("arctv-swarm").toFile()
        val rnd = Random(42)
        fun bytes(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
        files["pack/Show.S01E01.mkv"] = bytes(5_000_000)
        files["pack/Show.S01E02.mkv"] = bytes(6_500_000)
        files["pack/Show.S01E09.mkv"] = bytes(24_000_000)
        files["pack/Sample/sample.mkv"] = bytes(300_000)
        files["pack/readme.txt"] = "hello".toByteArray()
        for ((path, data) in files) File(work, path).apply { parentFile.mkdirs() }.writeBytes(data)
        try {
            torrentBytes = TorrentBuilder().path(File(work, "pack")).pieceSize(256 * 1024).generate().entry().bencode()
            seeder = SessionManager(false)
            val settings = SettingsPack().listenInterfaces("127.0.0.1:$seederPort")
            settings.setEnableDht(false)
            seeder.start(SessionParams(settings))
        } catch (t: Throwable) {
            assumeTrue("libtorrent native library unavailable: $t", false)
        }
        val ti = TorrentInfo(torrentBytes)
        infoHash = ti.infoHash().toHex()
        seeder.download(ti, work, null, null, null, TorrentFlags.SEED_MODE)
        Thread.sleep(500)
    }

    @After fun tearDown() {
        engines.forEach { runCatching { it.shutdown() } }
        runCatching { seeder.stop() }
        work.deleteRecursively()
    }

    private fun engine(): TorrentEngine = TorrentEngine(File(work, "engine-${engines.size}")) { println("[engine] $it") }.also { engines += it }

    private fun magnet() = MagnetLink.parse("magnet:?xt=urn:btih:$infoHash&dn=pack&x.pe=127.0.0.1:$seederPort")!!

    private fun fast(config: TorrentStreamConfig = TorrentStreamConfig()) =
        config.copy(readAheadBytes = 2 * 1024 * 1024, startBufferBytes = 512 * 1024, metadataTimeoutMs = 30_000, startTimeoutMs = 40_000, stallTimeoutMs = 30_000)

    private suspend fun playing(stream: TorrentEngine.TorrentStream): TorrentStreamState.Playing = withTimeout(60_000) {
        stream.state.first { it is TorrentStreamState.Playing || it is TorrentStreamState.Failed } as? TorrentStreamState.Playing
            ?: throw AssertionError("failed: " + (stream.state.value as TorrentStreamState.Failed).error.message)
    }

    private fun request(url: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.readTimeout = 60_000
        range?.let { c.setRequestProperty("Range", it) }
        val code = c.responseCode
        val headers = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.first() }
        val body = (if (code >= 400) c.errorStream else c.inputStream)?.readBytes() ?: ByteArray(0)
        return Triple(code, headers, body)
    }

    @Test fun streamsTheRequestedEpisodeFromAMagnetLinkWithCorrectRanges() = runBlocking {
        val expected = files.getValue("pack/Show.S01E02.mkv")
        val stream = engine().open(TorrentRequest(magnet(), null, FileHint(season = 1, episode = 2)), fast())
        val ready = playing(stream)
        assertEquals("Show.S01E02.mkv", ready.fileName)
        assertEquals(expected.size.toLong(), ready.fileSize)
        assertTrue(ready.url.startsWith("http://127.0.0.1:"))
        assertTrue(ready.url.endsWith("Show.S01E02.mkv"))

        val head = request(ready.url, method = "HEAD")
        assertEquals(200, head.first)
        assertEquals(expected.size.toString(), head.second["content-length"])
        assertEquals("bytes", head.second["accept-ranges"])
        assertEquals("video/x-matroska", head.second["content-type"])

        // Beginning, a seek into the middle that crosses piece boundaries, the tail (as a player probing for an index would), a suffix range.
        val a = request(ready.url, "bytes=0-99999")
        assertEquals(206, a.first)
        assertEquals("bytes 0-99999/${expected.size}", a.second["content-range"])
        assertArrayEquals(expected.copyOfRange(0, 100_000), a.third)

        val mid = request(ready.url, "bytes=3000017-4100016")
        assertEquals(206, mid.first)
        assertArrayEquals(expected.copyOfRange(3_000_017, 4_100_017), mid.third)

        val tail = request(ready.url, "bytes=-1000")
        assertEquals(206, tail.first)
        assertArrayEquals(expected.copyOfRange(expected.size - 1000, expected.size), tail.third)

        assertEquals(416, request(ready.url, "bytes=${expected.size}-").first)

        // Going back to the start still works after having jumped around.
        assertArrayEquals(expected.copyOfRange(0, 1000), request(ready.url, "bytes=0-999").third)

        // The whole file, hash-checked against the original.
        val whole = request(ready.url)
        assertEquals(200, whole.first)
        assertEquals(sha(expected), sha(whole.third))

        // Only the chosen file's data was ever fetched.
        val onDisk = File(work, "engine-0").walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        assertTrue("unexpected files: $onDisk", onDisk.all { it == "Show.S01E02.mkv" })

        stream.close()
        withTimeout(20_000) { stream.state.first { it is TorrentStreamState.Closed } }
        assertFalse("temporary data must be deleted", File(work, "engine-0").walkTopDown().any { it.isFile })
        // The native session and its sockets go away shortly after the last stream closes.
        val e = engines[0]
        val end = System.currentTimeMillis() + 15_000
        while (e.isRunning && System.currentTimeMillis() < end) Thread.sleep(100)
        assertFalse("engine should stop after the last stream closes", e.isRunning)
    }

    @Test fun streamsFromATorrentFile() = runBlocking {
        val expected = files.getValue("pack/Show.S01E02.mkv")
        val stream = engine().open(TorrentRequest(null, torrentBytes, FileHint(season = 1, episode = 2), peers = listOf("127.0.0.1:$seederPort")), fast())
        val ready = playing(stream)
        assertEquals("Show.S01E02.mkv", ready.fileName)
        assertArrayEquals(expected.copyOfRange(1_234_567, 1_300_000), request(ready.url, "bytes=1234567-1299999").third)
        stream.close()
    }

    @Test fun stoppingTheStreamMakesTheUrlStopWorking() = runBlocking {
        val stream = engine().open(TorrentRequest(magnet(), null, FileHint(fileIdx = 0)), fast())
        val ready = playing(stream)
        assertEquals(200, request(ready.url, "bytes=0-9").first.let { if (it == 206) 200 else it })
        stream.close()
        withTimeout(20_000) { stream.state.first { it is TorrentStreamState.Closed } }
        assertTrue(request(ready.url, "bytes=0-9").first in listOf(404, 503) || runCatching { request(ready.url, "bytes=0-9") }.isFailure)
    }

    @Test fun storageStaysBoundedWhileTheWholeFileIsPlayed() = runBlocking {
        val expected = files.getValue("pack/Show.S01E09.mkv")
        // A 4 MiB cap against a 24 MB file read slowly end to end: data behind the playhead must be dropped as it goes, and the file
        // still has to come out byte for byte right.
        val cap = 4L * 1024 * 1024
        val config = fast(TorrentStreamConfig(maxStorageBytes = cap))
        val trims = java.util.concurrent.atomic.AtomicInteger()
        val e = TorrentEngine(File(work, "engine-trim")) { if (it.startsWith("trim")) trims.incrementAndGet(); println("[engine] $it") }
        engines += e
        val stream = e.open(TorrentRequest(magnet(), null, FileHint(season = 1, episode = 9)), config)
        val ready = playing(stream)
        var maxOnDisk = 0L
        val out = java.io.ByteArrayOutputStream()
        val c = URL(ready.url).openConnection() as HttpURLConnection
        c.readTimeout = 60_000
        var nextSample = 0
        c.inputStream.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() >= nextSample) {
                    // A player consumes slower than the network delivers, so the cap is what limits the data held.
                    nextSample = out.size() + 256 * 1024
                    Thread.sleep(60)
                    // Steady state only: before the first trim the torrent may briefly hold more (see the engine's metadata poll).
                    val now = allocatedUnder(File(work, "engine-trim"))
                    if (trims.get() >= 1) maxOnDisk = maxOf(maxOnDisk, now)
                }
            }
        }
        val got = out.toByteArray()
        val firstDiff = (0 until minOf(got.size, expected.size)).firstOrNull { got[it] != expected[it] }
        assertEquals("length ${got.size}, first difference at $firstDiff", expected.size, got.size)
        assertEquals("first difference at $firstDiff", null, firstDiff)
        println("max allocated on disk: $maxOnDisk of ${expected.size}, trims: ${trims.get()}")
        assertTrue("storage was trimmed", trims.get() >= 2)
        // cap + the read-ahead window + what a player takes between two checks
        assertTrue("on-disk data stayed near the cap: $maxOnDisk", maxOnDisk <= cap + 2L * 1024 * 1024 + 4L * 1024 * 1024)
        stream.close()
    }

    @Test fun concurrentReadersAndSeeksAllGetCorrectBytes() = runBlocking {
        val expected = files.getValue("pack/Show.S01E09.mkv")
        val stream = engine().open(TorrentRequest(magnet(), null, FileHint(season = 1, episode = 9)), fast(TorrentStreamConfig(maxStorageBytes = 6L * 1024 * 1024)))
        val ready = playing(stream)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val rnd = Random(7)
        val threads = (0 until 4).map { t ->
            Thread {
                try {
                    val local = Random(rnd.nextLong())
                    repeat(12) {
                        val start = local.nextInt(expected.size - 400_000)
                        val len = 1 + local.nextInt(400_000)
                        val got = request(ready.url, "bytes=$start-${start + len - 1}")
                        if (got.first != 206 || !got.third.contentEquals(expected.copyOfRange(start, start + len))) failures += "reader $t range $start+$len status ${got.first}"
                    }
                } catch (e: Throwable) {
                    failures += "reader $t: $e"
                }
            }.also { it.start() }
        }
        threads.forEach { it.join(120_000) }
        assertTrue("failures: $failures", failures.isEmpty())
        stream.close()
    }

    @Test fun reportsAReadableErrorWhenNoOneIsSharing() = runBlocking {
        val dead = MagnetLink.parse("magnet:?xt=urn:btih:${"ab".repeat(20)}")!!
        val stream = engine().open(TorrentRequest(dead, null), fast().copy(metadataTimeoutMs = 4_000))
        val failed = withTimeout(30_000) { stream.state.first { it is TorrentStreamState.Failed } } as TorrentStreamState.Failed
        assertEquals(TorrentErrorKind.METADATA_TIMEOUT, failed.error.kind)
        assertTrue(failed.error.message.isNotBlank())
        stream.close()
    }

    @Test fun rejectsATorrentFileThatIsNotOne() = runBlocking {
        val stream = engine().open(TorrentRequest(null, "not a torrent".toByteArray()), fast())
        val failed = withTimeout(30_000) { stream.state.first { it is TorrentStreamState.Failed } } as TorrentStreamState.Failed
        assertEquals(TorrentErrorKind.INVALID_SOURCE, failed.error.kind)
        stream.close()
    }

    /** Bytes actually allocated under [dir] (the holes of a sparse file do not count), via `du`; the files' apparent size when `du` is missing. */
    private fun allocatedUnder(dir: File): Long = try {
        val p = ProcessBuilder("du", "-sk", dir.absolutePath).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().trim().substringBefore('\t').substringBefore(' ').toLong() * 1024
    } catch (_: Exception) {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
