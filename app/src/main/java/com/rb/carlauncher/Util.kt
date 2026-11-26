package com.rb.carlauncher

import android.net.Uri

import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.util.Locale

fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(this, "Unable to open", Toast.LENGTH_SHORT).show()
    }
}

fun formatDist(meters: Double): String =
    if (meters < 1000.0) "${Math.round(meters / 10.0) * 10} m"
    else String.format(Locale.US, "%.1f km", meters / 1000.0)

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

fun instruction(s: Step): String {
    val road = if (s.name.isBlank()) "the road" else s.name
    val mod = s.modifier
    return when (s.type) {
        "arrive" -> "Arrive at your destination"
        "depart" -> "Head onto $road"
        "turn", "end of road" -> when {
            mod == "straight" -> "Continue straight onto $road"
            mod == "uturn" -> "Make a U-turn onto $road"
            mod.startsWith("slight") -> "Bear ${mod.removePrefix("slight ")} onto $road"
            mod.startsWith("sharp") -> "Sharp ${mod.removePrefix("sharp ")} onto $road"
            else -> "Turn $mod onto $road"
        }
        "fork" -> "Keep $mod onto $road"
        "merge" -> "Merge onto $road"
        "on ramp", "off ramp" -> "Take the ramp onto $road"
        "roundabout", "rotary", "roundabout turn" -> "Take the roundabout onto $road"
        else -> "Continue onto $road"
    }
}

/** Rotation (degrees) to apply to an upward arrow for this maneuver. */
fun arrowAngle(s: Step): Float = when (s.modifier) {
    "left" -> -90f
    "right" -> 90f
    "slight left" -> -45f
    "slight right" -> 45f
    "sharp left" -> -135f
    "sharp right" -> 135f
    "uturn" -> 180f
    else -> 0f
}

/** Open a navigation app (to a destination if given). adjacent = in the other half of split screen. */
fun Context.openExternalNav(pkg: String, lat: Double?, lon: Double?, adjacent: Boolean = false) {
    var flags = Intent.FLAG_ACTIVITY_NEW_TASK
    if (adjacent) flags = flags or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    val i = if (lat != null && lon != null) {
        val uri = if (pkg == "com.waze") "https://waze.com/ul?ll=$lat,$lon&navigate=yes"
        else "google.navigation:q=$lat,$lon&mode=d"
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg)
    } else packageManager.getLaunchIntentForPackage(pkg)
    try {
        startActivity((i ?: throw IllegalStateException("not installed")).addFlags(flags))
    } catch (e: Exception) {
        Toast.makeText(this, "Navigation app not installed", Toast.LENGTH_SHORT).show()
    }
}
