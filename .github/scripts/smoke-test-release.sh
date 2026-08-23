#!/bin/sh
set -eu

release_apk=''
release_apk_count=0
for candidate in app/build/outputs/apk/release/*.apk; do
  [ -f "$candidate" ] || continue
  release_apk="$candidate"
  release_apk_count=$((release_apk_count + 1))
done
if [ "$release_apk_count" -ne 1 ]; then
  printf '::error::Expected exactly one release APK for startup smoke testing.\n'
  exit 1
fi

package_name='com.codexbar.android'
adb install "$release_apk"
adb logcat -b crash -c
adb shell am force-stop "$package_name"
adb shell am start -W -n "$package_name/.MainActivity"
sleep 5

app_pid="$(adb shell pidof "$package_name" || true)"
app_pid="$(printf '%s' "$app_pid" | tr -d '\r')"
if [ -z "$app_pid" ]; then
  printf '::error::Release app process exited during startup.\n'
  adb logcat -b crash -d
  exit 1
fi
if adb logcat -b crash -d | grep -Fq "Process: $package_name"; then
  printf '::error::Release app produced a startup crash.\n'
  adb logcat -b crash -d
  exit 1
fi

adb logcat -b crash -c
adb shell am start -W \
  -a android.intent.action.VIEW \
  -d 'codexbar://connections' \
  "$package_name"
sleep 5

app_pid="$(adb shell pidof "$package_name" || true)"
app_pid="$(printf '%s' "$app_pid" | tr -d '\r')"
if [ -z "$app_pid" ]; then
  printf '::error::Release app process exited after opening Connections.\n'
  adb logcat -b crash -d
  exit 1
fi
if adb logcat -b crash -d | grep -Fq "Process: $package_name"; then
  printf '::error::Release app crashed after opening Connections.\n'
  adb logcat -b crash -d
  exit 1
fi
