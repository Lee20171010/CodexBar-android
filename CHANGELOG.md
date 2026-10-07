# Changelog

This file records changes maintained in this Android fork. The source baseline is
the Android upstream at `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`; it is separate
from the pinned Swift Core/CLI source revision.

## [Unreleased]

### Added

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

- Optimized the ARM64 native APK with Swift Release/`-Osize`, stripped binaries,
  linker section collection, R8/resource shrinking and single-ABI packaging.
  The initial APK measurement decreased from 76.91 to 33.28 MiB.
- Kept signing and device-specific configuration outside public source files,
  and expanded ignore rules for local credentials and private operator settings.

### Verification

- 33 JVM tests and toolchain-integrity checks passed.
- The Go Release APK passed five runtime probes and Settings validation on an
  API 36 emulator's ARM64 native-bridge path. See [TEST.md](TEST.md) for exact scope.
- Physical ARM64, real-account quota accuracy and complete migration of the other
  providers remain outside the recorded acceptance.
