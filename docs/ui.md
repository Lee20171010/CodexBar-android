# UI design language: Mac CodexBar internalized for mobile

The design authority is macOS CodexBar's settings, features, information hierarchy
and interactions, rendered with Compose and Android touch/accessibility conventions.
The reference is [steipete/CodexBar v0.73.0](https://github.com/steipete/CodexBar/tree/1d313fe50a361fc0a12383da0cdc11a75f59daa5),
including `docs/ui.md`, `MenuCardMetricRow.swift`, `UsageProgressBar.swift`,
`MetricRowHeader` and `UsageMenuCardLayout.swift`. Android fork layouts are not the
design authority. The native engine remains pinned independently to v0.71.0.

## Status and design workflow

Implemented in `e114632`: dashboard title/percent row, reset placement, 6dp
provider-accent bar and name/identity/plan header. These source changes follow the
last delivered daily APK and need renewed Release/UI acceptance. Full UI/UX
alignment is **not complete**; the remaining sections describe both current
behavior and targets, not a promise that every Mac capability is available.

Before the next UI/UX feature, review a side-by-side design: Mac on the left,
expected mobile interaction on the right. Cover settings, navigation, account
actions, loading/error/unknown states and display preferences, not only appearance.
Identify the upstream source and data contract for each feature. Implement the
approved slice in one tested commit. Temporary HTML mockups are review artifacts,
not evidence that the CLI supplies their illustrated values or controls.

## Settings and capability mapping

| Mac surface or behavior | Current Android behavior | Alignment work / boundary |
| --- | --- | --- |
| Account menu cards and account selection | Named accounts, compact dashboard, phone detail sheet and wide detail panel | Review multi-account navigation and grouping against Mac |
| Remaining/used percentage | Remaining percentage | Used/remaining switch is planned |
| Countdown/absolute reset | Per-account and independent widget display profiles | Settings organization still needs design review |
| Visible usage items | Stable window-ID filters; detail retains all windows and hidden risk remains visible | Align labels, placement and defaults |
| Provider accent colors | Provider-defined colors on dashboard bars | User-editable colors are planned |
| Pace copy, bar indicator and Work days | Bounded measurement history and a labeled linear estimate | Mac reserve/deficit computation, copy, stripes and Work days are not implemented |
| Reported balances and credits | Provider-reported money; native Codex credits/caps/reset inventory | Preserve units and unknown states; do not invent balances for unsupported sources |
| Preferred currency | Provider currency is retained; no conversion | Needs a rate source, timestamps and offline policy before implementation |
| Usage & Spend, ledger and heatmap | One quota-history chart; reported money where supported | A quota chart is not a spend ledger; desktop history sources need separate availability checks |
| Reset notifications | Opt-in measured recovery alert (95% used to at most 80% used) | This threshold rule is not full Mac reset-receipt/confirmed-reset parity |
| Service incidents | Official status for supported Core sources, independently aged | Align presentation while retaining unknown/unavailable states |
| Widgets and menu-bar glanceability | Account/overview widgets, Quick Settings tile, persistent notification | Review native Android equivalents; Mac menu-bar token editor has no direct mobile surface |
| Provider connection settings | Validated drafts, reconnect/delete, Codex sign-in; registration-gated Copilot device flow | Desktop browser/Keychain discovery is not provided by Android encrypted storage |
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
| Reset text right-aligned on the same line (`.footnote`, secondary, up to 2 lines); falls back to a second line when it doesn't fit | Right side of header row, `labelSmall`, `onSurfaceVariant`, up to 2 lines; separate-row fallback is not implemented and needs narrow/large-text review |
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

- Percent: `43% left` (default remaining semantics); never a bare number.
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
