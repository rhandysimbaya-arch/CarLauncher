package com.rb.carlauncher

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Applies equalizer + bass boost + loudness (volume boost) to the global audio mix (session 0)
 * and to any player session that announces itself. Whether the system accepts session-0 effects
 * depends on the device / Android version.
 */
class AudioFx {
    private class Session(val id: Int) {
        var eq: Equalizer? = null
        var bass: BassBoost? = null
        var loud: LoudnessEnhancer? = null
        fun release() {
            try { eq?.release() } catch (_: Throwable) {}
            try { bass?.release() } catch (_: Throwable) {}
            try { loud?.release() } catch (_: Throwable) {}
        }
    }

    class Info(val freqs: List<Float>, val minDb: Float, val maxDb: Float, val globalOk: Boolean)

    private val sessions = HashMap<Int, Session>()
    private var enabled = false
    private var freqs: List<Float> = emptyList()
    private var db: List<Float> = emptyList()
    private var bassStrength = 0
    private var boostMb = 0

    fun count(): Int = sessions.keys.count { it != 0 }
    fun globalActive(): Boolean = sessions.containsKey(0)

    fun open(id: Int): Boolean {
        if (sessions.containsKey(id)) return true
        val s = Session(id)
        s.eq = try { Equalizer(0, id) } catch (e: Throwable) { null }
        s.bass = try { BassBoost(0, id) } catch (e: Throwable) { null }
        s.loud = try { LoudnessEnhancer(id) } catch (e: Throwable) { null }
        if (s.eq == null && s.bass == null && s.loud == null) return false
        sessions[id] = s
        applyTo(s)
        return true
    }

    fun close(id: Int) {
        sessions.remove(id)?.release()
    }

    fun closeAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
    }

    fun info(): Info {
        val eq = sessions[0]?.eq ?: sessions.values.firstNotNullOfOrNull { it.eq }
        if (eq != null) {
            try {
                val n = eq.numberOfBands.toInt()
                val f = (0 until n).map { eq.getCenterFreq(it.toShort()) / 1000f }
                val r = eq.bandLevelRange
                return Info(f, r[0] / 100f, r[1] / 100f, sessions.containsKey(0))
            } catch (_: Throwable) {
            }
        }
        return Info(listOf(60f, 230f, 910f, 3600f, 14000f), -15f, 15f, sessions.containsKey(0))
    }

    fun update(enabled: Boolean, freqs: List<Float>, db: List<Float>, bass: Int, boostMb: Int) {
        this.enabled = enabled
        this.freqs = freqs
        this.db = db
        this.bassStrength = bass
        this.boostMb = boostMb
        sessions.values.forEach { applyTo(it) }
    }

    private fun interp(hz: Float): Float {
        if (freqs.isEmpty() || db.size != freqs.size) return 0f
        if (hz <= freqs.first()) return db.first()
        if (hz >= freqs.last()) return db.last()
        for (i in 0 until freqs.size - 1) {
            if (hz <= freqs[i + 1]) {
                val t = (ln(hz) - ln(freqs[i])) / (ln(freqs[i + 1]) - ln(freqs[i]))
                return db[i] + (db[i + 1] - db[i]) * t
            }
        }
        return 0f
    }

    private fun applyTo(s: Session) {
        val on = enabled
        s.eq?.let { eq ->
            try {
                val r = eq.bandLevelRange
                val n = eq.numberOfBands.toInt()
                for (b in 0 until n) {
                    val hz = eq.getCenterFreq(b.toShort()) / 1000f
                    val mb = (interp(hz) * 100f).roundToInt().coerceIn(r[0].toInt(), r[1].toInt())
                    eq.setBandLevel(b.toShort(), mb.toShort())
                }
                eq.setEnabled(on)
            } catch (_: Throwable) {
            }
        }
        s.bass?.let { b ->
            try {
                if (b.strengthSupported) b.setStrength(bassStrength.coerceIn(0, 1000).toShort())
                b.setEnabled(on && bassStrength > 0)
            } catch (_: Throwable) {
            }
        }
        s.loud?.let { l ->
            try {
                l.setTargetGain(boostMb.coerceAtLeast(0))
                l.setEnabled(on && boostMb > 0)
            } catch (_: Throwable) {
            }
        }
    }
}

/** Real-time spectrum + level of the audio output (needs the Record Audio permission). */
class SpectrumTap(private val onFrame: (FloatArray, Float) -> Unit) {
    private var vis: Visualizer? = null
    private val bars = 32
    private val smooth = FloatArray(bars)
    private var level = -60f

    fun start(): Boolean {
        stop()
        return try {
            val v = Visualizer(0)
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(min(range[1], 1024))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) calcLevel(waveform)
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) calcBars(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            v.setEnabled(true)
            vis = v
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun stop() {
        try {
            vis?.setEnabled(false)
            vis?.release()
        } catch (_: Throwable) {
        }
        vis = null
    }

    private fun calcLevel(w: ByteArray) {
        var sum = 0.0
        for (b in w) {
            val x = ((b.toInt() and 0xFF) - 128) / 128.0
            sum += x * x
        }
        val rms = sqrt(sum / w.size)
        level = if (rms < 1e-4) -60f else (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
    }

    private fun calcBars(fft: ByteArray) {
        val maxBin = fft.size / 2 - 1
        if (maxBin < 2) return
        val out = FloatArray(bars)
        for (i in 0 until bars) {
            val lo = max(1, maxBin.toDouble().pow(i / bars.toDouble()).toInt())
            val hi = min(maxBin + 1, max(lo + 1, maxBin.toDouble().pow((i + 1) / bars.toDouble()).toInt()))
            var mag = 0f
            for (k in lo until hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                mag = max(mag, hypot(re, im))
            }
            val v = (mag / 100f).coerceIn(0f, 1f).pow(0.6f)
            smooth[i] = max(v, smooth[i] * 0.8f)
            out[i] = smooth[i]
        }
        onFrame(out, level)
    }
}
