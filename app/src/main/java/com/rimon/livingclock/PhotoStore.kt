package com.rimon.depthlock

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File

/**
 * Decodes the photos once, scaled to the screen with a center crop, so the
 * draw loop never decodes or scales anything.
 */
object PhotoStore {

    /** Decodes [file] so its longest side is at most [maxDim] (power-of-two sampling). */
    fun decodeScaled(file: File, maxDim: Int): Bitmap? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    /** Center-crops [src] to exactly w x h, pre-scaled with filtering. */
    fun coverCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(src, null, android.graphics.RectF(left, top, left + dw, top + dh), paint)
        return out
    }

    /** Fit [src] inside w x h keeping aspect (used for the subject layer). */
    fun fitInside(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val tw = (src.width * scale).toInt().coerceAtLeast(1)
        val th = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }
}
