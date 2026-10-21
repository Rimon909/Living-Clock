package com.rimon.livingclock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class ClockRenderer {

    private static final int TICK_COUNT = 60;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private final float[] tickAlpha = new float[TICK_COUNT];

    private long previousFrameNanos = 0L;
    private long previousSecondIndex = -1L;

    public ClockRenderer() {
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
    }

    public void draw(Canvas canvas, long frameTimeNanos) {
        if (canvas == null) {
            return;
        }

        int save = canvas.save();

        float width = canvas.getWidth();
        float height = canvas.getHeight();
        float cx = width * 0.5f;
        float cy = height * 0.5f;

        float radius = Math.min(width, height) * 0.36f;

        long now = System.currentTimeMillis();

        Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
        calendar.setTimeInMillis(now);

        int hour = calendar.get(Calendar.HOUR);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        float millisecond =
                (now % 1000L) / 1000.0f;

        float smoothSecond = second + millisecond;
        float smoothMinute = minute + smoothSecond / 60.0f;
        float smoothHour = (hour % 12) + smoothMinute / 60.0f;

        long currentSecondIndex = now / 1000L;

        if (previousSecondIndex < 0L) {
            previousSecondIndex = currentSecondIndex;
            tickAlpha[second] = 1.0f;
        } else if (currentSecondIndex != previousSecondIndex) {
            tickAlpha[second] = 1.0f;
            previousSecondIndex = currentSecondIndex;
        }

        float deltaSeconds = 0.016f;

        if (previousFrameNanos != 0L && frameTimeNanos > previousFrameNanos) {
            deltaSeconds = (frameTimeNanos - previousFrameNanos) / 1_000_000_000.0f;
            deltaSeconds = Math.min(deltaSeconds, 0.05f);
        }

        previousFrameNanos = frameTimeNanos;

        // Fade the trail.
        for (int i = 0; i < TICK_COUNT; i++) {
            tickAlpha[i] = Math.max(0.0f, tickAlpha[i] - deltaSeconds * 1.15f);
        }

        tickAlpha[second] = Math.max(tickAlpha[second], 1.0f);

        // Keep the clock proportions consistent on different screens.
        canvas.translate(cx, cy);

        drawDial(canvas, radius);
        drawTicks(canvas, radius, smoothSecond);
        drawHands(canvas, radius, smoothHour, smoothMinute, smoothSecond);
        drawCenter(canvas, radius);

        canvas.restoreToCount(save);
    }

    private void drawDial(Canvas canvas, float radius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(radius * 0.004f);
        paint.setColor(Color.argb(25, 0, 0, 0));
        canvas.drawCircle(0f, 0f, radius, paint);
    }

    private void drawTicks(Canvas canvas, float radius, float smoothSecond) {
        for (int i = 0; i < TICK_COUNT; i++) {
            double angle = (i * TWO_PI / TICK_COUNT) - Math.PI / 2.0;

            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);

            boolean isHour = i % 5 == 0;

            float innerRadius = radius * (isHour ? 0.86f : 0.92f);
            float outerRadius = radius * 0.98f;

            float pulse = tickAlpha[i];
            float expansion = radius * 0.025f * pulse;

            innerRadius -= expansion;
            outerRadius += expansion * 0.35f;

            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(radius * (isHour ? 0.022f : 0.008f));

            float alpha = 255f - pulse * 255f;
            paint.setAlpha((int) Math.max(0f, Math.min(255f, alpha)));

            float x1 = cos * innerRadius;
            float y1 = sin * innerRadius;
            float x2 = cos * outerRadius;
            float y2 = sin * outerRadius;

            canvas.drawLine(x1, y1, x2, y2, paint);

            if (pulse > 0.01f) {
                paint.setColor(Color.WHITE);
                paint.setAlpha((int) (pulse * 255f));
                paint.setStrokeWidth(radius * (isHour ? 0.022f : 0.008f));

                canvas.drawLine(
                        cos * (innerRadius - radius * 0.004f),
                        sin * (innerRadius - radius * 0.004f),
                        cos * (outerRadius + radius * 0.004f),
                        sin * (outerRadius + radius * 0.004f),
                        paint
                );
            }
        }
    }

    private void drawHands(
            Canvas canvas,
            float radius,
            float hour,
            float minute,
            float second
    ) {
        float hourAngle = hour * TWO_PI / 12f - (float) Math.PI / 2f;
        float minuteAngle = minute * TWO_PI / 60f - (float) Math.PI / 2f;
        float secondAngle = second * TWO_PI / 60f - (float) Math.PI / 2f;

        // Hour hand: bold red.
        drawHand(
                canvas,
                hourAngle,
                radius * 0.48f,
                radius * 0.040f,
                Color.rgb(190, 25, 35)
        );

        // Minute hand: bold black.
        drawHand(
                canvas,
                minuteAngle,
                radius * 0.72f,
                radius * 0.025f,
                Color.BLACK
        );

        // Second hand: slim red.
        drawHand(
                canvas,
                secondAngle,
                radius * 0.83f,
                radius * 0.008f,
                Color.rgb(190, 25, 35)
        );
    }

    private void drawHand(
            Canvas canvas,
            float angle,
            float length,
            float thickness,
            int color
    ) {
        float x = (float) Math.cos(angle) * length;
        float y = (float) Math.sin(angle) * length;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(thickness);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        paint.setAlpha(255);

        canvas.drawLine(0f, 0f, x, y, paint);
    }

    private void drawCenter(Canvas canvas, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setAlpha(255);

        canvas.drawCircle(0f, 0f, radius * 0.045f, paint);
    }
}
