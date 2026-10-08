# CodexBar Android Test Plan

## 1. Verification scope

This document owns test commands, evidence and remaining acceptance work.
[SPEC.md](SPEC.md) describes behavior; [CONTRIBUTING.md](CONTRIBUTING.md) describes
development setup. Compilation, smoke checks, negative authentication tests and
successful real-account quota retrieval are distinct gates.

| Provider | Ordinary refresh path implemented | Synthetic checks | Android App-UID evidence | Owner-authorized live quota |
| --- | --- | --- | --- | --- |
| Claude / Codex / Gemini | Kotlin | HTTP fixtures | Full account acceptance pending | Pending |
| OpenCode Go | Native API in native-engine builds | Parser, repository, account lifecycle | Invalid-key CLI and Settings rejection | Pending |
| OpenRouter | Native API in native-engine builds | Budget/balance parser, ownership, cache | Invalid-key native and Settings rejection passed | Pending |
| Copilot / DeepSeek | Not implemented | Not recorded | Not recorded | Not recorded |

Codex device-code login is not implemented. Public-client/source compatibility must
be established before adding an OAuth flow; manual credentials remain available.

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
33 JVM tests pass. Debug lint passes after routing API-34+ Quick Settings launches
through an explicit, immutable PendingIntent. The legacy overload remains guarded
to older SDKs; its SDK-insensitive lint warning is suppressed only on that handler.
About/launcher and tile visual runtime acceptance remain pending.

The JVM suite currently contains **67 tests**:

| Suite | Count | Coverage |
| --- | ---: | --- |
| Claude repository | 9 | Existing HTTP/credential behavior |
| Codex repository | 11 | HTTP/credential behavior; request-local draft rotation, late-write cancellation, same-provider session isolation |
| Gemini repository | 6 | Existing HTTP/credential behavior |
| OpenCode Go repository | 2 | Missing key avoids execution; rejected keys are retained |
| Native process runner | 3 | Dual-pipe output bounds, timeout/reaping, cancellation/reaping |
| Go CLI parser | 5 | Window mapping, provider/source isolation, invalid data, truncation, sanitized errors |
| OpenRouter repository | 1 | Explicit provider/key ownership and wrong-credential rejection before native execution |
| Codex device-code auth | 6 | Pending/success/PKCE, denial/expiry, slow-down timing, cancellation, oversized responses and redirect rejection |
| OpenRouter CLI parser | 4 | Balance-only versus capped budgets, zero/unknown, measurement age, malformed data, process failures and sanitized API errors |
| Account storage | 9 | In-place/restart migration, same-provider isolation, rename/delete/reconnect, stale publication, concurrent token CAS, failed writes, invalid identities and OpenRouter restart isolation |
| Account coordinator | 6 | Failed draft isolation, serialized rotation, independent siblings, late deletion results, cancellation and reconnect |
| Widget account cache | 4 | Legacy pins, deleted-owner isolation, generation snapshots, actual measurement age, invalid/absent data and money/budget round trips |

Account storage tests use an in-memory SharedPreferences double, including the
memory-before-disk-failure behavior. They do not verify Android Keystore encryption
or claim device-level multi-account UI/worker/widget acceptance. Existing legacy credential keys
and global settings survive adoption; returning to an older build only exposes the
legacy accounts. See SPEC for the rollback restrictions.

