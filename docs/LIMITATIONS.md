# Known limitations and notes for forks

This file collects the parts of WallCarousel that are constrained by Android or by a specific
device, with enough implementation detail for a fork to try a different approach. None of these
are bugs in the UI: they are trade-offs of static wallpapers and of Samsung's theme model.

## Rotation and the portrait/landscape crop

### What the app does now

Android stores **one bitmap per target** (Home and Lock). When the wallpaper is applied,
`StaticWallpaperApplier` renders the variant for the orientation that is active at that moment
and calls `WallpaperManager.setBitmap`.

To avoid showing the old crop after a rotation, the app keeps the current wallpaper in memory in
both orientations:

- `TargetConfig.lastWallpaperId` and `TargetConfig.lastOrientation` record what was applied.
- `StaticWallpaperApplier.orientationCache` holds up to four bitmaps: Home/Lock × portrait/landscape
  (about 80 MB at 2880×1800 ARGB_8888). It is memory-only and is lost when the process dies.
- `Application.onConfigurationChanged` calls `reapplyCurrent()` for the target that is visible
  first (Lock when the keyguard is showing, Home otherwise), then for the other target. It uses
  the cached bitmap instead of re-rendering it.
- `warmOrientationCache()` runs on app start to prepare the other orientation in the background.
- `preRenderOtherOrientation()` prepares the opposite orientation after every apply or reapply, so
  the next rotation is a cache hit.
- The cache is replaced when a new wallpaper is applied for that target. It is not cleared by
  `clearPreRendered()`, which only handles the optional "pre-render next wallpaper" feature.

### Measured behaviour on the target tablet

Samsung Galaxy Tab S10 FE+ 5G, Android 16, 2880×1800:

- app-side pre-render of one orientation: about **0.2–0.3 s**;
- Android `WallpaperManager.setBitmap` for one target: about **2.5–3 s**;
- visible Home is correct after about **3 s**; both Home and Lock after about **5 s**;
- the first ~1 s can still show the system rotation transition with the old crop;
- if the lock screen is already visible while rotating, the keyguard may keep the old bitmap until
  the screen is turned off and on again.

The system write is the bottleneck. The app can prepare the crop earlier, but it cannot make the
launcher or the keyguard redraw before `setBitmap` returns, and Android has no "per-orientation
wallpaper slot" for static wallpapers.

### Possible fixes for a fork

1. **One bitmap with blurred fill (no delay).**
   Render a canvas that covers both aspect ratios: draw a scaled and blurred copy of the image as
   the background, then draw the selected crop centred on top. The system crops the same bitmap in
   both orientations, so no re-apply is needed. The trade-off is visible blurred bands in one
   orientation. Start from `WallpaperRenderer.render()` and add a mode that receives both target
   aspects; keep the existing render for the editor preview.

2. **Live wallpaper (`WallpaperService`).**
   Draw the correct variant directly on the wallpaper surface and react to
   `onSurfaceChanged`/`onVisibilityChanged`. Rotation becomes instant and there are no blurred
   bands. Costs: a wallpaper service, battery/rendering care, and OEM support. On Samsung, a
   third-party live wallpaper is generally not available on the lock screen, so this may only fix
   Home.

3. **Lower-resolution output.**
   Render at 1920–2560 px wide instead of 2880. The binder payload is smaller and `setBitmap` is
   usually faster; the image is slightly softer on a high-density panel. Make it a setting or cap
   `WallpaperRenderer.MAX_DIMENSION`, and measure on the target device.

4. **Persistent orientation cache.**
   Save the two rendered orientations to disk (PNG/JPEG) to avoid re-rendering after process death.
   Decoding still costs time and the system write remains, but it helps the first rotation after a
   restart. The in-memory cache is already enough once the app is open.

5. **Defer the non-visible target.**
   Apply the visible target first (already done) and postpone the other until the screen is turned
   off or the app returns to the foreground. This reduces total work at the cost of a stale
   wallpaper on the other target if it is shown before the update.

## Wallpaper palette and Samsung's system palette

### What the app does now

`WallpaperPalette.kt` takes the wallpaper most recently applied by WallCarousel for the selected
target (Home or Lock), decodes a small sample with `BitmapFactory`, and calls
`WallpaperColors.fromBitmap()` to get primary/secondary/tertiary seed colours. It then builds a
small Material 3 colour scheme:

- accent and container colours are derived from the seeds with HSL lightness/saturation limits;
- `readableOn()` picks black or white for text using the contrast ratio;
- surfaces, outlines and errors remain the static light/dark scheme, which keeps readability.

A reflection helper handles the API 36 change where `WallpaperColors` returns
`android.graphics.Color` objects instead of ARGB ints.

