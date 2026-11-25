package com.rb.carlauncher.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rb.carlauncher.CarViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File


/**
 * Dark, blue-tinted OpenStreetMap that follows the GPS position (heading-up),
 * draws the active route and a destination pin, with the car chevron in the middle.
 */
@Composable
fun NavMap(
    vm: CarViewModel,
    modifier: Modifier = Modifier,
    follow: Boolean = true,
    zoom: Double = 17.0,
    onUserTouch: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val touchCb = rememberUpdatedState(onUserTouch)

    val routeLine = remember {
        Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#2F8CFF")
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
            // "night mode": invert the standard OSM tiles and tint them navy-blue
            val cm = ColorMatrix(
                floatArrayOf(
                    -0.55f, 0f, 0f, 0f, 150f,
                    0f, -0.85f, 0f, 0f, 225f,
                    0f, 0f, -1.15f, 0f, 300f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(cm))
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

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { mv ->
                val nav = vm.nav
                if (nav != null) {
                    routeLine.setPoints(nav.route)
                    routeLine.isEnabled = true
                    destMarker.position = nav.dest
                    destMarker.isEnabled = true
                } else {
                    routeLine.setPoints(emptyList<GeoPoint>())
                    routeLine.isEnabled = false
                    destMarker.isEnabled = false
                }
                val loc = vm.location
                if (follow && loc != null) {
                    mv.controller.setCenter(loc)
                    mv.mapOrientation = -vm.bearing
                }
                mv.invalidate()
            }
        )

        // Car chevron
        Canvas(
            Modifier
                .align(Alignment.Center)
                .size(du(8f))
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w / 2f, 0f)
                lineTo(w, h)
                lineTo(w / 2f, h * 0.72f)
                lineTo(0f, h)
                close()
            }
            drawPath(path, Color(0xFF0C3D8F).copy(alpha = 0.85f))
            drawPath(path, C.Cyan, style = Stroke(width = w * 0.07f))
        }
    }
}
