# Privacy policy — WallCarousel

Last updated: 2026-11-22

WallCarousel is a local wallpaper manager. It has no server component, no account system, no
advertising and no analytics. The only network access is the optional GitHub update check. This
document explains which data the app handles and how you can remove it.

## Data stored on the device

- **Photos you import.** The app copies the selected image into its private storage
  (`/data/data/com.wallcarousel.app/files/wallpapers/`). The original photo in your gallery is
  never modified or deleted.
- **Wallpaper metadata.** Name, dimensions, crop and filter recipe, carousel order, screen
  assignments and schedules are stored in `state.json` inside the same private directory.
- **Preferences.** Theme mode, dynamic colour preference, palette source, language, update
  preferences and the optional unlock schedule, stored in the same file.
- **Update files.** If you check for updates, the downloaded APK is stored temporarily in the app's
  private cache (`cache/updates/`) and deleted on the next app start. It is not written to the
  gallery or to a public Downloads folder.

No contact data, location, device identifiers, advertising identifiers, crash reports or usage
statistics are collected.

## Permissions

- `android.permission.SET_WALLPAPER` — required to apply a wallpaper through the official
  `WallpaperManager` API.
- `android.permission.FOREGROUND_SERVICE` and
  `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` — used only while the "when the tablet
  wakes" schedule is enabled, to keep the unlock listener alive. The foreground service shows a
  silent, ongoing notification and is stopped as soon as the option is disabled.
- `android.permission.POST_NOTIFICATIONS` — requested on Android 13 and later so the notification
  above can be displayed. It is never used for anything else.
- `android.permission.RECEIVE_BOOT_COMPLETED` — restarts the unlock listener after a reboot when
  the option is enabled.
- The system gallery grants temporary read access to the specific images you choose. The app
  does not request `READ_MEDIA_IMAGES`, `READ_EXTERNAL_STORAGE` or broad file access.
- `android.permission.INTERNET` — used only by the optional update checker to read the latest
  GitHub release and download its signed APK. It is not used for anything else.
- `android.permission.REQUEST_INSTALL_PACKAGES` — used only to open the system package installer
  for a downloaded update. Android still asks you to confirm the install; the app cannot install
  anything silently. You can revoke the "install unknown apps" permission in system settings at
  any time.

## Processing and sharing

Images are decoded, cropped and filtered on the device. They are never uploaded, shared with third
parties or used to train models. The app contains no third-party SDKs for advertising, analytics or
crash reporting.

The update check contacts GitHub (`api.github.com`, `github.com` and GitHub's asset hosts) over
HTTPS only when you tap **Check now** or enable **Check for updates on startup**. The request
contains the app version in the User-Agent and standard HTTP metadata such as your IP address;
GitHub processes it according to its own privacy policy. No account, device identifier or usage
data is sent. The downloaded APK is verified against the installed app's signing certificate
before the installer is opened. The app cannot install it silently.

## Security

- Data lives in the app sandbox and is protected by Android file-based encryption.
- Cloud backup and device-to-device transfer are disabled for the app, so private photos are not
  copied into Google backups.
- No secrets are embedded in the app. Network connections are made only to GitHub over HTTPS for
  update checks and APK downloads. The downloaded APK must match the installed app's signing
  certificate; otherwise it is deleted and never offered to the installer.

## Retention and deletion

Data stays on the device until you delete it.

- Delete a single wallpaper from its editor.
- Delete a carousel without deleting its wallpapers.
- Use **Settings → Delete all data** to erase every image, carousel, schedule and preference stored
  by WallCarousel. This cannot be undone. The wallpaper currently applied to the system is not
  changed by this action.
- Downloaded update APKs are removed from the private cache on the next app start. They are never
  placed in the gallery or a public Downloads folder.

## Export

**Settings → Export data** creates a zip archive containing `wallcarousel.json` and the original
imported images. You choose the destination with the system document picker. The archive is
written directly to that destination and is not uploaded anywhere.

## Children

WallCarousel is a general-purpose utility and is not directed at children. It does not knowingly
collect personal data from anyone.

## Changes

Material changes to this policy will be documented in the project repository together with the
app release that introduces them.

## Contact

For privacy questions, open an issue in the project repository.
