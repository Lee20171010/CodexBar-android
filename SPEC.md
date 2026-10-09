# CodexBar Android Technical Specification

## Current implementation and design status

The delivered `0.0.5-beta-native` artifact contains the account, provider, snapshot,
scheduling, widget, status, recovery, history and display features described here.
`TEST.md` scopes the evidence to that artifact; implementation is not complete live
or physical-device acceptance. Current source additionally changes the dashboard
metric rows and card headers toward Mac CodexBar's design language (`e114632`).
Those presentation changes still need a new Release artifact and runtime acceptance.

Mac UI/UX alignment covers settings, features, display and interactions, not just
card styling. [docs/ui.md](docs/ui.md) owns the implementation/target distinction.
The Mac v0.73.0 design reference does not change the native v0.71.0 source pin.
SwiftUI/AppKit presentation, desktop credential discovery and local usage/spend
collection are not automatically exposed by the bundled CLI or Android adapters.

## Reported Codex inventory

Native Codex quota preserves optional credit balance, workspace ownership, personal
credit cap and reset-credit inventory. Credits are units, never assumed currency.
Unread balance remains unknown; confirmed zero and an empty inventory remain zero.
Each optional section retains its measurement time; reset items retain provider type,
status and expiry. Malformed enrichment is omitted without failing valid quota.
Detail displays this read-only inventory when amounts are enabled. No redemption
operation is exposed. Kotlin API 26–27 compatibility does not promise this enrichment.

## Display profiles

Account overview preferences use stable window IDs, show-amounts and absolute-reset
switches. Detail retains every quota window. Hidden windows still participate in
pace, recovery and risk; near-exhausted hidden metrics show a warning. Each widget
stores its own per-account profile, independent of dashboard choices. Restore
Defaults clears only that profile. Disappearing metrics retain their hidden ID;
renaming/localizing labels does not change selection. Account deletion clears its
profiles; widget deletion clears only that widget's profile. These preferences never
alter credentials, ownership, fetch sources or scheduling.

## Quota history and pace

History uses the no-backup display cache, keyed by account generation and stable
principal window ID. Retain at most 48 samples per window, 16 windows per account,
and 14 days. Only successful fresh readings enter history; duplicate, out-of-order,
future and legacy readings do not add samples. Changed source, reset, duration,
increased remaining capacity or a gap over six hours starts a new segment. Account
deletion removes history and its generation marker. Reads prune expired samples.

One principal-window chart uses epoch-second differences, not floating-point epoch
timestamps. A linear estimate requires three samples spanning at least 15 minutes,
with a latest sample no older than six hours. No projection continues past a known
reset. Unknown duration/reset omits the even-use guide. Estimates are not guarantees.

## Confirmed quota recovery

Recovery alerts are opt-in and default off. A notification requires the same account,
generation, source, and stable window ID to move from at least 95% used to at most
80% used in a fresh measurement. The thresholds are uniform across providers;
provider-specific recovery evidence remains a refinement. Reset timestamps, first cache loads,
legacy data, stale or future readings, and cache restoration cannot trigger it.
The durable receipt is written before display and suppresses repeats for the reported
window duration, or one day when unknown. Notification content is account name,
window label and remaining percent only.

## Official service status

Android's `usage --status-only` CLI route invokes Core status metadata/fetchers before
creating any credential or quota context. Codex, Claude and Copilot have official
sources in the pinned Core; other providers remain unavailable rather than healthy.
Status cache is provider-scoped, shared by all accounts, throttled to five minutes,
and considered unknown after thirty minutes or clock rollback. It never changes a
quota result or infers an incident from quota/auth/network errors. Detail shows
source and independent check age; overview only surfaces recently reported incidents.

## Widget families

Overview and pinned-account widgets share generation-scoped snapshots and explicit
stable connection pins. Compact layouts show the most constrained principal window;
taller pinned widgets show additional principal windows and reset progress. Overview
capacity grows from one to three accounts with height, disclosing omitted pins.
Deleted pins remain unavailable until reconfiguration, never selecting a sibling.
Configuration defaults to cancelled and publishes only on confirmation. A widget's
refresh enqueues explicit work for its own still-existing pins; opening leads to the
dashboard. Both families update when account state changes.

## Scheduled refresh ownership

The Application supplies HiltWorkerFactory to on-demand WorkManager initialization.
Startup, boot and cadence changes use the saved encrypted preference. Manual cancels
periodic quota work, legacy token work and automatic per-account retries. Explicit
one-shot refresh remains available. Each account/generation gets unique work with
network constraints and exponential transient backoff; terminal authentication is
suppressed until reconnect. Foreground and worker overlaps share one in-flight
account result; siblings retain independent retry outcomes. Android's 15-minute
periodic floor and background execution policy remain best-effort constraints.

## Shared measurement snapshots

