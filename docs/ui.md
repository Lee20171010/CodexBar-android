# UI design language: Mac CodexBar internalized for mobile

The design authority is macOS CodexBar's settings, features, information hierarchy
and interactions, rendered with Compose and Android touch/accessibility conventions.
The current reference is [steipete/CodexBar at 98d84b8](https://github.com/steipete/CodexBar/tree/98d84b8a06957e193a9a8e58b40590736c94bf70),
including `docs/ui.md`, `MenuCardMetricRow.swift`, `UsageProgressBar.swift`,
`MetricRowHeader` and `UsageMenuCardLayout.swift`. Android fork layouts are not the
design authority. The native engine remains pinned independently to v0.71.0.

## Status and design workflow

`0.0.6-beta-native` contains partial settings, display, pace, alerts and widget
implementation. Native Mac alignment is incomplete. The following implementation
defects are now fixed in source and host-verified (128 JVM tests, Debug lint,
NativeDebug compilation, 2026-10-10): preference propagation/per-field inheritance
(`a4dc917`, `b7f7dad`), Used percentage labels and pace ETA units (`8297d87`),
seconds-based default weekly duration (`ffb975a`), work-day-aware alerts with
retained overquota receipts (`2536445`), declared `en`/`zh-TW` locales (`1216f0f`),
native Mac sidebar grouping with individual provider panes and accessible wrapping
accents (`8f9256a`, `3338b8e`), and Mac-style reset-text wrapping (`33f3f8a`).
The signed 0.0.7 trial passed isolated native/repository probes, provider navigation,
preference restart persistence and synthetic phone/dark/200%-text/wide quota UI
assertions. This does not close screenshot-based Mac comparison, widget-host,
TalkBack or physical-device gates. See `TEST.md` for artifact-specific evidence.

The existing HTML is a historical source reconstruction awaiting correction.
Native `SettingsSidebarView`, grouped SwiftUI `Form` sections, `MetricRowHeader`
and menu-card presentation are the implementation references. Matching-version Mac
screenshots/runtime have not yet been verified. Source-backed changes must be
labelled separately from visually accepted changes.

Still open: account navigation/layout preferences, refresh-on-open/status controls,
pace visibility and menu-bar stripe indicators, Mac's frozen-height live-update
measurement, shared-surface visual behavior and the
phone/dark/large-text/widget/accessibility acceptance matrix. Spend/FX and the
explicit macOS-only integrations retain their separate data/platform gates.

### Native-source correction register

The owner confirmed retaining `98d84b8a06957e193a9a8e58b40590736c94bf70`
as the reference. These changes are source-backed; native Mac visual acceptance
remains pending.

| Native source at the pinned revision | Android correction | Remaining evidence / gap |
| --- | --- | --- |
| `Sources/CodexBar/PreferencesSidebar.swift`, `SettingsSidebarView`; `PreferencesComponents.swift`, `SettingsIconChip` | `SettingsScreen.kt`: compact icon rows, app panes before provider group, visible and accessible selected state; minimum 48dp touch rows; individual provider sidebar panes landed (`8f9256a`, `3338b8e`) | Phone/wide visual acceptance pending |
| `Sources/CodexBar/UsageMenuCardHeaderAndUsageSectionView.swift`, `MetricRowHeader` | `QuotaGaugeBar.kt`: title/reset can wrap into two rows, preserving trailing reset alignment (`33f3f8a`) | Large-text/narrow-screen runtime acceptance pending; Mac's frozen-height live-update measurement is not implemented |

Before the next UI/UX feature, review a side-by-side design: Mac on the left,
expected mobile interaction on the right. Cover settings, navigation, account
actions, loading/error/unknown states and display preferences, not only appearance.
Identify the upstream source and data contract for each feature. Implement the
approved slice in one tested commit. Temporary HTML mockups are review artifacts,
not evidence that the CLI supplies their illustrated values or controls.

## Settings and capability mapping

| Mac surface or behavior | Current Android behavior | Alignment work / boundary |
| --- | --- | --- |
| Account menu cards and account selection | Named accounts, compact dashboard, phone detail sheet and wide detail panel | Multi-account navigation in use; grouping refinements follow prototype feedback |
| Remaining/used percentage | Global Remaining/Used toggle with per-account and per-widget overrides (`e1bae92`, `490f15d`) | Widget bars follow the same fill direction |
| Countdown/absolute reset | Menu & display page toggle; per-account override retained | — |
| Visible usage items | Stable window-ID filters; detail retains all windows and hidden risk remains visible | Labels and defaults reviewed against prototype |
| Provider accent colors | Per-account accent swatches applied to cards, details, tile and widgets (`5e2c1be`) | Fixed palette, not a free color picker |
| Headline (percent) window | Per-account headline window with Auto fallback (`5e2c1be`) | Cached windows seed the picker |
| Pace copy, bar indicator and Work days | Mac `UsagePace.weekly` port: stage, ETA, will-last and Work-day split shown in details (`c7f8969`) | Menu-bar stripe indicators have no mobile counterpart yet |
| Reported balances and credits | Provider-reported money; native Codex credits/caps/reset inventory | Preserve units and unknown states; do not invent balances for unsupported sources |
| Preferred currency | Provider currency is retained; no conversion | Needs a rate source, timestamps and offline policy before implementation |
| Usage & Spend, ledger and heatmap | One quota-history chart; reported money where supported; Spend page states unavailability explicitly | A quota chart is not a spend ledger; desktop history sources need separate availability checks |
| Reset notifications | Independent depleted, low (20%), predictive-pace and measured recovery alerts, each with durable dedup (`96221c1`) | Recovery threshold rule is not full Mac reset-receipt parity |
| Service incidents | Official status for supported Core sources, independently aged | Align presentation while retaining unknown/unavailable states |
| Widgets and menu-bar glanceability | Account/overview widgets, Quick Settings tile, persistent notification sharing the display chain (`490f15d`) | Mac menu-bar token editor has no direct mobile surface |
| Provider connection settings | Validated drafts, reconnect/delete, Codex sign-in; registration-gated Copilot device flow | Desktop browser/Keychain discovery is not provided by Android encrypted storage |
| App language | System per-app locale settings entry point (API 33+) (`55a239b`); declared `en`/`zh-TW` locale list makes both appear in the system picker (`1216f0f`) | In-app language switcher not implemented; system picker is the platform path |
| About and updates | Version, offline licenses and manually delivered APK | Homebrew controls are macOS-specific |

Mac parity is evaluated per feature. SwiftUI presentation, desktop local files,
browser cookies and spend collectors are not automatically part of CLI JSON.
Platform-specific terminal, Keychain and menu-bar controls require an applicable
Android use case rather than a literal settings copy. New product features should
have a Mac counterpart; Android adaptation preserves touch, accessibility, back
navigation, insets and background-execution rules.

## Row anatomy (Mac `MetricRow` → mobile)

| Mac | Mobile internalization |
| --- | --- |
| Header line: `"\(title) \(percentLabel)"` one text, `.body`/`.medium`, monochrome | Same: `bodyMedium` + `FontWeight.Medium`, e.g. `Session 43% left`. Percent is NOT a separate colored number. |
| Reset text right-aligned on the same line (`.footnote`, secondary, up to 2 lines); falls back to a second line when it doesn't fit | `FlowRow` wraps the trailing reset onto its own line, `labelSmall`, `onSurfaceVariant`; narrow/large-text runtime acceptance pending |
| `UsageProgressBar`: 6pt full-capsule bar, neutral track, fill = provider accent color | Same: 6dp, `RoundedCornerShape(3.dp)`, track `surfaceContainerHighest`, fill = provider `brandColor` |
| Pace tip on the bar (punch + stripe: green reserve / red deficit) | Not yet plumbed per-bar; planned refinement |
| Meta line below bar (`.footnote`, secondary, up to 2 lines): pace · detail joined with ` · ` | Same pattern for future pace/status meta |
| Card-style metric variant: 10pt padding, secondary 8% background, 10pt radius | Reserved for grouped/stacked rows |

## Card header (Mac `UsageMenuCardHeaderView` → mobile)

| Mac | Mobile internalization |
| --- | --- |
| Provider name `.headline`/`.semibold` | `titleMedium` + `FontWeight.SemiBold`, max 2 lines |
| Account identity `.subheadline`, secondary | `labelMedium`, `onSurfaceVariant`, 1 line |
| Plan badge `.footnote`/`.semibold` | Small chip beside the name: `labelSmall` semibold, `surfaceContainerHighest`, 6dp radius |
| Spacing: 4pt line spacing, 12pt column gap | Same: 4dp / 12dp |

## Spacing scale (Mac `UsageMenuCardLayout` → dp)

- Card inner horizontal padding: 20dp
- Header content spacing: 6dp; header line spacing: 4dp; header column spacing: 12dp
- Section top padding: 10dp (usage), 6dp (other); section bottom: 6dp
- Row internal vertical spacing: 6dp
- Metric rows separated by 12dp (Mac's `VStack(spacing: 12)`)

## Type scale (Mac semantic → Material3 slot)

- `.headline` semibold → `titleMedium` SemiBold (card name)
- `.body` medium → `bodyMedium` Medium (metric title line)
- `.subheadline` → `labelMedium` (identity line)
- `.footnote` → `labelSmall` (reset, meta)
- `.caption` semibold uppercase → `labelSmall` SemiBold uppercase (group headers)

## Color semantics

- Monochrome by default: numbers and labels use primary/secondary text colors.
  Current rows use warning markers for explicit risk; Mac pace copy remains planned.
- Bar fill takes the **provider accent** (`brandColor`), like the Mac's
  `progressColor`.
- Red/green are reserved for pace semantics (deficit = red, reserve = green).
- `error` color stays for real errors and the hidden-risk warning.

## Copy

- Percent: `43% left` (default remaining semantics); never a bare number. Used style
  shows `35% used` with the flipped fill (`8297d87`); warning notifications keep
  remaining semantics.
- Reset: countdown `Resets in 6d 17h` by default; absolute date/time is a
  display option. Missing reset keeps label + percent and shows no invented time.
- Pace (when plumbed): `On pace` / `X% in deficit` / `X% in reserve`, plus
  `Runs out in …` vs `Lasts until reset`. Hidden until 3% of the window elapsed.
- Unknown ≠ zero; balance-only providers show money, never a fake quota.

## Platform adaptation

No hover-only affordances, no Mac window chrome, no AppKit/SwiftUI, no design
DSL, no animation/confetti. Android keeps touch targets, back navigation and
system insets. Material 3 supplies platform mechanics; Mac remains the product
feature and information-hierarchy reference.
