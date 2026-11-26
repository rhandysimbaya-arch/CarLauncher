package com.rb.carlauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.MapMode
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.ceil

/** "night mode": invert the standard OSM tiles and tint them navy-blue. */
private val NIGHT_FILTER = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            -0.55f, 0f, 0f, 0f, 150f,
            0f, -0.85f, 0f, 0f, 225f,
            0f, 0f, -1.15f, 0f, 300f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private class MapState {
    var night: Boolean? = null
    var etaText: String = ""
}

private fun bubble(ctx: Context, text: String): BitmapDrawable {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 38f
        typeface = Typeface.DEFAULT_BOLD
        color = android.graphics.Color.WHITE
    }
    val w = (p.measureText(text) + 44).toInt()
    val h = 70
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#1A5FD0") }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    c.drawRoundRect(2f, 2f, w - 2f, h - 2f, 20f, 20f, bg)
    c.drawRoundRect(2f, 2f, w - 2f, h - 2f, 20f, 20f, border)
    c.drawText(text, 22f, h / 2f + 13f, p)
    return BitmapDrawable(ctx.resources, bmp)
}

/**
 * OpenStreetMap that follows the GPS position (Top / Heading-up / 3D tilted views), draws the active route
 * (white casing + blue line), an ETA bubble on the route, a destination pin, and the blue car marker.
 * Day style by default; night style is the standard map darkened in-app.
 */
@Composable
fun NavMap(
    vm: CarViewModel,
    modifier: Modifier = Modifier,
    follow: Boolean = true,
    zoom: Double = 17.0,
    mode: MapMode = vm.mapMode,
    night: Boolean = vm.mapNight,
    onUserTouch: () -> Unit = {},
    onMapView: (MapView) -> Unit = {}
) {
    val ctx = LocalContext.current
    val touchCb = rememberUpdatedState(onUserTouch)
    val state = remember { MapState() }

    val casing = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.WHITE
            outlinePaint.strokeWidth = 26f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
    }
    val routeLine = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#2F7BFF")
            outlinePaint.strokeWidth = 16f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.isAntiAlias = true
        }
    }

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(14.5995, 120.9842))
            overlays.add(casing)
            overlays.add(routeLine)
            setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) touchCb.value()
                false
            }
        }
    }

    val destMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            isEnabled = false
        }.also { mapView.overlays.add(it) }
    }
    val etaMarker = remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setInfoWindow(null)
            isEnabled = false
        }.also { mapView.overlays.add(it) }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onMapView(mapView)
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    val tilt by animateFloatAsState(if (mode == MapMode.Drive3D) 58f else 0f, label = "tilt")
    LaunchedEffect(mode) {
        mapView.controller.setZoom(if (mode == MapMode.Drive3D) 18.0 else zoom)
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = tilt
                    val sc = 1f + tilt / 58f * 0.9f
                    scaleX = sc
                    scaleY = sc
                    cameraDistance = 14f * density
                },
            update = { mv ->
                if (state.night != night) {
                    state.night = night
                    mv.overlayManager.tilesOverlay.setColorFilter(if (night) NIGHT_FILTER else null)
                    casing.outlinePaint.color =
                        if (night) android.graphics.Color.parseColor("#0B1B44") else android.graphics.Color.WHITE
                }
                val nav = vm.nav
                if (nav != null) {
                    routeLine.setPoints(nav.route)
                    casing.setPoints(nav.route)
                    routeLine.isEnabled = true
                    casing.isEnabled = true
                    destMarker.position = nav.dest
                    destMarker.isEnabled = true
                    if (nav.route.size > 2) {
                        etaMarker.position = nav.route[nav.route.size / 2]
                        val min = ceil(nav.remainingSeconds / 60.0).toInt().coerceAtLeast(1)
                        val text = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
                        if (text != state.etaText) {
                            state.etaText = text
                            etaMarker.icon = bubble(ctx, text)
                        }
                        etaMarker.isEnabled = true
                    }
                } else {
                    routeLine.setPoints(emptyList<GeoPoint>())
                    casing.setPoints(emptyList<GeoPoint>())
                    routeLine.isEnabled = false
                    casing.isEnabled = false
                    destMarker.isEnabled = false
                    etaMarker.isEnabled = false
                }
                val loc = vm.location
                if (follow && loc != null) {
                    mv.controller.setCenter(loc)
                    mv.mapOrientation = if (mode == MapMode.Top) 0f else -vm.bearing
                }
                mv.invalidate()
            }
        )

        // Blue car marker (you), in a round badge like Google-style maps
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(du(12.5f))
        ) {
            val r = size.width / 2f
            val c = Offset(r, r)
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.0f, Offset(r, r + r * 0.06f))
            drawCircle(if (night) Color(0xFF0B1B44) else Color.White, r * 0.94f, c)
            drawCircle(
                if (night) C.Cyan else Color(0xFFBFD7FF), r * 0.94f, c, style = Stroke(r * 0.07f)
            )
            val ch = size.height * 0.74f
            val cw = ch * 0.55f
            translate((size.width - cw) / 2f, (size.height - ch) / 2f) { drawCar(cw, ch) }
        }
    }
}

