# CodexBar Android Test Plan

## 1. Evidence boundary and current baseline

This document owns test commands, evidence and remaining acceptance work.
[SPEC.md](SPEC.md) describes behavior; [CONTRIBUTING.md](CONTRIBUTING.md)
describes development setup. Compilation, smoke checks, negative authentication
tests and successful real-account quota retrieval are distinct gates.

The delivered `0.0.15-beta-native` artifact (versionCode 16) is the current
reference. The latest JVM result set is **263 tests, zero failures** at the
`0.0.15-beta-native` baseline commit `c73db11` (counted from the retained
Gradle test-report XML), with debug lint and native Kotlin compilation passing.
The signed ARM64 Release APK passed battery-flow UI checks on the shared API 36
emulator (x86_64 host with ARM64 translation): no startup battery prompt,
one-time optional offer on switching to periodic refresh, dismissal, repeated
enable and the permanent settings entry. This is not physical ARM64 hardware
proof, a background-timing guarantee, or full visual Mac parity.

### Pending work (Unreleased)

The native engine pin moved from v0.71.0 to **v0.76.0**
(`2d8b1409a1649dce60f3610ebe4eab52b92545de`). Native ARM64 Release compilation
**passed** (924 s, 2026-10-16). The Gradle gate passed 278 JVM tests, debug lint,
native Kotlin compilation and signed Release/Acceptance assembly. Updater fixtures
cover parsing, version comparison, download bounds, digest/size failures and signer
validation. Six isolated API 36 ARM64-bridge probes passed, including the actual
`CodexBar 0.76.0` version output, resources, configuration, status-only routing,
missing-credential handling and Manual scheduling persistence. The overall harness
run failed on a subsequent UI back-key timeout; it is not a full acceptance pass.
The exact 0.0.16 Release APK passed its manual GitHub update check on the same
emulator: About → Check updates correctly reports the installed version as current
relative to the published 0.0.15 release. Full download-to-installer acceptance
remains open because no newer production release is available to this build.

Live-account acceptance is confirmed only for Codex and OpenCode Go. Copilot
phone sign-in and the API-key providers added since 0.0.12 (Venice, Amp, z.ai,
Factory, Alibaba Coding Plan, Charm Hyper, Kimi, MiniMax) are covered by
synthetic parser/storage tests only; no live-account quota retrieval is claimed
for them. Claude and Gemini remain manual-credential setup paths with the
limitations recorded in [docs/providers.md](docs/providers.md).

Counts in the historical sections record their check runs at the time; they are
not restated per release. Neither the Mac comparison mockups nor source
compilation prove full Mac UI/UX parity.

### Production packaging and daily artifact

`nativeRelease` is the daily build: dashboard launcher only, no diagnostic
activity, `.native` identity, versionCode 16 / `0.0.15-beta-native`, minSdk 26,
ARM64-only. Release-optimized diagnostics use the separate `nativeAcceptance`
variant (`.native.acceptance`) that reuses the same ARM64 Release payload and
adds the smoke activity, synthetic UI demo and the fresh-install
cadence-persistence probe.

Signed ARM64 `0.0.15-beta-native` daily APK: **35,896,403 bytes**, SHA-256:

```text
27b65171f1098dc7a1747de147516a22270ff3bf1c51fe0e4f7fd81ac23ed34e
```

This exact APK was installed and passed the battery-flow UI checks above, then
delivered. Earlier artifacts' acceptance runs (native/repository probes,
provider settings rejection flows, preference persistence, synthetic quota UI
assertions for phone/dark/200%-text/wide layouts) belong to their recorded
versions below and are not retroactive evidence for 0.0.15.

### Historical artifacts

Signed ARM64 `0.0.7-beta-native` daily APK: **35,839,584 bytes**, SHA-256:

```text
5233271cb906beae613ddc5b8e3a3cc8ad20c7675ebcd5b2a9c0ef122b28feca
```

Signed `0.0.7` acceptance APK: **35,847,480 bytes**, SHA-256:

```text
923b67485ab35e9005e16e70e94d53ee494b0a4c6e13291aa704a61d4cf545e0
```

Historical `0.0.6-beta-native` daily APK: **35,817,287 bytes**, SHA-256:

