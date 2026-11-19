# Material 3 Expressive conformance audit — Sukashi Map Web

This document is a **reproducible conformance argument**, not a claim of Google certification.
It defines a practical subset of Material 3 Expressive rules used by this app, then proves the
running UI satisfies that subset using measured DOM values from Chromium.

## 1. Conformance model

Let:

- `S = {0, 4, 8, 12, 16, 20, 24, 32, 40, 48, 56, 64}` be the spacing grid (4dp base, 8dp rhythm).
- `R = {0, 8, 12, 16, 20, 24, 28, 32, ∞}` be the Expressive shape scale (px; ∞ = pill / full).
- `T = {12, 14, 16, 20, 22, 24, 28}` be the type-role sizes used by this product.
- `H = 44px` be the minimum coarse-pointer target, and `Hd = 40px` the dense desktop target.

The app claims the following subset:

| Rule | Requirement | Implementation |
|---|---|---|
| R1 | Page/section spacing ∈ S | Tailwind 4/8/12/16/24/32 classes, M3 spacing tokens |
| R2 | Component radii ∈ R | `rounded-[24px]` sections, 32px stage/sheets, pill buttons |
| R3 | Type sizes ∈ T | 22px app/sheet titles (16px app title below 360px), 24px empty-state headline, 14px body, 12px labels |
| R4 | Coarse-pointer targets ≥ 44px | global `@media (pointer: coarse)` rule, 44px controls, expanded slider/switch hit areas |
| R5 | Compact/medium/expanded windows | 320/390 portrait compact, 568/844 landscape compact, 768 tablet, 1440 expanded; bottom sheet in portrait, side sheet otherwise |
| R6 | Safe areas and reduced motion | `env(safe-area-inset-*)`, `prefers-reduced-motion` |
| R7 | Expressive component grammar | floating toolbar + FAB, segmented buttons, sheets, large rounded surfaces |
| R8 | Per-element watermark sizing | six independent 50–250% controls + map zoom, persisted and rendered live |

Canvas watermark text is a user-authored content layer, not UI chrome. Its per-element percentages
(see §5) are excluded from `T`; the UI controls that manipulate it remain in `T`.

## 2. Automated measurement

```bash
cd web
npm run dev             # terminal 1
npm run audit:m3        # terminal 2 (writes docs/m3-audit-latest.json)
```

The script loads `docs/audit-sample.jpg`, opens the **style sheet**, and measures six viewports,
including 568×320 phone landscape and the 768px breakpoint.
It checks the base screen, the editor, and the open style sheet, including every visible
button/input/slider/switch and the font select options. It also runs a functional pass on a second page: it sets a location label, moves each element
slider to 250%, requires the rendered canvas to change, resets it to 100%, and checks that the
new value is written to persisted state.

| Viewport | Class | Watermark stage with sheet open | Sheet |
|---|---|---|---|
| 320×568 | compact | 155×116, bottom 241 < sheet top 284 | 320×284 bottom sheet |
| 390×844 | compact | 339×254, bottom 379 < sheet top 422 | 390×422 bottom sheet |
| 568×320 | compact landscape | 232×174, right 248 = sheet left 248 | 320×320 side sheet |
| 768×1024 | medium (touch) | 344×258, right 368 = sheet left 368 | 400×1024 side sheet |
| 844×390 | compact landscape (touch) | 311×233, right 430 < sheet left 524 | 320×390 side sheet |
| 1440×1000 | expanded | 916×687, right 990 < sheet left 1040 | 400×1000 side sheet |

Portrait compact sheets are up to 50dvh tall, bottom-anchored, and the stage is capped to the
space above them (`max-h-[calc(50dvh-8.5rem)]`). Coarse-pointer short-landscape layouts use a
320px side sheet with the floating toolbar and app-bar title hidden; 768px touch and desktop side
sheets reserve 400px. The stage and app-bar content shift left (the title is hidden on compact
landscape), so neither the preview nor the title is hidden by a side sheet.

Latest result: **PASS — 0 findings** (`docs/m3-audit-latest.json`).

## 3. Four audit passes

### Pass 1 — spacing and rhythm
Measured page, sheet, section, and toolbar padding/gaps at all six viewports.
Adjusted the header to 64px, the page gutter to 16px, sheet gutters to 16/24px, stage inset to
16/24px, section padding to 16px, section field grouping to 12px, and side sheets from 480px to
320–400px. Section header/body hooks
(`data-testid`) make the measurement independent of utility-class names.
Result: all measured spacing ∈ S.

### Pass 2 — type, shape, color
Normalized 10/11px labels to 12px, app/sheet titles to 22px (16px app title below 360px), empty-state headline to 24px,
section cards from 35.2px to 24px, stage/sheet to 32px, and inputs/buttons to 40–44px.
The select trigger and segmented controls are 40px on dense desktop and 44px on coarse pointers.
The sheet close button is a measured 44px pill; its full radius is normalized and checked as `∞`.
Color roles use `surface`, `surfaceContainer`, `primary`, `onSurfaceVariant`, `outlineVariant`;
no hard-coded text color is used for preset names.
Result: all measured type sizes ∈ T and radii ∈ R.

### Pass 3 — components and touch
Added a coarse-pointer rule for 44px buttons/inputs, 44px color swatches and eyedropper,
44px segmented controls, 44px sheet actions, 48–56px save FAB. Slider roots are 44px tall; slider
thumbs expand to a 46×46px hit area, switches to 62×48px, and the sheet close button is 44×44px.
The audit measures actual controls inside the open style sheet, not just the editor, and computes
the effective `::after` hit box for sliders/switches.
Result: 0 target violations at 320/390/568; desktop controls ≥ 40px.

