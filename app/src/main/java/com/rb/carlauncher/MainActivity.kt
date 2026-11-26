package com.rb.carlauncher

import android.Manifest
import android.widget.Toast
import android.os.Looper
import android.os.Handler
import android.app.role.RoleManager
import android.provider.Settings
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rb.carlauncher.ui.BootScreen
import com.rb.carlauncher.ui.BottomNav
import com.rb.carlauncher.ui.HomeScreen
import com.rb.carlauncher.ui.C
import com.rb.carlauncher.ui.LocalNeon
import com.rb.carlauncher.ui.LocalU
import com.rb.carlauncher.ui.SoundScreen
import com.rb.carlauncher.ui.MapsScreen
import com.rb.carlauncher.ui.MusicScreen
import com.rb.carlauncher.ui.PhoneScreen
import com.rb.carlauncher.ui.SettingsScreen
import com.rb.carlauncher.ui.TopStatusBar
import com.rb.carlauncher.ui.du

class MainActivity : ComponentActivity() {

    private val vm: CarViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            vm.onPermissionsChanged()
            maybeAskDefault()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.refreshDefault()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // A launcher must never "exit" on back: go to Home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.tab = Tab.Home
            }
        })

        setContent {
            CarApp(
                vm,
                onRequestPermissions = { requestPermissions() },
                onSetDefaultLauncher = { requestDefaultLauncher() }
            )
        }
        vm.inSplit = if (Build.VERSION.SDK_INT >= 24) isInMultiWindowMode else false
        vm.onSplitNav = { startSplitNav(it) }
        vm.onCloseSplit = { closeSplit() }
        requestPermissions()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        vm.inSplit = isInMultiWindowMode
    }

    /** Dashboard on one half, navigation app on the other. The UI re-scales automatically. */
    private fun startSplitNav(pkg: String) {
        val nav = vm.nav
        val lat = nav?.dest?.latitude
        val lon = nav?.dest?.longitude
        if (vm.inSplit) {
            openExternalNav(pkg, lat, lon, adjacent = true)
        } else if (SplitService.instance != null) {
            SplitService.toggleSplit()
            Handler(Looper.getMainLooper()).postDelayed({ openExternalNav(pkg, lat, lon, adjacent = true) }, 1000)
        } else {
            Toast.makeText(this, "Turn on 'Car Launcher split screen' in Accessibility to use split screen", Toast.LENGTH_LONG).show()
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun closeSplit() {
        if (!SplitService.toggleSplit()) {
            Toast.makeText(this, "Drag the divider to the edge to close split screen", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.onResume()
        vm.loadApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed while we're already the launcher -> back to the dashboard.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.tab = Tab.Home
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** On first launch, ask once to make this the default home app. */
    private fun maybeAskDefault() {
        if (vm.shouldAskDefault()) requestDefaultLauncher()
    }

    private fun requestDefaultLauncher() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (!rm.isRoleHeld(RoleManager.ROLE_HOME)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    private fun requestPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 31) wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        val missing = wanted.filter { !vm.has(it) }
        if (missing.isEmpty()) {
            vm.onPermissionsChanged()
            maybeAskDefault()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun CarApp(vm: CarViewModel, onRequestPermissions: () -> Unit, onSetDefaultLauncher: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF030818), Color(0xFF010208))))
        ) {
            // scale by height, but also shrink when the window is narrow (split screen)
            val u = minOf(maxHeight.value / 100f, maxWidth.value / 150f)
            CompositionLocalProvider(LocalU provides u, LocalNeon provides (vm.theme == ThemeMode.Neon)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = du(1.5f), vertical = du(1.2f))
                ) {
                    TopStatusBar(vm, Modifier.weight(0.12f), onRequestPermissions)
                    if (vm.theme == ThemeMode.Neon) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, C.Cyan, Color(0xFF1E6BFF), C.Cyan, Color.Transparent)))
                        )
                    }
                    Box(
                        Modifier
                            .weight(0.72f)
                            .fillMaxWidth()
                            .padding(vertical = du(0.8f))
                    ) {
                        when (vm.tab) {
                            Tab.Home -> HomeScreen(vm)
                            Tab.Maps -> MapsScreen(vm)
                            Tab.Music -> MusicScreen(vm)
                            Tab.Sound -> SoundScreen(vm)
                            Tab.Phone -> PhoneScreen(vm)
                            Tab.Settings -> SettingsScreen(vm, onRequestPermissions, onSetDefaultLauncher)
                        }
                    }
                    BottomNav(vm, Modifier.weight(0.14f))
                }
                if (vm.bootActive) BootScreen(vm)
            }
        }
    }
}
