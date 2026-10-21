package com.rimon.depthlock

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import java.io.File

/** Minimal setup screen: pick photos, tune depth, choose the second-hand mode, apply. */
class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs
    private lateinit var modeLabel: TextView

    private val pickBackground = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { copyTo(it, prefs.backgroundFile) }
    }
    private val pickSubject = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { copyTo(it, prefs.subjectFile) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(Button(this).apply {
            text = "Choose background photo"
            setOnClickListener { pickBackground.launch("image/*") }
        })
        root.addView(Button(this).apply {
            text = "Choose subject (transparent PNG, optional)"
            setOnClickListener { pickSubject.launch("image/png") }
        })

        root.addView(TextView(this).apply { text = "\nDepth" })
        root.addView(SeekBar(this).apply {
            max = 200
            progress = (prefs.depth * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) prefs.depth = p / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        })

        modeLabel = TextView(this).apply { text = "\nSecond hand: ${prefs.secondMode.name.lowercase()}" }
        root.addView(modeLabel)
        root.addView(Button(this).apply {
            text = "Cycle second-hand mode"
            setOnClickListener {
                prefs.secondMode = prefs.secondMode.next()
                modeLabel.text = "\nSecond hand: ${prefs.secondMode.name.lowercase()}"
            }
        })

        root.addView(Button(this).apply {
            text = "Set as wallpaper"
            setOnClickListener {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, DepthWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            }
        })

        setContentView(root)
    }

    /** Copies the picked file into app storage, then bumps the version so the wallpaper reloads. */
    private fun copyTo(uri: android.net.Uri, dest: File) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            prefs.version = prefs.version + 1
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read that image", Toast.LENGTH_LONG).show()
        }
    }
}
