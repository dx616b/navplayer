package com.dean.navplayer.data

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

/** Picks a saturated mid-tone from a tiny downscale — no Palette dependency. */
object CoverAccent {
    private const val SAMPLE = 12

    fun fromBitmap(bitmap: Bitmap): Int {
        val w = min(bitmap.width, SAMPLE)
        val h = min(bitmap.height, SAMPLE)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        var bestSat = 0f
        var best = 0
        for (argb in pixels) {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val maxC = max(r, max(g, b)) / 255f
            val minC = min(r, min(g, b)) / 255f
            val lightness = (maxC + minC) / 2f
            if (lightness < 0.12f || lightness > 0.88f) continue
            val sat = if (maxC == minC) {
                0f
            } else {
                val d = maxC - minC
                if (lightness > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
            }
            if (sat > bestSat) {
                bestSat = sat
                best = 0xFF000000.toInt() or (argb and 0x00FFFFFF)
            }
        }
        return if (bestSat >= 0.18f) best else 0
    }
}
