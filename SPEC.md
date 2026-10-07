# CodexBar Android Technical Specification

## 1. Purpose and current scope

CodexBar Android presents AI-service quota information through a Compose dashboard,
Glance widgets, a Quick Settings tile and local notifications. The native integration
reuses upstream Swift `CodexBarCore` through an Android-targeted `CodexBarCLI` process.

This document describes the implemented architecture and its boundaries. Build
instructions live in [CONTRIBUTING.md](CONTRIBUTING.md) and the
[native build guide](native/README.md); verification evidence lives in [TEST.md](TEST.md).

| Provider | Current fetch implementation | Credential |
| --- | --- | --- |
| Claude | Existing Kotlin repository / HTTP services | Access token; refresh token for renewal |
| Codex | Existing Kotlin repository / HTTP services | Access token, refresh token, optional account ID |
| Gemini | Existing Kotlin repository / HTTP services | Access token, refresh token, OAuth client ID and secret |
| OpenCode Go | Native Swift Core/CLI, API source | API key |

OpenCode Go is exposed in native-engine build variants. Compiling the complete CLI
does not establish Android support for every upstream provider or desktop source.

## 2. Components and ownership

### Account-storage transition

`AccountConnection` separates a local connection ID, provider, editable name and
configuration generation. New IDs/generations are random UUIDs; adopted legacy IDs
retain their provider namespace for downgrade compatibility. `EncryptedPrefsManager`
keeps the existing encrypted file/Keystore and adopts legacy entries without moving
or deleting credentials. Metadata discovery is idempotent and preserves global
settings and reset receipts. No DataStore or encryption-format migration occurs.

Draft objects are not persisted. The caller validates first, then publishes with
`saveValidatedConnection`; reconnect compares the captured generation and keeps the
connection ID. Rename preserves generation; token rotation compares both generation
and expected credentials. Optional credential fields are cleared on replacement.
`publishIfCurrent` excludes deletion/reconnect during a small synchronous publication;
network/suspending work must remain outside that lock. Deleted accounts cannot be
recreated by these connection-aware APIs. Failed synchronous writes fail closed until
restart, because SharedPreferences may change its memory before reporting disk failure.

Rollback: an older build can still read unchanged legacy credential namespaces from
the same encrypted file. New UUID accounts remain on disk but are invisible to that
build. Do not use an old build to edit accounts or run its destructive credential
reset; restore the newer build to recover access to new accounts. Android Keystore
backup/restore is not replaced by this source-level compatibility path.

This is the storage foundation. Existing repository/UI/worker entry points still use
the legacy APIs until all-surface routing lands; end-user multi-account support and
foreground/background lifecycle acceptance are not yet complete.

Repositories also accept a `CredentialSession` containing an explicit connection and
request-local credentials. Draft rotation changes memory only; saved-account callers
supply a generation/credential compare-and-set writer. A rejected write propagates
cancellation before retrying with late tokens. Codex fetch rejection retains the
account for reconnect. Legacy no-argument entry points remain during the transition;
this overload alone does not establish a single foreground/background refresh owner.

Settings includes an offline About/license reader. The checked-in asset bundle
contains original Android and native upstream notices, static runtime dependencies
and provenance hashes. `NOTICE.md` records unofficial identity, research credit
and the decision to retain the Android-origin launcher vectors. License collection
is a developer operation; app builds and the reader need no network or toolchain.

```text
app/src/main/java/com/codexbar/android/
├── feature/dashboard/       Compose cards and foreground refresh state
├── feature/settings/        Credential input, validation and preferences
├── core/domain/             AiService, Credential, QuotaInfo, Result, QuotaRepository
├── core/data/               Provider repository implementations
├── core/nativecli/          Native process execution and OpenCode Go result mapping
├── core/network/            Existing Kotlin HTTP clients and OAuth refresh services
├── core/security/           Encrypted preference storage
├── core/workmanager/        Scheduled quota and token refresh
├── core/widget/             Widget configuration and cached quota presentation
├── core/notification/       Snapshot/reset notifications
├── core/tile/               Quick Settings presentation
└── di/                     Hilt bindings

native/
├── toolchains.json          Official tool archives, versions and checksum pins
├── setup_toolchain.py       Verified toolchain download and extraction
├── android.patch            Android adaptations to the pinned upstream source
├── build.py                 CLI compilation and APK payload staging
└── test.py                  Locked device acceptance and report collection
```

`QuotaRepository` is the shared boundary for fetch and credential validation.
Provider-specific adapters produce the existing `QuotaInfo` model; UI and widget
code do not parse native stdout. Hilt supplies a singleton native client, while
ViewModels and workers own the lifetime of their suspending requests.

## 3. OpenCode Go data flow

```text
Settings input → EncryptedPrefsManager
                         ↓
Settings Validate / DashboardViewModel / QuotaRefreshWorker
                         ↓
OpenCodeGoRepositoryImpl → NativeCodexBarClient
                         ↓
libcodexbar.so usage --provider opencodego --source api --json
                         ↓
upstream API fetcher → OpenCodeGoCliParser → QuotaInfo
                         ↓
dashboard cards / background widget cache / notifications / tile refresh
```

The upstream fetcher issues an authenticated HTTPS GET to the OpenCode Go usage
API. `--source api` is explicit: this integration does not use local desktop usage
estimates, browser-cookie discovery or another tool's saved account.

### Credential contract

- The Settings field is masked and uses the existing encrypted preference store.
- Surrounding whitespace is trimmed when saving. Blank input removes the saved Go
  credential; the native client rejects remaining whitespace/control characters.
- The key is supplied through the child's `OPENCODE_API_KEY` environment variable,
  not command arguments or a plaintext credential file.
