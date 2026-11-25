package com.rb.carlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.WelcomeAudio

/** Neon intro: shock-wave ring, gauge sweeps 0 -> 200 -> 0 with the speed bar, title and greeting fade in. Tap to skip. */
@Composable
fun BootScreen(vm: CarViewModel) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        vm.onBootStart()
        p.animateTo(1f, tween(4300, easing = LinearEasing))
        vm.finishBoot()
    }
    val t = p.value * 4.3f // seconds

    fun ramp(from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

    val gauge = when {
        t < 0.6f -> 0f
        t < 1.8f -> ramp(0.6f, 1.8f)
        t < 2.7f -> 1f - ramp(1.8f, 2.7f)
        else -> 0f
    }
    val gaugeAlpha = ramp(0.3f, 0.9f)
    val titleAlpha = ramp(1.0f, 1.8f)
    val greetAlpha = ramp(2.4f, 3.1f)
    val outAlpha = 1f - ramp(3.9f, 4.3f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = outAlpha }
            .background(Brush.radialGradient(listOf(Color(0xFF06143A), Color.Black)))
            .pointerInput(Unit) { detectTapGestures { vm.finishBoot() } },
        contentAlignment = Alignment.Center
    ) {
        // expanding shock-wave rings
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.maxDimension * 0.7f
            for (k in 0..2) {
                val f = ramp(0.05f + k * 0.25f, 1.25f + k * 0.25f)
                if (f > 0f && f < 1f) {
                    val col = if (k == 1) C.Blue else C.Cyan
                    drawCircle(col.copy(alpha = (1f - f) * 0.55f), maxR * f, c, style = Stroke(6f * (1f - f) + 1.5f))
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = du(6f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.graphicsLayer { alpha = gaugeAlpha }) {
                Speedometer(
                    speed = gauge * 200f,
                    gear = if (gauge > 0.02f) "D" else "P",
                    neon = true,
                    modifier = Modifier.size(du(56f))
                )
            }
            Spacer(Modifier.height(du(1f)))
            Box(Modifier.fillMaxWidth(0.42f).graphicsLayer { alpha = gaugeAlpha }) {
                SpeedBarMeter(gauge * 200f, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(du(2f)))
            Text(
                "CAR LAUNCHER",
                color = Color.White,
                fontSize = su(6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = (titleAlpha * 10f + 2f).sp,
                modifier = Modifier.graphicsLayer { alpha = titleAlpha }
            )
            Spacer(Modifier.height(du(0.8f)))
            Text(
                WelcomeAudio.PHRASE,
                color = C.Cyan,
                fontSize = su(3.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = greetAlpha }
            )
        }
    }
}
