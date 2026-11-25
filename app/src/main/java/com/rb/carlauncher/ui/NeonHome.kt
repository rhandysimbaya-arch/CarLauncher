package com.rb.carlauncher.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rb.carlauncher.BuildConfig
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.Tab
import com.rb.carlauncher.formatClock
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

// ---------------------------------------------------------------------------------------------
// Speed colour ramp + horizontal speed bar meter (replaces the old fuel bar)
// ---------------------------------------------------------------------------------------------
fun speedColor(f: Float): Color = when {
    f < 0.4f -> lerp(C.Cyan, C.Blue, f / 0.4f)
    f < 0.75f -> lerp(C.Blue, C.Purple, (f - 0.4f) / 0.35f)
    else -> lerp(C.Purple, C.Red, (f - 0.75f) / 0.25f)
}

@Composable
fun SpeedBarMeter(speed: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(du(3.2f))
        ) {
            val n = 40
            val gap = size.width * 0.006f
            val w = (size.width - gap * (n - 1)) / n
            val lit = speed.coerceIn(0f, 200f) / 200f * n
            for (i in 0 until n) {
                val base = speedColor(i / (n - 1f))
                val on = i < lit
                val x = i * (w + gap)
                if (on) {
                    drawRoundRect(base.copy(alpha = 0.22f), Offset(x - w * 0.2f, -size.height * 0.1f), Size(w * 1.4f, size.height * 1.2f), CornerRadius(w * 0.4f))
                }
                drawRoundRect(
                    base.copy(alpha = if (on) 1f else 0.14f),
                    Offset(x, 0f), Size(w, size.height), CornerRadius(w * 0.25f)
                )
            }
        }
        Spacer(Modifier.height(du(0.5f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (t in listOf("0", "50", "100", "150", "200 km/h")) {
                Text(t, color = C.Dim, fontSize = su(2.4f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Neon home layout (matches the reference dashboard)
// ---------------------------------------------------------------------------------------------
@Composable
fun NeonHome(vm: CarViewModel) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(du(1.8f))) {
        Column(
            Modifier
                .weight(0.44f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(1.5f))
        ) {
            Speedometer(
                speed = vm.speedKmh,
                gear = if (vm.speedKmh < 1f) "P" else "D",
                neon = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(du(1f)))
            SpeedBarMeter(vm.speedKmh, Modifier.fillMaxWidth().padding(horizontal = du(1f)))
        }
        Column(
            Modifier
                .weight(0.56f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(du(1.6f))
        ) {
            NeonMusicCard(vm, Modifier.weight(0.44f).fillMaxWidth())
            EqualizerCard(vm, Modifier.weight(0.31f).fillMaxWidth())
            TripStrip(vm, Modifier.weight(0.25f).fillMaxWidth())
        }
    }
}

@Composable
private fun NeonMusicCard(vm: CarViewModel, modifier: Modifier) {
    val m = vm.media
    val tick by produceState(SystemClock.elapsedRealtime(), m) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = m.livePosition(tick)
    val art = remember(m.art) { m.art?.asImageBitmap() }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    val artShape = RoundedCornerShape(du(1.6f))

    Row(
        modifier
            .panel(RoundedCornerShape(du(4f)))
            .padding(du(1.8f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(du(21f))
                .clip(artShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF7A3D), Color(0xFFB02BFF), Color(0xFF2F6BFF))))
                .border(1.5.dp, C.Blue, artShape),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(du(8f)))
            }
        }
        Spacer(Modifier.width(du(2.2f)))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.available) m.title.ifBlank { "Unknown" }
                        else if (BuildConfig.MEDIA_ACCESS) "Nothing playing" else "Media controls",
                        color = Color.White, fontSize = su(5.2f), fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (m.available) m.artist.ifBlank { m.appName }
                        else if (vm.btName != null) "Bluetooth Connected" else "Controls any music player",
                        color = Color(0xFF6C8CFF), fontSize = su(3.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Canvas(Modifier.size(du(4.6f), du(4.6f))) {
                    val bw = size.width * 0.22f
                    val hs = listOf(0.45f, 0.8f, 1f)
                    for (i in 0..2) {
                        val h = size.height * hs[i]
                        drawRoundRect(
                            lerp(C.Cyan, C.Blue, i / 2f),
                            Offset(i * (bw + size.width * 0.09f), size.height - h), Size(bw, h), CornerRadius(bw * 0.2f)
                        )
                    }
                }
            }
            Column {
                SeekBar(
                    fraction = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f,
                    onSeek = { vm.seekFraction(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(du(3.4f))
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatClock(pos), color = Color.White, fontSize = su(3.1f))
                    Spacer(Modifier.weight(1f))
                    Text(formatClock(m.durationMs), color = Color.White, fontSize = su(3.1f))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (BuildConfig.MEDIA_ACCESS) {
                    Icon(
                        Icons.Filled.Shuffle, null,
                        tint = if (shuffle) C.Cyan else Color.White,
                        modifier = Modifier.size(du(5f)).clickable { shuffle = !shuffle; vm.setShuffle(shuffle) }
                    )
                }
                Icon(
                    Icons.Filled.SkipPrevious, null, tint = Color.White,
                    modifier = Modifier.size(du(7.4f)).clickable { vm.previous() }
                )
                Box(
                    Modifier
                        .size(du(10.4f))
                        .drawBehind { drawCircle(C.Cyan.copy(alpha = 0.16f), size.minDimension * 0.62f) }
                        .border(2.dp, C.Cyan, CircleShape)
                        .clip(CircleShape)
                        .clickable { vm.playPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.White, modifier = Modifier.size(du(6.8f))
                    )
                }
                Icon(
                    Icons.Filled.SkipNext, null, tint = Color.White,
                    modifier = Modifier.size(du(7.4f)).clickable { vm.next() }
                )
                if (BuildConfig.MEDIA_ACCESS) {
                    Icon(
                        Icons.Filled.Repeat, null,
                        tint = if (repeat) C.Cyan else Color.White,
                        modifier = Modifier.size(du(5f)).clickable { repeat = !repeat; vm.setRepeat(repeat) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Equalizer visualiser + sound level
// ---------------------------------------------------------------------------------------------
@Composable
private fun EqualizerCard(vm: CarViewModel, modifier: Modifier) {
    val sim by produceState(FloatArray(32), vm) {
        while (true) {
            val active = vm.musicActive()
            value = if (active) FloatArray(32) { i -> (0.2f + 0.6f * Random.nextFloat()) * (1f - i / 56f) }
            else FloatArray(32) { 0.04f }
            delay(110)
        }
    }
    val bars = if (vm.specLive) vm.spectrum else sim
    val level = vm.soundLevelDb()

    Row(
        modifier
            .panel(RoundedCornerShape(du(4f)))
            .clickable { vm.tab = Tab.Sound }
            .padding(du(1.2f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Canvas(Modifier.fillMaxSize()) { drawSpectrum(bars) }
            Text(
                "EQ: ${vm.presetName}",
                color = C.Dim, fontSize = su(2.6f),
                modifier = Modifier.align(Alignment.TopStart).padding(start = du(0.6f))
            )
        }
        Box(
            Modifier
                .padding(horizontal = du(1.2f))
                .width(1.dp)
                .fillMaxHeight(0.8f)
                .background(Color(0x663A5BFF))
        )
        Column(
            Modifier.width(du(23f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (vm.specLive) "Sound Level" else "Volume Level",
                color = Color.White, fontSize = su(3.1f)
            )
            Text(
                "${level.roundToInt()} dB",
                color = Color.White, fontSize = su(6.6f), fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(du(0.8f)))
            LevelMeter(level, Modifier.fillMaxWidth().height(du(2.4f)))
        }
    }
}

private fun DrawScope.drawSpectrum(bars: FloatArray) {
    val n = bars.size
    val slot = size.width / n
    val barW = slot * 0.74f
    val segs = 14
    val segH = size.height / segs
    val gap = segH * 0.18f
    for (i in 0 until n) {
        val f = i / (n - 1f)
        val col = when {
            f < 0.35f -> lerp(Color(0xFF00F0FF), Color(0xFF2F6BFF), f / 0.35f)
            f < 0.7f -> lerp(Color(0xFF2F6BFF), Color(0xFFA03DFF), (f - 0.35f) / 0.35f)
            else -> lerp(Color(0xFFA03DFF), Color(0xFFFF2BD6), (f - 0.7f) / 0.3f)
        }
        val lit = (bars[i].coerceIn(0f, 1f) * segs).roundToInt().coerceIn(1, segs)
        for (s in 0 until lit) {
            val y = size.height - (s + 1) * segH
            drawRect(
                col.copy(alpha = 0.55f + 0.45f * (s / segs.toFloat())),
                Offset(i * slot + (slot - barW) / 2f, y + gap / 2f),
                Size(barW, segH - gap)
            )
        }
    }
}

@Composable
private fun LevelMeter(levelDb: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 10
        val gap = size.width * 0.02f
        val w = (size.width - gap * (n - 1)) / n
        val lit = ((levelDb.coerceIn(-48f, 0f) + 48f) / 48f * n).roundToInt()
        for (i in 0 until n) {
            val col = when {
                i < 5 -> Color(0xFF7CFF3A)
                i < 8 -> Color(0xFFFFE53A)
                else -> Color(0xFFFF5A3A)
            }
            drawRoundRect(
                col.copy(alpha = if (i < lit) 1f else 0.16f),
                Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(w * 0.15f)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Trip strip (Trip A / Avg speed / Max speed); long-press resets. Shows the route when navigating.
// ---------------------------------------------------------------------------------------------
@Composable
private fun TripStrip(vm: CarViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    if (vm.nav != null) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(du(1.4f)), verticalAlignment = Alignment.CenterVertically) {
            NavBanner(vm, Modifier.weight(1f))
            EtaBar(vm, Modifier.weight(1f))
        }
        return
    }
    Row(
        modifier
            .panel(RoundedCornerShape(du(4f)))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    vm.resetTrip()
                    Toast.makeText(ctx, "Trip reset", Toast.LENGTH_SHORT).show()
                })
            }
            .padding(horizontal = du(1.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TripItem(Icons.Filled.DirectionsCar, "Trip A", String.format(Locale.US, "%.1f km", vm.tripKm), Modifier.weight(1f))
        VDivider()
        TripItem(Icons.Filled.Schedule, "Avg. Speed", "${vm.avgKmh.roundToInt()} km/h", Modifier.weight(1f))
        VDivider()
        TripItem(Icons.Filled.Speed, "Max Speed", "${vm.maxKmh.roundToInt()} km/h", Modifier.weight(1f))
    }
}

@Composable
private fun TripItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Row(modifier.padding(horizontal = du(1f)), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(du(5.8f)))
        Spacer(Modifier.width(du(1.4f)))
        Column {
            Text(label, color = C.Dim, fontSize = su(2.8f))
            Text(value, color = Color.White, fontSize = su(3.9f), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
