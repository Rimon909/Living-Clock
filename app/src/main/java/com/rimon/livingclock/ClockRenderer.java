package com.rimon.livingclock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Draws one complete frame, back to front:
 *   photo background (vignette baked in) -> glass clock face -> hands and pulse -> subject cutout.
 * The subject is drawn last, so it sits in front of the clock while the photo sits behind it.
 *
 * Expensive work happens once in setSize(): the dial becomes a single bitmap and the hands
 * become Paths. draw() does only blits, a few path fills and a few lines. It allocates nothing.
 */
public final class ClockRenderer {

    // Parallax travel as a fraction of screen width at 100% depth. Nearer layers move more.
    // The background shift must stay inside LayerLoader.OVERSCAN even at maximum depth.
    private static final float BG_SHIFT = 0.015f;
    private static final float CLOCK_SHIFT = 0.020f;
    private static final float FG_SHIFT = 0.025f;

    private static final float PULSE_MS = 900f;
    private static final int FACE_ALPHA = 190;
    private static final int COLOR_RED = Color.rgb(200, 28, 40);
    private static final int COLOR_BG_FALLBACK = Color.rgb(10, 14, 22);

    private static final float[] COS = new float[60];
    private static final float[] SIN = new float[60];

    static {
        for (int i = 0; i < 60; i++) {
            double a = i * Math.PI / 30.0 - Math.PI / 2.0;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar calendar = Calendar.getInstance(TimeZone.getDefault());

    private int width;
    private int height;
    private float radius;
    private Bitmap dial;
    private int dialSize;
    private Path hourPath;
    private Path minutePath;

    private Bitmap background;
    private Bitmap foreground;
    private int margin;

    private float depth = 1f;
    private int sweepMode = Settings.SWEEP_BALANCED;

    private float curX;
    private float curY;
    private float targetX;
    private float targetY;
    private long lastFrameNanos;
    private boolean moving;

    private long lastSecond = Long.MIN_VALUE;
    private long pulseStartMs = Long.MIN_VALUE;

    public ClockRenderer() {
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    // ---------- configuration ----------

    /** Sets the drawing size. Clears any layers, which the caller must reload for the new size. */
    public void setSize(int w, int h) {
        if (w == width && h == height && dial != null) {
            return;
        }
        width = w;
        height = h;
        radius = Math.min(w, h) * 0.36f;
        releaseLayers();
        recycle(dial);
        dial = null;
        if (w <= 0 || h <= 0) {
            return;
        }
        buildDial();
        buildHands();
    }

    public void setLayers(LayerLoader.Layers layers) {
        releaseLayers();
        if (layers == null) {
            return;
        }
        background = layers.background;
        foreground = layers.foreground;
        margin = layers.margin;
    }

    /** depthPercent 0..100. 60 is the default; 100 gives the strongest parallax. */
    public void setDepthPercent(int percent) {
        depth = Math.max(0, Math.min(100, percent)) / 60f;
    }

    public void setSweepMode(int mode) {
        sweepMode = mode;
    }

    /** Returns true only if the target actually changed, so callers can skip redundant redraws. */
    public boolean setTarget(float x, float y) {
        if (x == targetX && y == targetY) {
            return false;
        }
        targetX = x;
        targetY = y;
        return true;
    }

    public boolean isMoving() {
        return moving;
    }

    /**
     * Eases the parallax toward its target. The easing is frame-rate independent, so motion
     * looks identical at 60, 90 or 120 Hz. Returns true while the layers are still travelling.
     */
    public boolean step(long frameNanos) {
        float dt = lastFrameNanos == 0L
                ? 0f
                : Math.min(0.1f, Math.max(0L, frameNanos - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = frameNanos;

        float k = 1f - (float) Math.exp(-dt * 8f);
        curX += (targetX - curX) * k;
        curY += (targetY - curY) * k;

        moving = Math.abs(targetX - curX) > 0.0005f || Math.abs(targetY - curY) > 0.0005f;
        if (!moving) {
            curX = targetX;
            curY = targetY;
        }
        return moving;
    }

    public void release() {
        releaseLayers();
        recycle(dial);
        dial = null;
        width = 0;
        height = 0;
    }

    // ---------- drawing ----------

    public void draw(Canvas canvas, long nowMs) {
        if (dial == null) {
            return;
        }

        long second = nowMs / 1000L;
        if (second != lastSecond) {
            lastSecond = second;
            pulseStartMs = nowMs;
            calendar.setTimeZone(TimeZone.getDefault()); // picks up time-zone changes once a second
        }
        calendar.setTimeInMillis(nowMs);
        int hour = calendar.get(Calendar.HOUR);
        int minute = calendar.get(Calendar.MINUTE);
        int sec = calendar.get(Calendar.SECOND);
        int ms = calendar.get(Calendar.MILLISECOND);

        boolean sweeping = sweepMode != Settings.SWEEP_EFFICIENT;
        float hourDeg = (hour + minute / 60f + sec / 3600f) * 30f;
        float minuteDeg = (minute + sec / 60f) * 6f;
        float secondDeg = sweeping ? (sec + ms / 1000f) * 6f : sec * 6f;

        float shift = width * depth;

        // 1. Background photo.
        if (background != null) {
            canvas.drawBitmap(background,
                    -margin + curX * BG_SHIFT * shift,
                    -margin + curY * BG_SHIFT * shift,
                    bitmapPaint);
        } else {
            canvas.drawColor(COLOR_BG_FALLBACK);
        }

        // 2. Clock face, then hands, in the clock's own coordinate space.
        float cx = width * 0.5f + curX * CLOCK_SHIFT * shift;
        float cy = height * 0.5f + curY * CLOCK_SHIFT * shift;
        canvas.drawBitmap(dial, cx - dialSize * 0.5f, cy - dialSize * 0.5f, bitmapPaint);

        canvas.save();
        canvas.translate(cx, cy);
        if (sweeping && pulseStartMs != Long.MIN_VALUE) {
            float p = (nowMs - pulseStartMs) / PULSE_MS;
            if (p >= 0f && p < 1f) {
                drawPulse(canvas, sec, p);
            }
        }
        drawHands(canvas, hourDeg, minuteDeg, secondDeg);
        canvas.restore();

        // 3. Subject cutout: the nearest layer, moves the most, always on top.
        if (foreground != null) {
            canvas.drawBitmap(foreground,
                    -margin + curX * FG_SHIFT * shift,
                    -margin + curY * FG_SHIFT * shift,
                    bitmapPaint);
        }
    }

    private void drawPulse(Canvas canvas, int index, float p) {
        canvas.save();
        canvas.rotate(index * 6f);
        strokePaint.setColor(COLOR_RED);
        strokePaint.setAlpha(Math.round(255f * (1f - p)));
        strokePaint.setStrokeWidth(radius * 0.024f);
        float inner = radius * (0.84f - 0.03f * p);
        float outer = radius * (0.97f + 0.02f * p);
        canvas.drawLine(0f, -inner, 0f, -outer, strokePaint);
        canvas.restore();
    }

    private void drawHands(Canvas canvas, float hourDeg, float minuteDeg, float secondDeg) {
        float sx = radius * 0.010f;
        float sy = radius * 0.016f;

        // Soft drop shadows give the hands depth against the photo.
        fillPaint.setColor(Color.argb(70, 0, 0, 0));
        canvas.save();
        canvas.translate(sx, sy);
        canvas.rotate(hourDeg);
        canvas.drawPath(hourPath, fillPaint);
        canvas.restore();

        canvas.save();
        canvas.translate(sx, sy);
        canvas.rotate(minuteDeg);
        canvas.drawPath(minutePath, fillPaint);
        canvas.restore();

        // Hour hand: red.
        fillPaint.setColor(COLOR_RED);
        canvas.save();
        canvas.rotate(hourDeg);
        canvas.drawPath(hourPath, fillPaint);
        canvas.restore();

        // Minute hand: black, with a faint light edge so it reads on dark photos.
        canvas.save();
        canvas.rotate(minuteDeg);
        fillPaint.setColor(Color.BLACK);
        canvas.drawPath(minutePath, fillPaint);
        strokePaint.setColor(Color.argb(110, 255, 255, 255));
        strokePaint.setStrokeWidth(radius * 0.0035f);
        canvas.drawPath(minutePath, strokePaint);
        canvas.restore();

        // Second hand: thin red line with a short counterweight.
        strokePaint.setColor(COLOR_RED);
        strokePaint.setStrokeWidth(radius * 0.007f);
        canvas.save();
        canvas.rotate(secondDeg);
        canvas.drawLine(0f, radius * 0.18f, 0f, -radius * 0.86f, strokePaint);
        canvas.restore();

        // Centre cap.
        fillPaint.setColor(Color.BLACK);
        canvas.drawCircle(0f, 0f, radius * 0.045f, fillPaint);
        fillPaint.setColor(COLOR_RED);
        canvas.drawCircle(0f, 0f, radius * 0.014f, fillPaint);
    }

    // ---------- precomputed assets ----------

    private void buildDial() {
        float r = radius;
        int pad = Math.round(r * 0.10f) + 4;
        dialSize = Math.round(r * 2f) + pad * 2;
        dial = Bitmap.createBitmap(dialSize, dialSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dial);
        float c0 = dialSize * 0.5f;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Glass face with a soft shadow so it lifts off the photo.
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(r * 0.06f, 0f, r * 0.025f, Color.argb(120, 0, 0, 0));
        p.setColor(Color.argb(FACE_ALPHA, 242, 244, 248));
        c.drawCircle(c0, c0, r, p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.006f);
        p.setColor(Color.argb(90, 0, 0, 0));
        c.drawCircle(c0, c0, r, p);

        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0;
            float inner = r * (hour ? 0.84f : 0.92f);
            float outer = r * 0.97f;
            p.setStrokeWidth(r * (hour ? 0.026f : 0.009f));
            p.setColor(hour ? Color.BLACK : Color.argb(200, 20, 20, 24));
            c.drawLine(c0 + COS[i] * inner, c0 + SIN[i] * inner,
                    c0 + COS[i] * outer, c0 + SIN[i] * outer, p);
        }
    }

    private void buildHands() {
        float r = radius;
        hourPath = handPath(r * 0.50f, r * 0.032f, r * 0.016f, r * 0.10f);
        minutePath = handPath(r * 0.76f, r * 0.024f, r * 0.009f, r * 0.12f);
    }

    /** Tapered hand pointing up (negative y) from the centre, with a short tail. */
    private static Path handPath(float len, float baseHalf, float tipHalf, float tail) {
        Path p = new Path();
        p.moveTo(-baseHalf, tail);
        p.lineTo(-tipHalf, -len);
        p.lineTo(tipHalf, -len);
        p.lineTo(baseHalf, tail);
        p.close();
        return p;
    }

    private void releaseLayers() {
        recycle(background);
        recycle(foreground);
        background = null;
        foreground = null;
        margin = 0;
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
