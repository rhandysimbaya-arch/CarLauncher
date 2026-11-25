package com.rb.carlauncher.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Streetview
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rb.carlauncher.BuildConfig
import com.rb.carlauncher.CarViewModel
import com.rb.carlauncher.MapMode
import com.rb.carlauncher.ThemeMode
import com.rb.carlauncher.safeStart

private fun Modifier.card(): Modifier = this.panel(RoundedCornerShape(24.dp))

// ---------------------------------------------------------------------------------------------
// Maps
// ---------------------------------------------------------------------------------------------
@Composable
fun MapsScreen(vm: CarViewModel) {
    val ctx = LocalContext.current
    var follow by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(du(4f))

    Box(Modifier.fillMaxSize().clip(shape).border(1.5.dp, C.Blue.copy(alpha = 0.6f), shape)) {
        NavMap(vm, Modifier.fillMaxSize(), follow = follow, zoom = 17.0, onUserTouch = { follow = false })

        // search + results (top-left)
        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(du(1.6f))
                .fillMaxWidth(0.46f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(du(2f)))
                    .background(Color(0xF0050A1C))
                    .border(1.dp, Color(0x772F6BFF), RoundedCornerShape(du(2f)))
                    .padding(horizontal = du(1.6f), vertical = du(1.4f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = C.Cyan, modifier = Modifier.size(du(5f)))
                Spacer(Modifier.width(du(1.2f)))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search destination", color = C.Dim, fontSize = su(3.6f))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = su(3.8f)),
                        cursorBrush = SolidColor(C.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            vm.search(query)
                        }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close, null, tint = Color.White,
                        modifier = Modifier
                            .size(du(4.6f))
                            .clickable { query = ""; vm.clearResults() }
                    )
                }
            }

            val status = when {
                vm.busy -> "Working..."
                else -> vm.message
            }
            if (status != null) {
                Spacer(Modifier.height(du(1f)))
                Text(
                    status, color = Color.White, fontSize = su(3.2f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(du(1.4f)))
                        .background(Color(0xE0050A1C))
                        .padding(horizontal = du(1.6f), vertical = du(0.9f))
                )
            }

            if (vm.results.isNotEmpty()) {
                Spacer(Modifier.height(du(1f)))
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(du(2f)))
                        .background(Color(0xF0050A1C))
                ) {
                    items(vm.results) { place ->
                        Text(
                            place.name,
                            color = Color.White,
                            fontSize = su(3.3f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboard?.hide()
                                    follow = true
                                    vm.startRoute(place)
                                }
                                .padding(horizontal = du(1.8f), vertical = du(1.6f))
                        )
                    }
                }
            }
        }

        // maneuver banner (top-right)
        if (vm.nav != null) {
            NavBanner(
                vm,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(du(1.6f))
                    .fillMaxWidth(0.4f)
            )
        }

        EtaBar(
            vm,
            Modifier
                .align(Alignment.BottomStart)
                .padding(du(1.6f))
                .fillMaxWidth(0.4f)
        )

        // view angle: Top / Heading / 3D + Street View
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = du(1.6f)),
            verticalArrangement = Arrangement.spacedBy(du(0.8f))
        ) {
            MapMode.values().forEach { m ->
                val sel = vm.mapMode == m
                Box(
                    Modifier
                        .width(du(13f))
                        .clip(RoundedCornerShape(du(1.6f)))
                        .background(if (sel) C.Cyan else Color(0xEE050A1C))
                        .border(1.dp, C.Blue, RoundedCornerShape(du(1.6f)))
                        .clickable { vm.mapMode = m; follow = true }
                        .padding(vertical = du(1.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.label,
                        color = if (sel) Color(0xFF02060F) else Color.White,
                        fontSize = su(3.2f), fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                Modifier
                    .width(du(13f))
                    .clip(RoundedCornerShape(du(1.6f)))
                    .background(Color(0xEE050A1C))
                    .border(1.dp, C.Blue, RoundedCornerShape(du(1.6f)))
                    .clickable {
                        val loc = vm.location
                        if (loc != null) {
                            val app = Intent(Intent.ACTION_VIEW, Uri.parse("google.streetview:cbll=${loc.latitude},${loc.longitude}"))
                                .setPackage("com.google.android.apps.maps")
                            val web = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${loc.latitude},${loc.longitude}")
                            )
                            ctx.safeStart(if (app.resolveActivity(ctx.packageManager) != null) app else web)
                        }
                    }
                    .padding(vertical = du(1.2f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Streetview, null, tint = C.Cyan, modifier = Modifier.size(du(3.8f)))
                Spacer(Modifier.width(du(0.6f)))
                Text("Street", color = Color.White, fontSize = su(3.0f), fontWeight = FontWeight.Bold)
            }
        }

        // recenter
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(du(1.6f))
                .size(du(10f))
                .clip(CircleShape)
                .background(Color(0xEE050A1C))
                .border(1.dp, C.Blue, CircleShape)
                .clickable { follow = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.MyLocation, null,
                tint = if (follow) C.Cyan else Color.White,
                modifier = Modifier.size(du(5.6f))
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Music
// ---------------------------------------------------------------------------------------------
@Composable
fun MusicScreen(vm: CarViewModel) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(du(1.8f))) {
        MediaCard(vm, Modifier.weight(0.5f).fillMaxHeight())
        Column(
            Modifier
                .weight(0.5f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(2f))
        ) {
            Text("Music apps", color = Color.White, fontSize = su(4.6f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(du(1.5f)))
            if (BuildConfig.MEDIA_ACCESS && !vm.mediaAccess) {
                Text(
                    "Allow media access so the launcher can show and control what's playing.",
                    color = C.Dim, fontSize = su(3.2f)
                )
                Spacer(Modifier.height(du(1f)))
                PillButton("Allow media access") {
                    ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Spacer(Modifier.height(du(1.5f)))
            }
            val list = if (vm.musicApps.isNotEmpty()) vm.musicApps else vm.apps
            LazyVerticalGrid(
                columns = GridCells.Adaptive(du(15f)),
                modifier = Modifier.fillMaxSize()
            ) {
                items(list) { app -> AppTile(app.label, app.icon) { vm.launchApp(app.pkg) } }
            }
        }
    }
}

@Composable
private fun AppTile(label: String, icon: androidx.compose.ui.graphics.ImageBitmap, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(du(0.6f))
            .clip(RoundedCornerShape(du(1.6f)))
            .clickable(onClick = onClick)
            .padding(du(1f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(icon, label, Modifier.size(du(9f)))
        Spacer(Modifier.height(du(0.6f)))
        Text(
            label, color = Color.White, fontSize = su(2.8f), maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(C.Blue, C.Purple)))
            .clickable(onClick = onClick)
            .padding(horizontal = du(3f), vertical = du(1.4f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = su(3.4f), fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Phone
// ---------------------------------------------------------------------------------------------
@Composable
fun PhoneScreen(vm: CarViewModel) {
    val ctx = LocalContext.current
    var number by remember { mutableStateOf("") }
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#")
    )
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(du(1.8f))) {
        Column(
            Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(2f))
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (number.isEmpty()) "Enter number" else number,
                    color = if (number.isEmpty()) C.Dim else Color.White,
                    fontSize = su(6f), fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.Backspace, null, tint = Color.White,
                    modifier = Modifier
                        .size(du(6f))
                        .clickable { if (number.isNotEmpty()) number = number.dropLast(1) }
                )
            }
            Spacer(Modifier.height(du(1.5f)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(du(1f))) {
                keys.forEach { row ->
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(du(1f))
                    ) {
                        row.forEach { k ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(du(2f)))
                                    .background(Color(0xFF0E1C52))
                                    .clickable { number += k },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(k, color = Color.White, fontSize = su(5.6f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(2f)),
            verticalArrangement = Arrangement.spacedBy(du(1.6f))
        ) {
            BigAction("Call", Icons.Filled.Call, Brush.horizontalGradient(listOf(Color(0xFF0FA958), Color(0xFF0B7D3B)))) {
                if (number.isNotEmpty()) {
                    ctx.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
                }
            }
            BigAction("Recents", Icons.Filled.History, Brush.horizontalGradient(listOf(C.Blue, Color(0xFF1B3FA8)))) {
                ctx.safeStart(Intent(Intent.ACTION_VIEW).apply { type = CallLog.Calls.CONTENT_TYPE })
            }
            BigAction("Contacts", Icons.Filled.People, Brush.horizontalGradient(listOf(C.Purple, Color(0xFF4B22A8)))) {
                ctx.safeStart(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }
        }
    }
}

@Composable
private fun BigAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    brush: Brush,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(du(14f))
            .clip(RoundedCornerShape(du(2.4f)))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = du(3f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(du(6.4f)))
        Spacer(Modifier.width(du(2f)))
        Text(label, color = Color.White, fontSize = su(4.6f), fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Settings + all apps
// ---------------------------------------------------------------------------------------------
@Composable
fun SettingsScreen(vm: CarViewModel, onRequestPermissions: () -> Unit) {
    val ctx = LocalContext.current
    // re-read permission state when it changes
    @Suppress("UNUSED_VARIABLE") val tick = vm.permTick
    val locOk = vm.has(android.Manifest.permission.ACCESS_FINE_LOCATION)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(du(1.8f))) {
        LazyColumn(
            Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(1.6f)),
            verticalArrangement = Arrangement.spacedBy(du(1f))
        ) {
            item { SettingRow("Permissions", if (locOk) "Location granted" else "Grant location, Bluetooth, phone") { onRequestPermissions() } }
            if (BuildConfig.MEDIA_ACCESS) {
                item {
                    SettingRow("Media access", if (vm.mediaAccess) "Enabled" else "Needed for music info") {
                        ctx.safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }
            item {
                SettingRow("Default launcher", "Set Car Launcher as home app") {
                    ctx.safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                }
            }
            item {
                SettingRow("Theme", if (vm.theme == ThemeMode.Neon) "Neon Drive (tap for Classic)" else "Classic (tap for Neon Drive)") {
                    vm.toggleTheme()
                }
            }
            item { SettingRow("Bluetooth", "Pair and connect devices") { ctx.safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
            item { SettingRow("Wi-Fi", "Networks") { ctx.safeStart(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
            item { SettingRow("Display", "Brightness and more") { ctx.safeStart(Intent(Settings.ACTION_DISPLAY_SETTINGS)) } }
            item { SettingRow("Sound", "Volume") { ctx.safeStart(Intent(Settings.ACTION_SOUND_SETTINGS)) } }
            item { SettingRow("System settings", "All Android settings") { ctx.safeStart(Intent(Settings.ACTION_SETTINGS)) } }
        }
        Column(
            Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .panel(RoundedCornerShape(du(4f)))
                .padding(du(2f))
        ) {
            Text("All apps", color = Color.White, fontSize = su(4.6f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(du(1f)))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(du(15f)),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vm.apps) { app -> AppTile(app.label, app.icon) { vm.launchApp(app.pkg) } }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(du(2f)))
            .background(Color(0xFF0E1C52))
            .clickable(onClick = onClick)
            .padding(horizontal = du(2f), vertical = du(1.6f))
    ) {
        Text(title, color = Color.White, fontSize = su(3.9f), fontWeight = FontWeight.Bold)
        Text(subtitle, color = C.Dim, fontSize = su(2.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