```text
d1e0a029209f11bcf618cfa00b6df0b30231450cf349bf31110e6f6abb13f2b7
```

Signed `0.0.4-beta-native` ARM64 APK: **35,656,926 bytes**, SHA-256:

```text
d7761ce5d78d4310e1d503203e0532fdfd9f1075425677afe2d15d387cd77b69
```

These hashes identify the tested artifacts; later documentation/history edits
do not imply that a newly rebuilt APK will have identical bytes. Revalidate
changed payloads. The current daily artifact is the 0.0.15-beta-native APK
identified above.

The 0.0.7 locked acceptance run passed ten native/repository probes, four
provider settings rejection flows, Used/Manual preference persistence across
process restart, Codex sign-in entry and offline attribution; synthetic quota UI
assertions passed for phone, dark, 200% text, wide, detail, chart, credits and
display controls. Those were automated text/state assertions, not a completed
screenshot, TalkBack or launcher widget-host review, and used no real account
credentials. The machine-local report is
`build/native/device-report-arm64-v8a-release.json` (not a tracked artifact).

## 2. Verification scope by provider

| Provider | Ordinary refresh path implemented | Synthetic checks | Android App-UID evidence | Owner-authorized live quota |
| --- | --- | --- | --- | --- |
| Claude / Gemini | Kotlin | HTTP fixtures | Full account acceptance pending | Pending; manual-credential setup only (no phone OAuth) |
| Codex | Native OAuth on API 28+ native builds; Kotlin otherwise | Protocol, ownership, parser and HTTP fixtures | General CLI/missing-credential smoke | Owner confirmed Codex working on the phone (reported on the 0.0.4 Kotlin path; native-path live renewal remains owner-authorized pending) |
| OpenCode Go | Native API in native-engine builds | Parser, repository, account lifecycle | Invalid-key CLI and Settings rejection | Owner confirmed OpenCode Go working on the phone |
| OpenRouter | Native API in native-engine builds | Budget/balance parser, ownership, cache | Invalid-key native and Settings rejection passed | Pending |
| Copilot | Native API in native-engine builds | Premium/chat/plan-only parser and account isolation | Invalid-token and Settings rejection passed | Pending; phone device sign-in added in 0.0.14, live acceptance unverified |
| DeepSeek | Kotlin official balance API | Numeric/currency/unknown/zero and account-header HTTP fixtures | Invalid-key and Settings rejection passed | Pending |
| Venice, Amp, z.ai / GLM, Factory, Alibaba Coding Plan, Charm Hyper, Kimi, MiniMax | Native API in native-engine builds | Parser fixtures (success/partial/malformed/error redaction), repository ownership, storage round-trip | None yet | Pending — synthetic tests only, no live-account claim |

Codex device-code login is implemented with synthetic protocol checks. Owner-authorized
browser login, real quota retrieval and token renewal remain separate pending gates;
manual credentials remain available.

## 3. Host checks

From the repository root:

```sh
bash ./gradlew :app:testDebugUnitTest --max-workers=2
PYTHONDONTWRITEBYTECODE=1 python3 native/test_setup_toolchain.py
PYTHONDONTWRITEBYTECODE=1 python3 native/test_runtime_config.py
git diff --check
```

Attribution checks: `python3 native/collect_notices.py --check` verifies all 24
checked-in notice assets and their hashes, including exact copies of the Android
license and the root attribution document. Debug lint passes after routing API-34+ Quick Settings launches
through an explicit, immutable PendingIntent. The legacy overload remains guarded
to older SDKs; its SDK-insensitive lint warning is suppressed only on that handler.
About/launcher runtime acceptance is recorded below; tile visual acceptance remains pending.

The latest retained JVM result set contains **263 tests, zero failures** at the
`0.0.15-beta-native` baseline (`c73db11`), with debug lint and native Kotlin
compilation passing. The per-suite breakdown below is the historical itemization
from the earlier **128 tests across 26 suites** run (2026-10-10, source through
`b7f7dad`); later provider/onboarding/battery slices added suites without
re-itemizing:

