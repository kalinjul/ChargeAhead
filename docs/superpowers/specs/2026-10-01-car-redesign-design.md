# Car UI redesign on Car App API level 1

The car app is built around following the active route. It stays on API
level 1 and uses what that level offers beyond plain lists: the host-drawn
place map, grid tiles, panes, distance spans and app colours.

## Screens

**Home** (`CarHomeScreen`, `GridTemplate`). Tiles: "Ziel eingeben",
"Jetzt laden", "Ladestand" (text: the current percent, or "–"), and
"Aktive Route" with the destination as text while a trip is committed.
When a trip is committed the home pushes the route screen on top by itself,
once per commit; back from there lands on the grid.

**Route** (`RouteScreen`, `PlaceListMapTemplate`). The committed trip's
stops as rows with a numbered purple marker each; the driver's position on
the map; the destination as the anchor marker. Row text: line 1 the
straight-line distance from the current fix as a `DistanceSpan`, then
"· Ankunft 15 %" where the percent is coloured red under 10, yellow under
20; line 2 power and charge time. The first row stays "Ganze Route an Maps
senden" without a marker. Action strip: "Jetzt laden" and "Neu planen".
"Neu planen" re-plans with the car's own reading when there is one;
otherwise it opens the percent picker first, and the pick re-plans.
Loading, failure and direct-route states keep their message templates.
Tapping a stop opens the stop detail.

**Jetzt laden** (`ChargeNowScreen`, `PlaceListMapTemplate`). Candidates as
rows with a numbered marker in ranking order, distance span and power in
line 1, operator and connectors in line 2. The host's refresh (the listener
is set) re-ranks from the current fix, same as the strip's refresh today.
The relaxed-filter row stays, without a marker. Tapping opens the detail.

**Detail** (`SiteDetailScreen`, `PaneTemplate`). Title: site name. Rows:
address; distance and, for a route stop, arrival → departure percent and
charge time, else power; connector lines, live when the site has a live id
(`LiveConnectorsObserver`); operator. Image: the brand mark. Actions:
"Navigieren" (primary colour), "Zurück". Skipping a stop needs a planner
feature that does not exist and is out of scope.

**Ladestand** and **Ziel eingeben** stay as they are, except that the
picker can hand its pick to a caller.

## Colour

A `CarAppTheme` style with `carColorPrimary` #7F77DD / dark #AFA9EC and
`carColorSecondary` #26215C / dark #3B3684, declared as
`androidx.car.app.theme` on the application. Markers, the FAB, the primary
action and spans use `CarColor.PRIMARY`; availability-like values use the
host's RED/YELLOW/GREEN.

## Tests

The existing `RouteScreenTest` harness (production graph, fake planner,
fake location) covers the new route rows, markers, spans and the re-plan
decision. New tests of the same shape for the home grid, the charge-now
map list and the detail pane.
