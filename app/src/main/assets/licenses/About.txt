# CodexBar — unofficial Android port

This is an independent, unofficial Android port. It is not published, endorsed or
supported by the macOS CodexBar maintainers or the monitored service providers.
Product names identify their respective owners; attribution grants no trademark rights.

## Incorporated upstream work

- **hyunnnchoi/CodexBar-android** — original Android application, Kotlin provider
  implementations and existing Android launcher vectors. Copyright (c) 2026
  hyunnnchoi, MIT. Baseline: `1811d1fe032fd4a80240fd6bc5d04cee649a0afb`.
  https://github.com/hyunnnchoi/CodexBar-android
- **steipete/CodexBar** — Android builds of Swift Core/CLI and its bundled provider
  resources. Copyright (c) 2026 Peter Steinberger, MIT. Version 0.71.0,
  revision `cb5f0cbe88615a441272c6f9e44675bf594a3fa3`.
  Android adaptations are recorded in `native/android.patch`.
  https://github.com/steipete/CodexBar

Thank you to both upstream projects. Their full MIT notices are preserved in
`LICENSE` and the packaged `licenses/Android-origin.txt` and
`licenses/CodexBar-Core-CLI.txt` assets.

## Runtime dependencies and distribution

Full texts and source/hash provenance are packaged under
`app/src/main/assets/licenses/` and accessible offline in **Settings → About &
licenses**. Native notices apply to builds bundling the native engine. The bundle
retains complete upstream notices, including some notices for code removed by
linking; it is not a claim that every optional upstream component is shipped.

- QuickJS: MIT, including its named individual contributors.
- Commander and SweetCookieKit: MIT; Swift Crypto, Swift ASN.1 and Swift Log:
  Apache-2.0 with their accompanying notices.
- Swift 6.4.0 runtime, Foundation and libdispatch: Apache-2.0 with runtime-library
  exceptions. Foundation's additional third-party notices are retained.
- ICU 76.1: Unicode and third-party notices; curl 8.9.1: curl license;
  libxml2 2.11.5: MIT; SDK BoringSSL: OpenSSL/ISC/MIT notices.
- NDK r30 C++ runtime: Apache-2.0 with LLVM exceptions and applicable retained
  notices; SQLite 3.50.4: public-domain dedication.
- AndroidX (including Compose, Glance, WorkManager and Security), Kotlin and
  kotlinx libraries, Hilt/Dagger, Accompanist, Retrofit, OkHttp/Okio and Tink:
  Apache-2.0. The full Apache-2.0 text is included in `swift-log-LICENSE.txt`;
  dependency versions are maintained in `gradle/libs.versions.toml` and Gradle's
  resolved dependency graph. Their copyright notices remain with upstream sources
  and library metadata.

Regenerate native texts using `native/collect_notices.py --tools
"$SWIFT_ANDROID_TOOLS"` against the pinned build inputs. Verify the checked-in
bundle offline with `python3 native/collect_notices.py --check`. Re-audit when
changing dependencies, native toolchains or bundled resources.

## Research and design references

The implementation plan draws on work by lingmulongtai, igor-popov-dev and
simonsteiner, and related CodexBar forks. These are research/design references,
not a claim that their implementations have been incorporated:

- https://github.com/lingmulongtai/CodexBar-android
- https://github.com/igor-popov-dev/CodexBar-android
- https://github.com/simonsteiner/codexbar-android-tailnet
- https://github.com/simonsteiner/codexbar-tailnet-pwa

## Launcher artwork decision

Retain the original Android vector launcher resources from the Android upstream
under its MIT notice. No macOS icon artwork is imported. The macOS v0.73.0
`docs/icon.md` describes an Apple Icon Composer build pipeline, not a separate
trademark permission. Any later macOS-artwork adoption or unresolved branding
decision requires owner review before publication. Display name and package/signing
identities are unchanged.