| Suite | Count | Coverage |
| --- | ---: | --- |
| Claude repository | 9 | Existing HTTP/credential behavior |
| Codex repository | 13 | HTTP/credential behavior, renewal classification, draft rotation and native credential handoff |
| Gemini repository | 6 | Existing HTTP/credential behavior |
| OpenCode Go repository | 2 | Missing key avoids execution; rejected keys are retained |
| Native process runner | 3 | Dual-pipe output bounds, timeout/reaping, cancellation/reaping |
| Go CLI parser | 5 | Window mapping, provider/source isolation, invalid data, truncation, sanitized errors |
| Copilot CLI parser | 3 | Premium/chat identity, plan-only results, over-quota values, invalid data and sanitized auth rejection |
| DeepSeek repository | 3 | Funded currency selection, zero versus absent/malformed money, sibling headers and sanitized errors |
| OpenRouter repository | 1 | Explicit provider/key ownership and wrong-credential rejection before native execution |
| Codex device-code auth | 7 | Pending/success/PKCE, denial/expiry/deadline, slow-down timing, cancellation, oversized responses and redirect rejection |
| OpenRouter CLI parser | 4 | Balance-only versus capped budgets, zero/unknown, measurement age, malformed data, process failures and sanitized API errors |
| Account storage | 9 | In-place/restart migration, same-provider isolation, rename/delete/reconnect, stale publication, concurrent token CAS, failed writes, invalid identities and OpenRouter restart isolation |
| Account coordinator | 7 | Failed draft isolation, serialized rotation, independent siblings, late deletion results, cancellation, reconnect and overlapping refresh |
| Widget account cache | 5 | Legacy pins, deleted-owner isolation, generation snapshots, actual measurement age, invalid/absent data, money/budget round trips and the per-widget → connection → global display chain |
| Native Codex | 3 | Parser, private auth files, ownership and cleanup |
| Reported Codex credits | 3 | Unknown/zero balance, separate caps, inventory and malformed enrichment |
| Copilot device auth | 3 | Registration gate, device polling, expiry and cancellation |
| Official status | 2 | Source/indicator mapping, freshness and shared cache |
| Quota recovery | 3 | Confirmed recovery, rejected observations and durable receipts |
| Quota alerts | 6 | Depleted/low fire-once and rearm, per-cycle pace dedup, disabled and stale-reading guards, effective-work-day pace and overquota receipt retention (`2536445`) |
| Display options | 16 | Stable IDs, risk visibility, independent profiles, global fallback, marker mirroring and round-trips, plus explicit per-field inheritance/merge across the widget → account → global chain (`a4dc917`) |
| Quota pace | 7 | Segmentation, retention, estimates, sparse/invalid measurements, Mac UsagePace.weekly linear/work-day expectations and the seconds-based default weekly duration (`ffb975a`) |
| Quota presentation | 4 | Used/Remaining percent mapping and clamping; en/zh-TW pace ETA and percent string-resource formatting (`8297d87`) |
| Dashboard display | 1 | Saved display-preference updates reach the retained dashboard (`b7f7dad`) |
| Quota snapshots | 2 | Freshness, generation and cache mapping |
| Refresh worker | 1 | Terminal/transient retry classification |

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

## 4. Release build and artifact checks

Prepare the pinned tools and private signing environment as documented in
[native/README.md](native/README.md), then:

```sh
python3 native/build.py --tools "$SWIFT_ANDROID_TOOLS" --abi arm64-v8a --configuration release
bash ./gradlew :app:assembleNativeRelease :app:assembleNativeAcceptance --max-workers=2
```

Artifact: `app/build/outputs/apk/nativeRelease/app-nativeRelease.apk`.

Before transfer, verify that the exact APK is signed, non-debuggable and contains
only `arm64-v8a` native libraries. The Codexbar launcher must resolve to
`MainActivity`. For example, using an installed Android SDK build-tools version:

```sh
export ANDROID_BUILD_TOOLS="$ANDROID_HOME/build-tools/36.0.0"
"$ANDROID_BUILD_TOOLS/apksigner" verify --verbose app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
"$ANDROID_BUILD_TOOLS/aapt2" dump badging app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
sha256sum app/build/outputs/apk/nativeRelease/app-nativeRelease.apk
```

`native-code` should list only ARM64, and `application-debuggable` must be absent.
Use the same APK for remote acceptance and delivery, identified by its SHA-256.
Do not rebuild solely to transfer an already verified artifact.

## 5. Locked Android runtime acceptance