### Pass 4 — adaptive, motion, and safety
Checked compact/medium/expanded layouts, portrait/landscape (including 568×320, 768×1024, and 844×390), safe-area insets,
bottom/side sheet selection, stage/toolbar non-overlap, and reduced-motion rules. Fixed the stage from a fixed
`68dvh` calculation to a measured flex slot, made compact style/crop sheets 50dvh, capped the
stage above the compact sheet, shifted the stage/toolbar for desktop and short-landscape side sheets,
and removed sheet/dialog backdrop blur so the live preview stays sharp while editing.
Result: no stage/toolbar overlap, no stage/sheet overlap, no horizontal overflow, no console errors.
Emulated `prefers-reduced-motion: reduce` computes transitions/animations at ≤1ms
(`reducedMotion` in the JSON). Closing the sheet restores keyboard focus to its opener in both the
bottom-sheet and compact-side cases (`sheetFocus`).

## 4. Proof sketch

**Lemma 1 (spacing).** For each measured selector in the audit, computed padding/gap values are
members of `S`. Evidence: `docs/m3-audit-latest.json`, `results[*].base.headerInner`,
`results[*].editor.stageSlot`, `results[*].sheet.titleRow`, `results[*].sheet.header`,
`results[*].sheet.body`, `results[*].sheet.cardHeader`, `results[*].sheet.cardBody`,
`results[*].editor.toolbar`.

**Lemma 2 (shape/type).** Measured radii are in `R` and measured title/select/hint sizes are in `T`.
Evidence: auditor's `empty card`, `stage`, `sheet card`, `sheet content`, and `sheet close` checks
plus app title, empty headline, sheet title, select, and hint font sizes. The previously observed 35.2px section
radius and 36px stage radius were removed (24px and 32px respectively).

**Lemma 3 (targets).** Every visible button/input/slider/switch in the base screen, editor, and open
style sheet has an effective target of at least 44px on coarse pointers and 40px on desktop.
Evidence: auditor's target list; slider thumbs and switches use measured pseudo-element hit boxes,
and all six element-percentage sliders report a 46×46px thumb target.

**Lemma 4 (adaptation).** At 320/390 the sheet is bottom-anchored, at most 50dvh tall, and the stage bottom is above the sheet top;
at 568×320 and 844×390 the 320px compact side sheet, at 768×1024 and 1440×1000 the 400px side
sheet, starts to the right of the stage and of the visible app-bar title. The stage remains above the floating toolbar whenever the toolbar is visible
(it is hidden for compact side sheets). Evidence: `results[*].sheet.content`, `results[*].sheet.stage`,
`results[*].sheet.appTitle`, `results[*].editor.toolbar`.

**Lemma 5 (controls).** Each of the six element controls exists, has range 0.5–2.5, reaches 2.5
via keyboard, changes the rendered canvas after a location label is present, resets to 100%, and
persists 250% in local storage. The map-zoom control has range 0.5–5. Evidence:
`elementControls` in `docs/m3-audit-latest.json`. The 14 Vitest tests additionally prove
composition, clamping, legacy normalization, and title/spot/location/date row mapping.

**Theorem.** Under the definitions above, the measured web UI satisfies R1–R8. ∎

## 5. Per-element size controls

| Control | Field | Range | Applies to |
|---|---|---|---|
| 文字全体 | `textScale` | 50–250% | base size for every text row |
| タイトル | `titleScale` | 50–250% | line/transport name, flight code, IATA row, first non-empty spot line |
| サブタイトル | `subtitleScale` | 50–250% | flight registration, train name/model, airport names, arrows/stations, remaining spot lines |
| 座標・地名 | `locationScale` | 50–250% | pin, location/coordinate row |
| 日付 | `dateScale` | 50–250% | date/time row |
| 地図 | `mapBoxScale` | 50–250% | map box width/height and internal stroke scale |
| 地図内部ズーム | `mapInternalZoom` | 50–500% | geographic zoom inside the map box |

The renderer resolves them once:

```text
master    = unit × clamp(textScale)
title     = master × clamp(titleScale)
subtitle  = master × clamp(subtitleScale)
location  = master × clamp(locationScale)
date      = master × clamp(dateScale)
```

The same resolved unit is used for measuring, line height, draw offsets, arrow/glyph/pin geometry,
and shadows, so changing an element scale cannot desynchronise layout from drawing. At 100% the
spot, airport, train, date and dual flight rows keep their original sizes; a registration-only
flight row now consistently follows the subtitle group. Persisted
version-2 states are merged with the new defaults; “位置・サイズをリセット” restores all six to 100%.
The Android app uses the same labels/ranges and the same composition formula for parity.

## 6. Exclusions and limitations

- Native `<input type="date">`/`time` popups and browser scrollbars are platform UI, not app tokens.
- Overlay canvas text is user-content with its own percentage controls; it intentionally does not
  use the app UI typescale.
- The automated DOM measurement targets the empty state, editor, and style sheet. Info, settings,
  and crop sheets reuse the same primitives and the global 44px coarse-pointer rule; they were
  spot-checked at 390×844 and 568×320.
- Headless Chromium reports zero safe-area insets. The app uses `safe-x` / `env()` for sheet,
  dialog, header, and toolbar edges, but notched-device inset values are not numerically emulated.
- The audit is a deterministic measurement model for this product, not an exhaustive audit of every
  M3 Expressive component in the Material spec.
