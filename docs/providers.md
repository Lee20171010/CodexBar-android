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
| Claude | Kotlin HTTP | OAuth (refresh token) | service endpoint; no region selector | % windows | shipped earlier releases |
| Codex | Kotlin HTTP; native CLI when enabled on API 28+ | OAuth (refresh token) | service endpoint; no region selector | % windows | shipped earlier releases |
| Gemini | Kotlin HTTP | OAuth (refresh token) | service endpoint; no region selector | % windows | shipped earlier releases |
| OpenCode Go | native CLI | API key | service endpoint; no region selector | % windows | shipped earlier releases |
| DeepSeek | Kotlin HTTP (public balance API) | API key | service endpoint; no region selector | balance-only money per currency (USD/CNY); no % windows | shipped earlier releases |
| GitHub Copilot | native CLI | API token | service endpoint; no region selector | % windows | shipped earlier releases |
| OpenRouter | native CLI | API key | service endpoint; no region selector | balance-only money (balance/spent); no % windows | shipped earlier releases |
| Venice | native CLI | API key | not separately verified | `Balance` window; percent is upstream-derived, not recomputed on Android | synthetic CLI-parser tests only; device/live **not verified** |
| Amp | native CLI | access token | not separately verified | `Amp` primary + `Orb` secondary % windows, `Amp Free` supplemental window; plan/tier from reported login method | synthetic CLI-parser tests only; device/live **not verified** |
| Z.ai | native CLI | access token | global; personal coding plan | `5-Hour` + `Weekly` % windows | synthetic CLI-parser tests only; device/live **not verified** |
| Factory | native CLI | access token | not separately verified | `Standard`/`Premium`/`Monthly` % windows + reported provider cost as money (currency from payload, default USD) | synthetic CLI-parser tests only; device/live **not verified** |
| Alibaba Coding Plan | native CLI | access token | international Coding Plan | % windows; plan from reported login method | synthetic CLI-parser tests only; device/live **not verified** |
| Charm Hyper | native CLI | manual API key | not separately verified | balance-only: raw `Hypercredits`/`Balance` value rendered verbatim as HC provider credits — not fiat, no conversion, no reset windows | synthetic CLI-parser tests only; device/live **not verified** |
| Kimi | native CLI | Kimi Code API key | China site only (kimi.com); international kimi.ai not supported | `7-Day` primary + `5-Hour` secondary % windows, `Total usage` supplemental monthly; plan from membership level; weekly reset alerts only (session lane not detected), no pace alerts | synthetic CLI-parser tests only; device/live **not verified** |
| MiniMax | native CLI | Token Plan Subscription Key (`sk-cp-`) | global region default; upstream retries the China mainland host once on credential rejection (MiniMaxUsageFetcher.swift:101-139); pay-as-you-go `sk-api-` keys rejected client-side, unknown prefixes decided by server | % windows labeled from actual minutes (5-Hour/7-Day/Monthly; arbitrary minutes render "N min", neutral slot name only when duration is missing); plan from reported login method; no balance/money on the API path; **no pace** (PacePolicy lanes cover Codex/Claude/OpenCode Go/Copilot only) and no predictive alerts (Codex/Claude only); recovery alerts only where lanes match: session needs PRIMARY with explicit duration ≤21600 s, weekly needs exactly one 604800 s window (ambiguous if several); monthly windows have no alert lane | synthetic CLI-parser tests only; device/live **not verified** |

## Deferred and future sources

- Cookie-based and desktop-session sources the upstream CLI uses are not
  available on Android and remain deferred; no Android credential surface
  exists for them.
- Additional upstream catalog entries become Android candidates only when a
  supported Android credential source (API key or OAuth) exists and synthetic
  plus real-account acceptance can be performed.

This matrix records integration state, not a delivered release; see
[CHANGELOG.md](../CHANGELOG.md) for release metadata.