Configure `ANDROID_TEST_ADB_WRAPPER` and `ANDROID_TEST_SESSION_WRAPPER` through
local operator configuration. Prefer the Release variant to reduce remote transfer:

```sh
python3 native/test.py --abi arm64-v8a --configuration release --opencode-go
# Exercise all added providers and their masked-draft rejection paths:
python3 native/test.py --abi arm64-v8a --configuration release --opencode-go --openrouter --copilot --deepseek
```

The harness holds one shared-runtime lock through discovery, installation, launch,
assertions and cleanup. It refuses to replace an existing test package. Release
results are collected from a dedicated logcat tag using a unique run ID, without
clearing shared logs or enabling `run-as` on the non-debuggable APK.

The probes are:

1. CLI launch/version command exits successfully and includes the packaged numeric version.
2. Resource smoke initializes an actual bundled provider plugin and emits its success marker.
3. Isolated provider configuration validates.
4. Credential-free `--status-only` takes the status route rather than quota fetching.
5. Codex without credentials emits the expected provider-error JSON; exit 1 is intentional.
6. The fresh-install cadence probe verifies scheduling, Manual cancellation and startup reapply.
7. With `--opencode-go`, the production Go client makes an HTTPS request with a fixed invalid key and
   maps its rejection to the expected authentication failure.
8. With `--openrouter`, the production OpenRouter client rejects a fixed invalid key;
   its Settings draft stays masked and is not published after validation fails.
9. With `--copilot`, the native Copilot source rejects a fixed invalid GitHub token.
10. With `--deepseek`, the Kotlin balance API rejects a fixed invalid key.

Release commands select `.native.acceptance`. Run the daily artifact's separate
launcher/sign-in/notices/no-diagnostics checks with `--product-only`, and synthetic
phone/dark/200%-text/wide/detail/chart/credits/display checks with `--ui-demo`:

```sh
python3 native/test.py --abi arm64-v8a --configuration release --product-only
python3 native/test.py --abi arm64-v8a --configuration release --ui-demo
```

Release UI checks also verify the dashboard launcher, Codex sign-in entry, offline
MIT text and system display name. Notification/battery prompts are handled before
assertions; no browser authorization is started. License and system-icon screenshots
remain ignored runtime artifacts for visual review.

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

## 6. Recorded evidence

Current baseline verification: **2026-10-14** (0.0.15 battery-flow check).
Earlier rows retain their historical scope and do not establish current acceptance.

| Gate | Recorded result |
| --- | --- |
| 0.0.15 host gate | 263 JVM tests, debug lint and native Kotlin compilation passed |
| 0.0.15 signed delivery | Exact ARM64 Release APK (35,896,403 bytes, SHA-256 in §1) installed; battery-flow UI passed on the API 36 emulator (x86_64 with ARM64 translation): no startup prompt, one-time offer, dismissal, repeated enable, process restart, settings entry; then delivered. Not physical ARM64 or background-timing proof |
| 0.0.7 host gate | 128/128 JVM tests (26 suites), Debug lint, NativeDebug Kotlin compilation, three Python tool/runtime-config tests and 24 notice checks passed |
| 0.0.7 signed builds | Native Release and Native Acceptance R8 builds passed; signature, ABI, non-debuggable manifest and diagnostic isolation checked |
| 0.0.7 isolated acceptance | Ten probes, four rejected-draft provider flows, display/cadence restart persistence, sign-in entry, licenses and synthetic UI matrix passed on the API 36 ARM64 bridge; daily app preserved |
| JVM suite | 100/100 passed; Debug/Native Release/Native Acceptance lint and native compilation passed |
| Toolchain integrity check | Passed; three official cached archive hashes matched |
| Full Android Core/CLI compilation | x86_64 and ARM64 passed |
| Initial App-UID smoke | 4/4 on x86_64 and 4/4 on ARM64 native-bridge path |
| Optimized ARM64 APK | Release/R8/resource shrinking and signature checks passed |
| Go native API acceptance | 5/5 runtime probes passed with synthetic credentials |
| Go Settings acceptance | Masked draft, native validation rejection and no saved account passed |
| OpenRouter acceptance | 6/6 combined native probes; Go and OpenRouter masked draft/rejection/no-publication UI flows passed |
| Added-provider runtime | Eight synthetic probes passed, including Go/OpenRouter/Copilot/DeepSeek rejection; all four masked draft/rejection/no-publication UI paths passed |
| Product UI / original artwork | Dashboard launcher, Codex sign-in entry, offline MIT reader and system Codexbar label passed; screenshot review confirmed original artwork under the system mask |
| Production acceptance (0.0.5-beta) | Six App-UID probes on the acceptance build (incl. numeric CLI version, status-only, manual-cadence persistence) and daily-APK product UI passed on the API 36 ARM64-bridge emulator |
| Synthetic detail (0.0.5-beta) | Phone, dark, 200% text, wide, detail, chart, credits and display-controls harness assertions passed; not TalkBack or widget-host acceptance |
| UI/UX slice smoke (0.0.6-beta) | Signed daily APK installed and launched on the API 36 ARM64-bridge emulator: dashboard empty state, settings directory (all seven pages listed) and provider page rendered with no runtime crashes; widget hosts, tiles and live account flows not re-exercised |
| Owner live confirmation | Owner reported Codex and OpenCode Go quotas working on the phone (0.0.4-beta-native, Kotlin Codex path); native-path live renewal remains owner-authorized pending |
| Test cleanup | Owned test installation removed before releasing the lock |

