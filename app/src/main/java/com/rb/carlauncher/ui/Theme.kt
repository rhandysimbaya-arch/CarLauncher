package com.rb.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Panel1 = Color(0xFF0A1544)
    val Panel2 = Color(0xFF050B24)
    val Cyan = Color(0xFF14E0FF)
    val Blue = Color(0xFF2F6BFF)
    val Purple = Color(0xFF8A3DFF)
    val Red = Color(0xFFFF2D55)
    val Green = Color(0xFF0B7D3B)
    val Dim = Color(0xFFA9BCE6)
    val Dark = Color(0xFF040918)
}

/** 1% of the screen height, in dp. Everything is sized from this so the layout scales to any screen. */
val LocalU = compositionLocalOf { 8f }

@Composable
fun du(p: Float): Dp = (LocalU.current * p).dp

@Composable
fun su(p: Float): TextUnit = (LocalU.current * p).sp

val LocalNeon = compositionLocalOf { false }

fun Modifier.panel(shape: Shape): Modifier = composed {
    if (LocalNeon.current) {
        this
            .drawBehind {
                val o = shape.createOutline(size, layoutDirection, this)
                val rr = (o as? Outline.Rounded)?.roundRect
                val corner = rr?.topLeftCornerRadius?.x ?: 0f
                fun glow(color: Color, w: Float) {
                    drawRoundRect(color, Offset.Zero, size, CornerRadius(corner), style = Stroke(w))
                }
                glow(C.Cyan.copy(alpha = 0.05f), 30f)
                glow(C.Cyan.copy(alpha = 0.09f), 18f)
                glow(C.Blue.copy(alpha = 0.22f), 8f)
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF04102E), Color(0xFF020512))), shape)
            .border(
                2.dp,
                Brush.linearGradient(listOf(C.Cyan, Color(0xFF1E6BFF), C.Cyan)),
                shape
            )
    } else {
        this
            .clip(shape)
            .background(Brush.verticalGradient(listOf(C.Panel1, C.Panel2)), shape)
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(Color(0xFF2A7BFF), Color(0xFF1A2E7A), Color(0xFF7B4DFF))),
                shape
            )
    }
}
