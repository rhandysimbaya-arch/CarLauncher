package com.rb.carlauncher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import org.osmdroid.util.GeoPoint

enum class Tab(val label: String) {
    Home("Home"), Maps("Maps"), Music("Music"), Phone("Phone"), Settings("Settings")
}

data class MediaInfo(
    val available: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val stampMs: Long = 0L,
    val appName: String = ""
) {
    /** Position now, extrapolated from the last update while playing. */
    fun livePosition(nowElapsed: Long): Long {
        val p = if (playing) positionMs + (nowElapsed - stampMs) else positionMs
        return if (durationMs > 0) p.coerceIn(0L, durationMs) else p.coerceAtLeast(0L)
    }
}

data class Step(
    val type: String,
    val modifier: String,
    val name: String,
    val distance: Double,
    val lat: Double,
    val lon: Double
)

data class NavState(
    val destName: String,
    val dest: GeoPoint,
    val route: List<GeoPoint>,
    val steps: List<Step>,
    val totalMeters: Double,
    val totalSeconds: Double,
    val stepIndex: Int = 0,
    val distToNext: Double = 0.0,
    val remainingMeters: Double = 0.0,
    val remainingSeconds: Double = 0.0
)

data class PlaceResult(val name: String, val lat: Double, val lon: Double) {
    val short: String get() = name.split(",").take(2).joinToString(",").trim()
}

data class AppEntry(val label: String, val pkg: String, val icon: ImageBitmap)
