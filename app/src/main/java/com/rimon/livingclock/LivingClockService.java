package com.rimon.livingclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.SurfaceHolder;

/**
 * Battery-first live wallpaper.
 *
 *  - Nothing runs while the wallpaper is hidden or the screen is off.
 *  - Frames are synced to display vsync (Choreographer), never to a fixed timer.
 *  - When the scene is still and the second hand is in Efficient mode, the phone wakes
 *    once per second and sleeps in between.
 *  - The tilt sensor is registered only while visible and only if motion is enabled.
 *  - Frame rate is capped by mode: Efficient 1/s, Balanced 30 fps, Ultra 60 fps.
 *    Parallax motion always runs at up to 60 fps so tilting stays fluid.
 */
public class LivingClockService extends WallpaperService {

    private static final long NS_60FPS = 1_000_000_000L / 60L;
    private static final long NS_30FPS = 1_000_000_000L / 30L;

    @Override
    public Engine onCreateEngine() {
        return new ClockEngine();
    }

    private final class ClockEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener, TiltSensor.Listener {

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Choreographer choreographer = Choreographer.getInstance();
        private final ClockRenderer renderer = new ClockRenderer();
        private final Choreographer.FrameCallback frameCallback = this::onVsync;
        private final Runnable requestFrameTask = this::requestFrame;

        private SharedPreferences prefs;
        private TiltSensor tilt;

        private float tiltX;
        private float tiltY;
        private float homeX;

        private boolean visible;
        private boolean surfaceReady;
        private boolean destroyed;
        private boolean framePending;
        private boolean motion;
        private int sweepMode;
        private int width;
        private int height;
        private int loadGeneration;
        private long lastDrawNanos;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            if (Build.VERSION.SDK_INT >= 27) {
                setOffsetNotificationsEnabled(true);
            }
            prefs = Settings.prefs(LivingClockService.this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            tilt = new TiltSensor(LivingClockService.this, this, false);
            readSettings();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                readSettings();
                requestFrame();
            } else {
                stopScheduling();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            boolean sizeChanged = w != width || h != height;
            width = w;
            height = h;
            surfaceReady = w > 0 && h > 0;
            if (surfaceReady) {
                if (sizeChanged) {
                    renderer.setSize(w, h);
                    reloadLayers();
                }
                readSettings();
            }
            requestFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            stopScheduling();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep,
                                     int xPixels, int yPixels) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPixels, yPixels);
            homeX = clamp((xOffset - 0.5f) * 2f);
            updateTarget();
        }

        @Override
        public void onTilt(float x, float y) {
            tiltX = x;
            tiltY = y;
            updateTarget();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (Settings.KEY_LAYERS_VERSION.equals(key)) {
                if (surfaceReady) {
                    reloadLayers();
                }
            } else {
                readSettings();
                requestFrame();
            }
        }

        @Override
        public void onDestroy() {
            destroyed = true;
            stopScheduling();
            if (prefs != null) {
                prefs.unregisterOnSharedPreferenceChangeListener(this);
            }
            renderer.release();
            super.onDestroy();
        }

        // ---------- settings and targets ----------

        private void readSettings() {
            Context c = LivingClockService.this;
            motion = Settings.motionEnabled(c);
            sweepMode = Settings.sweepMode(c);
            renderer.setSweepMode(sweepMode);
            renderer.setDepthPercent(Settings.depthPercent(c));

            if (visible && motion) {
                tilt.start();
            } else {
                tilt.stop();
                tiltX = 0f;
                tiltY = 0f;
            }
            updateTarget();
        }

        private void updateTarget() {
            float x = clamp(tiltX + homeX * 0.35f);
            if (renderer.setTarget(x, tiltY)) {
                requestFrame();
            }
        }

        private void reloadLayers() {
            final int generation = ++loadGeneration;
            LayerLoader.loadAsync(LivingClockService.this, width, height, layers -> {
                if (destroyed || generation != loadGeneration) {
                    LayerLoader.recycle(layers);
                    return;
                }
                renderer.setLayers(layers);
                requestFrame();
            });
        }

        // ---------- frame scheduling ----------

        private boolean canRender() {
            return !destroyed && visible && surfaceReady;
        }

        private void requestFrame() {
            if (framePending || !canRender()) {
                return;
            }
            framePending = true;
            choreographer.postFrameCallback(frameCallback);
        }

        private void onVsync(long frameTimeNanos) {
            framePending = false;
            if (!canRender()) {
                return;
            }

            long minInterval = minIntervalNanos(renderer.isMoving());
            if (minInterval > 0L && lastDrawNanos != 0L
                    && frameTimeNanos - lastDrawNanos < minInterval * 9L / 10L) {
                requestFrame(); // too soon for the chosen rate; try the next vsync
                return;
            }
            lastDrawNanos = frameTimeNanos;

            boolean keepAnimating = renderer.step(frameTimeNanos);
            long nowMs = System.currentTimeMillis();
            drawFrame(nowMs);
            scheduleNext(keepAnimating, nowMs);
        }

        private long minIntervalNanos(boolean parallaxMoving) {
            if (parallaxMoving || sweepMode == Settings.SWEEP_ULTRA) {
                return NS_60FPS;
            }
            if (sweepMode == Settings.SWEEP_BALANCED) {
                return NS_30FPS;
            }
            return 0L; // Efficient and still: no throttle needed, because we schedule per second
        }

        private void scheduleNext(boolean keepAnimating, long nowMs) {
            if (!canRender()) {
                return;
            }
            if (keepAnimating || sweepMode != Settings.SWEEP_EFFICIENT) {
                requestFrame();
            } else {
                // Efficient and still: sleep until just after the next whole second.
                handler.postDelayed(requestFrameTask, 1000L - (nowMs % 1000L) + 4L);
            }
        }

        private void stopScheduling() {
            framePending = false;
            handler.removeCallbacks(requestFrameTask);
            choreographer.removeFrameCallback(frameCallback);
            if (tilt != null) {
                tilt.stop();
            }
        }

        // ---------- drawing ----------

        private void drawFrame(long nowMs) {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    canvas = holder.lockHardwareCanvas();
                }
                if (canvas == null) {
                    canvas = holder.lockCanvas();
                }
                if (canvas != null) {
                    renderer.draw(canvas, nowMs);
                }
            } catch (RuntimeException ignored) {
                // The surface can disappear mid-frame during rotation or lock-screen transitions.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // Surface no longer valid.
                    }
                }
            }
        }
    }

    private static float clamp(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