The palette follows the **last wallpaper applied by WallCarousel**. It is not a system-wide theme:
it only changes the app. It does not detect a wallpaper changed by another app, and it does not
change the Samsung system palette.

On Samsung One UI the system-wide "Color palette" is manual. After changing the wallpaper you have
to open **Wallpaper and style → Color palette** and pick the generated palette again; One UI does
not reliably recalculate it. Good Lock's Theme Park can build a theme/palette from one wallpaper,
but it is a fixed theme and does not follow later wallpaper changes. There is no public Android API
that lets a third-party app set the system Material You palette; a shortcut button would only open
the manual screen, so the app does not include one.

### Possible fixes for a fork

- Replace the small HSL scheme with a full tonal palette from Material color utilities
  (`com.google.android.material.color.utilities` or a Kotlin port) and map every `ColorScheme`
  role. This gives better containers, surfaces and contrast in both themes.
- Add more palette sources (the photo currently open in the editor, a fixed brand colour, system
  Material You) and persist the choice.
- Try `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` where it is available. On the target
  device it returned `null` without extra permissions, which is why the app reads its own image.
- Listen for `WallpaperManager` colour changes or a wallpaper-change broadcast to follow external
  changes; validate permission and OEM behaviour first.
- System-wide automation on Samsung would require a theme engine, Shizuku or root integration.
  That is outside the scope of this offline app.

## In-app language

### What the app does now

`AppLanguage` stores the selected tag in `SharedPreferences`. `WallCarouselApplication` and
`MainActivity` wrap `attachBaseContext` with the selected `Locale`, and changing the language
recreates the activity. The list comes from `res/values/arrays.xml` (`app_languages`); labels are
the locale's own display name.

To add a language:

1. add `app/src/main/res/values-xx/strings.xml`;
2. add the tag (for example `fr`) to `app/src/main/res/values/arrays.xml`;
3. missing strings fall back to English in `values/strings.xml`.

The app UI follows the selection. Manifest labels, Quick Settings tile labels and some
notification text can still follow the system language because the platform's per-app locale is
not used.

### Possible fixes for a fork

- Use `LocaleManager.setApplicationLocales()` on Android 13+ and AppCompat's
  `AppCompatDelegate.setApplicationLocales()` on older versions, with a `localeConfig` XML, so the
  system per-app language screen and manifest labels follow the choice too.
- Generate the picker from the resource table instead of a manual array, and add search for a long
  language list.
- Add a "Help translate" link in About.

## In-app updater

### What the app does now

`UpdateManager` reads the latest release from the GitHub Releases API
(`api.github.com/repos/Megaantony00/WallCarousel/releases/latest`) over HTTPS. It chooses the first
asset whose name ends in `.apk`, downloads it to the app's private cache
(`cache/updates/`), and verifies:

- the package name is `com.wallcarousel.app`;
- the APK's version code is not lower than the installed one;
- the first signing certificate is byte-for-byte identical to the installed app's certificate.

Only then it exposes a content URI through `FileProvider` and opens the system package installer.
The APK is limited to 80 MB, written to a `.part` file and renamed after a complete download. The
cache is cleaned on the next app start, so the file is never left in the gallery or a public
Downloads folder.

Android does not allow silent installs from a normal app. The user must grant "install unknown
apps" to WallCarousel and confirm the update in the system installer. Google Play Protect may also
ask to analyse the APK before installing it, especially for an app distributed outside the Play
Store. The automatic check is off by default; when enabled it runs at most once every 24 hours.
The manual "Check now" button always works.

GitHub's unauthenticated API is limited to 60 requests per hour per IP. The request sends only a
`User-Agent` with the app version and standard HTTP metadata; there is no account or device ID.

### Possible fixes for a fork

- Move the check into a periodic `WorkManager` job with a notification when a new release exists,
  so the user does not have to open the app.
- Support a pre-release or beta channel, or select assets by ABI/version instead of the first APK.
- Add a delta/incremental update or use Obtainium/F-Droid as the updater instead of a custom HTTP
  client.
- Show the APK signing certificate SHA-256 and the release notes in a dedicated update screen.
- Add download cancellation and resume; the current implementation uses a single connection.
- If you change the release host, update `UpdateManager.API_URL` and keep the HTTPS-only check and
  the signature verification.

## Other notes

- Release signing: `upload.jks` and `keystore.properties` are gitignored. Forks must use their own
  key and cannot update an installation signed with the upstream key.
- The app has no `INTERNET` permission. Keep any fork offline unless you deliberately change the
  privacy model.
- The F-Droid recipe is kept in `fdroid/` for a future submission; check the current F-Droid
  inclusion rules and the upstream MR history before resubmitting.
- Canonical repository: <https://github.com/Megaantony00/WallCarousel>.
