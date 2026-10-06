package com.mangotv.app.ui.player

import androidx.media3.common.MediaItem
import com.mangotv.app.data.model.Stream

/**
 * The ExoPlayer item for a source with a link. A torrent never gets here without a link: the player screen first starts the torrent engine
 * (see TorrentSourceHost) and passes the source on with the engine's local address as its link. A source that still has no link (an addon
 * that sent neither a link nor a usable info hash) returns null. DefaultMediaSourceFactory sniffs HLS/DASH/progressive playback from the URL
 * itself, so no manual container selection is needed here.
 */
fun Stream.toMediaItemOrNull(): MediaItem? {
    val streamUrl = url ?: return null
    return MediaItem.Builder().setUri(streamUrl).build()
}
