# Maps-style home screen

Date: 2026-09-21. Phone UI only; Android Auto is untouched.

## Goal

Make the map the one screen. Destination search is always one tap away
(a persistent search bar), the plan appears as a sheet over the same map,
and the settings drawer moves to the right next to the bar. The separate
"Planen" pill, the Plan sheet and the trip page with its own map go away.

## 1. Browsing state (no trip)

Top overlay, under the status bar, 16dp inset:

- A full-width floating search bar (pill, surface color, elevation 6),
  placeholder "Wohin?" (existing `plan_search_hint`), search icon left.
  A settings icon button (46dp circle, current burger styling, `ic_settings`
  or existing tune icon) sits to the right of the bar. The
  `filtersCustomized` dot moves onto the settings icon.
- Right edge, below the settings icon, a vertical column: compass (only
  when the map is rotated, as today) then the locate button. Both move out
  of `HomeGoogleMap` into the `HomeScreen` overlay so they line up with the
  settings icon; `HomeGoogleMap` exposes bearing and a `onResetBearing`
  callback instead of drawing them.
- Bottom center, above the navigation bar: two pills in one row,
  "Jetzt laden" (battery icon, tertiary tint) and "Favoriten" (heart,
  error tint, with label). No "Planen" pill.
- Directly above the pills (8dp gap): the `map_zoom_hint` chip when
  `belowMinZoom`. `phone_status_location_unavailable` stays top-center
  under the search bar. The permission card and the planning spinner stay
  centered on the map.
- Drawer opens from the right, content unchanged. `ModalNavigationDrawer`
  is start-anchored, so wrap it in
  `CompositionLocalProvider(LocalLayoutDirection provides Rtl)` and wrap
  drawer content and the screen body back in `Ltr`.

## 2. Searching state

- Tapping the bar focuses it, raises the keyboard and shows a results panel
  under the bar over the map (surface card, max ~55% height, scrollable).
  Settings icon, compass/locate and pills stay; the zoom hint is hidden.
- Empty query: recent destinations. Non-empty (>= `MIN_QUERY_LENGTH`):
  geocoder results via the existing `ObserveDestinationSearch`, debounce
  as today. Each row: primary label, address line, and straight-line
  distance from the current fix formatted "42 km" (`< 1 km` -> "< 1 km",
  `< 10 km` -> one decimal). No fix: no distance. Recents show a clock
  icon, results a pin.
- Bar shows a clear (x) button when the query is non-empty and a
  progress spinner while searching. Existing messages `plan_search_failed`
  and `plan_no_results` render inside the panel.
- Back, tapping the map, or the x with an empty query leaves searching and
  returns to browsing.
- Picking a row plans immediately: `PlanTrip` with the stored SoC exactly
  as the removed Plan sheet's CTA did, planning spinner centered as today.
  No vehicle: snackbar `plan_vehicle_missing` plus action opening the
  garage; state stays browsing.

## 3. Trip state

- The bar becomes a destination header: route icon, destination label
  (`ChargeStopFormatter.label`), stop count subtitle (`trip_topbar_sub`),
  and an X. X drops the trip (`TripViewModel.clear`) and returns to
  browsing; the route disappears from the map. System back with the sheet
  collapsed does the same; with the sheet expanded it collapses first.
- `HomeGoogleMap` draws the route polyline, stop markers and the
  destination marker (reusing the existing `TripGoogleMap` drawing code),
  and fits the camera to the route bounds once when a trip appears.
  Charger markers, the zoom hint and the pills are hidden in this state;
  compass and locate stay.
- A `BottomSheetScaffold`-style sheet with a partially expanded peek of
  ~120dp showing the `TripSummary`. Expanded, it shows the current trip
  page content: summary with "Neu planen", start and arrival SoC chips,
  stop list, section-to-Maps picker, "An Maps senden", save toggle.
  "Neu planen" enters searching prefilled with the current destination
  while the trip stays on the map until a new plan replaces it or X is
  tapped.
- Stops in the list are not tappable in this iteration; the `StopDetail`
  page and the `Trip` page are removed from the phone back stack.
  Charger tap while browsing keeps the current charger detail sheet.
- Loading a route from Favoriten lands in this same trip state.

## 4. State ownership

Shared (`org.julakali.chargeahead.shared.ui`):

- New `SearchViewModel` replacing `PlanSheetViewModel`: `query`,
  `results: List<SearchRow>?`, `searching`, `recent`, `hasVehicle`.
  `SearchRow(destination, label, address, distanceKm: Double?)`. Owns
  `onQueryChanged`, `onRowChosen(row)` (plans via the existing
  `PlanTrip` path with the stored SoC), `onOpened(prefill: Destination?)`,
  `onClosed`. Distance computed from `ChargeStopsFeature.currentFix` with
  `distanceKmTo`.
- `HomeViewModel` unchanged except: `chargers` are irrelevant during a
  trip; the composable hides them, no shared change.
- `TripViewModel` gains `clear()`. Its `Planned` state already carries
  what the sheet needs.
- The shell mode (`Browsing`/`Searching`/`Trip`) is derived in the
  Android `PhoneApp`: `Trip` when `TripUiState.Planned`, `Searching` when
  the bar has focus, else `Browsing`. Sheet expansion is Compose UI state.

Android (`android.phone`):

- `HomeScreen` gets the new overlay; `SearchBar`, `SearchResultsPanel`,
  `DestinationHeader`, `TripSheet` composables.
- Delete `PlanSheet.kt`, `TripPlanScreen.kt`'s page wrapper (keep and move
  the list/summary pieces into `TripSheet`), `StopDetailScreen.kt` usage,
  `Sheet.PLAN`.
- `MainActivity` back stack keeps root plus drawer targets only.

## 5. Testing

- Shared unit tests: `SearchViewModelTest` covering recents-then-results,
  distance present/absent by fix, no-vehicle path does not plan,
  prefill on reopen. `TripViewModelTest` for `clear()`.
- No Android UI tests. Raphael verifies on device.

## Out of scope

Stop detail during a trip, drawer content changes, iOS, Android Auto.
