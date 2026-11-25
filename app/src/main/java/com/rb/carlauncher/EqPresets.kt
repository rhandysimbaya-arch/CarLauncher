package com.rb.carlauncher

import kotlin.math.ln

/** Sound presets. Each preset is a 10-point curve (dB) that is interpolated onto the device's own EQ bands. */
object EqPresets {
    val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    class Preset(val name: String, val gains: FloatArray, val bass: Int)

    val all = listOf(
        Preset("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0),
        // tight, punchy low end: kick/low-mid lift, slight mid scoop, bit of top-end snap
        Preset("Punchy Bass", floatArrayOf(5f, 6f, 5f, 3f, 0f, -1f, 0f, 1.5f, 2f, 1f), 500),
        Preset("Deep Bass", floatArrayOf(8f, 7f, 5f, 2f, 0f, -1f, -1f, 0f, 0f, 0f), 800),
        Preset("Bass Boost", floatArrayOf(6f, 5f, 4f, 2f, 1f, 0f, 0f, 0f, 0f, 0f), 600),
        Preset("Rock", floatArrayOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4f), 200),
        Preset("Pop", floatArrayOf(-1f, 2f, 4f, 4f, 2f, 0f, -1f, -1f, -1f, -1f), 100),
        Preset("Hip-Hop", floatArrayOf(5f, 4f, 3f, 1f, 0f, 0f, 1f, 0f, 1f, 2f), 500),
        Preset("Dance / EDM", floatArrayOf(6f, 5f, 2f, 0f, 0f, -2f, 0f, 3f, 4f, 3f), 500),
        Preset("Vocal", floatArrayOf(-3f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f), 0),
        Preset("Treble Boost", floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f), 0),
        Preset("Jazz", floatArrayOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f), 0),
        Preset("Classical", floatArrayOf(4f, 3f, 3f, 2f, -1f, -1f, 0f, 2f, 3f, 3f), 0),
        Preset("Loudness", floatArrayOf(5f, 3f, 0f, 0f, -1f, 0f, -1f, 0f, 3f, 5f), 300)
    )

    fun find(name: String): Preset? = all.firstOrNull { it.name == name }

    private fun gainAt(gains: FloatArray, hz: Float): Float {
        if (hz <= FREQS.first()) return gains.first()
        if (hz >= FREQS.last()) return gains.last()
        for (i in 0 until FREQS.size - 1) {
            if (hz <= FREQS[i + 1]) {
                val t = (ln(hz) - ln(FREQS[i])) / (ln(FREQS[i + 1]) - ln(FREQS[i]))
                return gains[i] + (gains[i + 1] - gains[i]) * t
            }
        }
        return 0f
    }

    fun forBands(p: Preset, bandHz: List<Float>): List<Float> = bandHz.map { gainAt(p.gains, it) }
}
