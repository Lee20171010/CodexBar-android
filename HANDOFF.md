# Handoff: adapt native macOS CodexBar to Android

Prepared 2026-10-10. Implementation baseline: `7c0abb5a40568fedb49b48437dac5a47ce5ae48c`,
already pushed to `origin/main`. Recheck Git state before starting.

## Owner's requested outcome

Implement Android from native macOS CodexBar's information hierarchy, settings,
visual structure and interactions. Use the pinned SwiftUI/AppKit implementation
for behavior and matching-version native screenshots/runtime for visual acceptance.
Adapt touch targets, narrow layouts, navigation and Android system integration.
HTML is a comparison medium, not the design authority. The existing reconstruction
needs reconciliation against the native Mac before it can guide further UI work.
Work incrementally, one tested feature per commit. Do not stop at another plan or
mockup. Keep changes small and reuse existing Compose, Glance, account and native
integration boundaries. Do not push or publish without renewed authorization.

The owner also wants future Mac changes handled incrementally: preserve a pinned
reference and an item-by-item mapping so a later upstream diff identifies the
affected Android components, preferences and tests.

## Authoritative artifacts

- Native macOS source at `98d84b8a06957e193a9a8e58b40590736c94bf70`:
  the current product and behavior reference. Matching-version native visual
  evidence is still required for fidelity claims.
- `docs/uiux-prototype/index.html`, `review.css`, `review.js`: historical comparison
  reconstruction, pending native-Mac reconciliation; not an approved visual target.
- `docs/ui.md`: source mapping and acceptance gaps at the current pinned reference.
- `SPEC.md`: implemented data/ownership contracts; `TEST.md`: evidence and gaps.
- `AGENTS.md`: working agreement; ignored `AGENTS.local.md`: operator configuration
  before signing or device work. Never copy private values into tracked files.
- `FutureWork.md`: pre-existing untracked historical plan. Preserve it. Its checked
  boxes mean implementation commits landed, not full live/device/UIUX acceptance.
  This handoff supersedes its design-phase entry instructions where they differ.

Serve from the repository root, for example:

```sh
python3 -m http.server 8787 --bind 127.0.0.1 --directory docs/uiux-prototype
```

### What the reference proves

The HTML left side is a **source-based reconstruction**, not an actual running Mac
screenshot. The right side is a historical mobile proposal requiring correction.
Both use synthetic accounts, quota, credits and spend. Some controls only demonstrate
selection; they do not implement their corresponding algorithms. Do not transplant
demo data, the prototype's shared sample accounts or in-memory state into production.

The current prototype links Mac upstream source revision
`98d84b8a06957e193a9a8e58b40590736c94bf70`. Earlier design documents referenced
v0.73.0; this prototype used a newer available source checkout. The owner's installed
Mac version is **not known**. Do not claim pixel-perfect or installed-version parity.
Lock this source reference for implementation. Source-backed layout and behavior
corrections can proceed; visual acceptance requires matching-version native evidence.
Do not treat the right-hand HTML reconstruction as proof of Mac fidelity.

The native engine remains CodexBar Core/CLI v0.71.0 at
`cb5f0cbe88615a441272c6f9e44675bf594a3fa3`. It is not OpenAI's Codex coding-agent CLI.
Do not upgrade it implicitly for UI work. Mac preferences, SwiftUI views, local
spend collectors, Keychain and system integration are not automatically CLI JSON.

## Current implementation and acceptance

- Seven provider adapters; multiple generation-scoped accounts, validated drafts,
  encrypted credentials and one account refresh owner already exist.
- Normal native-build refresh uses native Codex OAuth on API 28+, Go, OpenRouter
  and Copilot. Claude/Gemini/DeepSeek use Kotlin; Codex has a Kotlin fallback.
- Shared freshness/last-good snapshots, display profiles, bounded history, linear
  estimate chart, official status, recovery receipts and two widget families exist.
- `e114632` implemented dashboard title/percent rows, reset placement, 6dp bars
  and identity/plan headers. This is partial alignment, not the complete prototype.
- Current signed trial: `0.0.7-beta-native` (versionCode 8; hashes and acceptance
  scope in `TEST.md`), with the review fixes
  (`ffb975a`, `a4dc917`, `8297d87`, `2536445`, `1216f0f`, `3338b8e`, `b7f7dad`);
  both Release and Native Acceptance builds pass. The old acceptance packaging
  failure is no longer an open build gate.
- Latest recorded host gate (2026-10-10): 128 JVM tests across 26 suites, Debug
  lint, NativeDebug Kotlin compilation, 24 notice checks and three Python
  tool/runtime-config tests pass. Isolated non-debuggable `nativeAcceptance`
  passed ten probes, four provider rejection flows, preference restart persistence,
  sign-in entry, offline notices and the synthetic quota UI matrix. The installed
  daily `.native` app was preserved; this is not runtime proof of the exact daily
  APK or physical ARM64 hardware.
- Real native Codex renewal, full widget-host/TalkBack/physical-device acceptance
  remain unproven. Earlier owner confirmation of Codex/Go on 0.0.4 used Kotlin Codex.

## Implement in reviewable slices

First inspect callers and current code, then maintain a concise mapping table in
`docs/ui.md`: native Mac surface/control → Mac file/symbol/revision → Android
file/component/preference → data dependency → implementation/test status.
Use this concrete gap inventory rather than repeating broad exploratory research.

