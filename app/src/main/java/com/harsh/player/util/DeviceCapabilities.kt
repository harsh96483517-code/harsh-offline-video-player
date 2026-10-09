package com.harsh.player.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics

/**
 * Runtime capability detection. Nothing here is hard-coded to a specific TV —
 * every value is read from the device so the player adapts to what the hardware
 * actually supports.
 */
object DeviceCapabilities {

    @Volatile
    private var lowRam: Boolean = false

    @Volatile
    private var totalRamMb: Int = 0

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        totalRamMb = (mi.totalMem / (1024 * 1024)).toInt()
        // System flag is authoritative on API 16+, but treat < 1.5 GB as low-RAM
        // to stay conservative on Android TV boxes.
        lowRam = am.isLowRamDevice || totalRamMb in 1..1536
    }

    /** True when the device should run in LOW-RAM MODE by default. */
    val isLowRamDevice: Boolean get() = lowRam

    val totalRamMegabytes: Int get() = totalRamMb

    /** Screen width bucket used to decide whether to render 720p-style layouts. */
    fun isHdReady(context: Context): Boolean {
        val dm: DisplayMetrics = context.resources.displayMetrics
        return dm.widthPixels <= 1280
    }

    fun androidVersionLabel(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}
