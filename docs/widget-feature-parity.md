# Android widget feature parity

This matrix maps macOS CodexBar-style quota widget semantics to the Android Glance implementation.

| Feature | Android status | Notes |
| --- | --- | --- |
| Provider quota bars | Shipped | Uses the shared immutable presentation snapshot and cached renderer data. |
| Used/remaining numbers | Shipped | Remaining labels and bar progress come from the shared mapper; widget does not recalculate percentages. |
| Multiple windows/model buckets | Shipped | Per-instance profile controls maximum rows per provider. |
| Reset countdown/date | Shipped | Can be toggled per widget instance. |
| Pace / reserve / forecast | Shipped | Pace is computed from bounded local quota history and can be toggled per widget. |
| Plan/tier | Shipped | Displayed when provided by the provider. |
| Credits/balance | Shipped where provider exposes it | Claude credit labels are preserved by the presentation snapshot; unknown fields are hidden. |
| Freshness/stale state | Shipped | Freshness can be shown or hidden per widget. |
| Provider ordering | Shipped | Per-instance profile stores selected providers in stable order. |
| Responsive layout | Shipped | Glance composes the launcher's exact size and adapts visible provider count by height. |
| Multiple widget profiles | Shipped | Configuration is keyed by `appWidgetId`; deleting one widget removes only that profile. |
| Redaction | Shipped | Widget redaction follows privacy settings and cached quota data is excluded from backup. |
| Refresh action | Shipped | Routes through unique WorkManager refresh so repeated taps coalesce. |
| Launcher theming | Shipped | Surfaces and labels come from `GlanceTheme`, and severity colors resolve from resources with a `values-night` variant. |
| Guaranteed first render | Shipped | Every setup read before composition is guarded and time bounded, so the provider's loading layout is always replaced. |
| Unrenderable fallback | Shipped | After the render worker exhausts its retries the widget is replaced with a tappable error layout instead of an inert loading tile. |

Unsupported provider fields are intentionally hidden instead of fabricated. Widget previews use static synthetic layout resources and never include real account or quota data.