Runtime evidence is from an **Android 16 / API 36 x86_64 emulator** supporting an
ARM64 native bridge. It is not physical ARM64 hardware evidence.

The provider run passed eight probes and all four rejection UI flows. Product UI
was checked separately on the same APK after fixing test setup for startup prompts;
an intervening rerun encountered a remote ADB tap timeout. The final focused run
passed all four base probes plus product UI and removed its test installation.
The APK's complete MIT text matches `LICENSE` byte-for-byte; the reader scrolls.

The first optimized build reduced APK size from **76.91 MiB to 33.28 MiB (56.7%)**.
Uncompressed CLI size changed from 142.14 to 73.47 MiB, and the C++ runtime from
9.05 to 1.36 MiB. These are artifact measurements, not performance benchmarks.

The upstream Swift `make check` attempt stopped because `plutil` was unavailable;
plain `make test` stopped because the host `swift` was not on PATH in the isolated
cross-compilation setup. Neither upstream suite is claimed as passing. Cross-build
and App-UID checks are the native evidence recorded here.

## 7. Feature slice evidence history

Per-slice results at the time each feature landed. Test counts are historical
run counts for that slice, not the current total.

### Reported credits

100 JVM tests, Debug lint and NativeDebug Kotlin compilation pass. Added fixtures
distinguish zero/unread balance and empty/unknown inventory, preserve separate caps,
units and expiry through serialization, and keep good quota after malformed optional
enrichment. Real-account inventory availability has not been independently verified.

### Display profiles

97 JVM tests, Debug lint and NativeDebug Kotlin compilation pass. Fixtures cover
stable metric IDs across labels/disappearance, hidden-risk retention, independent
dashboard/widget/account choices, persistence, reconnect and deletion. The delivered
Release synthetic detail demo reached display controls; widget-host interaction and
the subsequent Mac row/header changes still need acceptance.

### Quota history and pace

95 JVM tests, Debug lint and Native Debug compilation pass. Pace fixtures cover a
fixed reset cycle, moving resets, source changes, recovery, gaps, sparse/unknown data,
duplicate/backwards/future measurements, over-quota data, exact 48-sample retention,
restart/generation isolation, sibling deletion and 14-day expiry. The delivered
Release synthetic detail demo reached the chart, credits and display controls;
TalkBack and physical-device accessibility remain unverified.

### Confirmed quota recovery

90 JVM tests, Debug lint and Native Debug compilation pass. Fixtures reject timestamp
jitter, first observations, stale/future/legacy readings, source changes and repeated
rolling events. Encrypted receipt tests cover restart, sibling isolation, reconnect
and deletion. Device notification permission/display acceptance is pending.

### Official service status

87 JVM tests, Debug lint and Native Debug compilation pass. Fixtures cover every
Core status indicator, stale/future checks, provider/source mismatch, unknown failures,
concurrent accounts, five-minute throttling and persisted-cache restart. Optimized
ARM64 Core build passed. The App-UID `--status-only` probe now passes on device after
fixing the Android flag lookup to the ArgumentParser property name (`statusOnly`);
the earlier dashed-name check silently fell through to the quota path.
No fixture or unknown response proves a real service healthy.

