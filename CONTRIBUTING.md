# Contributing to CodexBar Android

## 1. Read the contracts

- [README.md](README.md): usage and build-variant selection.
- [SPEC.md](SPEC.md): architecture, provider ownership and behavior boundaries.
- [TEST.md](TEST.md): runnable checks and evidence requirements.
- [docs/ui.md](docs/ui.md): Mac UI/UX reference and implementation status.
- [native/README.md](native/README.md): pinned Swift toolchain and native packaging.
- [AGENTS.md](AGENTS.md): repository and automation working agreement.

## 2. Development setup

The Android app uses Gradle with JDK 21 and Android SDK platform 35. Configure
`ANDROID_HOME` or an ignored `local.properties`; use the checked-in Gradle wrapper.

```sh
git clone https://github.com/Lee20171010/CodexBar-android.git
cd CodexBar-android
bash ./gradlew :app:testDebugUnitTest --max-workers=2
```

The ordinary `debug` variant can be developed without the native toolchain:

```sh
bash ./gradlew :app:assembleDebug --max-workers=2
```

It does not package the CLI or expose the Go setting. To develop the native path,
follow the standalone setup and Release recipe in [native/README.md](native/README.md).
The supported native build host is x86_64 Ubuntu 24.04 with Python 3.12+, Git,
`tar`, `unzip` and Swift's documented host-library prerequisites.

## 3. Code ownership and changes

- Keep provider fetching behind `QuotaRepository`; UI/widgets consume domain models.
- Reuse the pinned Core/CLI for the native provider path rather than duplicating its HTTP API logic.
- Treat stdout as untrusted data: validate provider/source, result shape, exit status and output limits.
- Preserve cancellation and process cleanup. The current native client supports API sources and direct children only.
- Keep credentials in the encrypted store. Use local configuration or CI secrets for signing and test infrastructure.
- Put private operator settings in ignored `AGENTS.local.md`; do not publish machine names, network addresses or personal paths.
- Keep comments and maintained technical documents in English, following the surrounding source style.

## 4. Updating upstream or the toolchain

The bundled executable is CodexBar Core/CLI, not OpenAI's Codex coding-agent CLI.
The native source pin (v0.71.0) and Mac UI/UX reference (v0.73.0) are independent.
Maintenance includes Android-owned OAuth renewal, Kotlin providers, result mapping,
UI/widgets and scheduling as well as native source/toolchain updates.

`native/build.py` checks the upstream revision before applying `native/android.patch`.
The reference checkout is ignored. Editing it alone does not update this repository's
deliverable; export intended changes into the tracked patch and check that it applies
to the pinned revision.

For an upstream/toolchain update:

1. Record the intended source revision or official archive URL/hash and its provenance.
2. Review the platform adaptations, resources, native dependencies and CLI JSON contract.
3. Build the relevant ABI/configuration and verify packaged dependencies.
4. Run focused parser/process checks and the changed provider's App-UID acceptance.
5. Update specifications, commands and evidence to describe the resulting state.

Do not add downloaded toolchains, reference source trees, native payloads, APKs or
device logs to source control. Preserve license/attribution requirements when
changing bundled dependencies or preparing a public binary release.

## 5. Signing and device work

Native variants use an operator-provided signing key through:

```text
ANDROID_KEYSTORE_PATH
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Use the existing signing identity for updates. Do not print credentials or place
them in tracked configuration. The standard `release` variant retains the upstream
release workflow. `nativeRelease` and `nativeDebug` share `.native`;
release-optimized `nativeAcceptance` uses `.native.acceptance`. Use the latter for
isolated diagnostics alongside the daily app. Never install `nativeDebug` over an
owner's daily app to obtain test access.

Configure the ADB/session-lock wrappers described in [TEST.md](TEST.md). Hold one
lock through installation, assertions and cleanup, and build before acquiring it.
Prefer the optimized single-ABI Release APK for off-LAN devices; explain any need
for a Debug transfer. Never substitute or reconfigure a shared device without approval.

## 6. Checks, documentation and publication

Run focused checks for the changed behavior, then the relevant broader gates from
[TEST.md](TEST.md). Reuse valid cached results; do not claim unexecuted physical-device
or real-account scenarios as passing.

Document changes at their proper boundary:

| Change | Update |
| --- | --- |
| User setup or feature availability | `README.md` |
| Architecture, data flow or behavior contract | `SPEC.md` |
| Test commands, results or remaining acceptance | `TEST.md` |
| Developer setup or workflow | `CONTRIBUTING.md` / `native/README.md` |
| Notable user/developer-facing change | `CHANGELOG.md` under `Unreleased` |
| Mac UI/UX mapping or implementation status | `docs/ui.md` |

For UI/UX work, first map the Mac setting, feature and interaction to its proposed
mobile counterpart, with a source reference and data-availability boundary. Review
the comparison design before implementation. Use Compose accessibility, touch,
navigation and system-inset conventions for Android adaptation. A mockup does not
prove implementation or CLI capability. Keep one independently testable feature per
commit; run its focused checks before committing. Documentation reconciliation can
be one commit when the files describe the same state change.

Review staged content for credentials, private infrastructure details and generated
artifacts. Use a public-safe commit identity, such as your verified GitHub noreply
address. Follow the repository working agreement and maintainer instructions for
commit structure and publication.

Before publication, inspect each unpublished commit's full diff and message, including
deleted text, fixtures and binary additions, rather than only the final tree. Retain
required public license attributions. If sensitive content is found, follow the
maintainer's history-rewrite authorization and verify remote reachability first;
adding a later deletion does not remove it from history. Keep scan artifacts private.

Passing checks is not approval to push or publish. Include scope, checks, limitations
and any relevant issue/change link when requesting review. Contributions use the
repository's [MIT license](LICENSE).