Dashboard, widgets, persistent notification and tile read connection/generation-scoped
snapshots. A successful measurement owns its source, stable window IDs, durations,
model-pool classification and timestamp. A transient failure retains that measurement
with a typed failure and separate attempt time; authentication rejection removes its
values. Reconnect never adopts the previous generation's reading. Saved account order
is stable, and model-only pools do not become notification/widget general headlines.

Readings older than one hour are stale; successful data is retained at most seven
days. A clock rollback marks data stale, and a timestamp more than five minutes in
the future is unavailable. A past reset means refresh to confirm, not recovered quota.
All surfaces use the same remaining-percentage rounding, age and reset semantics.

The existing private widget preferences now store versioned structured snapshots;
old label-keyed entries are read as legacy/stale until a real refresh replaces them.
Credentials and account identity are untouched. Downgraded builds may show no cached
reading until refresh; the cache is disposable and can be repopulated without changing
accounts. Cloud backup and device-transfer extraction exclude app data, including
measurement caches. No credentials are stored in snapshots.

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
| Codex | Native Swift Core/CLI, explicit OAuth on API 28+ native builds; Kotlin compatibility path otherwise | Android-owned access/refresh pair, optional account ID |
| Gemini | Existing Kotlin repository / HTTP services | Access token, refresh token, OAuth client ID and secret |
| OpenCode Go | Native Swift Core/CLI, API source | API key |
| OpenRouter | Native Swift Core/CLI, API source | API key |
| GitHub Copilot | Native Swift Core/CLI, API source | GitHub OAuth token with Copilot access |
| DeepSeek | Kotlin public balance API | API key |

OpenCode Go, OpenRouter and Copilot are exposed in native-engine build variants. Compiling the complete CLI
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

Settings supports adding, naming, reconnecting and deleting individual accounts.
Draft credentials remain in memory until a successful fetch validates them. Dashboard,
workers, widgets, Quick Settings and notifications use explicit connection identity.
Widget selections retain deleted IDs and show unavailable instead of choosing a sibling.

Repositories require a `CredentialSession` containing an explicit connection and
request-local credentials. Draft rotation changes memory only; saved-account callers
supply a generation/credential compare-and-set writer. A rejected write propagates
cancellation before retrying with late tokens. Codex fetch rejection retains the
account for reconnect. `AccountQuotaCoordinator` serializes foreground/background
requests per connection and reloads credentials after obtaining the lock. Generation
checks guard token persistence, quota publication and caches. The old independent
token worker is cancelled; already queued instances finish without refreshing tokens.
Widget cache reads use one generation-checked preference snapshot with the original
measurement time. Android runtime and real-account acceptance are separate from JVM
concurrency/storage tests.

Settings includes an offline About/license reader. The checked-in asset bundle
contains original Android and native upstream notices, static runtime dependencies
and provenance hashes. `NOTICE.md` records unofficial identity, research credit
and the original upstream artwork adapted for the Codexbar launcher. License collection
is a developer operation; app builds and the reader need no network or toolchain.

```text
app/src/main/java/com/codexbar/android/
├── feature/dashboard/       Compose cards and foreground refresh state
├── feature/settings/        Credential input, validation and preferences
├── core/domain/             AiService, Credential, QuotaInfo, Result, QuotaRepository
├── core/data/               Provider repository implementations
├── core/nativecli/          Native process execution and API-provider result mapping
├── core/network/            Existing Kotlin HTTP clients and OAuth refresh services
├── core/security/           Encrypted preference storage
├── core/workmanager/        Scheduled account refresh and legacy work cancellation
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
Settings draft / DashboardViewModel / QuotaRefreshWorker
                         ↓
AccountQuotaCoordinator ↔ EncryptedPrefsManager (validated accounts only)
                         ↓
OpenCodeGoRepositoryImpl → NativeCodexBarClient
                         ↓
libcodexbar.so usage --provider opencodego --source api --json
                         ↓
upstream API fetcher → NativeQuotaCliParser → QuotaInfo
                         ↓
dashboard cards / background widget cache / notifications / tile refresh
```

The upstream fetcher issues an authenticated HTTPS GET to the OpenCode Go usage
API. `--source api` is explicit: this integration does not use local desktop usage
estimates, browser-cookie discovery or another tool's saved account.

### Credential contract

- The Settings field is masked and uses the existing encrypted preference store.
- Surrounding whitespace is trimmed before validation. Blank input is rejected;
  removing a saved key requires explicit account deletion. The native client rejects
  remaining whitespace/control characters.
- The key is supplied through the child's `OPENCODE_API_KEY` environment variable,
  not command arguments or a plaintext credential file.
- Go API keys have no OAuth refresh token. Authentication failures do not delete
  saved accounts; failed drafts are not saved.
- The client starts with an explicitly constructed environment, including isolated
  provider homes and XDG paths. Only selected Android platform variables are inherited.
- Raw child stderr, provider error messages and request exceptions are not forwarded
  to user-facing errors by the Go adapter.

### OpenRouter API projection