| Slice | Native Mac surfaces and required outcome | Reuse / boundary |
| --- | --- | --- |
| 1. Navigation and settings structure | Settings directory with General, Menu/display, Provider/accounts, Notifications, Usage & Spend, Advanced and About; phone drill-down/back and wide-screen adaptation | Existing settings ViewModel/navigation and account state; no new navigation framework |
| 2. Overview and account flow | Identity/plan → quota/reset → freshness; account selection, refresh, offline last-good and reconnect-required states; full detail navigation | `ServiceCard`, `QuotaGaugeBar`, dashboard/detail and `QuotaSnapshot`; preserve all real windows and unknown states |
| 3. Menu/display preferences | Remaining/used, reset format, optional amounts, visible items/defaults, layout preferences and meaningful warning markers | Existing `DisplayOptions`/controls/profiles; persist independently where scope requires; narrow/large-font fallback needs work |
| 4. Provider settings | Account/reconnect, supported source, visible windows, accent and headline-window selection | Existing validated drafts and credential sessions; do not offer unsupported cookie/desktop sources |
| 5. General | Refresh cadence, refresh-on-open/status preferences and language; currency only with a supported data/formatting contract | Existing WorkManager owner; Manual and 15-minute periodic floor remain explicit |
| 6. Mac pace | Port verified Mac pace semantics, Work days, reserve/deficit copy and indicators | Existing history estimate is a different calculation; inspect pinned Mac rules and test edge cases before claiming parity |
| 7. Notifications | Depleted, threshold, predictive and confirmed-reset rules as verified in Mac; Android permission handling and durable deduplication | Existing recovery rule (95% → at most 80% used) is not full Mac reset parity; no timestamp-only recovery |
| 8. Widgets/system glance | Align existing pinned/overview widgets with the same display language, scope and freshness | Glance, tile, persistent notification; no mobile menu-bar token editor |
| 9. Advanced/About | Privacy display where implemented, correct version/source/licenses; accurate platform boundaries | Existing offline notices; masking identity must not change account ownership |
| 10. Spend/data-gated features | Match the target hierarchy when verified data exists; otherwise explicit unavailable state | Do not fabricate daily cost from balances or reuse quota history as a spend ledger; no invented FX conversion |

Ordering may change for real dependencies, but finish each slice before expanding.
Status update (2026-10-10): slice 1 provider panes and sidebar grouping landed
(`8f9256a`, `3338b8e`); slice 3 preference propagation/per-field inheritance landed
(`a4dc917`, `b7f7dad`); slice 6 pace duration and work-day fixes landed (`ffb975a`);
slice 7 alert work-day awareness and overquota receipt retention landed (`2536445`).
Host checks and the isolated synthetic runtime/UI matrix pass. Screenshot-based
Mac comparison, widget-host, TalkBack and physical-device acceptance remain open;
slices 5, 9 and 10 retain their remaining scope, with Spend/FX still data-gated.
Controls offered as active in the app must work and persist appropriately. Prototype
labels such as “planned” belong in implementation tracking, not as inert production
toggles. Data-gated screens can truthfully show unavailable with an explanation;
record exact missing source/contract rather than filling with sample data.

### Explicitly deferred by the current design

Mac iCloud Sync, Hooks, Plugins, desktop CLI install, terminal integration,
Keychain/browser discovery, Homebrew, menu-bar token editor/Merge Icons, debug and
storage tooling are reference inventory, not an instruction to build Android
replacement systems. Confetti is shown as Mac inventory but deferred. Spend/FX
requires actual data support. Do not block the rest on these platform/data gates.
Do not reproduce the prototype's review-only state picker, two-way Mac/mobile
synchronization, desktop traffic lights or diagnostics in the Android product.

## Verification and finish condition

For each feature: focused behavior tests where logic changes, compile/lint, staged
privacy review, then a small Conventional Commit. Keep zh/en resources, accessible
labels/touch targets, back navigation, IME/system insets and large text working.
Shared values must remain consistent across dashboard, details, widget, tile and
notifications. Rename/reconnect/delete, late completions and sibling isolation must
retain existing protections. No live credentials in fixtures or screenshots.

After UI slices, build the optimized signed ARM64 `nativeRelease` and isolated
`nativeAcceptance` with the existing key/private configuration and cached payload.
Use background bounded builds, Gradle `--max-workers=2`; use the configured full
runtime lock and ADB wrapper for device work. Do not overwrite the owner's daily
installation for diagnostics. Real quota/login/refresh requires explicit permission.

Verify phone/light/dark/200% text/wide, empty/loading/stale/offline/auth error,
account switching/reconnect/delete, detail scrolling, settings persistence/restart,
widget pinning/resizing, notification permission/receipts and accessibility.
Compare actual Android rendering/interactions against the pinned native Mac and
document deliberate adaptations. Use available supported vision/browser tooling;
obey the active harness's image-routing instructions rather than treating an old
handoff's tool preference as authority. The owner requested avoiding the obsolete
vision subagent, but tool availability and higher-priority rules govern execution.

The HTML received syntax checks and DOM-stub tests for 11 scenes, synchronized
percentage settings, offline/auth states and reset. Browser-panel requests timed
out, so actual browser layout/visual acceptance was **not completed**. Correct
prototype ambiguities against source and mobile constraints instead of copying bugs.

Finish by updating README/SPEC/TEST/CHANGELOG and the per-feature mapping, reporting
commit SHAs, checks, actual artifact/hash, screenshot/interaction evidence, and
remaining explicit platform/data gates. The requested end state is a working
Android adaptation of the native Mac design, not another HTML-only
handoff. Ask only for genuine account/platform/product decisions that block a slice;
continue independent work meanwhile. Delivery/push/release needs current authorization.

## Future upstream changes

Pin the Mac reference independently from engine/toolchain versions. On reference
updates, diff mapped source files and classify changes as presentation, preference
behavior, calculation or data contract. Update only affected Android components and
their dependents; shared component changes still require all-caller checks. Keep
per-feature evidence so each update is traceable without redesigning the whole app.
