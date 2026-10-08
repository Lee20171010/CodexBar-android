# Native Core/CLI Build Guide

This guide owns toolchain setup, native compilation, payload staging and APK build
commands. [SPEC.md](../SPEC.md) owns architecture and behavior;
[TEST.md](../TEST.md) owns acceptance procedures and results. For app setup and
OpenCode Go usage, start with [README.md](../README.md).

## Pinned source and tools

- Upstream: `steipete/CodexBar` v0.71.0,
  commit `cb5f0cbe88615a441272c6f9e44675bf594a3fa3`.
- Official Swift 6.4.0 Ubuntu 24.04 host compiler and matching Android SDK.
- Android NDK r30; the native CLI currently targets API 28 because upstream uses
  `posix_spawn`. The existing Android app still targets minimum API 26; the native
  acceptance activity explicitly requires API 28. Final product compatibility
  has not yet been decided. Child-process working-directory actions require API
  34 and report `ENOTSUP` below that version; no process-global `chdir` workaround.
- SQLite 3.50.4 amalgamation, built statically with the NDK. Core already uses
  SQLite; Android does not expose its system copy as a public NDK library.
- Source checkout: ignored `upstream-codexbar/`.
- Build outputs: ignored `build/native/<abi>/`.

### Standalone toolchain setup

Use an x86_64 Ubuntu 24.04 build host with Python 3.12+, Git, `tar`, `unzip`, and
the [Swift Linux host prerequisites](https://www.swift.org/install/linux/).
The APK build additionally requires JDK 21 and an Android SDK with platform 35;
set `ANDROID_HOME` or the ignored `local.properties` for that SDK.

Official archive URLs, SHA-256 pins and verification provenance are stored in
[`toolchains.json`](toolchains.json). No separate project checkout is required:

```sh
export SWIFT_ANDROID_TOOLS="$PWD/build/swift-android"
python3 native/setup_toolchain.py --tools "$SWIFT_ANDROID_TOOLS"
python3 native/build.py --tools "$SWIFT_ANDROID_TOOLS" --abi arm64-v8a --configuration release
```

Setup downloads to `downloads/`, verifies each archive **before extraction**, and
extracts the compiler, Android SDK and NDK into `toolchain/`, `sdk-bundles/` and
`ndk/` respectively. It does not install packages globally. Existing installations
with that layout can be reused via `--tools`; `setup_toolchain.py --verify-only`
checks cached archives without downloading or changing extracted tools. Other
Linux distributions may need the compiler's matching host libraries; Ubuntu
24.04 is the supported recipe rather than depending on private compatibility files.

The script verifies the source revision, applies `android.patch`, and uses a project-local Swift build
directory. It does not install tools globally or access provider credentials.

SQLite is downloaded from the official HTTPS release URL; `build.py` pins the
SHA-256 observed during the initial download. The amalgamation is public domain.
Generated code and archives stay under `build/`.

## App-UID smoke check

Provide `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD` through your private environment or CI secrets. No
operator-specific key path is embedded in the project. Use the existing signing
identity for updates; keep credentials outside tracked files.

Configure wrappers for your device and shared-runtime lock:

```sh
export ANDROID_TEST_ADB_WRAPPER=/path/to/adb-wrapper
export ANDROID_TEST_SESSION_WRAPPER=/path/to/session-lock-wrapper
```

The ADB wrapper accepts ordinary `adb` arguments and supplies the approved device
connection. The session wrapper runs its arguments under one exclusive lock shared
by all users of that runtime, holding it until the command finishes. These scripts
are operator configuration, not repository credentials. Both environment variables
must be configured before running device checks. Tests refuse to replace an
existing test installation.

The script stages the executable as `libcodexbar.so`, its C++ runtime, and the
resource bundle in the matching payload directory. Prefer the optimized Release
variant below for remote checks. Only when a debugger or `run-as` is required,
build the isolated Debug variant:

```sh
python3 native/build.py --tools "$SWIFT_ANDROID_TOOLS" --abi x86_64 --configuration debug
bash ./gradlew :app:assembleNativeDebug -PnativeAbi=x86_64 --max-workers=2
python3 native/test.py --abi x86_64
```

`nativeDebug` uses `com.codexbar.android.native` and the existing release key. Its
test activity runs fixed commands only and is absent from production variants.
The runner is reused from the prior process proof and manages direct children;
the production client restricts API-key providers to API and Codex to OAuth. `test.py` holds one shared
runtime lock, refuses to replace an existing test package, then installs, reads
the report and uninstalls. The report is `build/native/device-report-<abi>.json`.

## Optimized ARM64 acceptance APK

Prefer this variant for the remote emulator and phone delivery:

```sh
python3 native/build.py --tools "$SWIFT_ANDROID_TOOLS" --abi arm64-v8a --configuration release
# With the private signing environment configured:
bash ./gradlew :app:assembleNativeRelease --max-workers=2
python3 native/test.py --abi arm64-v8a --configuration release
```

This uses Swift Release with `-Osize`, linker section garbage collection, stripped
CLI/C++ runtime binaries, Android R8/resource shrinking and ARM64-only packaging.
Release payloads are separate from Debug payloads under
`build/native/package/release/arm64-v8a/`. The APK is
`app/build/outputs/apk/nativeRelease/app-nativeRelease.apk`.

`nativeRelease` is non-debuggable and uses the same isolated `.native` application
ID and existing signing key. Its Codexbar launcher opens the dashboard;
the credential-free self-test is an explicit diagnostic activity. It is a release-optimized native
test build, not a completed product
migration. The default production `release` variant has no acceptance activity.

Because `run-as` is unavailable for a non-debuggable APK, the harness reads the
fixed synthetic report from a dedicated logcat tag and matches a fresh run ID.
It does not clear shared logs. The saved release report records the tested APK's
SHA-256, byte size and ABI, allowing the exact verified artifact to be delivered.

## Acceptance and artifact delivery

For the Go API/Settings check, use the exact Release APK just built:

```sh
python3 native/test.py --abi arm64-v8a --configuration release --opencode-go
```

This makes real HTTPS requests using a fixed invalid synthetic key, expecting
authentication rejection. It requires a fresh owned test installation and does
not use a saved account. Detailed probe assertions, manual checks, artifact
measurements and recorded results are maintained in [TEST.md](../TEST.md).

Keep the report's SHA-256 associated with the APK actually delivered. Signing a
Debug APK with a release key does not turn it into a Release build. Prefer the
single-ABI `nativeRelease` payload for remote transfers; use Debug only for a
specific debugger or `run-as` requirement.
