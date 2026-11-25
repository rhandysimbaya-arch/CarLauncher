# Car Launcher

An Android home-screen launcher with a dark, glowing car-dashboard UI, built for landscape head units and phones/tablets on a car mount.

## Features

| Area | What it does |
|---|---|
| **Speedometer** | Live GPS speed on a 0-200 km/h gauge with glow arc, needle, and automatic **P / D** gear indicator |
| **Navigation** | Dark map that follows your position heading-up, turn banner (distance + "Turn right onto ..."), compass, ETA bar (minutes, km, arrival time) with cancel button. Search any place and get driving routes with automatic re-routing |
| **Music** | Now-playing card for whatever is playing (Bluetooth or any music app): title, artist, album art, progress, seek, previous / play-pause / next |
| **Status bar** | Clock, Bluetooth connected device, temperature, mobile signal, battery |
| **Neon Drive theme** | Default look (Settings > Theme to switch to Classic): neon-glow panels, speedometer, music player, live spectrum + sound-level meter, trip computer (Trip A / avg / max speed; long-press to reset) |
| **Sound** | Equalizer with presets (Punchy Bass, Deep Bass, Bass Boost, Rock, Pop, Hip-Hop, Dance/EDM, Vocal, Treble Boost, Jazz, Classical, Loudness), per-band sliders, bass boost, **volume booster** (up to +12 dB) |
| **Speed bar** | Segmented horizontal meter that follows the car's speed (replaces the old fuel bar) |
| **Bottom nav** | Home, Maps, Music, Phone (dialpad, recents, contacts), Settings (permissions, system shortcuts, **all apps** grid) |
| **Launcher** | Registers as a HOME app, immersive full-screen, screen stays on, Back always returns to the dashboard |

## Build

GitHub Actions builds the APK on every push to `main`:

* **Actions** tab -> latest run -> artifact `CarLauncher-debug-apk`, or
* **Releases** -> download `app-debug.apk`

Local build (needs the Android SDK): `gradle assembleDebug`

## Which APK?

* **app-lite-debug.apk** (install this one): no notification-listener service, so Google Play Protect allows sideloading. Music buttons (previous / play-pause / next) work with any player, but title/art/progress are not shown.
* **app-full-debug.apk**: adds now-playing title, art and progress, but Play Protect may block installing it.

## First run

1. Install the APK and open it. Allow **Location**, **Bluetooth** and **Phone** permissions.
2. Settings tab -> **Media access** -> enable *Car Launcher* (this lets it read/control the playing music). On Android 13+ you may first need to open the app's info page and choose *Allow restricted settings* (menu in the top-right).
3. Settings tab -> **Default launcher** -> choose *Car Launcher* so the Home button opens it.

## Notes / limits

* Works on **Android 5.0 (API 21) and newer**.
* Sound effects use Android's system audio effects; whether they apply to *all* apps depends on the device and Android version (shown on the Sound screen). The live spectrum needs the Record Audio permission; without it the bars animate decoratively.
* Speed comes from GPS, so the gauge works with any device. Real **gear and outside temperature need the car's OBD-II data**, which a normal phone cannot read; the temperature is the ambient sensor if present (else battery temperature), and the gear is P when stopped / D when moving.
* Maps use OpenStreetMap data with OpenStreetMap tiles (darkened in-app), Nominatim search and the public OSRM demo router. These are free and need no API key, but are intended for light use; swap in your own tile/routing server for heavy use.
* Internet is required for map tiles and routing.
