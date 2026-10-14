# Changelog archive (pre-baseline)

Archived historical record, superseded as the live changelog on 2026-10-14. The
current changelog is [CHANGELOG.md](../../CHANGELOG.md); per-release verification
narratives live in [docs/releases/history.md](../releases/history.md). This file is kept
as a reorganized record of per-version changes before the documentation baseline;
it is not the active changelog. Original wording remains in Git history.

---

# Changelog

All notable changes to this unofficial Android port are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/) for
future versions. Future delivered artifacts are named `Codexbar-Android-x.y.z.apk`;
the historical `-beta-native`-suffixed labels are the actual delivered names
and are kept as recorded. Release dates below are the dates of the release-prep
commit that set each `versionName` (verifiable in git), not separate delivery
dates. The Android source baseline is
`1811d1fe032fd4a80240fd6bc5d04cee649a0afb`, separate from the pinned Swift
Core/CLI revision. Artifact delivery does not imply a public GitHub release.
Per-release verification narratives and known limitations are recorded in
[docs/releases/history.md](../releases/history.md).

## [Unreleased]

### Changed

- Documentation rewritten to reflect the delivered app: README as a user
  guide, SPEC/TEST restructured, provider setup guidance consolidated into
  `docs/providers.md`, this changelog normalized, and historical verification
  narratives moved to `docs/releases/history.md`. No code or version changes.

## [0.0.15-beta-native] - 2026-10-14

### Changed

- The battery optimization prompt no longer appears at startup. Optional
  exemption is offered once when switching from manual to periodic refresh,
  with a Not now action, and the answer is remembered.
- A permanent battery settings entry sits under General. Background scheduling
  remains independent of battery exemption.

## [0.0.14-beta-native] - 2026-10-14

### Added

- Phone-only GitHub Copilot device sign-in using the public OAuth client used
  by upstream CodexBar. The Visual Studio Code consent identity is explained
  before authorization; an explicit empty build override still disables login.
- Amp account settings link and access-token guidance.

### Changed

- Gemini setup wording clarifies that this path reads CLI / Code Assist quota,
  not Gemini App usage, and describes Google's consumer-service retirement and
  the supported license scope.

## [0.0.13-beta-native] - 2026-10-14

### Added

- Provider connection setup guidance: verified official key-page links for
  eight API-key providers, provider-specific key-type help (Factory `fk-`,
  Alibaba Coding Plan, z.ai regional keys, Amp), original-OAuth-client
  wording for Gemini, collapsed manual token entry for device-flow providers
  (reconnect and prefilled drafts stay expanded), and a visible URL fallback
  when no browser can open an authorization or key page. Copilot intentionally
  links no token page: the settings PAT page cannot issue the GitHub OAuth
  token the flow requires.
- Two native-CLI providers (pinned CLI unchanged): Kimi (Kimi Code API key,
  China site only — international kimi.ai keys are not supported; `7-Day`
  and `5-Hour` windows plus `Total usage` monthly supplement) and MiniMax
  (Token Plan Subscription Key `sk-cp-` only — pay-as-you-go `sk-api-` keys
  are rejected before any request; global region default with the upstream
  single China-mainland retry on credential rejection, no region selection
  in-app). Fifteen selectable providers in total.

### Fixed

- Persistent, recovery and warning notifications now use a monochrome silhouette
  derived from the CodexBar launcher artwork instead of the generic quota chart.
- Notification acceptance fixtures use valid account UUIDs and verify the actual
  posted notification and small-icon resource through app-scoped diagnostics.

## [0.0.12-beta-native] - 2026-10-12

### Added

- Six native-CLI provider integrations (pinned CLI v0.71.0 unchanged): Venice
  (API key, upstream-derived `Balance` percent), Amp (access token, `Amp`/`Orb`
  extra quotas plus reported plan), Z.ai (personal coding plan, `5-Hour` and
  `Weekly` windows), Factory (`Standard`/`Premium`/`Monthly` windows plus
  reported provider cost as money), Alibaba Coding Plan (international,
  reported plan) and Charm Hyper (balance-only HC provider credits — not fiat,
  no reset windows). Thirteen selectable providers in
  total; see [docs/providers.md](../providers.md) for the support matrix,
  including which entries are synthetic-tested only and not yet device/live
  verified.

### Fixed

- The persistent notification's expanded view aligns account columns with
  weighted single-line label/value columns, concise status markers without
  per-row age, and a header timer that counts only from the oldest trustworthy
  measurement (`5a7e64f`).

## [0.0.11-beta-native] - 2026-10-11

### Fixed

- Stable theme-aware navigation background with no fade during dashboard and
  settings transitions.

## [0.0.10-beta-native] - 2026-10-11

### Fixed

- The persistent quota status notification now uses the system collapsed
  title/text and an expanded inbox summary covering every account, replacing
  the custom layout that OEM shades clipped or dropped.

## [0.0.9-beta-native] - 2026-10-11

### Fixed

- Removed duplicate outer insets on the dashboard and settings screens.
- Custom quota notification text now uses notification text appearances that
  follow the system light/dark theme instead of fixed light-theme colors.

## [0.0.8-beta-native] - 2026-10-11

### Added

- Segmented and stacked multi-account dashboard layouts with persistent display
  selection, scoped refresh and safe fallback after account removal.
- Detailed and compact overview layouts, global pace visibility, refresh-on-open
  policy and independent provider-status checks.

### Fixed

- Pace uses provider window slots, reported durations and confidence metadata;
  predictive alerts follow the supported Mac lanes and cycle rules.
- Confirmed-reset notifications use boundary evidence, delayed Codex weekly
  confirmation and persistent receipts instead of a simple usage-drop heuristic.
