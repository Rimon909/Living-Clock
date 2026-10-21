package com.rimon.depthlock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import java.util.Calendar

/**
 * Owns every pre-built object. draw() allocates nothing: paints, dial bitmap,
 * scaled photos and the calendar are created once or reused.
 */
class DepthRenderer {

    private var width = 0
    private var height = 0

    private var background: Bitmap? = null   // cover-cropped, oversized for parallax headroom
    private var subject: Bitmap? = null     // fitted transparent PNG, drawn in front of the clock
    private var dial: Bitmap? = null       // pre-rendered glass face with shadow
    private var dialRadius = 0f
    private var centerX = 0f
    private var centerY = 0f

    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val subjPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dialPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeCap = Paint.Cap.ROUND
    }
    private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; strokeCap = Paint.Cap.ROUND
    }
    private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 30, 30); strokeCap = Paint.Cap.ROUND
    }
    private val calendar: Calendar = Calendar.getInstance()

    /** Largest parallax travel as a fraction of width, used to size the background. */
    val headroom = 0.06f

    fun rebuild(w: Int, h: Int, bg: Bitmap?, subj: Bitmap?) {
        width = w; height = h
        centerX = w / 2f
        centerY = h / 2f
        dialRadius = minOf(w, h) * 0.22f
        background = bg?.let { PhotoStore.coverCrop(it, (w * (1 + 2 * headroom)).toInt(), (h * (1 + 2 * headroom)).toInt()) }
        subject = subj?.let { PhotoStore.fitInside(it, (w * 0.9f).toInt(), (h * 0.9f).toInt()) }
        dial = buildDial(dialRadius)
        hourPaint.strokeWidth = dialRadius * 0.07f
        minutePaint.strokeWidth = dialRadius * 0.045f
        secondPaint.strokeWidth = dialRadius * 0.018f
    }

    /** Glass face with a soft drop shadow so it lifts off the photo. Built once per size. */
    private fun buildDial(r: Float): Bitmap {
        val pad = (r * 0.2f).toInt()
        val size = ((r + pad) * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.setShadowLayer(r * 0.12f, 0f, r * 0.05f, Color.argb(120, 0, 0, 0))
        paint.color = Color.argb(200, 245, 248, 250)
        c.drawCircle(cx, cx, r, paint)
        paint.clearShadowLayer()

        paint.shader = RadialGradient(
            cx - r * 0.3f, cx - r * 0.35f, r * 1.4f,
            intArrayOf(Color.argb(170, 255, 255, 255), Color.argb(40, 255, 255, 255)),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cx, r, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.02f
        paint.color = Color.argb(160, 255, 255, 255)
        c.drawCircle(cx, cx, r - r * 0.01f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val major = i % 3 == 0
            val inner = r * (if (major) 0.80f else 0.88f)
            val outer = r * 0.95f
            paint.strokeWidth = r * (if (major) 0.035f else 0.018f)
            paint.style = Paint.Style.STROKE
            c.drawLine(
                cx + (inner * kotlin.math.sin(a)).toFloat(), cx - (inner * kotlin.math.cos(a)).toFloat(),
                cx + (outer * kotlin.math.sin(a)).toFloat(), cx - (outer * kotlin.math.cos(a)).toFloat(), paint
            )
        }
        return bmp
    }

    /**
     * Draws one frame. px/py are parallax offsets in [-1, 1] already scaled by depth.
     * sweepSeconds is fractional seconds (for sweep) or whole seconds (for jump).
     */
    fun draw(c: Canvas, px: Float, py: Float, depth: Float, now: Calendar, sweep: Float) {
        c.drawColor(Color.BLACK)

        background?.let { bg ->
            val travelX = width * headroom * depth
            val travelY = height * headroom * depth
            val bx = (px * travelX * 0.6f).coerceIn(-width * headroom, width * headroom)
            val by = (py * travelY * 0.6f).coerceIn(-height * headroom, height * headroom)
            c.drawBitmap(bg, -width * headroom - bx, -height * headroom - by, bgPaint)
        }

        dial?.let { d ->
            val ox = px * width * 0.012f * depth
            val oy = py * height * 0.012f * depth
            val half = d.width / 2f
            c.drawBitmap(d, centerX - half + ox, centerY - half + oy, dialPaint)
            drawHands(c, centerX + ox, centerY + oy, now, sweep)
        }

        subject?.let { s ->
            val sx = px * width * 0.035f * depth
            val sy = py * height * 0.035f * depth
            c.drawBitmap(s, (width - s.width) / 2f + sx, height - s.height + sy, subjPaint)
        }
    }

    private fun drawHands(c: Canvas, cx: Float, cy: Float, now: Calendar, sweep: Float) {
        val minute = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f
        val hour = (now.get(Calendar.HOUR) % 12) + minute / 60f
        val hourAngle = hour * 30f
        val minuteAngle = minute * 6f
        val secondAngle = sweep * 6f

        c.save()
        c.rotate(hourAngle, cx, cy)
        c.drawLine(cx, cy + dialRadius * 0.12f, cx, cy - dialRadius * 0.5f, hourPaint)
        c.restore()

        c.save()
        c.rotate(minuteAngle, cx, cy)
        c.drawLine(cx, cy + dialRadius * 0.15f, cx, cy - dialRadius * 0.78f, minutePaint)
        c.restore()

        c.save()
        c.rotate(secondAngle, cx, cy)
        c.drawLine(cx, cy + dialRadius * 0.2f, cx, cy - dialRadius * 0.85f, secondPaint)
        c.restore()
    }

    fun time(): Calendar {
        calendar.timeInMillis = System.currentTimeMillis()
        return calendar
    }
}
