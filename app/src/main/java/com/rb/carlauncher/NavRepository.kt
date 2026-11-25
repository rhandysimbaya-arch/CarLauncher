package com.rb.carlauncher

import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

/**
 * Free, key-less navigation backend:
 *  - Nominatim (OpenStreetMap) for place search
 *  - OSRM public demo server for driving routes + turn-by-turn steps
 */
object NavRepository {
    private const val UA = "CarLauncher/1.0 (Android)"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", UA)
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun search(query: String): List<PlaceResult> {
        val q = URLEncoder.encode(query, "UTF-8")
        val arr = JSONArray(get("https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=6"))
        val out = ArrayList<PlaceResult>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(PlaceResult(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
        return out
    }

    fun route(from: GeoPoint, to: GeoPoint, destName: String): NavState? {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson&steps=true"
        val root = JSONObject(get(url))
        if (root.optString("code") != "Ok") return null
        val r = root.getJSONArray("routes").getJSONObject(0)

        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = ArrayList<GeoPoint>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
        }

        val stepsJson = r.getJSONArray("legs").getJSONObject(0).getJSONArray("steps")
        val steps = ArrayList<Step>(stepsJson.length())
        for (i in 0 until stepsJson.length()) {
            val s = stepsJson.getJSONObject(i)
            val m = s.getJSONObject("maneuver")
            val loc = m.getJSONArray("location")
            steps.add(
                Step(
                    type = m.optString("type"),
                    modifier = m.optString("modifier"),
                    name = s.optString("name"),
                    distance = s.getDouble("distance"),
                    lat = loc.getDouble(1),
                    lon = loc.getDouble(0)
                )
            )
        }
        if (steps.isEmpty()) return null

        val total = r.getDouble("distance")
        val secs = r.getDouble("duration")
        val first = if (steps.size > 1) 1 else 0
        val base = NavState(
            destName = destName,
            dest = to,
            route = pts,
            steps = steps,
            totalMeters = total,
            totalSeconds = secs,
            stepIndex = first
        )
        return advance(base, from)
    }

    /** Recompute upcoming maneuver, distances and time left for the current position. */
    fun advance(nav: NavState, p: GeoPoint): NavState {
        val steps = nav.steps
        var idx = nav.stepIndex.coerceIn(0, steps.size - 1)
        while (idx < steps.size - 1 && p.distanceToAsDouble(GeoPoint(steps[idx].lat, steps[idx].lon)) < 25.0) {
            idx++
        }
        val d = p.distanceToAsDouble(GeoPoint(steps[idx].lat, steps[idx].lon))
        var rem = d
        for (i in idx until steps.size) rem += steps[i].distance
        val avg = if (nav.totalSeconds > 0) nav.totalMeters / nav.totalSeconds else 10.0
        return nav.copy(
            stepIndex = idx,
            distToNext = d,
            remainingMeters = rem,
            remainingSeconds = rem / avg
        )
    }

    /** Distance (m) from point to the nearest segment of the route. */
    fun distanceToRoute(route: List<GeoPoint>, p: GeoPoint): Double {
        if (route.size < 2) return 0.0
        val k = 111320.0
        val cosLat = cos(Math.toRadians(p.latitude))
        var best = Double.MAX_VALUE
        for (i in 0 until route.size - 1) {
            val ax = (route[i].longitude - p.longitude) * k * cosLat
            val ay = (route[i].latitude - p.latitude) * k
            val bx = (route[i + 1].longitude - p.longitude) * k * cosLat
            val by = (route[i + 1].latitude - p.latitude) * k
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            best = min(best, hypot(ax + t * dx, ay + t * dy))
        }
        return best
    }
}
