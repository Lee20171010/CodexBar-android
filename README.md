# CodexBar for Android

> **Unofficial Android port** of [**CodexBar**](https://github.com/steipete/CodexBar) by [@steipete](https://github.com/steipete). Independently maintained; not published, endorsed or supported by the macOS upstream or service providers.

Monitor AI-service quotas on Android. This fork integrates an Android-native Swift Core/CLI alongside the existing Kotlin app. **OpenCode Go uses the native engine**; Claude, Codex (ChatGPT), and Gemini retain their Kotlin implementations.

The native integration is currently an experimental build. Its automated acceptance covers synthetic credentials; see [TEST.md](TEST.md) for results and remaining real-account/device checks.

<p align="center">
  <img src="docs/Screenshot_20260305_025201_CodexBar.jpg" width="320" alt="Dashboard" />
  &nbsp;&nbsp;
  <img src="docs/Screenshot_20260305_025207_CodexBar.jpg" width="320" alt="Settings" />
</p>

These screenshots show the original Android UI. Native-engine builds additionally expose OpenCode Go settings.

## Features

- On-demand quota monitoring for Claude, Codex, Gemini, and OpenCode Go in native-engine builds
- OpenCode Go API-key configuration with 5-hour, weekly, and monthly windows when supplied by the provider
- Animated gauge bars showing remaining usage percentage
- Quick Settings tile for at-a-glance status
- WorkManager-based background refresh
- Persistent notification with per-service breakdown
- Local alerts for detected quota reset times
- Encrypted credential storage
- Material 3 with Dynamic Color

## Builds and requirements

| Build | Purpose |
| --- | --- |
| `debug` / `release` | Existing Kotlin provider paths; no bundled CLI or Go setting |
| `nativeDebug` | Native diagnostics for a selected ABI |
| `nativeRelease` | Optimized ARM64 native test build with OpenCode Go |

The app minimum is Android 8.0 / API 26; the native engine requires Android 9 / API 28. The native test package is `com.codexbar.android.native`, separate from the original app.

For the native build, follow [CONTRIBUTING.md](CONTRIBUTING.md) and the [native build guide](native/README.md). Prefer `nativeRelease` for remote testing and phone delivery. The measured Go APK is approximately **33.3 MiB**.

Credentials are stored encrypted on-device and sent directly to the corresponding provider for authenticated requests. There is no project-operated quota backend.

## OpenCode Go setup

1. Install the ARM64 `nativeRelease` APK and open **CodexBar Native Test**.
2. Open the dashboard using its button, then navigate to **Settings → OpenCode Go**.
3. Paste your **OpenCode Go API key** and press **Validate**.
4. Return to the dashboard and pull to refresh.

This source needs no refresh token, browser cookie or workspace ID. Clearing its field removes the saved key. The launcher self-test does not use your saved credentials.

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

If you have the [Codex CLI](https://github.com/openai/codex) installed and logged in, extract tokens from `~/.codex/auth.json`:

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
- The refresh-interval setting currently persists a preference without rescheduling WorkManager. Background scheduling is best-effort, not a precise timer.
- Physical ARM64, real-account quota accuracy, minimum-API and 16 KiB-page coverage remain acceptance targets; see [TEST.md](TEST.md).

## Documentation

| Document | Purpose |
| --- | --- |
| [SPEC.md](SPEC.md) | Architecture, data flow, ownership and behavior contracts |
| [TEST.md](TEST.md) | Test commands, evidence and remaining acceptance |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Developer setup and contribution workflow |
| [CHANGELOG.md](CHANGELOG.md) | Changes maintained in this fork |
| [native/README.md](native/README.md) | Pinned native toolchain, packaging and build commands |

## Acknowledgments

Based on [CodexBar](https://github.com/steipete/CodexBar) by Peter Steinberger.
The Android UI and original Kotlin provider implementation originate from [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android).

## License

[MIT](LICENSE). Upstream attribution, native dependency notices and artwork provenance
are recorded in [NOTICE.md](NOTICE.md). Full license texts are included in every APK
and available offline under **Settings → About & licenses**. Download/release
descriptions must identify the app as an unofficial Android port.