### Responsive account widgets

85 JVM tests, Debug lint and Native Debug compilation pass. Existing widget-cache
tests cover legacy pin migration, sibling deletion and generation isolation.
Single-account/overview binding, resize, cancelled/reconfigured pins, refresh actions
and R8 launcher rendering require final locked Release acceptance; compilation alone
is not widget-host acceptance.

### Background refresh reliability

85 JVM tests and Debug lint pass, including terminal/transient retry classification
and simultaneous foreground/worker coalescing with retained rotated credentials.
Native Debug compilation also passes. The acceptance build's fresh-install probe passed
on device: a saved 15-minute cadence schedules the unique periodic work, Manual (0)
cancels it and it stays cancelled across startup reapply, and the legacy second token
writer is never scheduled. Long-run OEM doze behavior remains an observation target.

### Compact dashboard and detail

- 84 JVM tests, Debug lint, native Debug Kotlin compilation and optimized native
  Release assembly passed after the compact UI/resource changes.
- `native/test.py --abi arm64-v8a --configuration release --ui-demo` exercises
  fixed synthetic Compose readings on phone, dark, 200% text and wide layouts,
  plus principal/model detail semantics; retained captures use `build/native/quota-*`.
  These fixtures never read credentials or make quota requests.
- Runtime/visual results are recorded separately from compilation. TalkBack,
  OEM launchers and the owner's real accounts remain separate acceptance.
- ARM64-on-x86_64 native-bridge Release runtime passed the four CLI probes,
  dashboard/sign-in/notices checks and all five synthetic UI layouts. Visual review
  confirmed remaining-first bars and principal/detail separation; forced-theme demo
  system-bar contrast was corrected. Detail panels are scrollable, with bottom-action
  assertions added to the harness. Physical ARM64 and TalkBack remain unverified.

### Shared snapshots

84 JVM tests and Debug lint passed, including owner-to-cache failure publication.

Fixtures cover repeated labels with distinct IDs, model pools, duration/source and
measurement-age preservation across transient failure and cache restart, reconnect
isolation, authentication invalidation, retention pruning, future timestamps,
over-quota rounding and reset-due wording. Existing legacy-cache tests retain missing
and invalid-value coverage. Final all-surface Release/runtime acceptance remains a
separate gate from these JVM checks.

### Copilot device sign-in

81 JVM tests and Debug lint passed after this addition.

Synthetic tests cover the registered client/scope/grant, pending authorization,
unregistered configuration, untrusted verification URLs, denial, expiry, five-second
slow-down and cancellation. Shared bounded transport retains the Codex redirect and
oversized-response checks. At this historical slice the button was disabled.
Version 0.0.14 enabled the public client used by upstream, with the Visual Studio
Code consent explanation; live OAuth/Copilot eligibility remains unverified.

### Native Codex ownership

- 78 JVM tests and Debug lint pass, including native Codex parser/error mapping,
  private-file permissions, real freshness, orphan cleanup and published-pair-before-retry.
- Optimized ARM64 Core/CLI compilation and reverse application of `native/android.patch`
  pass. This proves compilation, not App-UID or real native Codex quota acceptance.
- User reported Codex and OpenCode Go working on the phone with `0.0.4-beta-native`.
  That version used Kotlin for Codex, so this is not evidence for the new native path
  or automatic token renewal. No real-account request was made by the test runner.
- The packaged native runtime passed the general App-UID probes in §5. Successful
  native Codex quota and token renewal with an authorized real account remain pending.

## 8. Manual and remaining acceptance

Codex OAuth synthetic coverage includes bounded polling, cancellation, absolute expiry,
PKCE exchange and renewal-classification regression tests. Live browser authorization, real quota and token renewal are separate
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
- [x] Refresh-interval/Manual scheduling: setter reschedules immediately, startup
  reapplies the saved cadence, and the acceptance probe confirmed schedule/cancel/
  reapply on device. Long-run OEM behavior remains observational.
- [ ] Verify account renewal for existing OAuth providers independently of Go API-key support.

Future provider ports must satisfy their own credential, mapping and runtime checks;
the Go results cannot be generalized to every provider compiled into the CLI.
