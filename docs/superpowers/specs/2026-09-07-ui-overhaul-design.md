# UI overhaul — mockup fidelity on native machinery

2026-09-07 · replaces the sketch in `plans/UI.md`

## Goal

Make the phone app look like `docs/mockup/index.html` — the Google-Maps-adjacent
design language it was designed with — while keeping native Compose/Material 3
machinery underneath. The current UI uses stock M3 styling with three color
overrides; the mockup's language (bordered white cards, bold type, section
labels, colored network dots, expandable sheets) never made it into the app.
That is why the plan/charge-now sheets read "unfinished" and the drawer "awful".

## Decisions (made with Raphael)

- **Mockup look, native machinery.** M3 components stay for behavior
  (ModalBottomSheet, ModalNavigationDrawer, Slider, segmented buttons); their
  styling is fully replaced by the mockup's design language. Where a mockup
  control fights the platform (vertical sliders), the native form wins.
- **Scope: every phone screen.** Android Auto screens are system-templated and
  out of scope.
- **Font: system sans** (`FontFamily.SansSerif`, = Roboto on stock Android).
  No bundled font files. The type *scale* is reworked to the mockup's voice.
- **Approach: design-system first.** One foundation commit (theme +
  components), then screens on top of it.
- **Light-only** stays — the mockup's recorded decision ("no dark mode, no
  neon").
- **No mockup fiction.** Controls and data the app cannot back are omitted:
  no live-availability bar, no "choose a different charger" button, no
  occupancy counts, no amenity chips, no From/To swap (From is always the
  current location).

## Foundation

New packages `androidApp/…/phone/theme/` and `phone/components/`.
`MainActivity.kt` (800 lines) is split: theme → `theme/Theme.kt`, drawer →
own file, home screen → own file. Navigation keeps the enum-page pattern.

### Colors (`ChargeAheadTheme`)

| Mockup CSS var | Value | M3 slot |
|---|---|---|
| `--blue` | `#1A73E8` | primary |
| `--blue-deep` | `#174EA6` | onPrimaryContainer |
| chip/selected bg | `#E8F0FE` | primaryContainer |
| `--green` | `#188038` | tertiary |
| best-tag bg | `#E6F4EA` | tertiaryContainer |
| `--red` | `#D93025` | error |
| `--bg` | `#F5F7FA` | background |
| `--surface` | `#FFFFFF` | surface |
| `--soft` | `#F1F3F4` | surfaceVariant |
| `--ink` | `#202124` | onSurface |
| `--dim` | `#5F6368` | onSurfaceVariant |
| `--line` | `#DADCE0` | outlineVariant |

Colors without an honest M3 slot go into a `ChargeAheadColors` object:
`--faint #80868B`, traffic-badge amber (`#F9AB00` / bg `#FEF7E0` / text
`#B06000`, reserved for when traffic data exists).

### Typography & shapes

System sans across all roles (already pinned; kept). Reworked scale:

- Titles/headlines: 700–800 weight, −0.02em tracking (mockup h2/h3/`.big`).
- Section label style: 11sp, 700, uppercase, +0.09em tracking
  (mockup `.sectionlabel`).
- Body 13–15sp; fineprint 12sp dim.
- Tabular numerals: a `tnum` font-feature text-style helper applied to every
  price, time, distance, and percentage (mockup `.num`).

Shapes: 14dp cards (`--radius`), 12dp buttons, fully-round pills/chips.

### Components (`phone/components/`)

Each translates one mockup CSS class:

| Composable | Mockup class | Look |
|---|---|---|
| `AppCard` | `.card` | white, 1dp `#DADCE0` border, 14dp radius, soft shadow |
| `SectionLabel` | `.sectionlabel` | uppercase micro-header |
| `RankBadge(n, color)` | `.no` / `.rank` | 24dp colored circle, white 800 number |
| `NetworkDot(color)` | `.name i` | 8–9dp dot, `operatorColor()` |
| `PriceText` | `.eur` / `.stopprice` | green 800 price + dim `/kWh` suffix |
| `TickRow` | `.netrow` | list row, trailing check-circle (blue when on; red ✕ delete / blue ＋ add variants) |
| `SearchField` | `.searchwrap` | bordered rounded input, magnifier icon |
| `AppChip` | `.chip` | soft-gray round chip with icon |
| `GoButton` | `.gobtn` / `.sendone` | 36dp rounded-square light-blue icon button |
| `KeyValueGrid` | `.kv` | 2-column bordered grid, uppercase keys |
| `ExpandableSheet` | `.sheet` (+`.full`) | ModalBottomSheet: drag handle, half-open default, drags to fullscreen, `fullOnly` content slot visible only expanded |
| `PrefRow` | `.prefrow` | icon + label + dim sublabel + `›` chevron |

Top bars app-wide: `CenterAlignedTopAppBar`, 15sp/700 centered title with
optional dim 11sp sub-line, 38dp soft-gray rounded-square icon buttons
(mockup `.topbar` / `.iconbtn`).

## Sheets

All three use `ExpandableSheet` — this fixes "won't open to the fullscreen".

