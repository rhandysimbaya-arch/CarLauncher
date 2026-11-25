package com.rb.carlauncher.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun Speedometer(speed: Float, gear: String, modifier: Modifier = Modifier, neon: Boolean = false) {
    val shown by animateFloatAsState(
        targetValue = speed.coerceIn(0f, 200f),
        animationSpec = tween(450),
        label = "speed"
    )
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val r = minOf(maxWidth, maxHeight) / 2

        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) / 2f * 0.94f
            val c = Offset(size.width / 2f, size.height / 2f)
            val stroke = radius * 0.085f
            val arcR = radius - stroke / 2f
            val topLeft = Offset(c.x - arcR, c.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)
            val start = 135f
            val sweep = 270f

            val brush = if (neon) {
                Brush.sweepGradient(
                    0.0f to Color(0xFFFF2D2D),
                    0.12f to Color(0xFFFF2D2D),
                    0.375f to Color(0xFF0A84FF),
                    0.55f to Color(0xFF2EA8FF),
                    0.72f to Color(0xFFCFE6FF),
                    0.90f to Color(0xFF6C8CFF),
                    0.95f to Color(0xFFFF3B3B),
                    1.0f to Color(0xFFFF2D2D),
                    center = c
                )
            } else {
                Brush.sweepGradient(
                    0.0f to C.Red,
                    0.12f to C.Red,
                    0.375f to C.Cyan,
                    0.62f to C.Blue,
                    0.86f to C.Purple,
                    1.0f to C.Red,
                    center = c
                )
            }

            if (neon) {
                drawCircle(C.Cyan.copy(alpha = 0.10f), radius * 0.995f, c, style = Stroke(stroke * 0.18f))
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFF06102C), Color(0xFF01030A)), center = c, radius = radius * 0.62f),
                    radius * 0.62f, c
                )
                drawCircle(C.Blue.copy(alpha = 0.45f), radius * 0.62f, c, style = Stroke(stroke * 0.12f))
            }

            // track
            drawArc(
                color = Color(0xFF101C48),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            val prog = sweep * shown / 200f
            if (prog > 0.5f) {
                // glow
                drawArc(
                    brush = brush,
                    startAngle = start,
                    sweepAngle = prog,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = 0.22f,
                    style = Stroke(stroke * 2.1f, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = brush,
                    startAngle = start,
                    sweepAngle = prog,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }

            // ticks
            val tickOuter = arcR - stroke * 0.9f
            for (i in 0..40) {
                val a = Math.toRadians((start + sweep * i / 40f).toDouble())
                val major = i % 4 == 0
                val len = if (major) stroke * 0.95f else stroke * 0.5f
                val p1 = Offset((c.x + tickOuter * cos(a)).toFloat(), (c.y + tickOuter * sin(a)).toFloat())
                val p2 = Offset(
                    (c.x + (tickOuter - len) * cos(a)).toFloat(),
                    (c.y + (tickOuter - len) * sin(a)).toFloat()
                )
                drawLine(
                    color = Color.White.copy(alpha = if (major) 0.9f else 0.45f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (major) stroke * 0.14f else stroke * 0.08f
                )
            }

            // numbers
            val paint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = radius * 0.105f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
            }
            val labelR = tickOuter - stroke * 0.95f - stroke * 1.2f
            drawIntoCanvas { canvas ->
                for (v in 0..200 step 20) {
                    val a = Math.toRadians((start + sweep * v / 200f).toDouble())
                    val x = (c.x + labelR * cos(a)).toFloat()
                    val y = (c.y + labelR * sin(a)).toFloat() + paint.textSize * 0.35f
                    canvas.nativeCanvas.drawText(v.toString(), x, y, paint)
                }
            }

            // needle
            val na = Math.toRadians((start + sweep * shown / 200f).toDouble())
            val n1 = Offset((c.x + radius * 0.50f * cos(na)).toFloat(), (c.y + radius * 0.50f * sin(na)).toFloat())
            val n2 = Offset((c.x + radius * 0.64f * cos(na)).toFloat(), (c.y + radius * 0.64f * sin(na)).toFloat())
            drawLine((if (neon) C.Cyan else Color(0xFF6FB6FF)).copy(alpha = 0.4f), n1, n2, strokeWidth = stroke * 0.7f, cap = StrokeCap.Round)
            drawLine(Color.White, n1, n2, strokeWidth = stroke * 0.24f, cap = StrokeCap.Round)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = -(r * 0.06f))
        ) {
            Text(
                text = speed.roundToInt().toString(),
                color = Color.White,
                fontSize = (r.value * 0.52f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "km/h",
                color = Color.White,
                fontSize = (r.value * 0.15f).sp
            )
        }

        val gearShape = RoundedCornerShape(topStart = r * 0.12f, topEnd = r * 0.12f, bottomStart = r * 0.04f, bottomEnd = r * 0.04f)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = r * 0.03f)
                .width(r * 0.44f)
                .height(r * 0.25f)
                .clip(gearShape)
                .background(Color(0xFF0A1C4E))
                .border(1.5.dp, C.Blue, gearShape),
            contentAlignment = Alignment.Center
        ) {
            Text(gear, color = Color.White, fontSize = (r.value * 0.19f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
