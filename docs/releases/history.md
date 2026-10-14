# Release verification history

Per-release verification narratives and known limitations moved out of
[CHANGELOG.md](../../CHANGELOG.md) when it was normalized to Keep a Changelog
section types. Facts are preserved as originally recorded; dates refer to the
release-prep commits listed in the changelog. Artifact hashes and full evidence
tables live in [TEST.md](../../TEST.md).

## 0.0.15-beta-native (2026-10-14)

- 263 JVM tests, debug lint and native Kotlin compilation passed.
- The signed ARM64 Release APK passed battery-flow UI checks on the shared API 36
  emulator (x86_64 with ARM64 translation): no startup prompt, optional first offer,
  dismissal, repeated enable, process restart and the system settings entry.
  This is not a physical ARM64 or background-timing guarantee.

## 0.0.14-beta-native (2026-10-14)

- 257 JVM tests, debug lint and native Kotlin compilation passed.
- Real Copilot authorization and quota retrieval still require account acceptance.
- Claude remains manual-credential setup; no new direct phone OAuth is claimed.

## 0.0.13-beta-native (2026-10-14)

- 256 JVM tests and debug lint passed. Signed ARM64 release builds passed.
- Isolated acceptance notification checks passed in light and dark modes on the
  API 36 emulator's ARM64 translation path. A separate test-only instrumentation
  snapshot bypassed the shared runtime's UI idle timeout; the standard CLI
  hierarchy dump remains unreliable there. Icon resource identity was checked;
  visual appearance and physical ARM64 behavior remain unverified.
- The final daily-package device check refused to replace an existing install;
  its data was preserved. This is not a completed daily-package runtime check.
- Known limitations: Kimi and MiniMax are synthetic-tested only; device and
  live-account behavior is **not verified** (see docs/providers.md). Neither
  new provider has pace display or predictive pace alerts: the Mac pace lanes
  cover Codex/Claude/OpenCode Go/Copilot only. Recovery alerts apply only
  where lanes match (session: PRIMARY with explicit duration ≤360 min; weekly:
  exactly one 604800 s window); MiniMax monthly windows have no alert lane.

## 0.0.12-beta-native (2026-10-12)

- See [docs/providers.md](../providers.md) for which of the six new providers
  are synthetic-tested only and not yet device/live verified.

## 0.0.5-beta-native (2026-10-09)

- 100 JVM tests, Debug/Native Release/Native Acceptance lint and native
  compilation passed. Signed ARM64 daily artifact and exact hash are recorded
  in [TEST.md](../../TEST.md).
- Six synthetic App-UID probes, separate daily-APK product UI, and synthetic
  phone/dark/200%-text/wide/detail/chart/credits/display checks passed on the
  API 36 emulator's ARM64 native bridge. These are not physical ARM64 or live
  renewal proof.

## 0.0.4-beta-native (2026-10-08)

- The signed 0.0.4 artifact passed eight synthetic probes and four
  provider-rejection UI flows, plus launcher/sign-in/notices checks on the
  API 36 ARM64-bridge emulator.
- The owner reported Codex and Go quotas working on the phone with this
  version. That confirms neither the later native Codex path nor automatic
  token renewal.
- [TEST.md](../../TEST.md) records remaining provider, widget-host,
  accessibility, minimum-API, 16 KiB-page, physical-device and long-running
  background checks.
