package com.rb.carlauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Boot sound: a synthesized "power-up" whoosh + two-note chime, then the spoken greeting.
 * The speech engine is started ahead of time (prepare()) so the voice follows the chime with no delay.
 * If a recording named welcome.mp3/ogg/wav is placed in res/raw it is played instead of the synthesized voice.
 */
class WelcomeAudio(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending = false
    private var player: MediaPlayer? = null

    companion object {
        const val PHRASE = "Welcome! Have a safe trip and enjoy your ride!"
        private const val SR = 44100
    }

    /** Warm up the speech engine (takes a second or two the first time). */
    fun prepare() {
        if (tts != null) return
        try {
            tts = TextToSpeech(ctx) { status ->
                val e = tts
                if (status != TextToSpeech.SUCCESS || e == null) return@TextToSpeech
                try {
                    val r = e.setLanguage(Locale.US)
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        e.setLanguage(Locale.getDefault())
                    }
                    e.setPitch(0.95f)
                    e.setSpeechRate(0.95f)
                    e.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    ready = true
                    if (pending) {
                        pending = false
                        speakNow()
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun play() {
        prepare()
        playChime()
        main.postDelayed({ speak() }, 650)
    }

    private fun playChime() {
        try {
            val pcm = buildChime()
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC, SR, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, pcm.size * 2, AudioTrack.MODE_STATIC
            )
            t.write(pcm, 0, pcm.size)
            t.play()
            main.postDelayed({
                try {
                    t.stop()
                    t.release()
                } catch (_: Exception) {
                }
            }, 1800)
        } catch (_: Throwable) {
        }
    }

    /** 0-0.55 s rising sweep, then two short decaying bell notes. */
    private fun buildChime(): ShortArray {
        val n = (SR * 1.4).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            if (t < 0.55) {
                val f = 180.0 + 1000.0 * (t / 0.55).pow(2.0)
                phase += 2.0 * PI * f / SR
                val env = sin(PI * t / 0.55)
                s += (sin(phase) * 0.7 + sin(phase * 2.0) * 0.2) * env * 0.35
            }
            if (t >= 0.55) {
                val d = t - 0.55
                s += sin(2.0 * PI * 1318.5 * t) * exp(-7.0 * d) * 0.28
                s += sin(2.0 * PI * 2637.0 * t) * exp(-10.0 * d) * 0.08
            }
            if (t >= 0.66) {
                val d = t - 0.66
                s += sin(2.0 * PI * 1760.0 * t) * exp(-6.0 * d) * 0.3
                s += sin(2.0 * PI * 3520.0 * t) * exp(-10.0 * d) * 0.08
            }
            out[i] = (s.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return out
    }

    private fun speak() {
        val res = ctx.resources.getIdentifier("welcome", "raw", ctx.packageName)
        if (res != 0) {
            try {
                player?.release()
                player = MediaPlayer.create(ctx, res)?.also { mp ->
                    mp.setOnCompletionListener { it.release() }
                    mp.start()
                }
                return
            } catch (_: Exception) {
            }
        }
        if (ready) speakNow() else pending = true
    }

    private fun speakNow() {
        try {
            tts?.speak(PHRASE, TextToSpeech.QUEUE_FLUSH, null, "welcome")
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            tts?.shutdown()
            player?.release()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
