package com.rimon.livingclock;

import android.content.Context;
import android.content.SharedPreferences;

/** User preferences shared by the wallpaper service and the studio screen. */
public final class Settings {

    public static final String PREFS = "living_clock";

    public static final String KEY_MOTION = "motion_enabled";
    public static final String KEY_SWEEP = "sweep_mode";
    public static final String KEY_DEPTH = "depth_percent";
    public static final String KEY_LAYERS_VERSION = "layers_version";

    /** Second hand jumps once per second; the phone sleeps in between. Lowest battery use. */
    public static final int SWEEP_EFFICIENT = 0;
    /** Second hand sweeps at 30 fps. Default. */
    public static final int SWEEP_BALANCED = 1;
    /** Second hand sweeps at up to 60 fps. */
    public static final int SWEEP_ULTRA = 2;

    public static final int DEFAULT_DEPTH = 60;

    private Settings() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean motionEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MOTION, true);
    }

    public static int sweepMode(Context context) {
        return prefs(context).getInt(KEY_SWEEP, SWEEP_BALANCED);
    }

    public static int depthPercent(Context context) {
        return prefs(context).getInt(KEY_DEPTH, DEFAULT_DEPTH);
    }

    /** Tells the wallpaper service that the photo files changed and must be reloaded. */
    public static void bumpLayersVersion(Context context) {
        SharedPreferences p = prefs(context);
        p.edit().putInt(KEY_LAYERS_VERSION, p.getInt(KEY_LAYERS_VERSION, 0) + 1).apply();
    }
}
