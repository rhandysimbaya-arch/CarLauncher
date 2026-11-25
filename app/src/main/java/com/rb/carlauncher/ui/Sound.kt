package com.rb.carlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.EqPresets
import java.util.Locale
import kotlin.math.roundToInt

private fun freqLabel(hz: Float): String =
    if (hz >= 1000f) {
        val k = hz / 1000f
        if (k - k.toInt() < 0.05f) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else "${hz.roundToInt()}"

@Composable
fun SoundScreen(vm: CarViewModel) {
    val shape = RoundedCornerShape(du(4f))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(du(1.8f))) {
        // ---- presets
        Column(
            Modifier
                .weight(0.32f)
                .fillMaxHeight()
                .panel(shape)
                .padding(du(1.8f))
        ) {
            Text("Sound presets", color = Color.White, fontSize = su(4.4f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(du(1.2f)))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(du(1f)),
                verticalArrangement = Arrangement.spacedBy(du(1f)),
                modifier = Modifier.fillMaxSize()
            ) {
                items(EqPresets.all) { p ->
                    val sel = vm.presetName == p.name
                    Box(
                        Modifier
                            .height(du(8.6f))
                            .clip(RoundedCornerShape(du(1.8f)))
                            .background(
                                if (sel) Brush.horizontalGradient(listOf(C.Cyan.copy(alpha = 0.85f), C.Blue))
                                else Brush.horizontalGradient(listOf(Color(0xFF0E1C52), Color(0xFF0A1440)))
                            )
                            .clickable { vm.selectPreset(p.name) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.name,
                            color = if (sel) Color(0xFF02060F) else Color.White,
                            fontSize = su(3.1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ---- equalizer + boosters
        Column(
            Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .panel(shape)
                .padding(du(2f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer  -  ${vm.presetName}", color = Color.White, fontSize = su(4.4f), fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !vm.fxEnabled -> "Sound effects are off"
                            vm.fxGlobal -> "Applied to all apps"
                            vm.fxSessions > 0 -> "Applied to ${vm.fxSessions} player(s)"
                            else -> "This device doesn't allow system-wide effects; works only with players that support them"
                        },
                        color = if (vm.fxEnabled && !vm.fxGlobal && vm.fxSessions == 0) Color(0xFFFFC857) else C.Dim,
                        fontSize = su(2.7f), maxLines = 2
                    )
                }
                Switch(
                    checked = vm.fxEnabled,
                    onCheckedChange = { vm.setFxEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = C.Cyan,
                        uncheckedThumbColor = C.Dim, uncheckedTrackColor = Color(0xFF14224F)
                    )
                )
            }
            Spacer(Modifier.height(du(1f)))

            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                vm.bandDb.forEachIndexed { i, db ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            (if (db > 0.05f) "+" else "") + String.format(Locale.US, "%.0f", db),
                            color = Color.White, fontSize = su(2.8f)
                        )
                        NeonSlider(
                            value = (db - vm.eqMinDb) / (vm.eqMaxDb - vm.eqMinDb),
                            onChange = { vm.setBand(i, vm.eqMinDb + it * (vm.eqMaxDb - vm.eqMinDb)) },
                            vertical = true,
                            centered = true,
                            modifier = Modifier.weight(1f).width(du(9f))
                        )
                        Text(
                            freqLabel(vm.bandFreqs.getOrElse(i) { 0f }),
                            color = C.Dim, fontSize = su(2.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(du(1f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(du(2f))) {
                LabeledSlider(
                    "Bass boost", "${vm.bassStrength / 10}%",
                    vm.bassStrength / 1000f, Modifier.weight(1f)
                ) { vm.setBass((it * 1000f).roundToInt()) }
                LabeledSlider(
                    "Volume booster", "+" + String.format(Locale.US, "%.1f", vm.boostDb) + " dB",
                    vm.boostDb / 12f, Modifier.weight(1f)
                ) { vm.setBoost(it * 12f) }
                LabeledSlider(
                    "Volume", "${vm.volume * 100 / vm.volumeMax}%",
                    vm.volume.toFloat() / vm.volumeMax, Modifier.weight(1f)
                ) { vm.setVolume((it * vm.volumeMax).roundToInt()) }
            }
            if (vm.boostDb > 6f) {
                Text(
                    "High boost can distort sound and strain speakers.",
                    color = Color(0xFFFFC857), fontSize = su(2.6f)
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Column(modifier) {
        Row {
            Text(label, color = Color.White, fontSize = su(3f), modifier = Modifier.weight(1f))
            Text(valueText, color = C.Cyan, fontSize = su(3f), fontWeight = FontWeight.Bold)
        }
        NeonSlider(value, onChange, Modifier.fillMaxWidth().height(du(6f)))
    }
}

/** Neon slider drawn on a canvas; vertical (EQ bands) or horizontal. value is 0..1. */
@Composable
fun NeonSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    centered: Boolean = false
) {
    val cb = rememberUpdatedState(onChange)
    val v = value.coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(vertical) {
                detectTapGestures { o ->
                    val f = if (vertical) 1f - o.y / size.height else o.x / size.width
                    cb.value(f.coerceIn(0f, 1f))
                }
            }
            .pointerInput(vertical) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val o = change.position
                    val f = if (vertical) 1f - o.y / size.height else o.x / size.width
                    cb.value(f.coerceIn(0f, 1f))
                }
            }
    ) {
        val r = minOf(size.width, size.height) * (if (vertical) 0.2f else 0.3f)
        val trackW = r * 0.8f
        val dim = Color(0xFF14224F)
        if (vertical) {
            val x = size.width / 2f
            val top = r
            val bottom = size.height - r
            val pos = bottom - v * (bottom - top)
            val mid = (top + bottom) / 2f
            drawLine(dim, Offset(x, top), Offset(x, bottom), strokeWidth = trackW, cap = StrokeCap.Round)
            val from = if (centered) mid else bottom
            drawLine(C.Cyan.copy(alpha = 0.25f), Offset(x, from), Offset(x, pos), strokeWidth = trackW * 2.2f, cap = StrokeCap.Round)
            drawLine(C.Cyan, Offset(x, from), Offset(x, pos), strokeWidth = trackW, cap = StrokeCap.Round)
            if (centered) drawLine(Color.White.copy(alpha = 0.5f), Offset(x - r * 1.4f, mid), Offset(x + r * 1.4f, mid), strokeWidth = 2f)
            drawCircle(C.Cyan.copy(alpha = 0.3f), r * 1.7f, Offset(x, pos))
            drawCircle(C.Cyan, r, Offset(x, pos))
            drawCircle(Color(0xFF020512), r * 0.45f, Offset(x, pos))
        } else {
            val y = size.height / 2f
            val left = r
            val right = size.width - r
            val pos = left + v * (right - left)
            drawLine(dim, Offset(left, y), Offset(right, y), strokeWidth = trackW, cap = StrokeCap.Round)
            drawLine(C.Cyan.copy(alpha = 0.25f), Offset(left, y), Offset(pos, y), strokeWidth = trackW * 2.2f, cap = StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(C.Cyan, C.Blue), startX = left, endX = right), Offset(left, y), Offset(pos, y), strokeWidth = trackW, cap = StrokeCap.Round)
            drawCircle(C.Cyan.copy(alpha = 0.3f), r * 1.7f, Offset(pos, y))
            drawCircle(C.Cyan, r, Offset(pos, y))
            drawCircle(Color(0xFF020512), r * 0.45f, Offset(pos, y))
        }
    }
}
