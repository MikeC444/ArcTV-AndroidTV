package com.mangotv.app.ui.player

/**
 * How big subtitles are drawn (Settings > Subtitles > Size). [vlcRelFontSize] is VLC's `--freetype-rel-fontsize`, where a SMALLER number
 * means BIGGER text (its default is 16); [fraction] is the share of the video's height the built-in player draws a line of text at.
 */
enum class SubtitleSize(val wire: String, val label: String, val vlcRelFontSize: Int, val fraction: Float) {
    SMALL("small", "Small", 20, 0.0426f),
    MEDIUM("medium", "Medium", 16, 0.0533f),
    LARGE("large", "Large", 12, 0.07f),
    EXTRA_LARGE("extra_large", "Extra large", 9, 0.09f);

    companion object {
        val DEFAULT = MEDIUM
        fun fromWire(value: String?): SubtitleSize = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/** The colour of subtitle text (Settings > Subtitles > Colour). [rgb] is 0xRRGGBB; the built-in player adds full opacity. */
enum class SubtitleColor(val wire: String, val label: String, val rgb: Int) {
    WHITE("white", "White", 0xFFFFFF),
    YELLOW("yellow", "Yellow", 0xFFFF00),
    CYAN("cyan", "Cyan", 0x00FFFF),
    GREEN("green", "Green", 0x00FF00);

    /** For Compose and the built-in player's text style: opaque. */
    val argb: Int get() = (0xFF shl 24) or rgb

    companion object {
        val DEFAULT = WHITE
        fun fromWire(value: String?): SubtitleColor = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/** The options VLC is started with so its subtitles are drawn at this size and colour (`--freetype-color` takes the colour as a plain number). */
fun subtitleVlcOptions(size: SubtitleSize, color: SubtitleColor): List<String> =
    listOf("--freetype-rel-fontsize=${size.vlcRelFontSize}", "--freetype-color=${color.rgb}")
