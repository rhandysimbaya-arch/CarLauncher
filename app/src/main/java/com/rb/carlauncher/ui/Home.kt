package com.rb.carlauncher.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.rb.carlauncher.BuildConfig
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.Tab
import com.rb.carlauncher.ThemeMode
import com.rb.carlauncher.arrowAngle
import com.rb.carlauncher.formatClock
import com.rb.carlauncher.formatDist
import com.rb.carlauncher.instruction
import com.rb.carlauncher.safeStart
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Status bar
// ---------------------------------------------------------------------------------------------
@Composable
fun TopStatusBar(vm: CarViewModel, modifier: Modifier = Modifier, onRequestPermissions: () -> Unit = {}) {
    val ctx = LocalContext.current
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("h:mm", Locale.US) }
    val ampmFmt = remember { SimpleDateFormat("a", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }
    val big = su(4.8f)
    val small = su(3.4f)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = du(2.5f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (vm.inSplit) {
            Icon(
                Icons.Filled.Close, null, tint = Color(0xFFFF6B6B),
                modifier = Modifier.size(du(5.6f)).clickable { vm.onCloseSplit() }
            )
            Spacer(Modifier.width(du(1.5f)))
        }
        Icon(
            Icons.Filled.Home, null, tint = Color.White,
            modifier = Modifier
                .size(du(5.6f))
                .clickable { vm.tab = Tab.Home }
        )
        Spacer(Modifier.width(du(3f)))
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = big, fontWeight = FontWeight.Medium)) { append(timeFmt.format(now)) }
                    withStyle(SpanStyle(fontSize = small)) { append(" " + ampmFmt.format(now).uppercase(Locale.US)) }
                },
                color = Color.White
            )
            // date: shrinks to fit exactly the width of the time above
            Text(
                dateFmt.format(now),
                color = C.Dim, fontSize = su(2.3f), maxLines = 1, softWrap = false,
                modifier = Modifier.width(0.dp).wrapContentWidth(unbounded = true, align = Alignment.Start)
            )
        }
        Spacer(Modifier.width(du(2.5f)))
        VDivider()
        Spacer(Modifier.width(du(2.5f)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                if (vm.btNeedsPermission) onRequestPermissions() else ctx.safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        ) {
            Icon(Icons.Filled.Bluetooth, null, tint = C.Cyan, modifier = Modifier.size(du(5f)))
            Spacer(Modifier.width(du(1f)))
            Column {
                Text(
                    if (vm.btName != null) "Connected" else "Bluetooth",
                    color = Color.White, fontSize = su(2.5f)
                )
                Text(
                    vm.btName ?: (if (vm.btNeedsPermission) "Tap to allow" else "Not connected"),
                    color = C.Dim, fontSize = su(2.9f), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.weight(1f))

        VolumeSlider(vm, Modifier.height(du(6f)))
        Spacer(Modifier.width(du(2.5f)))
        VDivider()
        Spacer(Modifier.width(du(2.5f)))
        val t = if (!vm.ambientTemp.isNaN()) vm.ambientTemp else vm.batteryTemp
        Icon(Icons.Filled.Thermostat, null, tint = C.Cyan, modifier = Modifier.size(du(5f)))
        Text(
            if (t.isNaN()) "--°C" else "${t.roundToInt()}°C",
            color = Color.White, fontSize = su(4f)
        )
        Spacer(Modifier.width(du(2.5f)))
        VDivider()
        Spacer(Modifier.width(du(2.5f)))
        WifiIcon(vm.wifiLevel, vm.wifiConnected)
        Spacer(Modifier.width(du(2f)))
        BatteryIcon(vm.batteryPct)
    }
}

@Composable
fun VDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(du(4.6f))
            .background(Color(0x663A5BFF))
    )
}