private fun DrawScope.drawCar(w: Float, h: Float) {
    val bx = w * 0.12f
    val bw = w * 0.76f
    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(bx + w * 0.05f, h * 0.05f), Size(bw, h * 0.92f), CornerRadius(bw * 0.4f))
    val wheel = Color(0xFF071230)
    for (y in listOf(0.16f, 0.66f)) {
        drawRoundRect(wheel, Offset(bx - w * 0.07f, h * y), Size(w * 0.12f, h * 0.15f), CornerRadius(w * 0.04f))
        drawRoundRect(wheel, Offset(bx + bw - w * 0.05f, h * y), Size(w * 0.12f, h * 0.15f), CornerRadius(w * 0.04f))
    }
    drawRoundRect(Color(0xFF2F7BFF), Offset(bx - w * 0.07f, h * 0.30f), Size(w * 0.1f, h * 0.05f), CornerRadius(w * 0.03f))
    drawRoundRect(Color(0xFF2F7BFF), Offset(bx + bw - w * 0.03f, h * 0.30f), Size(w * 0.1f, h * 0.05f), CornerRadius(w * 0.03f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF5BB0FF), Color(0xFF1E6BFF), Color(0xFF0B3FB0)), startY = 0f, endY = h),
        Offset(bx, 0f), Size(bw, h), CornerRadius(bw * 0.36f)
    )
    drawRoundRect(Color(0xFFBFE3FF), Offset(bx, 0f), Size(bw, h), CornerRadius(bw * 0.36f), style = Stroke(w * 0.035f))
    val glass = Color(0xFF0A1F4D)
    drawRoundRect(glass, Offset(bx + bw * 0.1f, h * 0.24f), Size(bw * 0.8f, h * 0.15f), CornerRadius(bw * 0.2f))
    drawRoundRect(Color(0xFF3C8BFF), Offset(bx + bw * 0.12f, h * 0.41f), Size(bw * 0.76f, h * 0.21f), CornerRadius(bw * 0.12f))
    drawRoundRect(glass, Offset(bx + bw * 0.14f, h * 0.64f), Size(bw * 0.72f, h * 0.1f), CornerRadius(bw * 0.18f))
    drawCircle(C.Cyan.copy(alpha = 0.35f), w * 0.2f, Offset(bx + bw * 0.22f, h * 0.05f))
    drawCircle(C.Cyan.copy(alpha = 0.35f), w * 0.2f, Offset(bx + bw * 0.78f, h * 0.05f))
    drawRoundRect(Color(0xFFE8FBFF), Offset(bx + bw * 0.1f, h * 0.025f), Size(bw * 0.24f, h * 0.035f), CornerRadius(w * 0.03f))
    drawRoundRect(Color(0xFFE8FBFF), Offset(bx + bw * 0.66f, h * 0.025f), Size(bw * 0.24f, h * 0.035f), CornerRadius(w * 0.03f))
    drawRoundRect(Color(0xFFFF2D4D), Offset(bx + bw * 0.1f, h * 0.935f), Size(bw * 0.24f, h * 0.03f), CornerRadius(w * 0.03f))
    drawRoundRect(Color(0xFFFF2D4D), Offset(bx + bw * 0.66f, h * 0.935f), Size(bw * 0.24f, h * 0.03f), CornerRadius(w * 0.03f))
}
