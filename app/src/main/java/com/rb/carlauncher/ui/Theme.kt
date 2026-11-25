package com.rb.carlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
    val Cyan = Color(0xFF18D8FF)
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

fun Modifier.panel(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(C.Panel1, C.Panel2)), shape)
    .border(
        1.5.dp,
        Brush.linearGradient(listOf(Color(0xFF2A7BFF), Color(0xFF1A2E7A), Color(0xFF7B4DFF))),
        shape
    )
