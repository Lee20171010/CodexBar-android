# CodexBar for Android

> **Unofficial Android port** of [**CodexBar**](https://github.com/steipete/CodexBar) by [@steipete](https://github.com/steipete). Independently maintained; not published, endorsed or supported by the macOS upstream or service providers.

Monitor seven AI services on Android. **Codex (ChatGPT), OpenCode Go, OpenRouter and GitHub Copilot use the native Swift engine** in native builds on API 28+. Claude, Gemini and DeepSeek use Kotlin API integrations; Codex also retains a Kotlin compatibility path on API 26/27. Balance-only services show reported money rather than invented quota windows.

The native integration is experimental. The last delivered daily APK is
`0.0.5-beta-native`; current source also includes a subsequent Mac-inspired card-row
and header change. That UI change is not covered by the delivered APK's acceptance.
Automated checks use synthetic credentials. The owner reported Codex and OpenCode Go
working in `0.0.4-beta-native`, which still used Kotlin for Codex; native Codex live
renewal remains unverified. See [TEST.md](TEST.md) for artifact-specific evidence.

<p align="center">
  <img src="docs/Screenshot_20260305_025201_CodexBar.jpg" width="320" alt="Dashboard" />
  &nbsp;&nbsp;
  <img src="docs/Screenshot_20260305_025207_CodexBar.jpg" width="320" alt="Settings" />
</p>

These screenshots show the original Android UI, not the current cards or proposed
Mac-aligned UI/UX. Current builds add multi-account setup and Codex sign-in;
native-engine builds support seven providers. The launcher displays **Codexbar**
with original upstream artwork.

## Features

- Multiple named accounts per provider, validated before saving; reconnect and delete accounts independently
- Codex browser/device-code sign-in; manual credentials for Claude, Gemini and Copilot; API keys for Go, OpenRouter and DeepSeek
- Quota windows for Claude, Codex, Gemini, Go and Copilot when reported; monetary balance/budget for OpenRouter and DeepSeek
- Remaining-first account cards, phone detail sheets and wide-screen detail panels
- Shared last-good measurements, freshness and typed failures across dashboard, widgets, tile and persistent notification
- Pinned-account and account-overview widgets with independent display profiles
- Saved WorkManager refresh cadence, Manual mode and per-account transient retries
- Official incident status for supported sources and opt-in confirmed quota-recovery alerts
- Bounded quota history, one linear-estimate chart and read-only reported Codex credits/reset inventory
- Encrypted credentials, generation-guarded account ownership and offline About/licenses
- Jetpack Compose and Material 3 platform integration, with Mac CodexBar as the UI/UX reference

## Design direction and maintenance

Mac CodexBar is the reference for settings, features, terminology and information
hierarchy. The next phase is design-first: review corresponding Mac and proposed
mobile flows before implementing each feature. Current source aligns metric rows
and card headers; full settings, pace and display parity is still planned.
[docs/ui.md](docs/ui.md) separates implementation from design targets.

The bundled engine is **CodexBar Core/CLI**, not OpenAI's coding-agent **Codex CLI**.
Maintaining this app includes the pinned Core/CLI and Android patch, JSON-to-domain
adapters, Android-owned sign-in/token renewal, Kotlin integrations, Compose/widgets/
scheduling, and toolchain/packaging checks. A newer CLI alone does not supply every
Mac UI feature, desktop credential source or local spend history. The engine remains
pinned to **v0.71.0**; the **v0.73.0** Mac design reference is a separate choice.

## Builds and requirements

| Build | Purpose |
| --- | --- |
| `debug` / `release` | Existing Kotlin provider paths; no bundled CLI or native API-key settings |
| `nativeDebug` | Native diagnostics for a selected ABI |
| `nativeRelease` | Optimized ARM64 daily build with seven providers; no diagnostic activity |
| `nativeAcceptance` | Isolated, release-optimized synthetic diagnostics |

The app minimum is Android 8.0 / API 26; the native engine requires Android 9 / API 28.
The daily package remains `com.codexbar.android.native`, preserving earlier native-build
accounts and signing identity. Release diagnostics use
`com.codexbar.android.native.acceptance`; `nativeDebug` shares the daily `.native`
package and must only be installed on an isolated test runtime.

For the native build, follow [CONTRIBUTING.md](CONTRIBUTING.md) and the [native build guide](native/README.md).
Deliver `nativeRelease`; use `nativeAcceptance` for synthetic runtime probes. Both reuse
the pinned ARM64 payload. Current artifact measurements and acceptance limits live in [TEST.md](TEST.md).

Credentials are stored encrypted on-device and sent directly to the corresponding provider for authenticated requests. There is no project-operated quota backend.

## DeepSeek setup

Choose **Settings → Add DeepSeek account**, enter an API key, then **Validate & save**.
The [official balance API](https://api-docs.deepseek.com/api/get-user-balance) supplies
USD/CNY balances. One funded currency is displayed (USD preferred); currencies are
never added or converted. Missing amounts remain unknown. Spending, monthly limits
and reset windows are not inferred. The pinned native CLI exposes a formatted balance
description rather than numeric money, so this adapter uses the existing Kotlin API stack.

## GitHub Copilot setup

GitHub device sign-in is implemented but disabled in distributed builds until this
project has its own registered OAuth App with Device Flow enabled. Maintainers can
build with its public `COPILOT_OAUTH_CLIENT_ID`; no client secret is used. Registration
and owner-authorized Copilot quota acceptance remain pending. Existing OAuth token
entry remains available; another application's client ID is not bundled.

GitHub Copilot is also available in native-engine builds: **Settings → Add GitHub
Copilot account** accepts a GitHub OAuth token with Copilot access. A short-lived
Copilot session token is not interchangeable. This integration queries github.com;
enterprise hosts are not implemented. Browser device sign-in requires the registration above. Premium
and Chat windows are reported separately when supplied; plan-only responses do not
imply zero usage. Real-account acceptance remains pending.

## OpenRouter setup

In a native-engine build, choose **Settings → Add OpenRouter account**, enter an
account name and OpenRouter API key, then **Validate & save**. Validation uses the
pinned native Core's public API source. Saved accounts participate in ordinary
dashboard, worker and widget refreshes.

OpenRouter exposes financial counters: reported USD balance/spend and, when the
key has a cap, an **API key budget**. Missing balance is **unavailable**, not zero;
balance-only accounts have no synthetic quota bar or reset time. Real-account
accuracy remains an acceptance gate; see [TEST.md](TEST.md).

## OpenCode Go setup

1. Install the ARM64 `nativeRelease` APK and open **Codexbar**.
2. From the dashboard, navigate to **Settings → OpenCode Go**.
3. Choose **Add OpenCode Go account**, name it, paste your **OpenCode Go API key**, and press **Validate & save**.
4. Return to the dashboard and pull to refresh.

This source needs no refresh token, browser cookie or workspace ID. Drafts are saved only after validation succeeds. Use **Delete** on a saved account to remove its key and cache; other accounts remain intact. Explicit diagnostic self-tests use synthetic credentials.

## Getting Your Tokens

### Claude (Anthropic)

Claude uses OAuth tokens from Claude Code CLI. Extract both tokens from macOS Keychain:

```bash
security find-generic-password -s "Claude Code-credentials" -w \
  | python3 -c "
import sys, json
d = json.loads(sys.stdin.read())['claudeAiOauth']
print('Access Token:', d['accessToken'])
print('Refresh Token:', d['refreshToken'])
"
```

Paste the tokens into the Claude fields in Settings. Access tokens expire; the existing renewal path uses the refresh token, but Claude renewal is affected by the known upstream issue noted below.

### Codex (OpenAI / ChatGPT)

In Settings, choose **Add Codex account → Sign in with ChatGPT**. Copy the displayed
device code, open the authorization page and approve it in your browser. Device-code
sign-in must be enabled for your ChatGPT account/workspace. The app polls for at most
15 minutes; Cancel, editing the draft or leaving Settings stops the flow. Tokens stay
in memory until a quota request validates the account, then are stored encrypted.
Reconnect preserves the existing account if authorization or validation fails.

This follows the [Codex device-code protocol](https://github.com/openai/codex/tree/main/codex-rs/login)
using the existing public Codex client ID. It is an unofficial integration, not a
separate OpenAI-approved client registration. Claude and Gemini retain manual setup.

Alternatively, if you have the [Codex CLI](https://github.com/openai/codex) installed and logged in, extract tokens from `~/.codex/auth.json`:

```bash
# Access token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['access_token'])"

# Refresh token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['refresh_token'])"
```

Paste both into the Codex fields in Settings.

<details>
<summary>Alternative: Extract from browser (if CLI is not installed)</summary>

1. Open [chatgpt.com](https://chatgpt.com) in your browser
2. Open DevTools (F12) > Network tab
3. Look for requests to `https://chatgpt.com/backend-api/`
4. Copy the `Authorization: Bearer ...` token from request headers

</details>

### Gemini (Google)

Gemini requires **4 values**: access token, refresh token, OAuth client ID, and OAuth client secret. The client ID/secret are needed because Gemini uses Google OAuth for token refresh.

If you have the [Gemini CLI](https://github.com/google-gemini/gemini-cli) installed and logged in:

```bash
# 1. Access token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['access_token'])"

# 2. Refresh token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['refresh_token'])"

# 3. OAuth Client ID & Secret (from Gemini CLI source)
oauth_js="$(dirname "$(which gemini)")/../lib/node_modules/@google/gemini-cli/node_modules/@google/gemini-cli-core/dist/src/code_assist/oauth2.js"
python3 -c "
import re, sys
text = open('$oauth_js').read()
cid = re.search(r\"OAUTH_CLIENT_ID\s*=\s*'([^']+)'\", text)
sec = re.search(r\"OAUTH_CLIENT_SECRET\s*=\s*'([^']+)'\", text)
print('Client ID:', cid.group(1) if cid else 'not found')
print('Client Secret:', sec.group(1) if sec else 'not found')
"
```

Paste all four values into the Gemini fields in Settings.

## Build

For the original Kotlin-only development variant, with JDK 21 and Android SDK platform 35 configured:

```bash
bash ./gradlew :app:assembleDebug --max-workers=2
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Native toolchain setup, Release packaging and signing are documented in [native/README.md](native/README.md).

## Tech Stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (DI), Retrofit2 + OkHttp (networking)
- WorkManager (background sync), EncryptedSharedPreferences (security)
- KSP, kotlinx.serialization
- Swift Core/CLI compiled for Android/Bionic, with the existing provider logic reused through a bounded process adapter

## Known limitations

- Claude refresh-token renewal failures reported by the Android upstream are not fixed by this integration.
- Cadence changes reschedule WorkManager immediately; Manual cancels periodic work and automatic retries. Android background scheduling and OEM doze remain best-effort.
- Copilot browser sign-in needs this project's registered OAuth App; distributed builds retain manual token entry.
- Native-path live Codex renewal, provider-specific live accuracy, physical ARM64/minimum-API/16 KiB-page checks, widget-host interaction and TalkBack remain acceptance targets; see [TEST.md](TEST.md).

## Documentation

| Document | Purpose |
| --- | --- |
| [SPEC.md](SPEC.md) | Architecture, data flow, ownership and behavior contracts |
| [TEST.md](TEST.md) | Test commands, evidence and remaining acceptance |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Developer setup and contribution workflow |
| [CHANGELOG.md](CHANGELOG.md) | Changes maintained in this fork |
| [native/README.md](native/README.md) | Pinned native toolchain, packaging and build commands |
| [docs/ui.md](docs/ui.md) | Mac UI/UX reference, implemented presentation and design targets |

## Acknowledgments

Based on [CodexBar](https://github.com/steipete/CodexBar) by Peter Steinberger.
The Android UI and original Kotlin provider implementation originate from [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android).

## License

[MIT](LICENSE). Upstream attribution, native dependency notices and artwork provenance
are recorded in [NOTICE.md](NOTICE.md). Full license texts are included in every APK
and available offline under **Settings → About & licenses**. Download/release
descriptions must identify the app as an unofficial Android port.
