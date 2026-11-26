package com.rb.carlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.rb.carlauncher.CarViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** [-]  ----o----  [+]  : same volume as the Sound module. */
@Composable
fun VolumeSlider(vm: CarViewModel, modifier: Modifier = Modifier) {
    val frac = vm.volume.toFloat() / vm.volumeMax
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.VolumeDown, null, tint = Color.White,
            modifier = Modifier.size(du(5f)).clip(CircleShape).clickable { vm.changeVolume(vm.volume - 1) }
        )
        Box(
            Modifier
                .width(du(18f))
                .fillMaxHeight()
                .pointerInput(vm.volumeMax) {
                    detectTapGestures { o -> vm.changeVolume((o.x / size.width * vm.volumeMax).let { Math.round(it) }) }
                }
                .pointerInput(vm.volumeMax) {
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume()
                        vm.changeVolume(Math.round(ch.position.x / size.width * vm.volumeMax))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(Modifier.width(du(18f)).fillMaxHeight()) {
                val y = size.height / 2f
                val pad = size.height * 0.18f
                val w = size.width - pad * 2
                val th = size.height * 0.16f
                drawLine(Color.White.copy(alpha = 0.22f), Offset(pad, y), Offset(pad + w, y), th, StrokeCap.Round)
                drawLine(
                    Brush.horizontalGradient(listOf(C.Cyan, C.Blue), startX = pad, endX = pad + w),
                    Offset(pad, y), Offset(pad + w * frac, y), th, StrokeCap.Round
                )
                drawCircle(Color.White, size.height * 0.2f, Offset(pad + w * frac, y))
                drawCircle(C.Cyan, size.height * 0.2f, Offset(pad + w * frac, y), style = Stroke(th * 0.6f))
            }
        }
        Icon(
            Icons.Filled.VolumeUp, null, tint = Color.White,
            modifier = Modifier.size(du(5f)).clip(CircleShape).clickable { vm.changeVolume(vm.volume + 1) }
        )
    }
}

/**
 * Vinyl-style disc: album art in the middle, spins while music plays, with sound waves
 * (radial bars + glow ring) that follow the real output intensity.
 */
@Composable
fun SpinningDisc(vm: CarViewModel, art: ImageBitmap?, playing: Boolean, modifier: Modifier = Modifier) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            angle = (angle + (now - last) / 1e9f * 40f) % 360f
            last = now
        }
    }
    val bars = vm.spectrum
    val lvl = if (vm.specLive) ((vm.levelDb + 60f) / 60f).coerceIn(0f, 1f) else if (playing) 0.35f else 0f
    val energy = if (playing) max(lvl, if (bars.isNotEmpty()) bars.average().toFloat().coerceIn(0f, 1f) else 0f) else 0f

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val disc = size.minDimension * 0.34f
            val n = 48
            for (i in 0 until n) {
                val b = if (bars.isEmpty()) 0f else bars[(i * bars.size / n).coerceIn(0, bars.size - 1)].coerceIn(0f, 1f)
                val v = if (playing) (b * 0.7f + energy * 0.5f).coerceIn(0.05f, 1f) else 0.04f
                val a = (i.toFloat() / n) * 2f * PI.toFloat() + angle * PI.toFloat() / 180f * 0.25f
                val r0 = disc * 1.12f
                val r1 = r0 + size.minDimension * 0.16f * v
                drawLine(
                    Brush.linearGradient(listOf(C.Cyan, C.Blue), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1)),
                    Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                    size.minDimension * 0.018f, StrokeCap.Round
                )
            }
            drawCircle(C.Cyan.copy(alpha = 0.15f + 0.35f * energy), disc * (1.06f + 0.05f * energy), c, style = Stroke(size.minDimension * 0.012f))
        }
        Box(
            Modifier
                .size(du(14f))
                .rotate(angle)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Brush.linearGradient(listOf(Color(0xFFFF7A3D), Color(0xFFB02BFF), Color(0xFF2F6BFF))))
            }
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(du(6f)))
            }
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.98f, style = Stroke(r * 0.1f))
                drawCircle(Color.White.copy(alpha = 0.18f), r * 0.66f, style = Stroke(1f))
                drawCircle(Color(0xFF05070F), r * 0.13f)
                drawCircle(Color.White.copy(alpha = 0.5f), r * 0.13f, style = Stroke(1.5f))
            }
        }
    }
}