Coordinator tests use synthetic credentials and controllable suspending repositories;
they exercise the shared entry point used by foreground and background callers. The
native Settings smoke path now opens an account draft and verifies that a rejected
key is not published as an account. Device results must identify the APK tested.

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
# Also exercise OpenRouter's native/API and masked-draft rejection paths:
python3 native/test.py --abi arm64-v8a --configuration release --opencode-go --openrouter
```

The harness holds one shared-runtime lock through discovery, installation, launch,
assertions and cleanup. It refuses to replace an existing test package. Release
results are collected from a dedicated logcat tag using a unique run ID, without
clearing shared logs or enabling `run-as` on the non-debuggable APK.

The probes are:

1. CLI launch/version command exits successfully.
2. Resource smoke initializes an actual bundled provider plugin and emits its success marker.
3. Isolated provider configuration validates.
4. Codex without credentials emits the expected provider-error JSON; exit 1 is intentional.
5. The production Go client makes an HTTPS request with a fixed invalid key and
   maps its rejection to the expected authentication failure.
6. With `--openrouter`, the production OpenRouter client rejects a fixed invalid key;
   its Settings draft stays masked and is not published after validation fails.

With `--opencode-go`, UI automation also opens an account draft in Settings, verifies
masked Go key input, presses Validate & save and checks the expected rejection.
Reopening Settings verifies the rejected draft did not become a saved account.
This exercises Settings → account coordinator → repository → native client.
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
| JVM suite | 67/67 passed; Debug lint passed |
| Toolchain integrity check | Passed; three official cached archive hashes matched |
| Full Android Core/CLI compilation | x86_64 and ARM64 passed |
| Initial App-UID smoke | 4/4 on x86_64 and 4/4 on ARM64 native-bridge path |
| Optimized ARM64 APK | Release/R8/resource shrinking and signature checks passed |
| Go native API acceptance | 5/5 runtime probes passed with synthetic credentials |
| Go Settings acceptance | Masked draft, native validation rejection and no saved account passed |
| OpenRouter acceptance | 6/6 combined native probes; Go and OpenRouter masked draft/rejection/no-publication UI flows passed |
| Test cleanup | Owned test installation removed before releasing the lock |

Runtime evidence is from an **Android 16 / API 36 x86_64 emulator** supporting an
ARM64 native bridge. It is not physical ARM64 hardware evidence.

The first optimized build reduced APK size from **76.91 MiB to 33.28 MiB (56.7%)**.
Uncompressed CLI size changed from 142.14 to 73.47 MiB, and the C++ runtime from
9.05 to 1.36 MiB. These are artifact measurements, not performance benchmarks.

The tested multi-account APK was **34,998,458 bytes**, SHA-256:

```text
3be1f5646fc53fa33e634ac2c0b8b8d0fcd8bd7d8db183ee64ac9ebdcc40c959
```

This hash identifies the tested artifact; later documentation/history edits do not
imply that a newly rebuilt APK will have identical bytes. Revalidate changed payloads.

The upstream Swift `make check` attempt stopped because `plutil` was unavailable;
plain `make test` stopped because the host `swift` was not on PATH in the isolated
cross-compilation setup. Neither upstream suite is claimed as passing. Cross-build
and App-UID checks are the native evidence recorded here.

## 6. Manual and remaining acceptance

Codex OAuth synthetic coverage includes six protocol tests and a renewal-classification
regression test. Live browser authorization, real quota and token renewal are separate
owner-authorized checks; no real credentials are used by the automated suite.

Use an owner-authorized test account for live checks; keep credentials out of logs
and committed screenshots. Still pending:

- [ ] Install and run on physical ARM64 hardware, including the native minimum API.
- [ ] Validate on a 16 KiB-page device and establish production APK/AAB coverage.
- [ ] Enter a valid Go key; compare 5-hour, weekly and monthly windows with the provider.
- [ ] Restart the app and verify credential persistence, then delete one Go account and verify sibling preservation.
- [ ] Exercise offline, timeout and rate-limit behavior through the user-facing UI.
- [ ] Verify background Go refresh updates widgets, notifications and the tile over time.
- [ ] Verify foreground/background overlap and cancellation during navigation on-device.
- [ ] Resolve and verify refresh-interval/Manual preference scheduling; the current setter only saves preferences.
- [ ] Verify account renewal for existing OAuth providers independently of Go API-key support.

Future provider ports must satisfy their own credential, mapping and runtime checks;
the Go results cannot be generalized to every provider compiled into the CLI.