- Go API keys have no OAuth refresh token. `TokenRefreshWorker` skips renewal for
  this credential type, and authentication failures do not delete the saved key.
- The client starts with an explicitly constructed environment, including isolated
  provider homes and XDG paths. Only selected Android platform variables are inherited.
- Raw child stderr, provider error messages and request exceptions are not forwarded
  to user-facing errors by the Go adapter.

## 4. Native execution contract

`NativeCodexBarClient` serializes its requests with a coroutine mutex. Each request
creates an app-private workspace under `noBackupFilesDir`, copies the packaged
resources, writes a non-secret provider configuration and deletes the workspace
after process cleanup. Copying resources per request avoids stale assets after an
app update, at the cost of repeated file I/O.

`CliProcess` uses `ProcessBuilder` with an argument list and a cleared/rebuilt
environment. It closes stdin and drains stdout/stderr concurrently. Each stream
retains at most **1 MiB** while continuing to drain excess bytes, preventing pipe
deadlock; truncated output is rejected by the parser.

The Go invocation allows **45 seconds for process completion**. Mutex waiting,
asset preparation and bounded cleanup waits are additional time, so this is not
a 45-second end-to-end latency guarantee. Timeout or coroutine cancellation kills
and waits for the direct child, closes streams and shuts down reader threads.

The runner manages **direct children only**. The app's supported native path is an
API fetch; arbitrary desktop commands, PTYs and descendant process trees are not
part of this contract. The child runs under the app's UID and is not a separate
security sandbox from its host.

## 5. JSON-to-domain contract

The Go parser accepts one JSON-array envelope with `provider: "opencodego"` and
`source: "api"`. A successful reading additionally requires a zero exit code,
valid usage data and at least one measured window.

| CLI field | Android projection |
| --- | --- |
| `usage.primary` | `5-Hour` window |
| `usage.secondary` | `Weekly` window, when present |
| `usage.tertiary` | `Monthly` window, when present |
| `usedPercent` | Used fraction: `usedPercent / 100` |
| `resetsAt` | Optional ISO-8601 reset instant |
| `usage.updatedAt` | Fetch timestamp supplied by the CLI |

Missing windows and synthetic placeholders are omitted, not fabricated as zero
usage. Percentages must be finite and nonnegative; over-quota values remain above
1.0 in the domain model. The dashboard's remaining-quota gauge separately clamps
its display to 0–100%. Invalid timestamps, malformed output, wrong provider/source,
nonzero successful-payload exits and truncated output produce failures.

Known credential errors map to `AuthError`; HTTP 429 and 503 messages map to the
existing rate-limit and unavailable states. Other errors become sanitized generic
messages. This error classification depends on the pinned CLI's error wording.
The Go API adapter currently maps usage windows, not Zen prepaid balance or history.

## 6. Packaging and platform contract

The build pins upstream CodexBar **v0.71.0** at
`cb5f0cbe88615a441272c6f9e44675bf594a3fa3`. Android changes remain a reviewable patch;
downloaded source, compilers and generated binaries are excluded from Git.

The executable targets Android/Bionic using the official Swift Android SDK. Swift
libraries are linked statically; the matching NDK C++ runtime is bundled alongside
the executable. The existing SQLite dependency is built statically with the NDK.

Executables are packaged as native libraries and extracted for execution from
`nativeLibraryDir`. Non-executable resources are copied from assets into private
storage and located through `CODEXBAR_RESOURCE_BUNDLE_PATH`. `$ORIGIN` runtime
lookup resolves the sibling C++ library. Executable code is supplied at build time.

| Variant | Native engine | Package / entry point |
| --- | --- | --- |
| `debug`, `release` | Not packaged; Go setting hidden | Original app package and dashboard |
| `nativeDebug` | Selected prebuilt ABI payload | Separate `.native` package; explicit smoke activity |
| `nativeRelease` | ARM64 Release payload | Separate `.native` package; self-test launcher with dashboard button |

`nativeRelease` is non-debuggable, signed with the operator-provided key, and uses
R8/resource shrinking. Swift uses `-Osize`, linker section collection and symbol
stripping. Build commands and exact paths are owned by [native/README.md](native/README.md).

The Android app minimum remains **API 26**; native execution requires **API 28**.
Native subprocess directory-change actions require API 34 and return `ENOTSUP`
below it. Native ARM64 hardware, API 28 minimum-device behavior and 16 KiB-page
devices remain separate validation targets.

## 7. Scheduling and persistence

Foreground refresh returns provider results to the dashboard. The background quota
worker includes configured Go accounts through the same repository, caches successful
readings for widgets and updates notifications/tile state. Widget cache storage is
separate from encrypted credentials.

WorkManager schedules require network connectivity and are best-effort rather than
precise timers. Initial scheduling currently uses the 30-minute default. The Settings
screen persists the selected interval, but its setter does not reschedule WorkManager;
the Manual/interval controls are therefore not a verified scheduling contract yet.

## 8. Current limits and acceptance boundary

- Only OpenCode Go is integrated with the native data layer; the other three providers
  retain their Kotlin implementations and existing authentication behavior.
- Runtime acceptance covers an API 36 emulator, including its ARM64 native bridge.
  That is distinct from physical ARM64 hardware verification.
- Automated Go API/UI checks use an invalid synthetic key. Real-account quota
  accuracy, long-running background behavior and OAuth renewal are not proven by them.
- The native CLI currently prints `CodexBar` without a numeric version because its
  version-file lookup expects a file next to the executable.
- Claude refresh-token failures reported by the Android upstream are not resolved
  by this native integration.

See [TEST.md](TEST.md) for runnable checks, measured results and pending acceptance.
