# 0.0.8-beta-native trial evidence

This trial includes segmented/stacked account layouts and confirmed-reset
notifications, plus the preceding status, refresh, overview and pace changes.
It does not complete all native Mac feature or visual parity.

## Artifacts

- Daily APK: `app/build/outputs/apk/nativeRelease/app-nativeRelease.apk`
  (`com.codexbar.android.native`, version code 9), 35,863,152 bytes.
- Daily SHA-256: `dbefc398e4fc90b1829995ba3afb6ce3aac31b93d9d3edaf358407c0df900cdd`.
- Isolated acceptance APK: 35,872,012 bytes; SHA-256
  `5b744a5fbd2bb3daea017103f76e10a5235cd0331e5be1f24e4c233848aa8c27`.
- Both Release APKs passed signature verification. The four packaged native
  libraries are byte-identical between them and ARM64-only.

## Checks completed

- 196 JVM tests passed; no failures, errors or skipped tests.
- Debug lint and native Debug Kotlin compilation passed.
- Signed native Release and native Acceptance builds passed, including R8.
- All 24 packaged license notice checks passed; staged secret scans passed.
- Shared-runtime acceptance used the session-lock and ADB wrappers with the
  isolated acceptance package. Ten native/runtime probes passed, including
  expected missing-credential and synthetic invalid-credential outcomes.
- UI assertions passed for provider navigation, display/cadence persistence,
  Codex sign-in entry, offline licenses, phone/dark/200% text/wide layouts,
  detail, history chart, credits and display controls.

## Evidence boundaries and trial focus

Runtime evidence is from the isolated acceptance APK on an API 36 emulator's
ARM64 native bridge, not a physical ARM64 device or the exact daily APK.
Synthetic credential rejection is not successful live quota retrieval.
New multi-account switching and reset transitions have JVM regression coverage;
the existing device harness does not specifically prove every new interaction
or actual Android notification delivery. Native Mac visual comparison,
TalkBack, widget-host interaction and 16 KiB hardware acceptance remain open.

On the phone, try Menu & display's Segmented/Stacked and Detailed/Compact
settings, switch between two accounts, then restart to check persistence.
Check refresh-on-open, provider-status visibility and pace visibility settings.
Confirmed-reset notifications require genuine new observations; a simple drop
in used quota is no longer sufficient. Notification defaults remain off.
