package com.rimon.depthlock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Battery and smoothness rules this service follows:
 *  - Nothing runs while hidden: sensor unregistered, no frame callbacks, no timers.
 *  - Drawing is driven by Choreographer (vsync), and only when something changed.
 *  - When everything is still and the mode is not sweeping, the engine sleeps until the next second.
 *  - Photo decoding and scaling happen on a background thread, never on the draw path.
 */
class DepthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DepthEngine()

    inner class DepthEngine : Engine(), SensorEventListener, Choreographer.FrameCallback {

        private val prefs = Prefs(this@DepthWallpaperService)
        private val renderer = DepthRenderer()
        private val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        private val choreographer = Choreographer.getInstance()
        private val loader = HandlerThread("depth-loader").apply { start() }
        private val loaderHandler = Handler(loader.looper)
        private val mainHandler = Handler(android.os.Looper.getMainLooper())

        private val visible = AtomicBoolean(false)
        private var surfaceW = 0
        private var surfaceH = 0
        private var ready = false
        private var frameScheduled = false
        private var lastFrameNanos = 0L
        private var lastDrawnSecond = -1
        private var lastDrawnNanos = 0L
        private var appliedVersion = -1

        // Parallax springs: one per axis, fed by tilt and by home-screen swipe.
        private val springX = Spring(stiffness = 160f)
        private val springY = Spring(stiffness = 160f)
        private var tiltX = 0f
        private var tiltY = 0f
        private var lowX = 0f
        private var lowY = 0f
        private var homeOffset = 0.5f
        private var lastTiltX = 0f
        private var lastTiltY = 0f

        private val sleepRunnable = Runnable { requestFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
        }

        override fun onDestroy() {
            super.onDestroy()
            loader.quitSafely()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible.set(v)
            if (v) {
                reloadAssetsIfChanged()
                startSensor()
                lastFrameNanos = 0L
                requestFrame()
            } else {
                stopSensor()
                cancelScheduled()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width != surfaceW || height != surfaceH) {
                surfaceW = width
                surfaceH = height
                rebuildLayers()
            }
            requestFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            cancelScheduled()
            ready = false
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            // Home-screen swipe: map page offset (0..1) to -1..1 and move the spring target.
            homeOffset = xOffset
            requestFrame()
        }

        private fun reloadAssetsIfChanged() {
            val v = prefs.version
            if (v != appliedVersion) {
                appliedVersion = v
                if (surfaceW > 0) rebuildLayers()
            }
        }

        /** Decodes off the main thread, then swaps in the built layers on the main thread. */
        private fun rebuildLayers() {
            val w = surfaceW
            val h = surfaceH
            if (w <= 0 || h <= 0) return
            ready = false
            loaderHandler.post {
                val maxDim = maxOf(w, h) * 2
                val bg: Bitmap? = PhotoStore.decodeScaled(prefs.backgroundFile, maxDim)
                val subj: Bitmap? = PhotoStore.decodeScaled(prefs.subjectFile, maxDim)
                mainHandler.post {
                    renderer.rebuild(w, h, bg, subj)
                    bg?.recycle()
                    subj?.recycle()
                    ready = true
                    lastDrawnSecond = -1
                    requestFrame()
                }
            }
        }

        private fun startSensor() {
            if (accel != null) {
                // 50 ms (in microseconds) is plenty for parallax and keeps wakeups low.
                sensorManager.registerListener(this, accel, 50_000)
            }
        }

        private fun stopSensor() {
            sensorManager.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            // Low-pass filter the raw accelerometer so jitter does not cause redraws.
            val ax = event.values[0]
            val ay = event.values[1]
            lowX += (ax - lowX) * 0.15f
            lowY += (ay - lowY) * 0.15f
            val nx = (-lowX / 9.8f).coerceIn(-1f, 1f)
            val ny = (lowY / 9.8f).coerceIn(-1f, 1f)
            // Dead zone: ignore changes too small to see.
            if (abs(nx - lastTiltX) < 0.004f && abs(ny - lastTiltY) < 0.004f) return
            lastTiltX = nx
            lastTiltY = ny
            tiltX = nx
            tiltY = ny
            requestFrame()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        /** Schedules one vsync-aligned frame if none is pending. */
        private fun requestFrame() {
            if (!visible.get() || !ready || frameScheduled) return
            frameScheduled = true
            choreographer.postFrameCallback(this)
        }

        private fun cancelScheduled() {
            if (frameScheduled) {
                choreographer.removeFrameCallback(this)
                frameScheduled = false
            }
            mainHandler.removeCallbacks(sleepRunnable)
        }

        override fun doFrame(frameTimeNanos: Long) {
            frameScheduled = false
            if (!visible.get() || !ready) return

            val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameTimeNanos

            // Parallax target: tilt plus home-screen offset (home gets its own share).
            springX.target = tiltX * 0.8f + (0.5f - homeOffset) * 2f * 0.6f
            springY.target = tiltY * 0.8f
            val movingX = springX.step(dt)
            val movingY = springY.step(dt)
            val parallaxMoving = movingX || movingY

            val mode = prefs.secondMode
            val now = renderer.time()
            val nowMs = System.currentTimeMillis()
            val second = now.get(java.util.Calendar.SECOND)
            val sweeping = mode != SecondMode.EFFICIENT
            val frameIntervalNanos = if (mode.fps > 0) 1_000_000_000L / mode.fps else 0L
            val sinceLast = frameTimeNanos - lastDrawnNanos
            val secondChanged = second != lastDrawnSecond

            val needDraw = parallaxMoving || secondChanged || (sweeping && sinceLast >= frameIntervalNanos * 9 / 10)
            if (needDraw) {
                val sweep = if (sweeping) (second + (nowMs % 1000) / 1000f) else second.toFloat()
                drawFrame(springX.value, springY.value, sweep)
                lastDrawnSecond = second
                lastDrawnNanos = frameTimeNanos
            }

            when {
                // Parallax or sweeping second hand: keep going at vsync (throttled by frameInterval above).
                parallaxMoving -> requestFrame()
                sweeping -> requestFrame()
                // Still and jump mode: sleep until the next second boundary.
                else -> {
                    val msToNextSecond = 1000L - (nowMs % 1000L) + 5L
                    mainHandler.postDelayed(sleepRunnable, msToNextSecond)
                }
            }
        }

        private fun drawFrame(px: Float, py: Float, sweep: Float) {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                if (canvas != null) {
                    renderer.draw(canvas, px, py, prefs.depth, renderer.time(), sweep)
                }
            } catch (t: Throwable) {
                // A failed draw must never crash the launcher. Skip this frame.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
