# Codexbar for Android

An unofficial Android port of [CodexBar](https://github.com/steipete/CodexBar):
monitor the usage quotas of your AI coding plans from your phone, with widgets,
a Quick Settings tile, an ongoing notification and reset alerts.

This is an independent port. It is not published, endorsed or supported by the
macOS CodexBar maintainers or the monitored service providers.

## Architecture in one paragraph

The quota engine is the real CodexBar **Swift Core/CLI**, cross-compiled and
bundled to run **on-device** — pinned to upstream `steipete/CodexBar` v0.76.0
(commit `2d8b1409`) with a recorded Android patch set (`native/android.patch`).
On top of that engine this port adds the Android-specific layers: device-flow
sign-in, encrypted credential storage (Android Keystore), Compose UI, widgets,
WorkManager scheduling and notifications. This engine is CodexBar's quota
engine, **not** the OpenAI Codex coding-agent CLI. Four providers
(Claude, Codex fallback, Gemini, DeepSeek) also have Kotlin network paths;
the remaining API-key providers are served by the bundled native CLI. See
[SPEC.md](SPEC.md) for the full architecture and [native/README.md](native/README.md)
for the engine build.

## Requirements

- Android 8.0 (API 26) or newer. The bundled native engine requires API 28+;
  on API 26/27 Codex falls back to the Kotlin compatibility path and
  native-only providers are unavailable.
- ARM64 devices (the delivered APK ships ARM64 native libraries; an x86_64
  emulator path exists for testing).
- Distribution is a manually delivered signed APK; there is no Play Store
  listing. Future artifacts are named `Codexbar-Android-x.y.z.apk`; delivered
  artifacts keep the build names recorded in [TEST.md](TEST.md).

## Providers

Fifteen selectable providers. Per-provider credential type, setup steps,
verification status and known limits are consolidated in
[docs/providers.md](docs/providers.md) — read it before adding an account.

| Provider | Credential |
| --- | --- |
| Codex | ChatGPT sign-in (device flow) or manual tokens |
| Claude | Manual OAuth tokens from an existing Claude login |
| Gemini | Manual CLI/Code Assist credentials (original issuing OAuth client required) |
| GitHub Copilot | GitHub device sign-in or manual token |
| OpenCode Go, DeepSeek, OpenRouter, Venice, Amp, z.ai / GLM, Factory, Alibaba Coding Plan, Charm Hyper, Kimi, MiniMax | Provider API key / access token |

Verification honesty: live account acceptance is confirmed only for Codex and
OpenCode Go. The newer API-key providers (Venice, Amp, z.ai, Factory, Alibaba,
Charm Hyper, Kimi, MiniMax) pass synthetic parser and storage tests but have
not had live-account device acceptance. Copilot phone sign-in is unverified
against a live account.

## Getting started

1. Install the signed APK and open **Codexbar**.
2. Open **Settings** — it has one destination per provider (plus app panes).
   Pick a provider and add an account. Device-flow providers (Codex, Copilot)
   open an authorization page with a short code; API-key providers offer a link
   to the official key page where verified, plus key-type guidance.
3. The dashboard shows one card per account with each quota window, percent
   used/remaining and reset countdown. Pull to refresh; tap a card for details,
   pace estimate and history.
4. Optional: add a home-screen **widget**, pin the **Quick Settings tile**, or
   keep the **ongoing notification** for glanceable quota state.

## Features

- **Dashboard** — per-account quota cards, headline window, accent colors,
  Detailed/Compact overview layout, multi-account segmented/stacked layouts,
  phone detail sheet and wide detail pane.
- **Quota history and pace** — local history chart; weekly pace estimate
  ("will last / runs out before reset") on the supported lanes: Codex, Claude
  and OpenCode Go windows, plus Copilot reset-date rules. Other providers show
  usage without pace.
- **Alerts** — depleted, low (20% remaining) and confirmed-reset notifications
  for windowed providers, plus predictive-pace alerts on Codex and Claude only;
  each with durable deduplication.
- **Official service status** — provider incident checks (can be disabled),
  fetched from the providers' official status endpoints independently of quota.
- **Widgets and tile** — per-account and overview Glance widgets plus a Quick
  Settings tile, sharing the same display settings chain.
- **Scheduled refresh** — periodic WorkManager refresh (15, 30 or 60 minutes,
  or manual), optional refresh-on-dashboard-open, one-time optional
  battery-optimization exemption with a permanent settings entry.
- **Manual updates** — Settings → About offers an explicit **Check updates**
  action (native delivery builds only): it queries this fork's latest stable
  GitHub release, downloads the matching `Codexbar-Android-x.y.z.apk` with
  progress and cancel, verifies size, the GitHub `sha256` digest, package,
  versionCode and signing certificate, then hands the file to the Android
  system installer. Nothing checks or installs automatically.
- **Privacy** — credentials in app-private encrypted storage; quota and status
  requests go only to the official provider endpoints for the providers you
  enable, and the manual update check contacts GitHub's release API only when
  you tap it; licenses are viewable offline in Settings → About & licenses.

## Settings map

Settings mirrors the Mac sidebar: app panes plus one destination per provider.

- **General** — refresh interval, refresh when the dashboard opens, provider
  status checks, battery optimization entry, app language (system picker).
- **Notifications** — usage alert toggles (depleted, low, predictive pace,
  recovery) and the ongoing notification.
- **Menu & display** — remaining/used, reset style, visible windows, accent,
  headline window, pace, overview and multi-account layouts, work days.
- **Usage & Spend** — placeholder: this port has no spend ledger data source,
  and balances or quota history are never converted into daily costs.
- **Advanced** — delete all credentials (with confirmation).
- **About & licenses** — version, offline license bundle, manual update check
  (native delivery builds only).
- **Provider destinations** — per-provider add/reconnect/rename/delete,
  per-account display overrides, sign-in guidance.

## Known limitations

- Quota semantics differ per provider; balance-only sources show money, never
  a fabricated percentage. Unknown is kept as unknown.
- Pace display is limited to the Codex, Claude and OpenCode Go lanes (plus
  Copilot reset-date rules); predictive-pace alerts are Codex and Claude only.
  Other providers show usage without pace.
- Claude and Gemini cannot complete a fresh OAuth login on the phone; they use
  manual credentials (see [docs/providers.md](docs/providers.md)).
- Spend/ledger, currency conversion and Mac-only sources (browser cookies,
  Keychain, local CLI files) are not implemented.
- Several acceptance gates (physical ARM64 hardware, TalkBack, screenshot
  parity) remain open; see [TEST.md](TEST.md) for what is and is not verified.
- The in-app updater is implemented and unit-tested at source level only; the
  real download-and-install flow has not been exercised on a device yet.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/providers.md](docs/providers.md) | Provider matrix: credentials, setup, verification status, limits |
| [SPEC.md](SPEC.md) | Architecture, components, data and execution contracts |
| [TEST.md](TEST.md) | Test harness, acceptance procedures, artifact evidence |
| [docs/ui.md](docs/ui.md) | Mac design-language mapping to Material/Compose |
| [native/README.md](native/README.md) | Swift Core/CLI toolchain, build, packaging |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Development setup, structure, checks |
| [CHANGELOG.md](CHANGELOG.md) | Current version baseline and pending changes |
| [docs/releases/history.md](docs/releases/history.md) | Per-release verification narratives |
| [docs/history/CHANGELOG-pre-baseline.md](docs/history/CHANGELOG-pre-baseline.md) | Archived pre-baseline per-version changelog |

## Acknowledgments

- [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android)
  — the original Android application and Kotlin provider implementations this
  port is based on.
- [steipete/CodexBar](https://github.com/steipete/CodexBar) — the macOS
  CodexBar product, the bundled Swift Core/CLI engine and the launcher artwork.
- Additional CodexBar forks (lingmulongtai, igor-popov-dev, simonsteiner) served
  as research and design references; see [NOTICE.md](NOTICE.md) for accurate
  provenance.

## License

MIT. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for the upstream
notices and the packaged license bundle.
