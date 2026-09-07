# ChargeAhead — EV charging route planner

*2026-09-07 · the repo that will grow into the Kotlin Multiplatform app, directory `chargeahead`.*

## Concept

Europe-wide EV route planner. You tell it your car and where you're going; it plans the
charging stops, shows what each stop actually costs across networks and tariffs, and hands
navigation off to Google Maps. The app plans — Google Maps drives.

## Features (MVP)

### 1. Route planning
- Origin / destination / optional waypoints with Google Places autocomplete.
- Vehicle profile: battery capacity, consumption, connector type, starting charge.
  Without this, charging-stop planning is guesswork — it's a hard requirement, not a setting.
  Consumption is a per-car slider seeded with the manufacturer spec — every driver is
  different, and the adjusted value is what the stop planner and range estimate use.
- Google Routes API, traffic-aware (`departure_time=now`), so ETAs and stop timing reflect
  real congestion. Traffic shown per leg on the route. No departure-time preferences in
  the MVP — you leave now, traffic is always live.
- Stop planner picks fast chargers near the route from remaining range, charger power,
  detour cost, and the active filters (see 3).

### 2. Google Maps handoff
Navigation never happens in-app. Three ways to send, all from the route view:

- **Whole route** — dedicated button, always visible. Sends origin + all charging stops +
  destination as one Google Maps directions link.
- **Route section** — a select mode: mark a section of the route by tapping its two
  endpoints (any of origin, stops, destination); the legs in between highlight; a
  contextual button sends just that section.
- **Single stop** — each stop card has a send action that ships only that charger to Maps.

Constraint: Google Maps URLs cap waypoints (~9 on mobile). Long routes are sent
leg-by-leg — the app tracks overall progress, Maps handles the current leg.

### 3. Networks & filtering
- European CPOs: Ionity, Fastned, Tesla Supercharger, EnBW, Allego, Shell Recharge,
  TotalEnergies, Aral pulse, Vattenfall InCharge, Mer, EWE Go, GreenWay, Circle K, … —
  too many for inline chips, so networks get their own menu (toggle list) in the sidebar.
- Filters: network/company, minimum power, max price. Changing filters re-plans stops
  and the Charge-now ranking.
- Not filters, but principles: only chargers that are free right now are ever suggested
  (an occupied charger is not a result), and the planner is CCS-only — nobody plans a
  long-distance route around AC posts. Connector choice lives in the vehicle profile.

### 4. Pricing
- Per-station €/kWh, including the roaming reality: the same plug costs differently
  ad-hoc vs. via EnBW mobility+ vs. via Ionity Passport. The user registers their
  tariffs; the app compares and marks the cheapest.
- Data source candidates: Chargeprice API (built exactly for this), Eco-Movement,
  Open Charge Map (free, but prices are sparse). Decision pending API/pricing check.

### 5. Charge now
- One tap on the home screen: the three best chargers around the current position,
  ranked by price and constrained by the user's filters (networks, min power, max price,
  max distance). Each with a direct navigate-in-Google-Maps action. No route needed —
  for the "I just need a plug" case.
- If the filters starve the list (fewer than two hits), they are relaxed automatically
  in a fixed order until it recovers: min power → networks → max price → max distance.
  The sheet says which filters were relaxed. Availability is never relaxed.
- Pulling the sheet up expands it to full height; below the best three, the list
  continues by distance and scrolls endlessly (mockup shows the rest of the fake pool).

### 6. Saved & recent routes
- Heart button on the home screen opens the routes sheet: saved routes (with custom,
  renamable names) on top, the three most recent routes below as small summary cards.
- A recent route can be quick-favorited via its heart icon; tapping a route reopens it.
- A freshly planned route is saved directly from the route view: a heart button sits next
  to the send/select actions and toggles saved state, in sync with the routes sheet.

### 7. Live data
- Traffic per route leg (Routes API).
- Charger availability via OCPI status feeds from the data provider.
- Prices refreshed server-side; the app subscribes to changes (Flow).

## Later (explicitly not MVP)

Price alerts, charging history, Plug&Charge, a user-customizable priority order for the
Charge-now filter relaxing. CarPlay / Android Auto deliberately skipped — navigation
lives in Google Maps anyway.

## Architecture (the real build)

- **shared (Kotlin Multiplatform):** domain models, stop-planning algorithm, Ktor API
  clients, SQLDelight cache, coroutines/Flow for live data. All logic lives here.
- **androidApp:** Compose UI + Google Maps Compose SDK.
- **iosApp:** SwiftUI + Google Maps iOS SDK.
- **Backend:** thin proxy holding the API keys, aggregating charger/price data, caching
  aggressively (keys in a mobile binary are keys made public; also: rate limits).
- **Handoff:** platform deep links — `google.navigation:` on Android,
  `comgooglemaps://` on iOS, `https://www.google.com/maps/dir/?api=1&…` as universal
  fallback.

### Decided
- In-app map for planning, navigation delegated to Google Maps — chosen over a map-less
  list app (too abstract to trust a route) and a full in-app nav SDK (expensive, and
  duplicates what Maps already does better).
- Europe-wide market, € pricing.

### Open
- Price/availability data provider (Chargeprice vs. Eco-Movement) — needs an API access
  and pricing check before the backend design is finalized.
- Real product name.
- Backend hosting.

## UI direction

Light, "normal maps" look — Google-Maps-style colors (cream land, blue water, white
roads), white surfaces, Google blue for actions and the route line. No dark mode, no
neon. The home screen is the map itself: full-bleed, floating **Plan**, **Charge now**
and **❤️** pills at the bottom, a burger button top-left opening a sidebar where
preferences (vehicle, tariffs) and filters live; networks open as a submenu there.
Filters drive everything: the stop planner and the Charge-now ranking both re-plan when
they change. On a planned route, the send/select actions sit at the bottom of the
scrollable stop list, and stop cards stay minimal — name, price, arrival time, charge
duration; SoC, power, road position and the tariff comparison live one tap away in the
charger detail view. Car, Subscriptions and
Networks each open as their own full-screen, searchable view from the sidebar. The car
view manages a garage: add cars from a searchable catalog (specs seeded from the model),
and a delete mode that turns the selection ticks into ×. The sidebar has no Done
button — tapping next to it closes it, and closing applies the filters. Filter controls
are compact: power steps without comparison symbols, and vertical sliders side by side
for max price (ending at €1.00/kWh) and max distance. The Plan and Charge-now bottom
sheets can be pulled up to full height: expanded, Plan shows recent routes and Charge
now continues the charger list. Reference device: Pixel 10 Pro (punch-hole camera,
gesture bar) — not an iPhone.

## Mockup

`mockup/index.html` — a single self-contained file, phone frame, no dependencies, fake
data. Map-only home with floating pills and burger drawer → Charge now sheet (best 3 by
filters) → plan sheet → route overview (Amsterdam → München, traffic-colored SVG map,
all three Maps-handoff interactions working) → charger detail with tariff comparison.
Excluding Ionity in the drawer visibly re-plans both the route and the Charge-now list.
Built to be shown around, not to be kept.
