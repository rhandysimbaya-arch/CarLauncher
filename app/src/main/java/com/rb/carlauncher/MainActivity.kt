package com.rb.carlauncher

import android.Manifest
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

        setContent { CarApp(vm, onRequestPermissions = { requestPermissions() }) }
        requestPermissions()
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

    private fun requestPermissions() {
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= 31) wanted.add(Manifest.permission.BLUETOOTH_CONNECT)
        val missing = wanted.filter { !vm.has(it) }
        if (missing.isEmpty()) vm.onPermissionsChanged() else permissionLauncher.launch(missing.toTypedArray())
    }
}

@Composable
fun CarApp(vm: CarViewModel, onRequestPermissions: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF030818), Color(0xFF010208))))
        ) {
            val u = maxHeight.value / 100f
            CompositionLocalProvider(LocalU provides u, LocalNeon provides (vm.theme == ThemeMode.Neon)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = du(1.5f), vertical = du(1.2f))
                ) {
                    TopStatusBar(vm, Modifier.weight(0.12f))
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
                            Tab.Settings -> SettingsScreen(vm, onRequestPermissions)
                        }
                    }
                    BottomNav(vm, Modifier.weight(0.14f))
                }
                if (vm.bootActive) BootScreen(vm)
            }
        }
    }
}
