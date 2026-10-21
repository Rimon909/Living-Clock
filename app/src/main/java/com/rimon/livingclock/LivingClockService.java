package com.rimon.livingclock;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class LivingClockService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new ClockEngine();
    }

    private final class ClockEngine extends Engine {

        private final Handler handler = new Handler(Looper.getMainLooper());

        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        private final ClockRenderer renderer = new ClockRenderer();

        private boolean visible = false;
        private boolean surfaceReady = false;

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            updateRendering();
        }

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder,
                int format,
                int width,
                int height
        ) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceReady = true;
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            handler.removeCallbacks(drawRunnable);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            visible = false;
            handler.removeCallbacks(drawRunnable);
            super.onDestroy();
        }

        private void updateRendering() {
            handler.removeCallbacks(drawRunnable);

            if (visible && surfaceReady) {
                drawFrame();
            }
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunnable);

            if (!visible || !surfaceReady) {
                return;
            }

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;

            try {
                canvas = holder.lockCanvas();

                if (canvas != null) {
                    canvas.drawColor(android.graphics.Color.TRANSPARENT,
                            android.graphics.PorterDuff.Mode.CLEAR);

                    renderer.draw(canvas, System.nanoTime());
                }
            } catch (RuntimeException ignored) {
                // The surface may disappear while the wallpaper is drawing.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                        // The surface may no longer be valid.
                    }
                }
            }

            if (visible && surfaceReady) {
                handler.postDelayed(drawRunnable, 16L);
            }
        }
    }
}
