# UI design language: Mac CodexBar internalized for mobile

This is the internalization spec. The design authority is the macOS CodexBar
menu card (steipete/CodexBar v0.73.0 sources: `MenuCardMetricRow.swift`,
`UsageProgressBar.swift`, `MetricRowHeader`, `UsageMenuCardLayout.swift`,
`docs/ui.md`). Android renders the **same design language** with Compose and
touch interaction. Fork patterns from other Android ports are not the reference.

## Row anatomy (Mac `MetricRow` → mobile)

| Mac | Mobile internalization |
| --- | --- |
| Header line: `"\(title) \(percentLabel)"` one text, `.body`/`.medium`, monochrome | Same: `bodyMedium` + `FontWeight.Medium`, e.g. `Session 43% left`. Percent is NOT a separate colored number. |
| Reset text right-aligned on the same line (`.footnote`, secondary, up to 2 lines); falls back to a second line when it doesn't fit | Same: right side of the header row, `labelSmall`, `onSurfaceVariant` |
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
  The old fork pattern of coloring the percent red at ≥85% is **not** the Mac
  design; urgency shows through pace copy and warning markers instead.
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

## Non-goals (unchanged)

No hover-only affordances, no Mac window chrome, no AppKit/SwiftUI, no design
DSL, no animation/confetti. Android keeps touch targets, back navigation and
system insets. Where the Mac has no counterpart, Material 3 is the fallback.