@Composable
private fun WifiIcon(level: Int, connected: Boolean) {
    Canvas(Modifier.size(du(5.2f), du(4.2f))) {
        val cx = size.width / 2f
        val cy = size.height * 0.92f
        val sw = size.height * 0.12f
        val on = Color.White
        val off = Color.White.copy(alpha = 0.25f)
        val radii = listOf(size.height * 0.36f, size.height * 0.64f, size.height * 0.92f)
        for (i in radii.indices) {
            val lit = connected && level >= i + 1
            val r = radii[i]
            drawArc(
                color = if (lit) on else off,
                startAngle = 225f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        drawCircle(if (connected) on else off, sw * 0.95f, Offset(cx, cy - sw * 0.2f))
        if (!connected) {
            drawLine(
                C.Red, Offset(size.width * 0.12f, size.height * 0.1f), Offset(size.width * 0.88f, size.height * 0.95f),
                strokeWidth = sw * 0.8f, cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun BatteryIcon(pct: Int) {
    Canvas(Modifier.size(du(5.6f), du(3.4f))) {
        val nub = size.width * 0.08f
        val bodyW = size.width - nub
        drawRoundRect(
            color = Color.White,
            size = Size(bodyW, size.height),
            cornerRadius = CornerRadius(size.height * 0.25f),
            style = Stroke(size.height * 0.1f)
        )
        val pad = size.height * 0.2f
        val fillW = (bodyW - pad * 2) * pct.coerceIn(0, 100) / 100f
        drawRoundRect(
            color = if (pct <= 15) C.Red else Color.White,
            topLeft = Offset(pad, pad),
            size = Size(fillW, size.height - pad * 2),
            cornerRadius = CornerRadius(size.height * 0.1f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(bodyW + nub * 0.2f, size.height * 0.3f),
            size = Size(nub * 0.8f, size.height * 0.4f),
            cornerRadius = CornerRadius(nub * 0.3f)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Home screen
// ---------------------------------------------------------------------------------------------
@Composable
fun HomeScreen(vm: CarViewModel) {
    if (vm.theme == ThemeMode.Neon) {
        NeonHome(vm)
        return
    }
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(du(1.8f))
    ) {
        SpeedPanel(vm, Modifier.weight(0.34f).fillMaxHeight())
        NavCard(vm, Modifier.weight(0.38f).fillMaxHeight())
        MediaCard(vm, Modifier.weight(0.28f).fillMaxHeight())
    }
}

@Composable
private fun SpeedPanel(vm: CarViewModel, modifier: Modifier) {
    Column(modifier.panel(RoundedCornerShape(du(4f))).padding(du(1.5f))) {
        Speedometer(
            speed = vm.speedKmh,
            gear = if (vm.speedKmh < 1f) "P" else "D",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(du(1f)))
        SpeedBarMeter(vm.speedKmh, Modifier.fillMaxWidth().padding(horizontal = du(1f), vertical = du(0.6f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Navigation card
// ---------------------------------------------------------------------------------------------
@Composable
private fun NavCard(vm: CarViewModel, modifier: Modifier) {
    Box(modifier.panel(RoundedCornerShape(du(4f)))) {
        NavMap(vm, Modifier.fillMaxSize(), follow = true, zoom = 17.0, night = true)
        // transparent layer: tapping the card opens the full Maps screen
        Box(
            Modifier
                .fillMaxSize()
                .clickable { vm.tab = Tab.Maps }
        )
        NavBanner(
            vm,
            Modifier
                .align(Alignment.TopStart)
                .padding(du(1.6f))
                .fillMaxWidth(0.62f)
        )
        Compass(
            vm.bearing,
            Modifier
                .align(Alignment.TopEnd)
                .padding(du(1.6f))
        )
        EtaBar(
            vm,
            Modifier
                .align(Alignment.BottomStart)
                .padding(du(1.6f))
                .fillMaxWidth(0.82f)
        )
    }
}

@Composable
fun NavBanner(vm: CarViewModel, modifier: Modifier = Modifier) {
    val nav = vm.nav
    val shape = RoundedCornerShape(du(1.6f))
    if (nav == null) {
        Row(
            modifier
                .clip(shape)
                .background(Color(0xE60A1544))
                .border(1.dp, C.Blue.copy(alpha = 0.6f), shape)
                .padding(du(1.8f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = C.Cyan, modifier = Modifier.size(du(6f)))
            Spacer(Modifier.width(du(1.5f)))
            Column {
                Text("Where to?", color = Color.White, fontSize = su(4.4f), fontWeight = FontWeight.Bold)
                Text(
                    if (vm.gpsFix) "Tap to set a destination" else "Waiting for GPS...",
                    color = C.Dim, fontSize = su(2.9f)
                )
            }
        }
        return
    }
    val step = nav.steps[nav.stepIndex]
    Row(
        modifier
            .clip(shape)
            .background(C.Green)
            .padding(horizontal = du(1.8f), vertical = du(1.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.ArrowUpward, null, tint = Color.White,
            modifier = Modifier
                .size(du(8f))
                .rotate(arrowAngle(step))
        )
        Spacer(Modifier.width(du(1.5f)))
        Column {
            Text(formatDist(nav.distToNext), color = Color.White, fontSize = su(6f), fontWeight = FontWeight.Bold)
            Text(instruction(step), color = Color.White, fontSize = su(3.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EtaBar(vm: CarViewModel, modifier: Modifier = Modifier) {
    val nav = vm.nav ?: return
    val minutes = ceil(nav.remainingSeconds / 60.0).toInt().coerceAtLeast(1)
    val duration = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    val eta = SimpleDateFormat("h:mm a", Locale.US)
        .format(Date(System.currentTimeMillis() + (nav.remainingSeconds * 1000).toLong()))
        .uppercase(Locale.US)
    val shape = RoundedCornerShape(du(2f))
    Row(
        modifier
            .clip(shape)
            .background(Color(0xEE050A1C))
            .border(1.dp, Color(0x552F6BFF), shape)
            .padding(horizontal = du(2f), vertical = du(1.2f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(du(5.4f)))
        Spacer(Modifier.width(du(1.5f)))
        Column(Modifier.weight(1f)) {
            Text(
                "$duration • ${formatDist(nav.remainingMeters)}",
                color = Color.White, fontSize = su(3.9f), fontWeight = FontWeight.Medium
            )
            Text(eta, color = C.Dim, fontSize = su(3.2f))
        }
        Icon(
            Icons.Filled.Close, null, tint = Color.White,
            modifier = Modifier
                .size(du(5.4f))
                .clickable { vm.cancelNav() }
        )
    }
}

@Composable
fun Compass(bearing: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(du(7.4f))
            .clip(CircleShape)
            .background(Color(0xEE050A1C))
            .border(1.dp, Color(0x772F6BFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(du(0.9f))) {
            rotate(-bearing) {
                val w = size.width
                val h = size.height
                val red = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w * 0.7f, h * 0.45f)
                    lineTo(w * 0.3f, h * 0.45f)
                    close()
                }
                drawPath(red, C.Red)
            }
        }
        Text(
            "N", color = Color.White, fontSize = su(2.8f), fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = du(0.6f))
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Media card
// ---------------------------------------------------------------------------------------------
@Composable
fun MediaCard(vm: CarViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val m = vm.media
    val tick by produceState(SystemClock.elapsedRealtime(), m) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = m.livePosition(tick)
    var menu by remember { mutableStateOf(false) }
    val art = remember(m.art) { m.art?.asImageBitmap() }
    val artShape = RoundedCornerShape(du(1.6f))

    Column(modifier.panel(RoundedCornerShape(du(4f))).padding(du(2f))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(du(6.4f))
                    .clip(RoundedCornerShape(du(1.8f)))
                    .background(C.Blue),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bluetooth, null, tint = Color.White, modifier = Modifier.size(du(4.2f)))
            }
            Spacer(Modifier.width(du(1.5f)))
            Text(
                when {
                    vm.btName != null -> "Bluetooth"
                    m.appName.isNotBlank() -> m.appName
                    else -> "Media"
                },
                color = Color.White, fontSize = su(4f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                Icon(
                    Icons.Filled.MoreVert, null, tint = Color.White,
                    modifier = Modifier
                        .size(du(5.4f))
                        .clickable { menu = true }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Choose music app") },
                        onClick = { menu = false; vm.tab = Tab.Music }
                    )
                    if (BuildConfig.MEDIA_ACCESS) {
                        DropdownMenuItem(
                            text = { Text("Media access settings") },
                            onClick = {
                                menu = false
                                ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(du(2f)))

        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            SpinningDisc(vm, art, m.playing, Modifier.size(du(24f)))
            Spacer(Modifier.width(du(2f)))
            Column(Modifier.weight(1f)) {
                if (m.available) {
                    Text(
                        m.title.ifBlank { "Unknown" }, color = Color.White, fontSize = su(5f),
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        m.artist.ifBlank { m.appName }, color = C.Dim, fontSize = su(3.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        if (BuildConfig.MEDIA_ACCESS) "Nothing playing" else "Media controls",
                        color = Color.White, fontSize = su(4.4f), fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !BuildConfig.MEDIA_ACCESS -> "Controls any music player"
                            vm.mediaAccess -> "Start music on your phone"
                            else -> "Tap to allow media access"
                        },
                        color = C.Dim, fontSize = su(3.2f),
                        modifier = Modifier.clickable(enabled = BuildConfig.MEDIA_ACCESS && !vm.mediaAccess) {
                            ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(du(1.5f)))
        SeekBar(
            fraction = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f,
            onSeek = { vm.seekFraction(it) },
            bars = vm.spectrum,
            playing = m.playing,
            modifier = Modifier
                .fillMaxWidth()
                .height(du(7f))
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(pos), color = Color.White, fontSize = su(3.2f))
            Spacer(Modifier.weight(1f))
            Text(formatClock(m.durationMs), color = Color.White, fontSize = su(3.2f))
        }
        Spacer(Modifier.height(du(1f)))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.SkipPrevious, null, tint = Color.White,
                modifier = Modifier.size(du(8f)).clickable { vm.previous() }
            )
            Box(
                Modifier
                    .size(du(11f))
                    .border(2.dp, Color.White, CircleShape)
                    .clip(CircleShape)
                    .clickable { vm.playPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                    tint = Color.White, modifier = Modifier.size(du(7.4f))
                )
            }
            Icon(
                Icons.Filled.SkipNext, null, tint = Color.White,
                modifier = Modifier.size(du(8f)).clickable { vm.next() }
            )
        }
    }
}

/** Music progress: orange track with a live sound wave rising above the played part, white-ringed thumb. */
@Composable
fun SeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bars: FloatArray = FloatArray(0),
    playing: Boolean = false
) {
    var phase by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            phase += (now - last) / 1e9f * 3.2f
            last = now
        }
    }
    val energy = if (!playing) 0f else if (bars.isEmpty()) 0.35f else maxOf(0.2f, bars.average().toFloat().coerceIn(0f, 1f))
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { off -> onSeek(off.x / size.width) }
        }
    ) {
        val h = size.height * 0.16f
        val cy = size.height * 0.74f
        val pad = size.height * 0.2f
        val w = size.width - pad * 2
        val f = fraction.coerceIn(0f, 1f)
        val px = pad + w * f
        val orange = Color(0xFFFF6A00)
        // remaining track
        drawRoundRect(Color(0x55FFFFFF), Offset(pad, cy - h / 2), Size(w, h), CornerRadius(h / 2))
        // sound wave above the played part
        if (px > pad + 4f) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(pad, cy - h / 2)
            var x = pad
            val maxAmp = size.height * 0.62f
            while (x <= px) {
                val rel = (x - pad) / w
                val b = if (bars.isEmpty()) 0.5f else bars[(rel * (bars.size - 1)).toInt().coerceIn(0, bars.size - 1)].coerceIn(0f, 1f)
                val wave = 0.5f + 0.5f * kotlin.math.sin(x * 0.045f + phase) * kotlin.math.sin(x * 0.011f - phase * 0.6f)
                val edge = minOf(1f, (x - pad) / 30f, (px - x) / 30f + 0.15f).coerceAtLeast(0f)
                val amp = maxAmp * energy * (0.25f + 0.75f * b) * wave * edge
                path.lineTo(x, cy - h / 2 - amp)
                x += 3f
            }
            path.lineTo(px, cy - h / 2)
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE066).copy(alpha = 0.95f), orange.copy(alpha = 0.85f)), startY = cy - maxAmp, endY = cy))
        }
        // played track
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFFE85D00), Color(0xFFFF8A1F)), startX = pad, endX = pad + w),
            Offset(pad, cy - h / 2), Size(px - pad, h), CornerRadius(h / 2)
        )
        // thumb
        drawCircle(Color.White, size.height * 0.2f, Offset(px, cy))
        drawCircle(orange, size.height * 0.135f, Offset(px, cy))
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------------------------
@Composable
fun BottomNav(vm: CarViewModel, modifier: Modifier = Modifier) {
    val items = listOf(
        Tab.Home to Icons.Filled.Home,
        Tab.Maps to Icons.Filled.Place,
        Tab.Music to Icons.Filled.MusicNote,
        Tab.Sound to Icons.Filled.GraphicEq,
        Tab.Phone to Icons.Filled.Phone,
        Tab.Settings to Icons.Filled.Settings
    )
    val barShape = RoundedCornerShape(topStart = du(5f), topEnd = du(5f), bottomStart = du(2f), bottomEnd = du(2f))
    Row(
        modifier
            .fillMaxWidth()
            .panel(barShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, icon) ->
            val selected = vm.tab == tab
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = du(5f), topEnd = du(2.5f)))
                    .then(
                        if (selected) Modifier.background(
                            Brush.verticalGradient(listOf(Color(0x552F6BFF), Color(0x112F6BFF)))
                        ) else Modifier
                    )
                    .clickable { vm.tab = tab },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = if (selected) Color.White else C.Dim, modifier = Modifier.size(du(5.6f)))
                Text(
                    tab.label,
                    color = if (selected) Color.White else C.Dim,
                    fontSize = su(3.1f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                if (selected) {
                    Spacer(Modifier.height(du(0.4f)))
                    Box(
                        Modifier
                            .width(du(9f))
                            .height(du(0.5f))
                            .background(C.Cyan)
                    )
                }
            }
        }
    }
}
