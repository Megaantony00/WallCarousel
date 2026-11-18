<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="WallCarousel icon">
</p>

<h1 align="center">WallCarousel</h1>

<p align="center">
  <b>Rotate home and lock wallpapers on your tablet — completely offline.</b>
</p>

<p align="center">
  <a href="https://github.com/Megaantony00/wallcarousel/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Megaantony00/wallcarousel?style=flat-square"></a>
  <a href="LICENSE"><img alt="License: GPL-3.0-or-later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue?style=flat-square"></a>
  <img alt="Android 10+" src="https://img.shields.io/badge/Android-10%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
</p>

WallCarousel is a lightweight, open-source Android app that rotates the photos you choose on the
home and lock screens of a tablet. It is designed for the Samsung Galaxy Tab S10 FE+ 5G and One UI,
but runs on any device with Android 10 (API 29) or later.

## Screenshots

| Wallpapers | Carousel editor |
|:---:|:---:|
| <img src="docs/screenshots/wallpapers.jpg" width="440" alt="Wallpaper library"> | <img src="docs/screenshots/carousel-editor.jpg" width="440" alt="Carousel editor with independent schedules"> |
| **Schedule** | **Crop and filters** |
| <img src="docs/screenshots/schedule.jpg" width="440" alt="Independent schedule options"> | <img src="docs/screenshots/wallpaper-editor.jpg" width="440" alt="Non-destructive crop and filters"> |

## Download

[**Download the latest APK**](https://github.com/Megaantony00/wallcarousel/releases/latest) — signed,
Android 10 or later.

An F-Droid submission is planned. The app has no `INTERNET` permission, no ads, no analytics and no
tracking.

## Features

- Import photos from the system gallery. No storage permission is requested.
- Keep separate portrait and landscape versions of the same wallpaper.
- Non-destructive editing: focal point, zoom, brightness, contrast, saturation and blur.
- Create ordered carousels and assign them to the home screen, the lock screen, or both.
- Combine, independently for each screen, an interval (15 minutes or more), specific times of day
  and an advance on every unlock.
- Advance the wallpaper from two Quick Settings tiles, one for home and one for lock.
- Adaptive Compose UI for tablets, Samsung DeX, split-screen and pop-up view.
- Optional pre-rendering of the next wallpaper (uses more CPU, memory and battery).
- English and Italian user interface.

## Current limitations

- A static Android wallpaper stores a single bitmap. WallCarousel renders it for the orientation
  that is active when the wallpaper is applied; rotating the device afterwards does not switch to
  the other version. Automatic per-orientation switching requires a live wallpaper.
- Samsung does not expose third-party live wallpapers on the lock screen. The lock screen is
  static by design.
- WorkManager is inexact by design: scheduled changes can be delayed by Doze and battery
  optimisations. Exact alarms are intentionally not used.
- The unlock schedule needs a small foreground service, because Android does not deliver the
  unlock broadcast to manifest receivers. The service runs only while the option is enabled and
  keeps a silent, ongoing notification visible; disabling the option stops it immediately.
- The DeX desktop wallpaper is managed by One UI and cannot be changed by this app. The app UI
  itself is fully adaptive and works in DeX.
- Lock-screen support depends on the device manufacturer. WallCarousel uses the official
  `WallpaperManager` lock flag and reports an error if the system rejects the change.

## Build

Requirements:

- JDK 21
- Android SDK platform 36 and build-tools 36.0.0
- A `local.properties` file pointing to the SDK, or `ANDROID_HOME` set

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Run the unit tests with:

```sh
./gradlew :app:testDebugUnitTest
```

### Release build

The release build enables R8 and resource shrinking. Signing is read from a
`keystore.properties` file in the repository root, which is ignored by git:

```properties
storeFile=/absolute/path/to/upload.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```sh
./gradlew :app:assembleRelease
```

Without `keystore.properties` the task still runs and produces an unsigned APK,
which is what the F-Droid build server uses. See [RELEASE.md](RELEASE.md) for the
publication checklist.

## Architecture

- Kotlin, Jetpack Compose and Material 3.
- Single-activity app with an adaptive navigation rail or bottom bar.
- State persisted as JSON in app-private storage with atomic writes.
- Original images copied into app-private storage; edits are stored as a recipe and rendered on
  demand, so the source file is never modified.
- Scheduling through WorkManager only. The interval uses periodic work, daily times use one-time
  work re-enqueued after each run and the unlock trigger uses a foreground service that enqueues a
  single WorkManager run per unlock. A 15 second guard prevents two triggers from advancing the
  same screen at the same moment.
- Quick Settings tiles share the same apply pipeline as the UI.

## Privacy

WallCarousel works completely offline. Photos, metadata and settings never leave the device.
See [PRIVACY.md](PRIVACY.md) for details, including export and deletion.

## License

Copyright (C) 2026 The WallCarousel contributors.

WallCarousel is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

See [LICENSE](LICENSE) for the full text.
