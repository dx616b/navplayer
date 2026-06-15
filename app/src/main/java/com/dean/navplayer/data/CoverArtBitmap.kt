package com.dean.navplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

import kotlin.math.max

object CoverArtBitmap {
    fun decode(file: File, maxSidePx: Int, lowMemory: Boolean = false): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSidePx)
            if (lowMemory) inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    /** Small downscaled decode + scale blur for now-playing background. */
    fun decodeBlurredBackground(file: File, maxSidePx: Int = 96): Bitmap? {
        val small = decode(file, maxSidePx, lowMemory = true) ?: return null
        return fastBlur(small)
    }

    private fun fastBlur(source: Bitmap): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val tinyW = max(1, w / 4)
        val tinyH = max(1, h / 4)
        val tiny = Bitmap.createScaledBitmap(source, tinyW, tinyH, true)
        if (tiny !== source) source.recycle()
        val blurred = Bitmap.createScaledBitmap(tiny, w, h, true)
        if (blurred !== tiny) tiny.recycle()
        return blurred
    }

    private fun sampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxSidePx * 2) {
            sample *= 2
        }
        return sample
    }
}
