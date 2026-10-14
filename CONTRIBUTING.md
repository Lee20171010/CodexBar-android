# Contributing to Codexbar for Android

Bug reports, feature requests and pull requests are welcome. Please read this
first; the project has strict provenance and verification rules that shape how
changes are made.

## Development setup

1. **Prerequisites**
   - JDK 21
   - Android SDK with platform 35 (`ANDROID_HOME` or ignored `local.properties`)
   - Python 3.12+ (native build and test harness scripts)
   - For rebuilding the native engine: x86_64 Ubuntu 24.04 with the Swift 6.4.0
     Linux toolchain — see [native/README.md](native/README.md).

   A clean clone ships **no prebuilt native payloads**. Debug builds run without
   the native engine (`NATIVE_CLI_ENABLED=false`); native variants need payloads
   built once via `native/build.py` (staged under `build/native/`) or an
   operator-supplied verified payload cache. Day-to-day app work does **not**
   require rebuilding the engine.

2. **Clone and build**

   ```bash
   git clone https://github.com/Lee20171010/CodexBar-android.git
   cd CodexBar-android
   bash ./gradlew :app:assembleDebug --max-workers=2
   ```

3. **Run tests**

   ```bash
   bash ./gradlew testDebugUnitTest --max-workers=2
   bash ./gradlew :app:lintDebug --max-workers=2
   ```

   Python harness self-checks (no device needed):

   ```bash
   python3 native/test_setup_toolchain.py
   python3 native/test_runtime_config.py
   python3 native/collect_notices.py --check
   ```

## Project structure

```
app/src/main/java/com/codexbar/android/
├── core/
│   ├── domain/          # Models (AiService, QuotaInfo, Credential), repository interfaces
│   ├── data/            # Repositories (Kotlin HTTP + native CLI bridges), coordinator
│   ├── nativecli/       # NativeCodexBarClient: subprocess bridge to the bundled Swift CLI
│   ├── network/         # Kotlin HTTP clients (claude, codex, copilot, deepseek, gemini)
│   ├── security/        # EncryptedPrefsManager: Keystore-backed credential + prefs storage
│   ├── presentation/    # Display options chain, pace, alerts, formatting
│   ├── widget/          # Glance widgets, tile, prefs, display-chain helpers
│   ├── workmanager/     # Periodic refresh + token refresh workers
│   └── util/            # Battery helper, misc
├── feature/
│   ├── dashboard/       # Dashboard VM/screens
│   └── settings/        # Settings VM/screens, onboarding guidance
└── di/                  # Hilt modules
app/src/test/            # JVM unit tests (JUnit 4 + Mockito, no device)
native/                  # Swift Core/CLI build/packaging/acceptance harness — see native/README.md
docs/                    # providers.md matrix, ui.md design mapping, historical artifacts
```

Ownership boundaries that matter for reviews:

- The native engine is **upstream code**. Android-side changes to it live only
  in `native/android.patch` against the pinned revision; edits inside the
  ignored `upstream-codexbar/` checkout are not deliverables.
- Credentials belong to the app's encrypted storage. Never introduce concurrent
  token-refresh writers, plaintext credential logging, or runtime-downloaded
  executables.
- Executables are packaged at build time as `lib*.so` and run from
  `nativeLibraryDir`; non-executable resources are copied to app-private
  storage.

## Adding or changing a provider

A provider slice is complete only when all of these exist (see any recent
provider commit as the template):

1. `AiService` entry (display name, brand color, base URL, verified
   `keyPageUrl` or an explicit null with a reason comment).
2. `Credential` type + `EncryptedPrefsManager` serialization branches.
3. Native client provider/env mapping and/or Kotlin network client.
4. Parser mapping from the CLI JSON envelope to `QuotaInfo` (units preserved,
   unknown kept unknown; no invented windows).
5. Repository + Hilt module + `AccountQuotaCoordinator` registration +
   `SettingsViewModel` credential branch + settings UI guidance.
6. Tests: parser fixtures (success, partial, malformed, error redaction),
   repository wrong-credential test, storage round-trip row, coordinator
   registration.
7. `docs/providers.md` row with honest verification status.

Provider claims must be source-backed: env var names, endpoints, error strings
and units must match the pinned upstream fetcher, and official key-page URLs
must be verified against the provider's own site.

## Updating the pinned engine

1. Update the revision pin in `native/build.py` and `NOTICE.md`, and keep the
   `CORE_CLI_VERSION` BuildConfig field in `app/build.gradle.kts` in sync with
   the new engine version.
2. Rebase `native/android.patch`; record any new Android adaptations there.
3. Re-verify the JSON contract (`SPEC.md` §JSON-to-domain), credential
   ownership, and provider acceptance.
4. Regenerate notices: `native/collect_notices.py --tools "$SWIFT_ANDROID_TOOLS"`,
   then `--check` passes offline.

## Build variants

| Variant | Purpose |
| --- | --- |
| `debug` | Development; `NATIVE_CLI_ENABLED=false`, so the native engine is not used even when payloads are staged |
| `nativeRelease` | Daily delivery: non-debuggable, ARM64, R8-shrunk, signed |
| `nativeAcceptance` | Same payload + fixed diagnostic probes, separate package |
| `nativeDebug` | Debugger/`run-as` diagnostics only |

Release signing uses the operator keystore via `ANDROID_KEYSTORE_PATH` plus
password/alias environment variables; nothing signing-related is committed.
Full commands: [native/README.md](native/README.md).

## Code style

- Match the surrounding code: Compose UI, kotlinx.serialization, Hilt,
  Kotlin conventions already in the tree.
- Keep changes minimal and scoped; one logical unit per commit with a concise
  imperative Conventional Commit subject.
- Comments state the *why* (upstream source references, adaptation reasons).
- New user-facing copy ships in both `values/` (English) and `values-zh-rTW/`.
- Tests accompany new logic; run `testDebugUnitTest` and `lintDebug` before
  review.

## Verification honesty

Documentation and commit messages must distinguish: compilation, JVM tests,
synthetic device probes, live-account acceptance, and physical-hardware
behavior. An error JSON response is not proof of quota retrieval; a synthetic
fixture pass is not live acceptance. `TEST.md` records artifact-level evidence.

## License

By contributing, you agree that your contributions are licensed under the
project's MIT license, with upstream notices preserved per `NOTICE.md`.
