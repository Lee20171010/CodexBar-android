# Changelog

This file records changes maintained in this Android fork. The source baseline is
the Android upstream at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; it is separate
from the pinned Swift Core/CLI source revision.

## [Unreleased]

### Added

- Route Codex quota through native Core OAuth on API 28+ native builds while Android
  remains the sole token writer. Preserve API 26/27 compatibility, private ephemeral
  credential files, account matching, bounded renewal and supplemental model windows.

- DeepSeek API-key accounts using the official numeric USD/CNY balance endpoint;
  no inferred spending, exchange rates or quota windows.
- GitHub Copilot accounts via the pinned native API source, with separate Premium/Chat
  measurements and plan-only responses retained without inventing zero usage.
- Codex browser/device-code sign-in with bounded polling, PKCE exchange, cancellation
  and validated encrypted account publication; existing manual token entry remains available.
- OpenRouter API-key accounts through the pinned native engine, with reported
  USD balance/spend and explicitly typed key budgets on dashboard, widgets and
  notifications; missing values remain unknown and balance-only results have no
  invented quota windows.
- Multi-account Settings, Dashboard, background refresh, widgets, tile and notification
  routing, with private validated drafts and one serialized refresh writer per account.
- Explicit credential sessions for provider fetches; draft token rotation stays in
  memory and rejected credential publication cancels before retry.
- Connection-aware encrypted storage foundation: stable local IDs, names, generation
  guards, in-place legacy adoption and credential compare-and-set.
- Offline Settings → About & licenses, explicit unofficial-port attribution,
  dependency license texts with source hashes, and Android artwork provenance.
- Android/Bionic builds of upstream CodexBar Core/CLI with pinned source, an Android
  compatibility patch and verified official toolchain setup.
- OpenCode Go API-key settings and native quota fetching through the existing
  dashboard/background repository boundary.
- Bounded native process capture, cancellation/reaping, isolated workspaces and
  provider/source-aware JSON mapping with sanitized user-facing errors.
- Isolated native test variants, a self-test launcher and locked device acceptance
  that exercises the Go API and Settings with an invalid synthetic key.
- Technical specification, testing guide, contribution guide and native build instructions.

### Changed

- Display **Codexbar** with the original upstream launcher artwork, adapted to
  Android masks. Native Release opens the dashboard; explicit smoke checks remain
  available under the separate `.native` identity. Version is 0.0.4-beta-native.
- Disable HTTP credential/body logging and redirects; keep token exchange retries
  explicit and distinguish transient Codex renewal failures from terminal authentication errors.
- Use an explicit, immutable PendingIntent for Quick Settings launches on Android
  14+, preserving the legacy launch path on earlier supported versions.
- Optimized the ARM64 native APK with Swift Release/`-Osize`, stripped binaries,
  linker section collection, R8/resource shrinking and single-ABI packaging.
  The initial APK measurement decreased from 76.91 to 33.28 MiB.
- Kept signing and device-specific configuration outside public source files,
  and expanded ignore rules for local credentials and private operator settings.

### Verification

- 74 JVM tests, Debug/Native Release lint and toolchain-integrity checks passed; account routing
  device acceptance remains separate from these synthetic tests.
- The signed 0.0.4-beta-native APK passed eight synthetic runtime probes and four
  provider rejection UI flows on an API 36 emulator's ARM64 native-bridge path.
  Focused dashboard, sign-in entry, offline attribution and original-icon checks
  also passed. See [TEST.md](TEST.md) for exact scope and artifact hash.
- Physical ARM64, real-account quota accuracy and complete migration of the other
  providers remain outside the recorded acceptance.
