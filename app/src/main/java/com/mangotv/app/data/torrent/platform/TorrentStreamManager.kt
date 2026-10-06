package com.mangotv.app.data.torrent.platform

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.torrent.FileHint
import com.mangotv.app.data.torrent.TorrentEngine
import com.mangotv.app.data.torrent.TorrentErrorKind
import com.mangotv.app.data.torrent.TorrentRef
import com.mangotv.app.data.torrent.TorrentRequest
import com.mangotv.app.data.torrent.TorrentStreamException
import com.mangotv.app.data.torrent.TorrentStreamState
import com.mangotv.app.data.torrent.deleteTorrentFolder
import com.mangotv.app.data.torrent.readTorrentFileBytes
import com.mangotv.app.data.torrent.torrentConfigFor
import com.mangotv.app.data.torrent.torrentFetchError
import com.mangotv.app.data.torrent.torrentRefOf
import com.mangotv.app.ui.player.DevicePlayerPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One torrent being streamed for one playback: watch [state], then close it when the playback ends. */
class TorrentPlayback internal constructor(
    val state: StateFlow<TorrentStreamState>,
    private val closeAction: () -> Unit
) {
    /** Stops the download, drops the connections and deletes the temporary data. Safe to call more than once. */
    fun close() = closeAction()
}

/**
 * The app's side of torrent streaming: decides whether a source is a torrent, fetches a .torrent file when the source is one, picks where
 * temporary data lives (the cache folder with the most free space, which can be a USB drive or SD card on a TV box), applies the buffer and
 * storage settings, and hands back a [TorrentPlayback] whose state the player screen shows. The engine itself is
 * [com.mangotv.app.data.torrent.TorrentEngine]; nothing here runs until a torrent source is actually played.
 */
class TorrentStreamManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val customTorrents: CustomTorrentRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine: TorrentEngine by lazy { TorrentEngine(chooseStorageRoot(), scope) { Log.d(TAG, it) } }

    /** The torrent a source stands for, or null when it is an ordinary link (direct, HLS, DASH, debrid) that plays as it always did. */
    fun refFor(stream: Stream): TorrentRef? = torrentRefOf(
        url = stream.url,
        infoHash = stream.infoHash,
        trackers = stream.trackers,
        displayName = stream.releaseTitle,
        allowLocalFiles = stream.providerId == CUSTOM_PROVIDER_ID
    )

    /** Starts streaming [stream] (a source [refFor] recognised); [season] / [episode] pick the right file in a season pack. */
    fun open(stream: Stream, season: Int?, episode: Int?): TorrentPlayback {
        val state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Starting)
        val lock = Any()
        var inner: TorrentEngine.TorrentStream? = null
        var closed = false
        val ref = refFor(stream)
        val hint = FileHint(fileIdx = stream.fileIdx, filename = stream.torrentFilename, season = season, episode = episode)
        val config = torrentConfigFor(DevicePlayerPrefs.torrentBuffer(context), DevicePlayerPrefs.torrentStorageLimit(context))

        val job: Job = scope.launch {
            try {
                val request = when (ref) {
                    is TorrentRef.Magnet -> TorrentRequest(ref.link, null, hint)
                    is TorrentRef.TorrentFile -> TorrentRequest(null, loadTorrentFile(ref.url), hint)
                    is TorrentRef.Invalid -> throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, ref.message)
                    null -> throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "This source isn't a torrent.")
                }
                val stream2 = synchronized(lock) {
                    if (closed) return@launch
                    engine.open(request, config).also { inner = it }
                }
                stream2.state.collect { state.value = it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: TorrentStreamException) {
                state.value = TorrentStreamState.Failed(e)
            } catch (e: Exception) {
                Log.w(TAG, "open failed", e)
                state.value = TorrentStreamState.Failed(TorrentStreamException(TorrentErrorKind.FAILED, "Couldn't start this torrent.", e))
            }
        }
        return TorrentPlayback(state.asStateFlow()) {
            val toClose = synchronized(lock) {
                if (closed) return@synchronized null
                closed = true
                inner
            }
            job.cancel()
            toClose?.close()
            if (state.value !is TorrentStreamState.Closed) state.value = TorrentStreamState.Closed
        }
    }

    /** Deletes temporary torrent data left behind by an earlier run that was killed (called once when the app starts). */
    fun purgeStaleStorage() {
        for (dir in cacheCandidates()) deleteTorrentFolder(File(dir, ROOT_NAME))
    }

    private suspend fun loadTorrentFile(url: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(url)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    val client = http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
                    val response = client.newCall(Request.Builder().url(url).header("User-Agent", "ArcTV").build()).await()
                    response.use {
                        if (!it.isSuccessful) throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "The .torrent file couldn't be downloaded (HTTP ${it.code}).")
                        readTorrentFileBytes(it.body?.byteStream() ?: throw IOException("empty body"))
                    }
                }
                "file" -> {
                    if (!customTorrents.isOwnFile(url)) throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "That .torrent file is no longer available.")
                    File(uri.path!!).inputStream().use { readTorrentFileBytes(it) }
                }
                else -> throw TorrentStreamException(TorrentErrorKind.INVALID_SOURCE, "That .torrent address isn't supported.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw torrentFetchError(e)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!cont.isCompleted) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
    }

    private fun cacheCandidates(): List<File> =
        (context.externalCacheDirs?.filterNotNull().orEmpty() + context.cacheDir).filter { it.exists() || it.mkdirs() }

    /** The cache folder with the most free space: temporary data can be big, and a TV box's own storage is often small. */
    private fun chooseStorageRoot(): File {
        val best = cacheCandidates().filter { it.canWrite() }.maxByOrNull { it.usableSpace } ?: context.cacheDir
        return File(best, ROOT_NAME)
    }

    private companion object {
        const val TAG = "ArcTorrent"
        const val ROOT_NAME = "torrent-stream"
    }
}
