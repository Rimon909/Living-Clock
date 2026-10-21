# DepthLock v2 (rebuild)

Live wallpaper: background photo, glass analog clock, transparent subject in front,
tilt and home-screen parallax with a spring.

## Build
Open in Android Studio, or push to GitHub and let the Gradle debug build run.
Requires minSdk 26, compileSdk 34, JDK 17.

## Setup
1. Open DepthLock, choose a background photo and optionally a transparent PNG subject.
2. Adjust depth, pick a second-hand mode.
3. Tap "Set as wallpaper".

## Battery and smoothness design
- Frames are driven by Choreographer (vsync), not a fixed timer.
- The tilt sensor is registered only while the wallpaper is visible, at 50 ms.
- Sensor noise is low-pass filtered and a dead zone skips tiny changes.
- Photos are decoded and scaled once on a background thread, then cropped to the screen.
- The draw path allocates nothing: the dial is pre-rendered, paints are reused.
- Still scenes in Efficient mode sleep until the next second.
- Hardware canvas is used on every supported Android version (minSdk 26).

## Second-hand modes
- Efficient: jumps once per second, lowest power.
- Balanced (default): sweeps at 30 fps.
- Ultra: sweeps at 60 fps.
