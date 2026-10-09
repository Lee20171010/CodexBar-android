# Changelog

Changes maintained in this unofficial Android port. The Android source baseline
is `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`, separate from the pinned Swift
Core/CLI revision. Artifact delivery does not imply a public GitHub release.

## [Unreleased]

### Changed

- Align dashboard metric rows and card headers with Mac CodexBar: monochrome
  title/remaining percentage, reset alongside it, a 6dp provider-accent capsule,
  and name/identity/plan hierarchy (`e114632`). New Release/UI acceptance is pending.
- Reconcile setup, architecture, contribution and test documents with delivered
  `0.0.5-beta-native` behavior and the current source. Record Mac UI/UX alignment
  as a design-first next phase, separate from engine and live-account acceptance.

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
