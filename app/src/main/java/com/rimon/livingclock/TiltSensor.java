package com.rimon.livingclock;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Low-power tilt source. Uses the gravity sensor when present (no gyroscope needed) and
 * falls back to the accelerometer. The pose held when motion starts becomes neutral.
 * Events are forwarded only when the value changes visibly, which avoids needless wake-ups.
 */
public final class TiltSensor implements SensorEventListener {

    public interface Listener {
        void onTilt(float x, float y);
    }

    /** Roughly 30 degrees of lean reaches full travel (4.9 m/s^2 is half of g). */
    private static final float RANGE = 4.9f;
    private static final float EPSILON = 0.004f;

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private final int delay;

    private float baseX;
    private float baseY;
    private float lastX;
    private float lastY;
    private boolean calibrated;
    private boolean running;

    public TiltSensor(Context context, Listener listener, boolean fast) {
        this.listener = listener;
        this.manager = (SensorManager) context.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        this.sensor = pick(manager);
        this.delay = fast ? SensorManager.SENSOR_DELAY_GAME : SensorManager.SENSOR_DELAY_UI;
    }

    public boolean isAvailable() {
        return sensor != null;
    }

    public void start() {
        if (sensor == null || running) {
            return;
        }
        calibrated = false;
        lastX = 0f;
        lastY = 0f;
        running = manager.registerListener(this, sensor, delay);
    }

    public void stop() {
        if (!running) {
            return;
        }
        manager.unregisterListener(this);
        running = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float gx = event.values[0];
        float gy = event.values[1];
        if (!calibrated) {
            baseX = gx;
            baseY = gy;
            calibrated = true;
        }
        float x = clamp((gx - baseX) / RANGE);
        float y = clamp((gy - baseY) / RANGE);
        if (Math.abs(x - lastX) < EPSILON && Math.abs(y - lastY) < EPSILON) {
            return;
        }
        lastX = x;
        lastY = y;
        listener.onTilt(x, y);
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
    }

    private static Sensor pick(SensorManager manager) {
        if (manager == null) {
            return null;
        }
        Sensor s = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (s == null) {
            s = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        return s;
    }

    private static float clamp(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
