package com.dean.navplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

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

    private fun sampleSize(width: Int, height: Int, maxSidePx: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxSidePx * 2) {
            sample *= 2
        }
        return sample
    }
}
