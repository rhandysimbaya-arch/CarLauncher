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
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.AudioDeviceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlin.math.roundToInt

class CarViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val prefs = ctx.getSharedPreferences("carlauncher", Context.MODE_PRIVATE)
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val fx = AudioFx()
    private var tap: SpectrumTap? = null
    private val welcome = WelcomeAudio(ctx)

    // ---- UI state -------------------------------------------------------------------------
    var tab by mutableStateOf(Tab.Home)

    var mapMode by mutableStateOf(
        MapMode.values().firstOrNull { it.name == prefs.getString("mapMode", "") } ?: MapMode.Heading
    )
        private set
    var mapNight by mutableStateOf(prefs.getBoolean("mapNight", false))
        private set
    var roadName by mutableStateOf("Unknown road")
        private set
    var isDefaultHome by mutableStateOf(false)
        private set
    var btNeedsPermission by mutableStateOf(false)
        private set

    // boot intro
    var bootAnim by mutableStateOf(prefs.getBoolean("bootAnim", true))
        private set
    var bootSound by mutableStateOf(prefs.getBoolean("bootSound", true))
        private set
    var bootActive by mutableStateOf(prefs.getBoolean("bootAnim", true))
        private set

    var theme by mutableStateOf(if (prefs.getString("theme", "neon") == "classic") ThemeMode.Classic else ThemeMode.Neon)
        private set

    var speedKmh by mutableFloatStateOf(0f)
        private set
    var bearing by mutableFloatStateOf(0f)
        private set
    var location by mutableStateOf<GeoPoint?>(null)
        private set
    var gpsFix by mutableStateOf(false)
        private set

    // trip computer
    var tripKm by mutableDoubleStateOf(prefs.getFloat("tripKm", 0f).toDouble())
        private set
    var tripMs by mutableLongStateOf(prefs.getLong("tripMs", 0L))
        private set
    var maxKmh by mutableFloatStateOf(prefs.getFloat("maxKmh", 0f))
        private set
    val avgKmh: Float get() = if (tripMs > 0) (tripKm / (tripMs / 3_600_000.0)).toFloat() else 0f

    var batteryPct by mutableIntStateOf(0)
        private set
    var batteryTemp by mutableFloatStateOf(Float.NaN)
        private set
    var ambientTemp by mutableFloatStateOf(Float.NaN)
        private set
    var wifiLevel by mutableIntStateOf(0)
        private set
    var wifiConnected by mutableStateOf(false)
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

    // ---- sound -----------------------------------------------------------------------------
    var fxEnabled by mutableStateOf(prefs.getBoolean("fxOn", true))
        private set
    var presetName by mutableStateOf(prefs.getString("preset", "Flat") ?: "Flat")
        private set
    var bandFreqs by mutableStateOf<List<Float>>(emptyList())
        private set
    var bandDb by mutableStateOf<List<Float>>(emptyList())
        private set
    var eqMinDb by mutableFloatStateOf(-15f)
        private set
    var eqMaxDb by mutableFloatStateOf(15f)
        private set
    var bassStrength by mutableIntStateOf(prefs.getInt("bass", 0))
        private set
    var boostDb by mutableFloatStateOf(prefs.getFloat("boost", 0f))
        private set
    var fxGlobal by mutableStateOf(false)
        private set
    var fxSessions by mutableIntStateOf(0)
        private set
    var volume by mutableIntStateOf(0)
        private set
    val volumeMax: Int = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    var spectrum by mutableStateOf(FloatArray(32))
        private set
    var levelDb by mutableFloatStateOf(-60f)
        private set
    var specLive by mutableStateOf(false)
        private set

    // ---- internals ------------------------------------------------------------------------
    private var monitor: MediaMonitor? = null
    private var gpsRegistered = false
    private var netRegistered = false
    private var lastGpsTime = 0L
    private var lastGpsLoc: Location? = null
    private var lastTripSave = 0L
    private var lastOffRouteCheck = 0L
    private var lastReroute = 0L
    private var lastRoadLoc: GeoPoint? = null
    private var lastRoadTime = 0L

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
        @Suppress("DEPRECATION")
        @SuppressLint("MissingPermission")
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == BluetoothDevice.ACTION_ACL_CONNECTED && btPermissionOk()) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val n = try { d?.name ?: d?.address } catch (_: SecurityException) { null }
                if (n != null) btName = n
            }
            main.postDelayed({ refreshBt() }, 1200)
        }
    }

    private val fxReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (id <= 0) return
            if (i.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) fx.open(id) else fx.close(id)
            pushFx()
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            if (e.values.isNotEmpty()) ambientTemp = e.values[0]
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            readSignal()
        }
    }

    init {
        ContextCompat.registerReceiver(
            ctx, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )?.let { applyBattery(it) }

        val btFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.bluetooth.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(ctx, btReceiver, btFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // media players announce their audio session here so effects can be attached to them
        val fxFilter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(ctx, fxReceiver, fxFilter, ContextCompat.RECEIVER_EXPORTED)

        try {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
                sm.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (_: Exception) {
        }

        val wifiFilter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(ctx, wifiReceiver, wifiFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        welcome.prepare()

        // no animation -> still greet by voice at start-up
        if (!bootActive && bootSound) welcome.play()

        initFx()
        refreshVolume()
        loadApps()
        startLocation()
        startSpectrum()

        viewModelScope.launch {
            while (isActive) {
                startLocation()
                startSpectrum()
                refreshBt()
                readSignal()
                checkMedia()
                refreshVolume()
                lookupRoad()
                if (gpsFix && SystemClock.elapsedRealtime() - lastGpsTime > 5000) {
                    gpsFix = false
                    speedKmh = 0f
                    lastGpsLoc = null
                }
                delay(5000)
            }
        }
    }

    fun onResume() {
        refreshDefault()
        fx.open(0)
        pushFx()
        readSignal()
        startLocation()
        startSpectrum()
        refreshBt()
        checkMedia()
        refreshVolume()
    }

    fun onPermissionsChanged() {
        permTick++
        startLocation()
        startSpectrum()
        refreshBt()
        readSignal()
    }

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    fun onBootStart() {
        if (bootSound) welcome.play()
    }

    fun finishBoot() {
        bootActive = false
    }

    fun replayBoot() {
        bootActive = true
    }

    fun toggleBootAnim() {
        bootAnim = !bootAnim
        prefs.edit().putBoolean("bootAnim", bootAnim).apply()
    }

    fun toggleBootSound() {
        bootSound = !bootSound
        prefs.edit().putBoolean("bootSound", bootSound).apply()
    }

    fun changeMapMode(m: MapMode) {
        mapMode = m
        prefs.edit().putString("mapMode", m.name).apply()
    }

    fun toggleMapNight() {
        mapNight = !mapNight
        prefs.edit().putBoolean("mapNight", mapNight).apply()
    }

    fun refreshDefault() {
        try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
            isDefaultHome = ri?.activityInfo?.packageName == ctx.packageName
        } catch (_: Exception) {
        }
    }

    /** True once, on first launch, if we are not yet the default home app. */
    fun shouldAskDefault(): Boolean {
        refreshDefault()
        if (isDefaultHome || prefs.getBoolean("askedHome", false)) return false
        prefs.edit().putBoolean("askedHome", true).apply()
        return true
    }

    private fun lookupRoad() {
        val loc = location ?: return
        if (nav != null || tab != Tab.Maps) return
        val now = SystemClock.elapsedRealtime()
        val last = lastRoadLoc
        if (now - lastRoadTime < 15000 || (last != null && last.distanceToAsDouble(loc) < 40.0)) return
        lastRoadTime = now
        lastRoadLoc = loc
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { NavRepository.reverse(loc.latitude, loc.longitude) }.getOrNull() }
            roadName = r ?: "Unknown road"
        }
    }

    fun toggleTheme() {
        theme = if (theme == ThemeMode.Neon) ThemeMode.Classic else ThemeMode.Neon
        prefs.edit().putString("theme", if (theme == ThemeMode.Neon) "neon" else "classic").apply()
    }

    // ---- location, speed & trip --------------------------------------------------------------
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
            val prevFix = lastGpsLoc
            var kmh = speedKmh
            val poorSpeed = Build.VERSION.SDK_INT >= 26 && l.hasSpeedAccuracy() && l.speedAccuracyMetersPerSecond > 3f
            if (!poorSpeed) {
                if (l.hasSpeed()) {
                    kmh = l.speed * 3.6f // Doppler speed from the GPS chip
                } else if (prevFix != null) {
                    val dt = (l.time - prevFix.time) / 1000f
                    if (dt in 0.3f..5f) kmh = prevFix.distanceTo(l) / dt * 3.6f
                }
            }
            val target = if (kmh < 1.8f) 0f else kmh
            // light smoothing: removes GPS jitter without visible lag
            speedKmh = if (target == 0f) 0f else if (speedKmh == 0f) target else speedKmh * 0.3f + target * 0.7f
            if (l.hasBearing() && kmh > 5f) bearing = l.bearing
            if (speedKmh > maxKmh) maxKmh = speedKmh
            updateTrip(l)
        }
        nav?.let { updateNav(it, p) }
    }

    private fun updateTrip(l: Location) {
        val prev = lastGpsLoc
        lastGpsLoc = l
        if (prev == null || speedKmh < 3f) return
        val dtMs = (l.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000L
        val d = prev.distanceTo(l)
        val accOk = !l.hasAccuracy() || l.accuracy < 40f
        if (dtMs in 200L..10_000L && d < 300f && accOk) {
            tripKm += d / 1000.0
            tripMs += dtMs
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastTripSave > 20_000) {
            lastTripSave = now
            saveTrip()
        }
    }

    private fun saveTrip() {
        prefs.edit().putFloat("tripKm", tripKm.toFloat()).putLong("tripMs", tripMs).putFloat("maxKmh", maxKmh).apply()
    }

    fun resetTrip() {
        tripKm = 0.0
        tripMs = 0L
        maxKmh = 0f
        saveTrip()
    }

    // ---- battery / temperature / signal / bluetooth ----------------------------------------
    private fun applyBattery(i: Intent) {
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) batteryPct = level * 100 / scale
        val t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (t != Int.MIN_VALUE) batteryTemp = t / 10f
    }

    @Suppress("DEPRECATION")
    private fun readSignal() {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            val connected = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI)?.isConnected == true &&
                info != null && info.networkId != -1
            wifiConnected = connected
            wifiLevel = if (connected) WifiManager.calculateSignalLevel(info.rssi, 5) else 0
        } catch (_: Throwable) {
            wifiConnected = false
            wifiLevel = 0
        }
    }

    private fun btPermissionOk() = Build.VERSION.SDK_INT < 31 || has(Manifest.permission.BLUETOOTH_CONNECT)

    // A2DP/HEADSET = phone side; 11 = A2DP sink, 16 = hands-free client (tablet / head-unit side)
    private val btProfiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET, 11, 16)

    @SuppressLint("MissingPermission")
    private fun refreshBt() {
        if (!btPermissionOk()) {
            btName = null
            btNeedsPermission = true
            return
        }
        btNeedsPermission = false
        try {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                btName = null
                return
            }
            probeBt(adapter, 0)
        } catch (e: Exception) {
            btName = null
        }
    }

    private fun probeBt(adapter: BluetoothAdapter, idx: Int) {
        if (idx >= btProfiles.size) {
            btName = fallbackBtName(adapter)
            return
        }
        queryProfile(adapter, btProfiles[idx]) { n ->
            if (n != null) btName = n else probeBt(adapter, idx + 1)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackBtName(adapter: BluetoothAdapter): String? {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                val d = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (d != null) return d.productName?.toString()?.ifBlank { null } ?: "Bluetooth audio"
            }
        } catch (_: Throwable) {
        }
        try {
            for (d in adapter.bondedDevices) {
                val connected = d.javaClass.getMethod("isConnected").invoke(d) as? Boolean
                if (connected == true) return d.name ?: d.address
            }
        } catch (_: Throwable) {
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun queryProfile(adapter: BluetoothAdapter, profile: Int, done: (String?) -> Unit) {
        var fired = false
        fun finish(n: String?) {
            if (fired) return
            fired = true
            done(n)
        }
        val ok = try {
            adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                    val name = try {
                        proxy.connectedDevices.firstOrNull()?.let { it.name ?: it.address ?: "Bluetooth device" }
                    } catch (e: Throwable) {
                        null
                    }
                    try { adapter.closeProfileProxy(p, proxy) } catch (_: Throwable) {}
                    main.post { finish(name) }
                }

                override fun onServiceDisconnected(p: Int) {}
            }, profile)
        } catch (e: Throwable) {
            false
        }
        if (!ok) finish(null) else main.postDelayed({ finish(null) }, 1500)
    }

    // ---- media -----------------------------------------------------------------------------
    private fun checkMedia() {
        if (!BuildConfig.MEDIA_ACCESS) return
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

    // Without the listener (lite build) fall back to system media keys: works with any player.
    private fun sendKey(code: Int) {
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Exception) {
        }
    }

    fun playPause() {
        val m = monitor
        if (m != null) m.playPause() else sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next() {
        val m = monitor
        if (m != null) m.next() else sendKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        val m = monitor
        if (m != null) m.previous() else sendKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekFraction(f: Float) {
        val d = media.durationMs
        if (d > 0) monitor?.seekTo((d * f.coerceIn(0f, 1f)).toLong())
    }

    fun musicActive(): Boolean = try {
        audio.isMusicActive
    } catch (_: Exception) {
        false
    }

    // ---- sound: equalizer, bass boost, volume booster ------------------------------------------
    private fun initFx() {
        fx.open(0)
        val info = fx.info()
        bandFreqs = info.freqs
        eqMinDb = info.minDb
        eqMaxDb = info.maxDb
        fxGlobal = info.globalOk
        val saved = prefs.getString("bands", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
        bandDb = if (saved != null && saved.size == bandFreqs.size) saved else presetBands(presetName)
        pushFx()
    }

    private fun presetBands(name: String): List<Float> {
        val p = EqPresets.find(name) ?: EqPresets.all[0]
        return EqPresets.forBands(p, bandFreqs).map { it.coerceIn(eqMinDb, eqMaxDb) }
    }

    private fun pushFx() {
        fx.update(fxEnabled, bandFreqs, bandDb, bassStrength, (boostDb * 100f).roundToInt())
        fxSessions = fx.count()
        fxGlobal = fx.globalActive()
        prefs.edit()
            .putBoolean("fxOn", fxEnabled)
            .putString("preset", presetName)
            .putString("bands", bandDb.joinToString(","))
            .putInt("bass", bassStrength)
            .putFloat("boost", boostDb)
            .apply()
    }

    fun switchFx(on: Boolean) {
        fxEnabled = on
        pushFx()
    }

    fun selectPreset(name: String) {
        val p = EqPresets.find(name) ?: return
        presetName = name
        bandDb = presetBands(name)
        bassStrength = p.bass
        pushFx()
    }

    fun setBand(index: Int, db: Float) {
        if (index !in bandDb.indices) return
        bandDb = bandDb.toMutableList().also { it[index] = db.coerceIn(eqMinDb, eqMaxDb) }
        presetName = "Custom"
        pushFx()
    }

    fun setBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        pushFx()
    }

    fun setBoost(db: Float) {
        boostDb = db.coerceIn(0f, 12f)
        pushFx()
    }

    fun refreshVolume() {
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    fun changeVolume(v: Int) {
        val x = v.coerceIn(0, volumeMax)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, x, 0)
        volume = x
    }

    /** dB shown in the "Sound Level" meter: real output level if we can read it, else the volume setting. */
    fun soundLevelDb(): Float =
        if (specLive) levelDb else -3f - (1f - volume.toFloat() / volumeMax) * 40f

    private fun startSpectrum() {
        if (tap != null || !has(Manifest.permission.RECORD_AUDIO)) return
        val t = SpectrumTap { bars, lvl ->
            spectrum = bars
            levelDb = lvl
        }
        if (t.start()) {
            tap = t
            specLive = true
        }
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
        a.steps.getOrNull((a.stepIndex - 1).coerceAtLeast(0))?.name?.takeIf { it.isNotBlank() }?.let { roadName = it }
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
        saveTrip()
        try {
            locationManager.removeUpdates(locListener)
            ctx.unregisterReceiver(batteryReceiver)
            ctx.unregisterReceiver(btReceiver)
            ctx.unregisterReceiver(fxReceiver)
            ctx.unregisterReceiver(wifiReceiver)
            (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(sensorListener)
        } catch (_: Exception) {
        }
        tap?.stop()
        welcome.release()
        fx.closeAll()
        monitor?.stop()
    }
}
