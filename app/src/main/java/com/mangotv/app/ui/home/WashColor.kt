package com.mangotv.app.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.concurrent.ConcurrentHashMap

private const val SAMPLE_W = 32
private const val SAMPLE_H = 18
private const val MIN_SHARE = 0.04f

/**
 * The colour of the soft light behind the Home hero: the title's own artwork, boiled down to one colour (the web app's `lib/washColor.ts`).
 * Dark corners and white text carry no colour, so pixels are weighted by how saturated and how bright they are; a picture with no real colour (black
 * and white, mostly dark) gives null and Home stays neutral. [pixels] are ARGB ints of a small sample.
 */
fun washColor(pixels: IntArray): Color? {
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var weight = 0.0
    var counted = 0
    for (p in pixels) {
        if (((p ushr 24) and 0xFF) < 200) continue // see-through
        val pr = (p shr 16) and 0xFF
        val pg = (p shr 8) and 0xFF
        val pb = p and 0xFF
        counted++
        val max = maxOf(pr, pg, pb)
        val min = minOf(pr, pg, pb)
        if (max < 40 || min > 225) continue // near black or near white
        val saturation = (max - min).toDouble() / max
        val w = saturation * saturation * (max / 255.0)
        r += pr * w
        g += pg * w
        b += pb * w
        weight += w
    }
    if (counted == 0 || weight / counted < MIN_SHARE * 0.25) return null
    val mr = r / weight
    val mg = g / weight
    val mb = b / weight
    // lift it to a clear, not muddy, colour: the brightest channel to 200, so a dim picture still gives a visible light
    val top = maxOf(mr, mg, mb)
    if (top < 1.0) return null
    val k = 200.0 / top
    return Color(
        red = (minOf(255.0, mr * k) / 255.0).toFloat(),
        green = (minOf(255.0, mg * k) / 255.0).toFloat(),
        blue = (minOf(255.0, mb * k) / 255.0).toFloat()
    )
}

private val washColors = ConcurrentHashMap<String, Color>()
private val noColour = ConcurrentHashMap.newKeySet<String>()

/** Reads a picture's wash colour (decoded small through Coil, so it reuses the download the hero already made). Each address is read once; null when it has none or fails. */
suspend fun backdropWashColor(context: Context, url: String): Color? {
    washColors[url]?.let { return it }
    if (url in noColour) return null
    return try {
        val request = ImageRequest.Builder(context).data(url).size(SAMPLE_W, SAMPLE_H).allowHardware(false).build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        val small = Bitmap.createScaledBitmap(bitmap, SAMPLE_W, SAMPLE_H, true)
        val pixels = IntArray(SAMPLE_W * SAMPLE_H)
        small.getPixels(pixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
        val colour = washColor(pixels)
        if (colour != null) washColors[url] = colour else noColour += url
        colour
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
