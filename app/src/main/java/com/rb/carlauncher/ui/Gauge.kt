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
        animationSpec = tween(160),
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

            // outer bezel
            drawCircle(
                Brush.sweepGradient(
                    listOf(Color(0xFF1B2A66), Color(0xFF5E7BFF), Color(0xFF0B1235), Color(0xFF3A52B8), Color(0xFF1B2A66)),
                    center = c
                ),
                radius, c, style = Stroke(radius * 0.016f)
            )
            drawCircle(Color.White.copy(alpha = 0.06f), radius * 0.965f, c, style = Stroke(radius * 0.01f))

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
                    color = if (i / 40f <= shown / 200f) (if (neon) C.Cyan else Color.White).copy(alpha = if (major) 1f else 0.8f)
                    else Color.White.copy(alpha = if (major) 0.55f else 0.25f),
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

            // glowing tip at the end of the progress arc
            if (prog > 0.5f) {
                val ta = Math.toRadians((start + prog).toDouble())
                val tp = Offset((c.x + arcR * cos(ta)).toFloat(), (c.y + arcR * sin(ta)).toFloat())
                drawCircle(Color.White.copy(alpha = 0.25f), stroke * 1.25f, tp)
                drawCircle(Color.White, stroke * 0.42f, tp)
            }

            // tapered needle pointing at the current speed
            val na = Math.toRadians((start + sweep * shown / 200f).toDouble())
            val dirX = cos(na).toFloat()
            val dirY = sin(na).toFloat()
            val perpX = -dirY
            val perpY = dirX
            val r0 = radius * 0.50f
            val r1 = radius * 0.66f
            val w0 = stroke * 0.30f
            val tip = Offset(c.x + dirX * r1, c.y + dirY * r1)
            val b1 = Offset(c.x + dirX * r0 + perpX * w0, c.y + dirY * r0 + perpY * w0)
            val b2 = Offset(c.x + dirX * r0 - perpX * w0, c.y + dirY * r0 - perpY * w0)
            val needle = androidx.compose.ui.graphics.Path().apply {
                moveTo(b1.x, b1.y); lineTo(tip.x, tip.y); lineTo(b2.x, b2.y); close()
            }
            drawLine((if (neon) C.Cyan else Color(0xFF6FB6FF)).copy(alpha = 0.35f),
                Offset(c.x + dirX * r0, c.y + dirY * r0), tip, strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
            drawPath(needle, Color.White)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (r * 0.02f))
        ) {
            Text(
                text = speed.roundToInt().toString(),
                color = Color.White,
                fontSize = (r.value * 0.52f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic
            )
            Text(
                text = "KM/H",
                color = if (neon) C.Cyan else Color(0xFF8FB8FF),
                fontSize = (r.value * 0.13f).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
        }
    }
}
