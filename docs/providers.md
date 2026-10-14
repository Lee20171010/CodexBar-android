# Provider Support Matrix

Android currently offers fifteen selectable providers: the seven shipped before
0.0.12 plus eight native-CLI integrations added since. Sources are split:
Claude, Codex, Gemini and DeepSeek use Kotlin HTTP implementations; the
remaining eleven use the pinned native CLI (`steipete/CodexBar` v0.71.0,
unchanged in this release; see [native/README.md](../native/README.md)). Codex
additionally uses the native CLI when it is enabled on API 28+; native-only
providers require API 28+.

**The CLI catalog is not the Android-usable list.** The upstream CLI knows more
providers than Android can supply credentials for: sources that depend on
browser cookies or desktop-app session files are deferred on Android, and
desktop capabilities are not automatically available through the supported
Android sources.

## Matrix

| Provider | Source | Auth | Region | Units | Verification |
| --- | --- | --- | --- | --- | --- |
| Claude | Kotlin HTTP | OAuth (refresh token) | service endpoint; no region selector | % windows | manual credentials only; live acceptance pending |
| Codex | Kotlin HTTP; native CLI when enabled on API 28+ | OAuth (refresh token) | service endpoint; no region selector | % windows | owner-confirmed live on phone |
| Gemini | Kotlin HTTP | OAuth (refresh token) | service endpoint; no region selector | % windows | manual credentials only; live acceptance pending |
| OpenCode Go | native CLI | API key | service endpoint; no region selector | % windows | owner-confirmed live on phone |
| DeepSeek | Kotlin HTTP (public balance API) | API key | service endpoint; no region selector | balance-only money per currency (USD/CNY); no % windows | live acceptance pending |
| GitHub Copilot | native CLI | API token | service endpoint; no region selector | % windows | phone device sign-in added 0.0.14; live acceptance unverified |
| OpenRouter | native CLI | API key | service endpoint; no region selector | balance-only money (balance/spent); no % windows | live acceptance pending |
| Venice | native CLI | API key | not separately verified | `Balance` window; percent is upstream-derived, not recomputed on Android | synthetic CLI-parser tests only; device/live **not verified** |
| Amp | native CLI | access token | not separately verified | `Amp` primary + `Orb` secondary % windows, `Amp Free` supplemental window; plan/tier from reported login method | synthetic CLI-parser tests only; device/live **not verified** |
| Z.ai | native CLI | access token | global; personal coding plan | `5-Hour` + `Weekly` % windows | synthetic CLI-parser tests only; device/live **not verified** |
| Factory | native CLI | access token | not separately verified | `Standard`/`Premium`/`Monthly` % windows + reported provider cost as money (currency from payload, default USD) | synthetic CLI-parser tests only; device/live **not verified** |
| Alibaba Coding Plan | native CLI | access token | international Coding Plan | % windows; plan from reported login method | synthetic CLI-parser tests only; device/live **not verified** |
| Charm Hyper | native CLI | manual API key | not separately verified | balance-only: raw `Hypercredits`/`Balance` value rendered verbatim as HC provider credits — not fiat, no conversion, no reset windows | synthetic CLI-parser tests only; device/live **not verified** |
| Kimi | native CLI | Kimi Code API key | China site only (kimi.com); international kimi.ai not supported | `7-Day` primary + `5-Hour` secondary % windows, `Total usage` supplemental monthly; plan from membership level; weekly reset alerts only (session lane not detected), no pace alerts | synthetic CLI-parser tests only; device/live **not verified** |
| MiniMax | native CLI | Token Plan Subscription Key (`sk-cp-`) | global region default; upstream retries the China mainland host once on credential rejection (MiniMaxUsageFetcher.swift:101-139); pay-as-you-go `sk-api-` keys rejected client-side, unknown prefixes decided by server | % windows labeled from actual minutes (5-Hour/7-Day/Monthly; arbitrary minutes render "N min", neutral slot name only when duration is missing); plan from reported login method; no balance/money on the API path; **no pace** (PacePolicy lanes cover Codex/Claude/OpenCode Go/Copilot only) and no predictive alerts (Codex/Claude only); recovery alerts only where lanes match: session needs PRIMARY with explicit duration ≤21600 s, weekly needs exactly one 604800 s window (ambiguous if several); monthly windows have no alert lane | synthetic CLI-parser tests only; device/live **not verified** |

## Setup guidance

Add accounts under **Settings → Accounts**. Device-flow providers open an
authorization page with a short code; API-key providers show a link to the
official key page where verified, plus key-type help. Manual token entry for
device-flow providers is collapsed behind an explicit toggle (reconnect and
prefilled drafts start expanded). If no browser can open a link, the URL is
shown for manual entry.

- **Codex** — primary path is **Sign in with ChatGPT** (device flow). Manual
  access/refresh token entry remains available.
- **GitHub Copilot** — phone device sign-in uses the public VS Code OAuth
  client; consent is shown before authorization. A GitHub PAT is **not** the
  required credential, so no token page is linked; manual entry accepts an
  existing GitHub OAuth token with Copilot access.
- **Claude** — no fresh on-phone OAuth exists: paste the access/refresh token
  pair from an existing Claude login (e.g. from the Claude CLI credentials on
  your computer; storage location is platform-specific). Token lifetimes are
  not universal; refresh happens against the Claude OAuth endpoint.
- **Gemini** — reads Gemini CLI / Gemini Code Assist quota, not Gemini App
  usage. Requires the credentials of the **original issuing OAuth client**
  (access token, refresh token, client ID and secret); an arbitrary new Google
  client cannot substitute. Google retired consumer access (free, AI Pro,
  Ultra) from Gemini CLI on June 18, 2026; a supported organization license
  (Code Assist Standard or Enterprise) is required for this path. Paid Gemini
  API keys are billed separately and are not read here.
- **API-key providers** — create a key on the linked official page:
  OpenCode Go (opencode.ai/auth), DeepSeek (platform.deepseek.com/api_keys),
  OpenRouter (openrouter.ai/keys), Venice (venice.ai/settings/api),
  z.ai (z.ai/manage-apikey), Factory (app.factory.ai/settings/api-keys,
  `fk-` keys), Alibaba Coding Plan (international Model Studio Coding Plan
  console page; pay-as-you-go keys are not accepted), Charm Hyper
  (hyper.charm.land). Amp guidance points at Security Settings in your
  ampcode.com account. Kimi supports China-site (kimi.com) keys only. MiniMax
  accepts Token Plan Subscription Keys (`sk-cp-`) only; pay-as-you-go
  `sk-api-` keys are rejected with an explanation before any request.

## Deferred and future sources

- Cookie-based and desktop-session sources the upstream CLI uses are not
  available on Android and remain deferred; no Android credential surface
  exists for them.
- Additional upstream catalog entries become Android candidates only when a
  supported Android credential source (API key or OAuth) exists and synthetic
  plus real-account acceptance can be performed.

This matrix records integration state, not a delivered release; see
[CHANGELOG.md](../CHANGELOG.md) for release metadata.
