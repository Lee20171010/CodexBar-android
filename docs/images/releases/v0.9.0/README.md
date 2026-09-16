# v0.9.0 screenshot captures

Captured from the real app UI on a disposable Android 16 (API 36.1) emulator,
1080 x 2400 pixels at 420 dpi, using the debug-only `ScreenshotActivity` and
demo quota data. No provider accounts or credentials were used. These are fresh
v0.9.0 captures, not copies of an earlier release.

The capture harness and release fixes are in commit `f060439`. The harness is
excluded from release builds.

## Reproduce

Install the debug APK on a disposable emulator. Launch the screenshot activity:

```sh
adb shell am start -W -S -n com.codexbar.android/.debug.ScreenshotActivity \
  --es theme MATERIAL_3 --ez dark_theme false
adb shell screencap -p /sdcard/capture.png
adb pull /sdcard/capture.png dashboard-material3.png
```

Wait for the screen to finish drawing before each capture. Repeat with
`LIQUID_GLASS`, `WINUI_3`, and `AURORA`; Aurora uses `--ez dark_theme true`.
For the two Codex details, use Liquid Glass, tap the Codex summary chip, expand
the detail sheet, then scroll to the daily and per-model token charts.

For the Android 16 monitoring notification:

```sh
adb shell pm grant com.codexbar.android android.permission.POST_NOTIFICATIONS
adb shell am start -W -S -n com.codexbar.android/.debug.ScreenshotActivity \
  --es theme MATERIAL_3 --ez notification true
adb shell cmd statusbar expand-notifications
```

The notification uses the production renderer with the same demo snapshot.
Samsung One UI's Now Bar appearance is not reproduced by this emulator.

## Verification

- Strict Gradle dependency verification, debug APK build, and all 313 unit tests passed.
- Android lint completed with 0 errors (96 warnings and 1 hint).
- All seven PNGs were visually inspected. The release workflow independently
  verifies the signed release APK's startup and Connections navigation.
- Physical-device launcher behavior, Samsung Now Bar, TalkBack, and live-account
  companion reconnection remain outside these captures.
