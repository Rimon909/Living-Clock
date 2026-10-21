# Living Clock v1.1: upgrade notes

Replace these files in the repo (same paths). Everything else is unchanged.

- app/build.gradle.kts (targetSdk 34, versionCode 2)
- app/src/main/java/com/rimon/livingclock/ClockRenderer.java (rewritten)
- app/src/main/java/com/rimon/livingclock/LivingClockService.java (rewritten)
- app/src/main/java/com/rimon/livingclock/MainActivity.java (rewritten)
- app/src/main/java/com/rimon/livingclock/LayerLoader.java (new)
- app/src/main/java/com/rimon/livingclock/Settings.java (new)
- app/src/main/java/com/rimon/livingclock/TiltSensor.java (new)

## What changed

Layers: background photo (vignette baked in), clock face and hands, then a transparent PNG subject drawn on top. Parallax moves the subject most and the background least. Swiping home screens also shifts the layers.

Smoothness: vsync-synced frames (Choreographer), hardware canvas on Android 8+, frame-rate-independent easing, precomputed dial bitmap and hand paths, zero allocations per frame.

Battery:
- Nothing runs when the wallpaper is hidden or the screen is off.
- The tilt sensor is registered only while visible, and only if motion is enabled.
- Sensor events are dropped unless the value changes visibly.
- Efficient mode wakes once per second and sleeps between ticks.
- Balanced (default) sweeps at 30 fps. Ultra sweeps at 60 fps.
- Parallax is capped at 60 fps and stops as soon as it settles.

## Preparing a subject layer
The subject must be a PNG with a transparent background. Use any app that can lift a subject from a photo, then export it as PNG.

## Known limits
- Subject lifting is manual for now. On-device segmentation could be added later.
- Transparent-PNG subjects should be exported at roughly screen resolution or smaller; larger files are downsampled on load.
