package com.mangotv.app.data.torrent.platform

import android.content.Context
import android.net.Uri
import com.mangotv.app.data.addon.StremioStream
import com.mangotv.app.data.addon.toStream
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.torrent.TorrentStreamException
import com.mangotv.app.data.torrent.UserTorrentInput
import com.mangotv.app.data.torrent.parseUserTorrentInput
import com.mangotv.app.data.torrent.readTorrentFileBytes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** The provider id of sources the person added themselves; only these may point at a file on the device. */
const val CUSTOM_PROVIDER_ID = "custom-torrent"

@Serializable
data class CustomTorrentEntry(val titleKey: String, val url: String, val name: String? = null)

/**
 * The magnet links and .torrent files a person added by hand to a title (or one episode of it), shown on Select a Source beside the
 * addons' own sources. Kept on this device only. A .torrent file chosen from the device is copied into the app's own storage first, so it
 * still plays after the original moves, and no other file on the device is ever opened.
 */
class CustomTorrentRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val filesDir: File get() = File(context.filesDir, "user-torrents")

    private fun key(providerId: String, contentId: String, type: ContentType, season: Int?, episode: Int?) =
        "$providerId|$contentId|${type.name}|${season ?: -1}|${episode ?: -1}"

    @Synchronized
    private fun all(): List<CustomTorrentEntry> =
        runCatching { json.decodeFromString(ListSerializer(CustomTorrentEntry.serializer()), prefs.getString(ENTRIES, null) ?: "[]") }
            .getOrDefault(emptyList())

    @Synchronized
    private fun save(entries: List<CustomTorrentEntry>) {
        prefs.edit().putString(ENTRIES, json.encodeToString(ListSerializer(CustomTorrentEntry.serializer()), entries)).apply()
    }

    fun streamsFor(providerId: String, contentId: String, type: ContentType, season: Int?, episode: Int?): List<Stream> {
        val titleKey = key(providerId, contentId, type, season, episode)
        return all().filter { it.titleKey == titleKey }.map { it.toStream() }
    }

    /** Adds what the person typed or pasted. Returns the new source, or null with [UserTorrentInput.Rejected]'s message in [error]. */
    fun addText(
        providerId: String, contentId: String, type: ContentType, season: Int?, episode: Int?,
        text: String, error: (String) -> Unit
    ): Stream? = when (val input = parseUserTorrentInput(text)) {
        is UserTorrentInput.Rejected -> { error(input.message); null }
        is UserTorrentInput.Magnet -> add(CustomTorrentEntry(key(providerId, contentId, type, season, episode), input.link.toUri(), input.link.displayName))
        is UserTorrentInput.TorrentUrl -> add(CustomTorrentEntry(key(providerId, contentId, type, season, episode), input.url, input.url.substringAfterLast('/').substringBefore('?')))
    }

    /** Adds a .torrent file the person picked ([uri], read once and copied into the app's storage). */
    fun addFile(
        providerId: String, contentId: String, type: ContentType, season: Int?, episode: Int?,
        uri: Uri, error: (String) -> Unit
    ): Stream? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readTorrentFileBytes(it) }
                ?: run { error("Couldn't open that file."); return null }
        } catch (e: TorrentStreamException) {
            error(e.message); return null
        } catch (e: Exception) {
            error("Couldn't open that file."); return null
        }
        val name = displayName(uri)
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        filesDir.mkdirs()
        val copy = File(filesDir, "$digest.torrent")
        if (!copy.exists()) copy.writeBytes(bytes)
        return add(CustomTorrentEntry(key(providerId, contentId, type, season, episode), Uri.fromFile(copy).toString(), name))
    }

    @Synchronized
    private fun add(entry: CustomTorrentEntry): Stream {
        val entries = all()
        val sameTitle = entries.filter { it.titleKey == entry.titleKey }
        val updated = if (sameTitle.any { it.url == entry.url }) entries
        else (entries - sameTitle.drop(MAX_PER_TITLE - 1).toSet()) + entry
        save(updated.takeLast(MAX_TOTAL))
        return entry.toStream()
    }

    /** Whether [url] is a .torrent file this app saved itself (the only local files a custom source may read). */
    fun isOwnFile(url: String): Boolean = try {
        val path = Uri.parse(url).path ?: return false
        val file = File(path).canonicalFile
        file.parentFile == filesDir.canonicalFile && file.isFile
    } catch (_: Exception) {
        false
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    } ?: uri.lastPathSegment

    private fun CustomTorrentEntry.toStream(): Stream {
        val label = name?.takeIf { it.isNotBlank() } ?: if (url.startsWith("magnet:", ignoreCase = true)) "Magnet link" else "Torrent file"
        return StremioStream(url = url, title = label, name = "My torrent")
            .toStream(providerId = CUSTOM_PROVIDER_ID, providerLabel = "My torrent")
    }

    private companion object {
        const val FILE = "arctv_custom_torrents"
        const val ENTRIES = "entries"
        const val MAX_PER_TITLE = 10
        const val MAX_TOTAL = 200
    }
}
