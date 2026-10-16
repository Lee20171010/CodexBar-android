# Changelog

All notable changes to this unofficial Android port are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).
Future delivered artifacts are named `Codexbar-Android-x.y.z.apk`; historically
delivered artifacts carried `-beta-native`-suffixed app labels, which are
recorded as delivered and are not renamed retroactively. Release dates are the
dates of the release-prep commits that set each `versionName` (verifiable in
git), not separate delivery dates. Artifact delivery does not imply a public
GitHub release.

This changelog starts from the current version state as a documentation
baseline. The complete per-version changelog kept before this baseline is
archived at
[docs/history/CHANGELOG-pre-baseline.md](docs/history/CHANGELOG-pre-baseline.md),
and per-release verification narratives are in
[docs/releases/history.md](docs/releases/history.md).

## [Unreleased]

## [0.0.16] - 2026-10-16

### Added

- Manual in-app updater in Settings → About (native delivery builds only):
  explicit **Check updates** queries this fork's latest stable GitHub release
  (strict stable SemVer tag, non-draft, exact `Codexbar-Android-x.y.z.apk`
  asset URL), downloads it to app-private storage with progress and cancel,
  verifies size, the GitHub `sha256` digest when supplied, package identity, a higher
  versionCode and matching signing certificates, then hands the file to the
  Android system installer over a restricted FileProvider URI. Nothing runs at
  startup and nothing auto-installs. The exact Release APK passed the manual
  GitHub check on the emulator; full download-to-installer acceptance remains open.

### Changed

- Native engine pin upgraded from v0.71.0 (`cb5f0cbe`) to v0.76.0
  (`2d8b1409a1649dce60f3610ebe4eab52b92545de`), with `CORE_CLI_VERSION` kept in
  sync. Native ARM64 compilation, 278 JVM tests, lint, signed assembly and six
  isolated runtime/scheduling probes passed. A subsequent general UI harness
  step timed out; full provider/live-account acceptance is not claimed.
- App versions now use plain SemVer (`0.0.16`); the previous
  0.0.15 artifact keeps its delivered `0.0.15-beta-native` label.
- Documentation rewritten to reflect the delivered app: README as a user
  guide, SPEC/TEST restructured, provider setup guidance consolidated into
  `docs/providers.md`, changelog rebaselined, and historical verification
  narratives moved to `docs/releases/history.md`. No code or version changes.
- Version identity policy: docs use plain SemVer (`0.0.15`); the actually
  delivered 0.0.15 artifact and in-app label remain `0.0.15-beta-native`.
  This normalization does not rebuild, relabel, or tag any APK.

## [0.0.15] - 2026-10-14

Baseline entry: this records the app's current feature state as of 0.0.15,
not a delta from the previous version. The delivered artifact and in-app label
were `0.0.15-beta-native` (versionCode 16, signed ARM64 release). Changes
specific to this version are listed under Changed.

### Added

- **Quota engine**: pinned upstream `steipete/CodexBar` Swift Core/CLI v0.71.0
  cross-compiled to run on-device (Android patch set in `native/android.patch`),
  plus Android layers: device-flow sign-in, Keystore-backed encrypted
  credential storage, Compose UI, widgets, WorkManager scheduling,
  notifications. This engine is not the OpenAI Codex coding-agent CLI.
- **Fifteen selectable providers** with per-account management
  (add/reconnect/rename/delete): Claude, Codex, Gemini, OpenCode Go, DeepSeek,
  GitHub Copilot, OpenRouter, Venice, Amp, Z.ai, Factory, Alibaba Coding Plan,
  Charm Hyper, Kimi, MiniMax. Sources and verification status per provider:
  [docs/providers.md](docs/providers.md).
- **Dashboard**: per-account cards with quota windows, percent used/remaining,
  reset countdowns, headline window and accent colors, Detailed/Compact
  overview layout, segmented/stacked multi-account layouts, phone detail sheet
  and wide detail pane, pull-to-refresh.
- **Quota history and pace**: local bounded history chart; weekly pace
  estimate on the Codex, Claude and OpenCode Go lanes plus Copilot
  reset-date rules; confirmed-reset receipts from boundary evidence.
- **Alerts**: depleted, low (20% remaining), predictive-pace (Codex and Claude
  only) and confirmed-recovery notifications, each independently toggleable
  with durable deduplication; ongoing silent notification with per-account
  summary; monochrome silhouette small icon.
- **Widgets and Quick Settings tile**: per-account and overview Glance widgets
  plus a tile, sharing the display-preference chain
  (global → per-account → per-widget inheritance and reset).
- **Scheduled refresh**: WorkManager periodic refresh (15, 30 or 60 minutes,
  or manual), optional refresh when the dashboard opens, independent official
  provider-status checks with per-provider caching.
- **Settings** mirroring the Mac sidebar: app panes (General, Usage & Spend
  placeholder, Notifications, Menu & display, Advanced, About & licenses) plus
  one destination per provider; per-provider onboarding guidance with verified
  official key-page links, key-type help, collapsed manual token entry, and a
  visible URL fallback when no browser is available.
- **Localization**: English and Traditional Chinese, per-app language setting.

### Changed

- 0.0.15: the battery optimization prompt no longer appears at startup.
  Optional exemption is offered once when switching from manual to periodic
  refresh, with a Not now action, and the answer is remembered. A permanent
  battery settings entry sits under General. Background scheduling remains
  independent of battery exemption.