- Reset receipts handle disabled notifications, late boundary metadata and legacy
  migration without duplicate notices.

## [0.0.7-beta-native] - 2026-10-10

### Changed

- Settings follows the pinned native Mac sidebar hierarchy: app panes first,
  then individual provider destinations; global credential deletion stays in
  Advanced with confirmation.
- Provider headline choices wrap on narrow screens; accent choices have localized
  labels, selected semantics and 48dp touch targets.

### Fixed

- Display preferences propagate through the shared chain with per-field
  inheritance and reset, so cards, details, widgets and tile agree.
- Default weekly window duration matches the Mac baseline.
- Used-style bars show the used value with a localized "used" label in gauge,
  widget and summary, matching the flipped fill; warning notifications keep
  remaining semantics.
- Pace ETA strings format hours and minutes with the correct values in English
  and Traditional Chinese.
- Usage alerts use the account's effective work days for pace and accept
  overquota readings: depletion fires once and the dedup receipt survives
  malformed measurements instead of being erased.
- Declared supported locales (English, Traditional Chinese) so per-app language
  settings list both choices on API 33+.

## [0.0.6-beta-native] - 2026-10-10

### Added

- Mac-style settings directory (General, Accounts, Notifications, Menu & display,
  Usage & Spend, Advanced, About) with phone drill-down and wide two-pane layout.
- Global menu-bar display preferences (Remaining/Used fill, warning markers,
  absolute reset, amounts) as defaults with per-account and per-widget overrides.
- Per-account headline (percent) window and accent color, applied on cards,
  details, tile and widgets; widgets follow the same display chain.
- Mac `UsagePace.weekly` port with work-day awareness: pace stage, ETA and
  will-last shown in details, with a weekly work-days picker.
- Usage-warning notifications split like Mac — depleted, low (20% remaining),
  predictive pace — each independently toggleable with durable per-window dedup,
  alongside the existing confirmed-recovery alerts.
- App Language entry opening the system per-app locale settings (API 33+).

### Changed

- Align dashboard metric rows and card headers with Mac CodexBar: monochrome
  title/remaining percentage, reset alongside it, a 6dp provider-accent capsule,
  and name/identity/plan hierarchy (`e114632`).
- Reconcile setup, architecture, contribution and test documents with delivered
  behavior; record the Mac UI/UX prototype and per-feature mapping (`docs/ui.md`).

## [0.0.5-beta-native] - 2026-10-09

### Added

- Codex quota through native Core OAuth on API 28+ native builds, with Android as
  the sole token writer, private ephemeral credential files, account matching and
  bounded renewal. Kotlin compatibility remains on API 26/27 and non-native builds.
- Registration-gated GitHub device sign-in with cancellable polling and validated
  account publication; manual Copilot OAuth-token entry remains available.
- Generation-scoped last-good measurements, typed failures, stable window/model
  identity, source and age shared by dashboard, widgets, notification and tile.
- Stable-order compact account cards, scrollable phone details and wide-screen
  detail panels, with English/Traditional Chinese quota wording.
- Pinned-account and overview widgets with responsive layouts, explicit account
  pins, freshness and omitted-account/window counts.
- Official service status fetched independently from quota/credentials and cached
  per provider with sourced incident, maintenance and unknown states.
- Opt-in measured quota-recovery alerts with durable duplicate suppression.
- Bounded quota history and one remaining-over-time chart with a labeled linear
  estimate; reset/source/recovery/gap changes break the history segment.
- Per-account overview window/amount/reset choices and independent widget profiles;
  hidden quota risk remains visible and defaults can be restored.
- Read-only reported native Codex credit balance, workspace ownership, personal
  caps and reset inventory, preserving units, expiry and unknown versus zero.

### Changed

- Apply saved refresh cadence at startup, boot and settings changes. Manual cancels
  automatic work; explicit refresh remains available. Coalesce account requests,
  retry transient failures and suppress automatic terminal-auth retries.
- Preserve the daily `.native` identity/signing key while removing diagnostics;
  release-optimized diagnostics use `.native.acceptance` and the same native payload.
- Resolve the pinned CLI's numeric version from the packaged resource directory.
- Fix the Android status-only flag lookup to the ArgumentParser property name so
  it does not fall through to quota fetching.

## [0.0.4-beta-native] - 2026-10-08

### Added

- Pinned Android/Bionic builds of CodexBar Core/CLI, a tracked Android patch,
  verified toolchain setup, bounded process output, cancellation and private homes.
- Native API-key Go/OpenRouter and OAuth-token Copilot accounts; Kotlin DeepSeek
  numeric balances. Balance-only services have no invented quota windows.
- Codex device-code sign-in with bounded polling, PKCE, cancellation and validated
  storage; Codex quota still used Kotlin in this delivered artifact.
- Named multi-account setup and routing, explicit credential sessions, generation
  guards, in-place encrypted legacy adoption and one serialized account owner.
- Offline About/licenses, dependency provenance and original upstream launcher
  artwork, plus technical, testing, contribution and native build guides.

### Changed

- Display Codexbar and open the dashboard from the native launcher. Initial smoke
  diagnostics shared `.native`; the later 0.0.5 daily/acceptance split supersedes it.
- Disable HTTP credential/body logging and redirects; keep token-exchange retries
  explicit and separate transient renewal failures from terminal authentication.
- Use an immutable PendingIntent for API 34+ Quick Settings launches.
- Optimize ARM64 packaging with Swift `-Osize`, stripping, linker section collection,
  R8/resource shrinking and single-ABI output; initial size fell 76.91 → 33.28 MiB.
- Keep signing, device configuration and generated artifacts outside source control.