**Plan** (`PlanSheetContent`): title; route card with From row (blue dot,
"Your location") / hairline / To row (red square, chosen destination — tap to
search, results and recents render as rows below in the same language); car
chip with inline editable SoC (chip + small numeric field replaces the
floating OutlinedTextField); blue full-width CTA **"Plan route"** with route
icon — search results *select* into the To row, the CTA plans. Expanded:
"Recent" section with recent destinations as cards.

**Charge now** (`ChargeNowSheetContent`): title; subtitle (normal, or red
relax notice — existing logic); candidates as `AppCard` rows: `RankBadge`,
`NetworkDot` + name, tabular meta (km · kW), `PriceText`, `GoButton`.
Expanded: **"More nearby"** — needs the shared change below.

**Routes** (`RoutesSheetContent`): "Your routes"; Saved and Recent
`SectionLabel`s; rows as `AppCard`s — saved: name, meta, pencil `GoButton`
(rename dialog stays) + delete; recent: name, heart-square `GoButton` to
favorite. Empty state: quiet hint card.

### Shared change

`ChargeNowResult` gains `more: List<ChargeNowCandidate>` — every candidate
that passed the DC floor but is not in `candidates`, sorted by distance.
`ChargeNowRanker` fills it; `ChargeNowRankerTest` (jvmTest) covers it. Only
additive; the car app ignores it.

## Drawer

`ModalNavigationDrawer` stays (edge-swipe stays disabled). Content rebuilt:

- Header: bolt icon + "ChargeAhead" wordmark, hairline bottom border.
- Preferences: `PrefRow`s Car (vehicle summary sublabel) and Subscriptions
  ("n active").
- Filters: `PrefRow` Networks ("12 of 14 active" summary).
- Minimum power: restyled segmented control (soft track, white selected
  segment, blue text), steps 50/150/300 kW.
- Max price / max distance: **horizontal** sliders (deliberate deviation from
  the mockup's vertical ones — rotation hacks fight the platform), stacked,
  each with `SectionLabel` + right-aligned tabular value.
- Filter badge: blue dot on the home burger and the trip screen's filter icon
  whenever `ChargeFilters` or network prefs differ from defaults.
- Kept app extras, restyled: map-label segmented control, Debug section
  (car-data screen), honest availability fineprint (current wording — the
  mockup's "only free chargers" line is fiction without an occupancy source).

## Screens

**Home**: bottom controls become mockup pills (fully round, float shadow):
blue "Plan" (route icon), white "Charge now" (green bolt), white heart circle.
Burger: white circular FAB + filter badge. White→transparent top scrim under
the status bar. Permission prompt / demo notice / zoom hint restyled to
fineprint/chip.

**Trip plan**: title "→ {destination}" with dim sub ("departs now · n stops");
filter icon (with badge) top-right opens the drawer. Map unchanged. Summary
bar: big tabular km + two dim meta lines, surface bg, hairline border, no
traffic badge (no data). List: terminus rows (green dot / red square, dim
right-aligned "dep now · 90 %" / "arr 15:46 · 24 %"), stop `AppCard`s with
operator-colored `RankBadge`, `PriceText`, meta line, and a per-stop
`GoButton` (↗) sending that one stop to Maps (new, from mockup). Actions row
at list end: blue "Send to Maps" (pin icon), ghost "Select section", heart
square (fills red when saved). Selection mode: dashed blue banner with the
two-step prompt, blue outline on picked rows, per-stop send hidden, send label
"Send {A} → {B}". Auto re-plan on filter change stays out of scope.

**Stop detail**: header (NetworkDot + uppercase network, 22sp/800 name, dim
address line — `ChargeSite.address` is nullable, the line simply drops when
absent); `KeyValueGrid`
(Connectors / Planned charge "28 min · 18 → 82 %" / Arrival / Energy); price
list `AppCard` with hairline rows, kind sublabels, green **CHEAPEST** tag on
best; estimate fineprint; one blue "Navigate here in Google Maps" CTA.

**Networks**: `SearchField` + one card of `NetworkDot`+name+check `TickRow`s;
active count as top-bar sub-line.

**Subscriptions**: same pattern; sublabel limited to what tariff data honestly
supports (kind/network; monthly price only if the field exists).

**Garage**: "Your cars" card with radio-style `TickRow`s + "Add a car…" (blue)
/ "Delete a car…" (red, existing delete-mode) rows; Specs `KeyValueGrid`
(battery, DC peak, connector, est. range via `RangeCalculator`); consumption
slider card with "spec says X — adjust to how you actually drive" fineprint;
SoC slider card stays. **Add car becomes its own page**: `SearchField` +
catalog `TickRow`s with spec sublabels and ＋ tick.

**Vehicle edit (advanced)** and **car-data debug**: light restyle only (cards
+ section labels). **Corridor stop dialog**: reuse detail components (kv grid,
price rows).

Cleanup: `phone/DestinationScreen.kt` is defined but never called — delete.

## Verification

- `ChargeNowRanker.more`: jvmTest coverage alongside existing ranker tests.
- UI: run via the `app-laufen-lassen` skill; walk the mockup's five
  demo-script flows (charge now, plan, send-to-Maps variants, drawer/settings,
  saved routes) and compare screenshots against the mockup screen by screen.
- All new user-facing strings via `strings.xml`, following its existing
  conventions.
