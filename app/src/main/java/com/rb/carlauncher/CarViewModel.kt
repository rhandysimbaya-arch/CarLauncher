package com.rb.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

class CarViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val main = Handler(Looper.getMainLooper())

    // ---- UI state -------------------------------------------------------------------------
    var tab by mutableStateOf(Tab.Home)

    var speedKmh by mutableFloatStateOf(0f)
        private set
    var bearing by mutableFloatStateOf(0f)
        private set
    var location by mutableStateOf<GeoPoint?>(null)
        private set
    var gpsFix by mutableStateOf(false)
        private set

    var batteryPct by mutableIntStateOf(0)
        private set
    var batteryTemp by mutableFloatStateOf(Float.NaN)
        private set
    var ambientTemp by mutableFloatStateOf(Float.NaN)
        private set
    var signalLevel by mutableIntStateOf(0)
        private set
    var btName by mutableStateOf<String?>(null)
        private set

    var nav by mutableStateOf<NavState?>(null)
        private set
    var results by mutableStateOf<List<PlaceResult>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    var media by mutableStateOf(MediaInfo())
        private set
    var mediaAccess by mutableStateOf(false)
        private set

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var musicApps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var permTick by mutableIntStateOf(0)
        private set

    // ---- internals ------------------------------------------------------------------------
    private var monitor: MediaMonitor? = null
    private var gpsRegistered = false
    private var netRegistered = false
    private var lastGpsTime = 0L
    private var lastOffRouteCheck = 0L
    private var lastReroute = 0L

    private val locationManager get() = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            handleLocation(l)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            applyBattery(i)
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            main.postDelayed({ refreshBt() }, 1200)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.values.isNotEmpty()) ambientTemp = e.values[0]
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    init {
        ContextCompat.registerReceiver(
            ctx, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )?.let { applyBattery(it) }

        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, btReceiver, btFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        try {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (_: Exception) {
        }

        loadApps()
        startLocation()

        viewModelScope.launch {
            while (isActive) {
                startLocation()
                refreshBt()
                readSignal()
                checkMedia()
                if (gpsFix && SystemClock.elapsedRealtime() - lastGpsTime > 5000) {
                    gpsFix = false
                    speedKmh = 0f
                }
                delay(5000)
            }
        }
    }

    fun onResume() {
        startLocation()
        refreshBt()
        checkMedia()
    }

    fun onPermissionsChanged() {
        permTick++
        startLocation()
        refreshBt()
        readSignal()
    }

    fun has(permission: String) = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ---- location & speed ------------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!has(Manifest.permission.ACCESS_FINE_LOCATION)) return
        try {
            val m = locationManager
            if (!gpsRegistered && m.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, locListener, Looper.getMainLooper())
                gpsRegistered = true
            }
            if (!netRegistered && m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, locListener, Looper.getMainLooper())
                netRegistered = true
            }
            if (location == null) {
                val last = m.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (last != null) location = GeoPoint(last.latitude, last.longitude)
            }
        } catch (_: Exception) {
        }
    }

    private fun handleLocation(l: Location) {
        val isGps = l.provider == LocationManager.GPS_PROVIDER
        val now = SystemClock.elapsedRealtime()
        if (isGps) {
            lastGpsTime = now
            gpsFix = true
        } else if (now - lastGpsTime < 15000) {
            return
        }
        val p = GeoPoint(l.latitude, l.longitude)
        location = p
        if (isGps) {
            val kmh = if (l.hasSpeed()) l.speed * 3.6f else 0f
            speedKmh = if (kmh < 2f) 0f else kmh
            if (l.hasBearing() && kmh > 5f) bearing = l.bearing
        }
        nav?.let { updateNav(it, p) }
    }

    // ---- battery / temperature / signal / bluetooth ----------------------------------------
    private fun applyBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (t != Int.MIN_VALUE) batteryTemp = t / 10f
    }

    private fun readSignal() {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val s = tm.signalStrength
                if (s != null) signalLevel = s.level
            }
        } catch (_: Throwable) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshBt() {
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            btName = null
            return
        }
        try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                btName = null
                return
            }
            queryProfile(adapter, BluetoothProfile.A2DP) { n ->
                if (n != null) {
                    btName = n
                } else {
                    queryProfile(adapter, BluetoothProfile.HEADSET) { n2 -> btName = n2 }
                }
            }
        } catch (e: Exception) {
            btName = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun queryProfile(adapter: BluetoothAdapter, profile: Int, done: (String?) -> Unit) {
        val ok = adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                val name = try {
                    proxy.connectedDevices.firstOrNull()?.let { it.name ?: it.address ?: "Bluetooth device" }
                } catch (e: SecurityException) {
                    null
                }
                adapter.closeProfileProxy(p, proxy)
                main.post { done(name) }
            }

            override fun onServiceDisconnected(p: Int) {}
        }, profile)
        if (!ok) done(null)
    }

    // ---- media -----------------------------------------------------------------------------
    private fun checkMedia() {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        mediaAccess = enabled
        if (enabled && monitor == null) {
            monitor = MediaMonitor(ctx) { media = it }.also { it.start() }
        } else if (enabled) {
            monitor?.refresh()
        } else if (monitor != null) {
            monitor?.stop()
            monitor = null
            media = MediaInfo()
        }
    }

    fun playPause() {
        monitor?.playPause()
    }

    fun next() {
        monitor?.next()
    }

    fun previous() {
        monitor?.previous()
    }

    fun seekFraction(f: Float) {
        val d = media.durationMs
        if (d > 0) monitor?.seekTo((d * f.coerceIn(0f, 1f)).toLong())
    }

    // ---- navigation ------------------------------------------------------------------------
    fun search(query: String) {
        if (query.isBlank()) return
        busy = true
        message = null
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { NavRepository.search(query) }.getOrNull()
            }
            busy = false
            if (list == null) {
                message = "Search failed - check your internet connection"
                results = emptyList()
            } else {
                results = list
                if (list.isEmpty()) message = "No places found"
            }
        }
    }

    fun startRoute(place: PlaceResult) {
        val from = location
        if (from == null) {
            message = "Waiting for GPS location..."
            return
        }
        busy = true
        results = emptyList()
        message = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { NavRepository.route(from, GeoPoint(place.lat, place.lon), place.short) }.getOrNull()
            }
            busy = false
            if (r == null) message = "Couldn't find a driving route" else nav = r
        }
    }

    fun cancelNav() {
        nav = null
        results = emptyList()
        message = null
    }

    fun clearResults() {
        results = emptyList()
        message = null
    }

    private fun updateNav(n: NavState, p: GeoPoint) {
        val a = NavRepository.advance(n, p)
        if (a.stepIndex >= a.steps.size - 1 && a.distToNext < 30.0) {
            nav = null
            message = "You have arrived"
            return
        }
        nav = a
        val now = SystemClock.elapsedRealtime()
        if (now - lastOffRouteCheck > 3000) {
            lastOffRouteCheck = now
            val off = NavRepository.distanceToRoute(a.route, p)
            if (off > 120.0 && now - lastReroute > 15000) {
                lastReroute = now
                reroute(a, p)
            }
        }
    }

    private fun reroute(a: NavState, p: GeoPoint) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { NavRepository.route(p, a.dest, a.destName) }.getOrNull()
            }
            if (r != null && nav != null) nav = r
        }
    }

    // ---- apps ------------------------------------------------------------------------------
    fun loadApps() {
        viewModelScope.launch {
            val (all, music) = withContext(Dispatchers.Default) {
                val pm = ctx.packageManager
                fun query(category: String?): List<AppEntry> {
                    val i = Intent(Intent.ACTION_MAIN)
                    i.addCategory(category ?: Intent.CATEGORY_LAUNCHER)
                    return pm.queryIntentActivities(i, 0)
                        .filter { it.activityInfo.packageName != ctx.packageName }
                        .distinctBy { it.activityInfo.packageName }
                        .map { ri ->
                            AppEntry(
                                ri.loadLabel(pm).toString(),
                                ri.activityInfo.packageName,
                                ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                }
                Pair(query(null), query(Intent.CATEGORY_APP_MUSIC))
            }
            apps = all
            musicApps = music
        }
    }

    fun launchApp(pkg: String) {
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.safeStart(it) }
    }

    override fun onCleared() {
        try {
            locationManager.removeUpdates(locListener)
            ctx.unregisterReceiver(batteryReceiver)
            ctx.unregisterReceiver(btReceiver)
            (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(sensorListener)
        } catch (_: Exception) {
        }
        monitor?.stop()
    }
}
