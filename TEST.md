# CodexBar Android Test Plan

## 1. Verification scope

This document owns test commands, evidence and remaining acceptance work.
[SPEC.md](SPEC.md) describes behavior; [CONTRIBUTING.md](CONTRIBUTING.md) describes
development setup. Compilation, smoke checks, negative authentication tests and
successful real-account quota retrieval are distinct gates.

## 2. Host checks

From the repository root:

```sh
bash ./gradlew :app:testDebugUnitTest --max-workers=2
PYTHONDONTWRITEBYTECODE=1 python3 native/test_setup_toolchain.py
PYTHONDONTWRITEBYTECODE=1 python3 native/test_runtime_config.py
git diff --check
```

Attribution checks: `python3 native/collect_notices.py --check` verifies all 24
checked-in notice assets and their hashes, including exact copies of the Android
license and the root attribution document. The About screen compiles and the
33 JVM tests pass. The initial full lint run also identified an existing
`QuotaTileService.startActivityAndCollapse(Intent)` API-34 compatibility error;
this is not suppressed. About/launcher visual runtime acceptance remains pending.

The JVM suite currently contains **33 tests**:

| Suite | Count | Coverage |
| --- | ---: | --- |
| Claude repository | 9 | Existing HTTP/credential behavior |
| Codex repository | 8 | Existing HTTP/credential behavior |
| Gemini repository | 6 | Existing HTTP/credential behavior |
| OpenCode Go repository | 2 | Missing key avoids execution; rejected keys are retained |
| Native process runner | 3 | Dual-pipe output bounds, timeout/reaping, cancellation/reaping |
| Go CLI parser | 5 | Window mapping, provider/source isolation, invalid data, truncation, sanitized errors |

The process checks use real Linux child processes (`/bin/sh`, `head`, `sleep` and
`/proc`) on the build host. They are not Android device tests. Other provider
repository checks use test HTTP responses/mocks, not live accounts.

The Python integrity test accepts a matching archive hash and rejects a modified
archive. Two offline runtime-configuration checks require explicit wrappers before
dispatch and preserve the selected acceptance options without contacting a device.
To verify already downloaded official tool archives without downloading
or extracting them again:

```sh
python3 native/setup_toolchain.py --tools "$SWIFT_ANDROID_TOOLS" --verify-only
```

## 3. Release build and artifact checks

Prepare the pinned tools and private signing environment as documented in
[native/README.md](native/README.md), then:

```sh
python3 native/build.py --tools "$SWIFT_ANDROID_TOOLS" --abi arm64-v8a --configuration release
bash ./gradlew :app:assembleNativeRelease --max-workers=2
```

Artifact: `app/build/outputs/apk/nativeRelease/app-nativeRelease.apk`.

Before transfer, verify that the exact APK is signed, non-debuggable and contains
only `arm64-v8a` native libraries. The launcher must resolve to the self-test
activity. For example, using an installed Android SDK build-tools version:

```sh
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
"$ANDROID_BUILD_TOOLS/apksigner" verify --verbose app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
"$ANDROID_BUILD_TOOLS/aapt2" dump badging app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
sha256sum app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
```

`native-code` should list only ARM64, and `application-debuggable` must be absent.
Use the same APK for remote acceptance and delivery, identified by its SHA-256.
Do not rebuild solely to transfer an already verified artifact.

## 4. Locked Android runtime acceptance

Configure `ANDROID_TEST_ADB_WRAPPER` and `ANDROID_TEST_SESSION_WRAPPER` through
local operator configuration. Prefer the Release variant to reduce remote transfer:

```sh
python3 native/test.py --abi arm64-v8a --configuration release --opencode-go
```

The harness holds one shared-runtime lock through discovery, installation, launch,
assertions and cleanup. It refuses to replace an existing test package. Release
results are collected from a dedicated logcat tag using a unique run ID, without
clearing shared logs or enabling `run-as` on the non-debuggable APK.

The five probes are:

1. CLI launch/version command exits successfully.
2. Resource smoke initializes an actual bundled provider plugin and emits its success marker.
3. Isolated provider configuration validates.
4. Codex without credentials emits the expected provider-error JSON; exit 1 is intentional.
5. The production Go client makes an HTTPS request with a fixed invalid key and
   maps its rejection to the expected authentication failure.

With `--opencode-go`, UI automation also opens Settings, verifies masked Go key
input, saves a synthetic key, presses Validate and checks the expected rejection.
This exercises the Settings → encrypted store → repository → native client path.
It never imports a saved account or supplies a real API key.

Generated reports are ignored by Git:

- `build/native/device-report-arm64-v8a-release.json`: probe/UI outcomes, run ID,
  ABI, configuration and tested APK hash/size.
- `build/native/go-settings-ui.xml`: latest UI snapshot from the fresh synthetic test install.

Failure reports must remain failures even if some probes passed. A missing-credential
or rejected-key result does not prove successful quota retrieval.

## 5. Recorded evidence

Latest functional verification: **2026-10-08**.

| Gate | Recorded result |
| --- | --- |
| JVM suite | 33/33 passed |
| Toolchain integrity check | Passed; three official cached archive hashes matched |
| Full Android Core/CLI compilation | x86_64 and ARM64 passed |
| Initial App-UID smoke | 4/4 on x86_64 and 4/4 on ARM64 native-bridge path |
| Optimized ARM64 APK | Release/R8/resource shrinking and signature checks passed |
| Go native API acceptance | 5/5 runtime probes passed with synthetic credentials |
| Go Settings acceptance | Masked input, save and native validation rejection passed |
| Test cleanup | Owned test installation removed before releasing the lock |

Runtime evidence is from an **Android 16 / API 36 x86_64 emulator** supporting an
ARM64 native bridge. It is not physical ARM64 hardware evidence.

The first optimized build reduced APK size from **76.91 MiB to 33.28 MiB (56.7%)**.
Uncompressed CLI size changed from 142.14 to 73.47 MiB, and the C++ runtime from
9.05 to 1.36 MiB. These are artifact measurements, not performance benchmarks.

The tested Go APK was **34,900,412 bytes**, SHA-256:

```text
7a93aa6cb7b476888bd34e4a9751a89694313feb825d21dbcabfb45f4009af29
```

This hash identifies the tested artifact; later documentation/history edits do not
imply that a newly rebuilt APK will have identical bytes. Revalidate changed payloads.

The upstream Swift `make check` attempt stopped because `plutil` was unavailable;
plain `make test` stopped because the host `swift` was not on PATH in the isolated
cross-compilation setup. Neither upstream suite is claimed as passing. Cross-build
and App-UID checks are the native evidence recorded here.

## 6. Manual and remaining acceptance

Use an owner-authorized test account for live checks; keep credentials out of logs
and committed screenshots. Still pending:

- [ ] Install and run on physical ARM64 hardware, including the native minimum API.
- [ ] Validate on a 16 KiB-page device and establish production APK/AAB coverage.
- [ ] Enter a valid Go key; compare 5-hour, weekly and monthly windows with the provider.
- [ ] Restart the app and verify credential persistence, then clear the Go key and verify removal.
- [ ] Exercise offline, timeout and rate-limit behavior through the user-facing UI.
- [ ] Verify background Go refresh updates widgets, notifications and the tile over time.
- [ ] Verify foreground/background overlap and cancellation during navigation on-device.
- [ ] Resolve and verify refresh-interval/Manual preference scheduling; the current setter only saves preferences.
- [ ] Verify account renewal for existing OAuth providers independently of Go API-key support.

Future provider ports must satisfy their own credential, mapping and runtime checks;
the Go results cannot be generalized to every provider compiled into the CLI.
