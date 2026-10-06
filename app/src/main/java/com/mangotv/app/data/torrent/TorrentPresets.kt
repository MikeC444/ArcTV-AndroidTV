package com.mangotv.app.data.torrent

private const val MB = 1024L * 1024L

/** How much of a torrent is fetched ahead of the picture (Settings > Player > Torrent buffer). */
enum class TorrentBuffer(val wire: String, val label: String, val readAheadBytes: Long, val startBufferBytes: Long) {
    SMALL("small", "Small (32 MB) — least data, may pause on slow torrents", 32 * MB, 4 * MB),
    MEDIUM("medium", "Medium (64 MB) — recommended", 64 * MB, 8 * MB),
    LARGE("large", "Large (128 MB) — smoother on uneven torrents", 128 * MB, 16 * MB),
    HUGE("huge", "Very large (256 MB) — for 4K, needs free storage", 256 * MB, 32 * MB);

    companion object {
        val DEFAULT = MEDIUM
        fun fromWire(value: String?): TorrentBuffer = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/** The most temporary storage one torrent may use at a time (Settings > Player > Torrent storage limit). */
enum class TorrentStorageLimit(val wire: String, val label: String, val bytes: Long) {
    MB_512("512mb", "512 MB", 512 * MB),
    GB_1("1gb", "1 GB", 1024 * MB),
    GB_2("2gb", "2 GB — recommended", 2048 * MB),
    GB_4("4gb", "4 GB", 4096 * MB),
    GB_8("8gb", "8 GB", 8192 * MB);

    companion object {
        val DEFAULT = GB_2
        fun fromWire(value: String?): TorrentStorageLimit = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

fun torrentConfigFor(buffer: TorrentBuffer, storage: TorrentStorageLimit): TorrentStreamConfig =
    TorrentStreamConfig(
        readAheadBytes = buffer.readAheadBytes,
        startBufferBytes = buffer.startBufferBytes,
        maxStorageBytes = storage.bytes
    )
