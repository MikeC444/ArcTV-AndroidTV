package com.mangotv.app.data.torrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TcpEndpoint
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.FileErrorAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.torrent_flags_t
import org.libtorrent4j.swig.torrent_info
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Streams a torrent to the app's players. Wraps libtorrent (via libtorrent4j, MIT-licensed bindings of the BSD-licensed libtorrent, which
 * also checks every piece against the torrent's hashes before it counts as received) and turns a magnet link or .torrent file into a local
 * `http://127.0.0.1:<port>/...` URL that serves the chosen video file with byte ranges, fetching pieces just ahead of whatever is being read.
 *
 * One [TorrentEngine] per app. The native session (sockets, DHT, threads) only exists while at least one stream is open and is torn down
 * a few seconds after the last one closes; every stream's temporary files live in their own folder under [rootDir] and are deleted when it
 * closes (and anything left behind by a crashed run is swept when the session next starts).
 */
class TorrentEngine(
    private val rootDir: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val log: (String) -> Unit = {}
) {
    private val lock = Any()
    private var manager: SessionManager? = null
    private var server: LoopbackHttpServer? = null
    private var active = 0
    private var stopJob: Job? = null
    private val byHash = ConcurrentHashMap<String, StreamSession>()
    private val byPath = ConcurrentHashMap<String, StreamSession>()

    private val alertListener = object : AlertListener {
        override fun types(): IntArray = intArrayOf(
            AlertType.ADD_TORRENT.swig(), AlertType.TORRENT_ERROR.swig(), AlertType.FILE_ERROR.swig()
        )

        override fun alert(alert: Alert<*>) {
            try {
                val torrentAlert = alert as? TorrentAlert<*> ?: return
                val session = byHash[torrentAlert.handle().infoHash().toHex()] ?: return
                when (alert) {
                    is AddTorrentAlert -> session.onAdded(alert.error().takeIf { it.isError }?.message)
                    is TorrentErrorAlert -> session.onEngineError(alert.error().message, alert.error().value)
                    is FileErrorAlert -> session.onEngineError(alert.error().message, alert.error().value)
                    else -> Unit
                }
            } catch (e: Exception) {
                log("alert handling failed: ${e.message}")
            }
        }
    }

    /** Starts streaming; returns at once. Watch [TorrentStream.state] for progress, the URL and errors, and call [TorrentStream.close]. */
    fun open(request: TorrentRequest, config: TorrentStreamConfig = TorrentStreamConfig()): TorrentStream {
        val session = StreamSession(request, config)
        session.start()
        return TorrentStream(session)
    }

    /** True while the native session (and with it every connection it holds) exists. */
    val isRunning: Boolean get() = synchronized(lock) { manager != null }

    /** Closes everything now (app shutdown); streams still open end as closed. */
    fun shutdown() {
        byHash.values.toList().forEach { it.closeBlocking() }
        synchronized(lock) { stopNow() }
    }

    private fun acquire(config: TorrentStreamConfig): Pair<SessionManager, LoopbackHttpServer> = synchronized(lock) {
        stopJob?.cancel()
        stopJob = null
        if (manager == null) {
            deleteTorrentFolder(rootDir)
            rootDir.mkdirs()
            try {
                val settings = SettingsPack()
                settings.connectionsLimit(config.connectionsLimit)
                settings.maxPeerlistSize(PEER_LIST_LIMIT)
                settings.maxQueuedDiskBytes(MAX_QUEUED_DISK_BYTES)
                // Only the window ahead of the picture is ever wanted, so between window moves the torrent counts as finished; by default
                // libtorrent then drops its connections to seeds as redundant and would not reconnect for up to a minute.
                settings.setBoolean(settings_pack.bool_types.close_redundant_connections.swigValue(), false)
                // A seed also closes its connection to a peer that wants nothing for the moment (the window is complete); coming back
                // for the next window should not wait for libtorrent's default one-minute reconnect delay.
                settings.setInteger(settings_pack.int_types.min_reconnect_time.swigValue(), MIN_RECONNECT_SECONDS)
                val params = SessionParams(settings)
                // Plain file I/O rather than memory-mapped files: a full disk then fails a write with an error instead of killing the
                // app with SIGBUS, and no address space is spent mapping a multi-gigabyte file (32-bit TV boxes have little).
                params.setPosixDiskIO()
                val created = SessionManager(false)
                created.start(params)
                created.addListener(alertListener)
                manager = created
            } catch (t: Throwable) {
                throw TorrentStreamException(
                    TorrentErrorKind.ENGINE_UNAVAILABLE,
                    "Torrent streaming isn't available on this device.",
                    t
                )
            }
        }
        if (server == null) server = LoopbackHttpServer { path -> byPath[path]?.content }
        active++
        manager!! to server!!
    }

    private fun release() {
        synchronized(lock) {
            active = (active - 1).coerceAtLeast(0)
            if (active == 0 && stopJob == null) {
                stopJob = scope.launch {
                    delay(STOP_GRACE_MS)
                    synchronized(lock) { if (active == 0) stopNow() }
                }
            }
        }
    }

    private fun stopNow() {
        stopJob = null
        server?.close()
        server = null
        manager?.let {
            try { it.removeListener(alertListener) } catch (_: Exception) {}
            try { it.stop() } catch (e: Exception) { log("stop failed: ${e.message}") }
        }
        manager = null
        deleteTorrentFolder(rootDir)
    }

    /** What the app holds for one playing torrent. */
    class TorrentStream internal constructor(private val session: StreamSession) {
        val state: StateFlow<TorrentStreamState> get() = session.state
        /** Ends the stream: connections are dropped and its temporary files deleted. Safe to call more than once, from any thread. */
        fun close() = session.close()
    }

    // ---------------------------------------------------------------------------------------------------------------------------------

    internal inner class StreamSession(private val request: TorrentRequest, private val config: TorrentStreamConfig) {
        private val id = randomHex(6)
        private val token = randomHex(16)
        private val _state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Starting)
        val state: StateFlow<TorrentStreamState> = _state.asStateFlow()

        @Volatile private var closed = false
        @Volatile private var failure: TorrentStreamException? = null
        private var job: Job? = null
        @Volatile private var acquired = false
        private var hashHex: String? = null
        private val dir = File(rootDir, id)
        // Completed by the add alert. The alert's own handle points into memory the library frees once the alert has been dispatched, so
        // the stream never keeps it: it looks the torrent up again (find returns an owned copy) once this says it was added.
        @Volatile private var pendingAdd = CompletableDeferred<Unit>()

        @Volatile private var handle: TorrentHandle? = null
        @Volatile private var info: TorrentInfo? = null
        @Volatile private var layout: PieceLayout? = null
        @Volatile private var window: ReadAheadWindow? = null
        @Volatile private var file: TorrentFileEntry? = null
        @Volatile private var filePath: File? = null
        @Volatile private var generation = 0
        @Volatile private var trimming = false
        @Volatile private var paused = false
        private val waiters = java.util.concurrent.atomic.AtomicInteger()
        @Volatile private var lastReadAt = System.currentTimeMillis()
        @Volatile private var lastProgressAt = System.currentTimeMillis()
        @Volatile private var lastPayload = 0L
        @Volatile private var primaryReader = 0L
        @Volatile private var primaryPosition = 0L
        private var capBytes = 0L
        private var applied: Array<Priority>? = null
        private val readerIds = java.util.concurrent.atomic.AtomicLong()
        private val monitor = Object()
        private val activeReads = java.util.concurrent.atomic.AtomicInteger()

        val content: RangedContent? get() = if (state.value is TorrentStreamState.Playing) contentImpl else null
        private var contentImpl: RangedContent? = null

        fun start() {
            job = scope.launch {
                try {
                    run()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: TorrentStreamException) {
                    fail(e)
                } catch (e: Throwable) {
                    log("stream failed: ${e.stackTraceToString().lineSequence().take(12).joinToString(" | ")}")
                    fail(TorrentStreamException(TorrentErrorKind.FAILED, "Couldn't start this torrent.", e))
                }
            }
        }

        fun close() {
            if (closed) return
            closed = true
            synchronized(monitor) { monitor.notifyAll() }
            scope.launch(NonCancellable) { teardown() }
        }

        suspend fun closeAndWait() {
            closed = true
            synchronized(monitor) { monitor.notifyAll() }
            teardown()
        }

        /** Used at shutdown: same as [close] but finishes before returning. */
        fun closeBlocking() {
            closed = true
            synchronized(monitor) { monitor.notifyAll() }
            kotlinx.coroutines.runBlocking { teardown() }
        }

        private suspend fun teardown() {
            job?.cancelAndJoin()
            val mgr = synchronized(lock) { manager }
            val h = handle
            val hash = hashHex
            if (mgr != null && h != null) {
                try {
                    if (h.isValid) mgr.remove(h, SessionHandle.DELETE_FILES)
                } catch (e: Exception) {
                    log("remove failed: ${e.message}")
                }
                if (hash != null) waitUntilGone(mgr, hash, 5_000)
            }
            if (hash != null) byHash.remove(hash, this)
            byPath.values.remove(this)
            withContext(Dispatchers.IO) { deleteTorrentFolder(dir) }
            handle = null
            _state.value = TorrentStreamState.Closed
            val wasAcquired = synchronized(lock) { acquired.also { acquired = false } }
            if (wasAcquired) release()
        }

        private suspend fun waitUntilGone(mgr: SessionManager, hash: String, timeoutMs: Long) {
            val sha = Sha1Hash.parseHex(hash)
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end) {
                try { if (mgr.find(sha) == null) return } catch (_: Exception) { return }
                delay(50)
            }
        }

        private fun fail(error: TorrentStreamException) {
            if (failure == null) failure = error
            if (!closed) _state.value = TorrentStreamState.Failed(failure!!)
            synchronized(monitor) { monitor.notifyAll() }
            // The torrent is of no more use: let go of the connections now, the screen shows the message.
            scope.launch(NonCancellable) {
                val mgr = synchronized(lock) { manager }
                val h = handle
                if (mgr != null && h != null) try { if (h.isValid) mgr.remove(h, SessionHandle.DELETE_FILES) } catch (_: Exception) {}
            }
        }

        // Called from the library's alert thread.
        fun onAdded(error: String?) {
            val pending = pendingAdd
            if (error != null) pending.completeExceptionally(TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "This torrent couldn't be opened."))
            else pending.complete(Unit)
        }

        fun onEngineError(message: String?, code: Int) {
            if (closed || failure != null || _state.value is TorrentStreamState.Closed) return
            log("engine error $code: $message")
            if (code == ENOSPC) {
                fail(TorrentStreamException(TorrentErrorKind.NO_SPACE, "The device ran out of free storage while buffering."))
            } else if (code != 0 && _state.value !is TorrentStreamState.Playing) {
                fail(TorrentStreamException(TorrentErrorKind.STORAGE, "Couldn't save the video while buffering."))
            }
        }

        // ---- the stream's life ----

        private suspend fun run() {
            val (mgr, srv) = acquire(config)
            acquired = true
            if (closed) return
            dir.mkdirs()
            val magnet = request.magnet
            val torrentBytes = request.torrentFile
            val hash: String
            var torrent: TorrentInfo? = null
            if (torrentBytes != null) {
                torrent = try { TorrentInfo(torrentBytes) } catch (e: Exception) {
                    throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "This isn't a valid .torrent file.", e)
                }
                hash = torrent.infoHash().toHex()
            } else {
                hash = magnet!!.infoHash
            }
            hashHex = hash

            // An earlier stream of the same torrent (retry, source change) is closed and gone before this one adds it.
            byHash[hash]?.takeIf { it !== this }?.closeAndWait()
            byHash[hash] = this
            val sha = Sha1Hash.parseHex(hash)
            val startedWaiting = System.currentTimeMillis()
            while (mgr.find(sha) != null && System.currentTimeMillis() - startedWaiting < 5_000) delay(50)

            // A magnet link is added wanting everything and narrowed to the chosen file the moment its metadata arrives (a few hundred
            // milliseconds): a torrent that wants nothing is "not interested", and seeds close their connection to such a peer.
            val flags = torrent_flags_t()
            val peers = request.peers + (magnet?.peers ?: emptyList())
            if (torrent != null) {
                val first = chooseFile(torrent) // fails early, before anything is added, when there is nothing to play
                val priorities = Array(torrent.numFiles()) { if (it == first.index) Priority.DEFAULT else Priority.IGNORE }
                val endpoints = request.peers.mapNotNull { toEndpoint(it) }
                mgr.download(torrent, dir, null, priorities, endpoints, flags)
            } else {
                val uri = magnet!!.let { buildMagnetUri(it.infoHash, it.displayName, it.trackers, peers.distinct()) }
                try { mgr.download(uri, dir, flags) } catch (e: IllegalArgumentException) {
                    throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "That magnet link isn't valid.", e)
                }
            }
            val h = awaitAdded(mgr, sha)
            handle = h
            if (closed) return

            // Metadata: instant for a .torrent, fetched from peers for a magnet link.
            val startedAt = System.currentTimeMillis()
            var found = torrent
            var lastReported = -POLL_MS
            while (found == null) {
                if (closed) return
                failure?.let { throw it }
                val st = h.status()
                if (st.hasMetadata()) found = h.torrentFile()
                if (found != null) break
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed > config.metadataTimeoutMs) {
                    throw TorrentStreamException(
                        TorrentErrorKind.METADATA_TIMEOUT,
                        if (st.numPeers() == 0) "Couldn't find anyone sharing this torrent. It may be dead or the link may be wrong."
                        else "Found peers but couldn't get the torrent's details in time. Try again or pick another source."
                    )
                }
                // Polled quickly: until the metadata is in, the whole torrent is wanted, and the sooner it is narrowed to the chosen
                // file the less data of the other files is fetched and thrown away.
                if (elapsed - lastReported >= POLL_MS) {
                    lastReported = elapsed
                    _state.value = TorrentStreamState.FetchingMetadata(st.numPeers(), elapsed)
                }
                delay(METADATA_POLL_MS)
            }
            // The info a handle returns is a view onto memory the torrent owns and frees when it is removed; this stream re-adds the
            // torrent when it trims storage, so it keeps its own copy.
            val ti: TorrentInfo = if (torrent != null) torrent else TorrentInfo(
                torrent_info(found?.swig() ?: throw TorrentStreamException(TorrentErrorKind.FAILED, "Couldn't read this torrent."))
            )
            info = ti

            val selected = chooseFile(ti)
            file = selected
            val files = ti.files()
            val lay = PieceLayout(ti.pieceLength().toLong(), ti.totalSize(), files.fileOffset(selected.index), selected.size)
            layout = lay
            filePath = File(dir, files.filePath(selected.index))

            // Storage: decided before any data is requested.
            when (val plan = planStorage(selected.size, config, dir.usableSpace)) {
                is StoragePlan.NotEnough -> throw TorrentStreamException(
                    TorrentErrorKind.NO_SPACE,
                    "Not enough free storage to buffer this video (about ${plan.neededBytes / MB} MB needed, ${plan.availableBytes / MB} MB free)."
                )
                is StoragePlan.Ok -> capBytes = plan.capBytes
            }

            val win = ReadAheadWindow(lay, config.readAheadBytes)
            window = win
            h.prioritizeFiles(Array(ti.numFiles()) { if (it == selected.index) Priority.DEFAULT else Priority.IGNORE })
            applyWindow(win.start)
            h.unsetFlags(TorrentFlags.AUTO_MANAGED)
            h.resume()

            // Start buffer.
            val head = lay.headPieces(config.effectiveStartBytes)
            val target = head.sumOf { lay.fileBytesIn(it) }
            val bufferStart = System.currentTimeMillis()
            lastProgressAt = bufferStart
            while (true) {
                if (closed) return
                failure?.let { throw it }
                val have = head.sumOf { if (h.havePiece(it)) lay.fileBytesIn(it) else 0L }
                if (have >= target) break
                val elapsed = System.currentTimeMillis() - bufferStart
                updateProgress(h)
                if (elapsed > config.startTimeoutMs && System.currentTimeMillis() - lastProgressAt > config.startTimeoutMs / 2) {
                    val peersNow = h.status().numPeers()
                    throw TorrentStreamException(
                        TorrentErrorKind.NO_PEERS,
                        if (peersNow == 0) "No one is sharing this torrent right now. Try another source."
                        else "This torrent is too slow to start ($peersNow peers, but almost no data). Try another source."
                    )
                }
                _state.value = TorrentStreamState.Buffering(have, target, selected.name, stats(h, lay))
                delay(POLL_MS)
            }

            val name = selected.name
            contentImpl = object : RangedContent {
                override val length: Long = selected.size
                override val mimeType: String = mimeTypeFor(name)
                override fun open(start: Long, endInclusive: Long): InputStream = PieceInputStream(start, endInclusive)
            }
            val path = "/$token/${URLEncoder.encode(name, "UTF-8").replace("+", "%20")}"
            byPath[path] = this
            lastReadAt = System.currentTimeMillis()
            val url = "http://127.0.0.1:${srv.port}$path"
            _state.value = TorrentStreamState.Playing(url, name, selected.size, stats(h, lay))
            log("ready: $name, ${selected.size} bytes, pieces ${lay.firstPiece}..${lay.lastPiece}")

            monitorLoop(mgr, lay)
        }

        private suspend fun awaitAdded(mgr: SessionManager, sha: Sha1Hash): TorrentHandle {
            withTimeoutOrNull(ADD_TIMEOUT_MS) { pendingAdd.await() }
                ?: throw TorrentStreamException(TorrentErrorKind.FAILED, "Couldn't start this torrent.")
            return mgr.find(sha) ?: throw TorrentStreamException(TorrentErrorKind.FAILED, "Couldn't start this torrent.")
        }

        private fun chooseFile(ti: TorrentInfo): TorrentFileEntry {
            val files = ti.files()
            val entries = (0 until ti.numFiles()).map { TorrentFileEntry(it, files.filePath(it).replace('\\', '/'), files.fileSize(it)) }
            return when (val sel = selectVideoFile(entries, request.hint)) {
                is FileSelection.Found -> sel.file
                FileSelection.NoVideo -> throw TorrentStreamException(
                    TorrentErrorKind.NO_VIDEO,
                    "This torrent has no video file Arc TV can stream (it may be an archive or disc image)."
                )
                FileSelection.EpisodeNotFound -> throw TorrentStreamException(
                    TorrentErrorKind.EPISODE_NOT_FOUND,
                    "This torrent doesn't seem to contain the episode you chose."
                )
            }
        }

        private fun currentPeers(h: TorrentHandle): List<String> =
            try { h.peerInfo().map { it.ip() } } catch (_: Exception) { emptyList() }

        private fun currentTrackers(h: TorrentHandle): List<String> =
            try { h.trackers().map { it.url() } } catch (_: Exception) { emptyList() }

        private fun toEndpoint(hostPort: String): TcpEndpoint? {
            if (hostPort.startsWith("[")) return null // IPv6 literals are left to the tracker / DHT
            val host = hostPort.substringBeforeLast(':')
            val port = hostPort.substringAfterLast(':', "").toIntOrNull() ?: return null
            return try { TcpEndpoint(host, port) } catch (_: Exception) { null }
        }

        /** Runs for as long as the stream is open: refreshes the numbers, pauses an idle stream and keeps storage under its cap. */
        private suspend fun monitorLoop(mgr: SessionManager, lay: PieceLayout) {
            while (scope.isActive && !closed) {
                delay(MONITOR_MS)
                if (closed || failure != null) return
                // The handle changes when the storage is trimmed, so it is read afresh on every turn.
                val h = handle ?: return
                try {
                    updateProgress(h)
                    val now = System.currentTimeMillis()
                    if (!paused && now - lastReadAt > config.idlePauseMs && waiters.get() == 0) {
                        h.pause()
                        paused = true
                        log("idle: paused")
                    }
                    if (h.status().totalDone() > capBytes && !trimming) trim(mgr)
                    val cur = _state.value
                    val now2 = handle
                    if (cur is TorrentStreamState.Playing && now2 != null) _state.value = cur.copy(stats = stats(now2, lay))
                } catch (e: TorrentStreamException) {
                    throw e
                } catch (e: Exception) {
                    // A handle going away under us (the torrent was removed, or the storage trimmed) is not a failure of the stream.
                    if (!closed) log("monitor: ${e.stackTraceToString().lineSequence().take(8).joinToString(" | ")}")
                }
            }
        }

        private fun updateProgress(h: TorrentHandle) {
            val payload = h.status().totalPayloadDownload()
            if (payload > lastPayload) {
                lastPayload = payload
                lastProgressAt = System.currentTimeMillis()
            }
        }

        private fun stats(h: TorrentHandle, lay: PieceLayout): TorrentStats {
            val st = h.status()
            var buffered = 0L
            val pos = lay.pieceOf(primaryPosition)
            var p = pos
            while (p <= lay.lastPiece && h.havePiece(p) && buffered < config.readAheadBytes) {
                buffered += lay.fileBytesIn(p)
                p++
            }
            return TorrentStats(
                peers = st.numPeers(),
                seeds = st.numSeeds(),
                downloadBytesPerSecond = st.downloadPayloadRate().toLong(),
                bufferedAheadBytes = buffered,
                stalled = waiters.get() > 0
            )
        }

        // ---- piece priorities ----

        /**
         * Aims the download at the window starting at [piece]: its pieces get top priority (the first few also deadlines, so they arrive
         * in order and soonest), the last pieces of the file stay wanted (video indexes often sit there), and nothing else is requested.
         */
        @Synchronized
        private fun applyWindow(piece: Int) {
            val h = handle ?: return
            val lay = layout ?: return
            val win = window ?: return
            val ti = info ?: return
            win.moveTo(piece)
            val wanted = Array(ti.numPieces()) { Priority.IGNORE }
            for (p in win.pieces) wanted[p] = Priority.TOP_PRIORITY
            val tail = maxOf(1, (TAIL_BYTES / lay.pieceLength).toInt())
            for (p in maxOf(lay.firstPiece, lay.lastPiece - tail + 1)..lay.lastPiece) if (wanted[p] == Priority.IGNORE) wanted[p] = Priority.DEFAULT
            val previous = applied
            try {
                if (previous == null) h.prioritizePieces(wanted)
                else for (p in wanted.indices) if (wanted[p] != previous[p]) h.piecePriority(p, wanted[p])
                h.clearPieceDeadlines()
                for ((p, deadline) in win.deadlines()) h.setPieceDeadline(p, deadline)
                applied = wanted
            } catch (e: Exception) {
                log("priorities failed: ${e.message}")
            }
        }

        /** A reader (a player's request) is at [position]; the newest reader steers the window, others only wait for their own pieces. */
        private fun noteRead(readerId: Long, position: Long) {
            lastReadAt = System.currentTimeMillis()
            if (paused) { paused = false; handle?.resume() }
            if (readerId != primaryReader) return
            primaryPosition = position
            val lay = layout ?: return
            val win = window ?: return
            val piece = lay.pieceOf(position)
            if (win.shouldMoveTo(piece)) applyWindow(piece)
        }

        /** Makes sure a piece outside the window that a reader needs is being fetched, urgently. */
        @Synchronized
        private fun demand(piece: Int) {
            val h = handle ?: return
            try {
                if (applied?.getOrNull(piece) != Priority.TOP_PRIORITY) {
                    h.piecePriority(piece, Priority.TOP_PRIORITY)
                    applied?.set(piece, Priority.TOP_PRIORITY)
                }
                h.setPieceDeadline(piece, 50)
            } catch (_: Exception) {}
        }

        // ---- storage cap ----

        /**
         * Over the storage cap: the data behind the picture is dropped. libtorrent has no way to free single pieces on disk, so the torrent
         * is removed (its files deleted) and re-added from the details already held, aimed at the place being watched. Readers wait
         * meanwhile; the cost is one re-buffer per cap's worth of playing, and a seek back over dropped data downloads it again.
         */
        private suspend fun trim(mgr: SessionManager) {
            val ti = info ?: return
            val hash = hashHex ?: return
            val sel = file ?: return
            val oldHandle = handle ?: return
            trimming = true
            try {
                log("trim: storage over cap")
                // Reads in progress finish first (each is one short read from disk); new ones wait until the new torrent is in place.
                while (activeReads.get() != 0) delay(5)
                val position = primaryPosition
                // The re-added torrent starts with no peers and, having been added from its details alone, no trackers: carry both over so
                // it reconnects at once instead of waiting to be found again.
                val knownPeers = (request.peers + (request.magnet?.peers ?: emptyList()) + currentPeers(oldHandle)).distinct().take(MAX_CARRIED_PEERS)
                val trackerUrls = (currentTrackers(oldHandle) + (request.magnet?.trackers ?: emptyList())).distinct()
                mgr.remove(oldHandle, SessionHandle.DELETE_FILES)
                waitUntilGone(mgr, hash, 10_000)
                deleteTorrentFolder(dir)
                dir.mkdirs()
                val fresh = CompletableDeferred<Unit>()
                pendingAdd = fresh
                val priorities = Array(ti.numFiles()) { if (it == sel.index) Priority.DEFAULT else Priority.IGNORE }
                mgr.download(ti, dir, null, priorities, knownPeers.mapNotNull { toEndpoint(it) }, torrent_flags_t())
                val h = awaitAdded(mgr, Sha1Hash.parseHex(hash))
                if (trackerUrls.isNotEmpty()) try { h.replaceTrackers(trackerUrls.map { AnnounceEntry(it) }) } catch (e: Exception) { log("trackers: ${e.message}") }
                synchronized(this) {
                    handle = h
                    applied = null
                    generation++
                }
                h.prioritizeFiles(Array(ti.numFiles()) { if (it == sel.index) Priority.DEFAULT else Priority.IGNORE })
                applyWindow(layout!!.pieceOf(position))
                h.unsetFlags(TorrentFlags.AUTO_MANAGED)
                h.resume()
                lastProgressAt = System.currentTimeMillis()
                lastPayload = 0
            } catch (e: TorrentStreamException) {
                fail(e)
            } finally {
                trimming = false
                synchronized(monitor) { monitor.notifyAll() }
            }
        }

        // ---- reading ----

        /** Blocks until [piece] has arrived (and passed its hash check, which is what `havePiece` means). */
        private fun awaitPiece(piece: Int) {
            var counted = false
            val waitStart = System.currentTimeMillis()
            try {
                while (true) {
                    if (closed) throw IOException("Playback was stopped.")
                    failure?.let { throw it }
                    val h = handle
                    if (!trimming && h != null) {
                        val have = try { h.isValid && h.havePiece(piece) } catch (_: Exception) { false }
                        if (have) return
                        if (paused) { paused = false; h.resume() }
                        demand(piece)
                    }
                    if (!counted) { counted = true; waiters.incrementAndGet() }
                    if (System.currentTimeMillis() - maxOf(lastProgressAt, waitStart) > config.stallTimeoutMs && !trimming) {
                        val e = TorrentStreamException(
                            TorrentErrorKind.STALLED,
                            "The torrent stopped sending data (${h?.status()?.numPeers() ?: 0} peers). Try again or pick another source."
                        )
                        fail(e)
                        throw e
                    }
                    synchronized(monitor) { monitor.wait(WAIT_MS) }
                }
            } finally {
                if (counted) waiters.decrementAndGet()
            }
        }

        private inner class PieceInputStream(start: Long, private val endInclusive: Long) : InputStream() {
            private var position = start
            private val readerId = readerIds.incrementAndGet().also { primaryReader = it }
            private var raf: RandomAccessFile? = null
            private var rafGeneration = -1
            private var closedStream = false

            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
            }

            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (closedStream) throw IOException("Stream closed.")
                if (position > endInclusive) return -1
                val lay = layout ?: throw IOException("Not ready.")
                val want = minOf(len.toLong(), endInclusive - position + 1)
                noteRead(readerId, position)
                while (true) {
                    val first = lay.pieceOf(position)
                    awaitPiece(first)
                    // From here to the end of the disk read the storage must not be trimmed under us (see trim), so this counts as a
                    // read in progress; if a trim started in between, go back and wait for it.
                    activeReads.incrementAndGet()
                    try {
                        if (trimming) continue
                        val h = handle ?: continue
                        if (!h.havePiece(first)) continue
                        // The first piece is there; take every following piece that already is too, up to what was asked for.
                        var piece = first
                        val last = lay.pieceOf(position + want - 1)
                        while (piece < last && h.havePiece(piece + 1)) piece++
                        val limit = (piece + 1).toLong() * lay.pieceLength - lay.fileOffset // file byte just past the last piece taken
                        val count = minOf(want, limit - position).toInt().coerceAtLeast(1)
                        val file = openFile()
                        file.seek(position)
                        var done = 0
                        while (done < count) {
                            val n = file.read(buffer, off + done, count - done)
                            if (n < 0) throw IOException("The downloaded data could not be read back.")
                            done += n
                        }
                        position += done
                        return done
                    } finally {
                        activeReads.decrementAndGet()
                    }
                }
            }

            private fun openFile(): RandomAccessFile {
                val current = raf
                if (current != null && rafGeneration == generation) return current
                current?.close()
                val path = filePath ?: throw IOException("Not ready.")
                val opened = try { RandomAccessFile(path, "r") } catch (e: IOException) {
                    throw IOException("The downloaded data could not be opened.", e)
                }
                raf = opened
                rafGeneration = generation
                return opened
            }

            override fun close() {
                closedStream = true
                try { raf?.close() } catch (_: IOException) {}
                raf = null
            }
        }
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val ENOSPC = 28
        const val POLL_MS = 250L
        const val METADATA_POLL_MS = 20L
        const val MONITOR_MS = 500L
        const val WAIT_MS = 100L
        const val ADD_TIMEOUT_MS = 15_000L
        const val STOP_GRACE_MS = 3_000L
        const val TAIL_BYTES = 2 * 1024 * 1024L
        const val PEER_LIST_LIMIT = 1_000
        const val MIN_RECONNECT_SECONDS = 2
        const val MAX_CARRIED_PEERS = 50
        const val MAX_QUEUED_DISK_BYTES = 4 * 1024 * 1024

        private val random = SecureRandom()
        fun randomHex(bytes: Int): String = ByteArray(bytes).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
