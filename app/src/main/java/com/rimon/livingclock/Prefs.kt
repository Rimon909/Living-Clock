package com.rimon.depthlock

import android.content.Context
import java.io.File

/** Small typed wrapper over SharedPreferences plus the on-disk asset locations. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("depthlock", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "wallpaper").apply { mkdirs() }

    val backgroundFile = File(dir, "background.jpg")
    val subjectFile = File(dir, "subject.png")

    var secondMode: SecondMode
        get() = SecondMode.values().getOrElse(sp.getInt("second_mode", 1)) { SecondMode.BALANCED }
        set(v) = sp.edit().putInt("second_mode", v.ordinal).apply()

    /** Parallax strength multiplier, 0.0 (off) to 2.0. */
    var depth: Float
        get() = sp.getFloat("depth", 1.0f)
        set(v) = sp.edit().putFloat("depth", v).apply()

    var version: Int
        get() = sp.getInt("asset_version", 0)
        set(v) = sp.edit().putInt("asset_version", v).apply()
}

enum class SecondMode(val fps: Int) {
    /** Second hand jumps once a second. Lowest power. */
    EFFICIENT(0),
    /** Sweeps at 30 fps. Default. */
    BALANCED(30),
    /** Sweeps at 60 fps. */
    ULTRA(60);

    fun next(): SecondMode = values()[(ordinal + 1) % values().size]
}