`OpenRouterRepositoryImpl` uses the same account coordinator and bounded native
client, with `usage --provider openrouter --source api --json` and a child-only
`OPENROUTER_API_KEY`. No management key or browser session is requested. The pinned
upstream plugin calls the public key/credits APIs; provider errors are sanitized.

`UsageWindow.id` and `kind` distinguish the stable `api-key-budget` spending cap
from timed quota. Synthetic primary windows are discarded. `ReportedMoney` stores
nullable USD balance/spend, reported period and its original measurement time;
missing financial values never become zero. Dashboard, cache, widget and ongoing
notification support balance-only results without inventing a quota bar or reset.
The cache preserves budget identity/type, over-limit readings and money age.
These fields are not an Android billing ledger or locally estimated cost.

Copilot uses a separate native parser for stable Premium/Chat windows; plan-only
responses contain no invented zero-use window. DeepSeek uses the official Kotlin
balance endpoint because the pinned CLI lacks numeric monetary fields. USD is
preferred among funded currencies, otherwise CNY; currencies are never summed or
converted. Missing money remains unknown. Provider/source eligibility and real-account
acceptance remain separate gates.

### Codex device-code sign-in

Android starts the Codex device-code protocol, displays the code and opens the fixed
HTTPS authorization URL in an external browser. The polling job belongs to the draft's
ViewModel scope and stops on cancellation, draft editing, navigation, denial or expiry.
Polling respects pending/slow-down responses and a maximum 15-minute lifetime. PKCE
exchange responses are bounded to 64 KiB; credentials and remote error bodies are never
logged. Token requests neither follow redirects nor automatically retry an exchange.

The acquired pair stays request-local until `AccountQuotaCoordinator.validateAndSave`
validates quota and publishes it under the existing generation guard. Saved-account
renewal continues through the same per-account owner; 429/5xx renewal failures remain
transient rather than being mislabeled as revoked credentials. Native builds on API
28+ fetch Codex quota through Core's explicit OAuth source. The same repository uses
Kotlin quota HTTP on API 26/27 and in non-native builds. Android alone renews tokens:
it records the actual successful exchange time and commits the rotated pair before
another fetch. JWT expiry takes precedence over recorded exchange time. One bounded
renewal/retry follows an authentication rejection; outages never discard a rotated pair.

The CLI receives only this request's token pair and selected account ID in a 0600
`auth.json` within a 0700 no-backup workspace. Cleanup follows success, error and
cancellation; the serialized client also removes its abandoned homes before reuse.
The Android Core patch rejects a returned account ID mismatch and disables desktop
CLI fallback for explicit OAuth without changing other platforms' credential policy.
Normal Settings validation, dashboard and worker refresh share this integration.
Live native sign-in/quota/renewal acceptance remains separate from synthetic tests.

Copilot device sign-in uses GitHub's device flow, the project's registered public
`COPILOT_OAUTH_CLIENT_ID`, and `read:user`. It is disabled when that ID is absent.
The flow enforces the fixed GitHub verification URL, bounded monotonic expiry,
pending/slow-down/denial handling and cancellable, size-limited HTTPS with redirects
disabled. A granted token still must pass the same account owner's quota validation
before storage. OAuth App registration and live Copilot eligibility are external gates.

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

Each supported API invocation allows **45 seconds for process completion**. Mutex waiting,
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
| `debug`, `release` | Not packaged; native-provider settings hidden | Original app package and dashboard |
| `nativeDebug` | Selected prebuilt ABI payload | Separate `.native` package; explicit smoke activity |
| `nativeRelease` | ARM64 Release payload | Separate `.native` package; dashboard launcher only, no diagnostic activity |
| `nativeAcceptance` | Same ARM64 Release payload | `.native.acceptance` package; release-optimized synthetic diagnostics |

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
precise timers. The Settings interval is rescheduled immediately and re-applied at
app startup; Manual cancels the periodic and automatic retry work while keeping the
explicit one-shot refresh. Per-account one-shot work validates the current generation
and mode before refreshing, so deleted or reconnected accounts cannot publish stale
results. Scheduling behavior is exercised by JVM tests and the acceptance build's
cadence persistence probe; long-run device behavior remains an observation target.

## 8. Current limits and acceptance boundary

- Codex (API 28+), OpenCode Go, OpenRouter and Copilot use the native data layer
  during normal refresh in native builds. Claude, Gemini and DeepSeek use Kotlin;
  Codex retains the Kotlin compatibility path on API 26/27 and non-native builds.
- Runtime acceptance covers an API 36 emulator, including its ARM64 native bridge.
  That is distinct from physical ARM64 hardware verification.
- Automated Go API/UI checks use an invalid synthetic key. Real-account quota
  accuracy, long-running background behavior and OAuth renewal are not proven by them.
- The Android version command reads the staged `VERSION` beside the resource bundle
  and reports `CodexBar 0.71.0`; the numeric-version App-UID probe passed.
- Claude refresh-token failures reported by the Android upstream are not resolved
  by this native integration.

See [TEST.md](TEST.md) for runnable checks, measured results and pending acceptance.
