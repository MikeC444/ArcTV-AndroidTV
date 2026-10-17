package com.mangotv.app.ui.home

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.inUserOrder
import com.mangotv.app.data.provider.orderServices
import com.mangotv.app.data.provider.streamingServiceById
import com.mangotv.app.data.provider.visibleServices

/** The id of Home's streaming-service tiles row (its tiles have ids `streaming:<service>`). */
const val STREAMING_SECTION_ID = "streaming_services"

/** The route-side id of a tile's service, or null when [contentId] is not one of the tiles' stand-ins. */
fun serviceOfTileId(contentId: String): String? = contentId.removePrefix("streaming:").takeIf { it != contentId }

/**
 * Home's "Streaming services" row, as a section so Home's focus handling treats it like any other row: one stand-in title per service, in the person's
 * own order (see Settings > Home Rows), leaving out the ones they turned off and the ones that are off until turned on. Null when no tile is left to
 * show. [services] are the services an installed addon has a catalogue for. Same rules as the web app (`StreamingTiles.tsx`).
 */
fun streamingTilesSection(services: List<String>, prefs: HomeRowPreferences): HomeSection? {
    val shown = visibleServices(inUserOrder(orderServices(services), prefs.order), prefs.hiddenRowIds)
    if (shown.isEmpty()) return null
    return HomeSection(
        id = STREAMING_SECTION_ID,
        title = "Streaming services",
        items = shown.mapNotNull { id ->
            val service = streamingServiceById(id) ?: return@mapNotNull null
            Content(id = "streaming:$id", type = ContentType.MOVIE, title = service.name, description = "", posterUrl = null, backdropUrl = null)
        },
        style = RowStyle.STREAMING_SERVICES
    )
}
