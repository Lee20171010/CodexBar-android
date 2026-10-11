# Changelog

Changes maintained in this unofficial Android port. The Android source baseline
is `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`, separate from the pinned Swift
Core/CLI revision. Artifact delivery does not imply a public GitHub release.

## 0.0.11-beta-native

### Fixed

- Stable theme-aware navigation background with no fade during dashboard and
  settings transitions.

## 0.0.10-beta-native

### Fixed

- The persistent quota status notification now uses the system collapsed
  title/text and an expanded inbox summary covering every account, replacing
  the custom layout that OEM shades clipped or dropped.

## 0.0.9-beta-native

### Fixed

- Removed duplicate outer insets on the dashboard and settings screens.
- Custom quota notification text now uses notification text appearances that
  follow the system light/dark theme instead of fixed light-theme colors.

## 0.0.8-beta-native

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

## 0.0.7-beta-native

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

## 0.0.6-beta-native

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

## 0.0.5-beta-native

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

### Verification

- 100 JVM tests, Debug/Native Release/Native Acceptance lint and native compilation
  passed. Signed ARM64 daily artifact and exact hash are recorded in [TEST.md](TEST.md).
- Six synthetic App-UID probes, separate daily-APK product UI, and synthetic
  phone/dark/200%-text/wide/detail/chart/credits/display checks passed on the API 36
  emulator's ARM64 native bridge. These are not physical ARM64 or live renewal proof.

## 0.0.4-beta-native and initial native integration

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

### Verification

- The signed 0.0.4 artifact passed eight synthetic probes and four provider-rejection
  UI flows, plus launcher/sign-in/notices checks on the API 36 ARM64-bridge emulator.
- The owner reported Codex and Go quotas working on the phone with this version.
  That confirms neither the later native Codex path nor automatic token renewal.
- [TEST.md](TEST.md) records remaining provider, widget-host, accessibility,
  minimum-API, 16 KiB-page, physical-device and long-running background checks.
