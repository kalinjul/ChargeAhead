# autoapp — Roadmap

What is built, what comes next, and which questions are still open.
Design and platform constraints live in **[ARCHITECTURE.md](ARCHITECTURE.md)**;
this file only says *what* gets built in which order, and *why not yet*.

As of: 2026-09-05

---

## 1. Milestones

| M | Content | Result |
|---|---|---|
| **M0** ✅ | Walking skeleton: Gradle/KMP scaffold, `CarAppService` with a static screen, CarPlay scene, manifest/entitlements | Android built, installed, POI service registered on the device. iOS code written, not compiled. |
| **M1** ✅ | Location, corridor, OCM connection, list sorted by distance, live updates | Android built, 109 tests green, all three iOS targets compile, Swift parses. OCM mapping verified against the real service (123 sites). Only what needs the Objective-C header or a device stays unverified. |
| **M2** ✅ | Vehicle profile, manual SoC, range, reachability rating, `CarHardwareSoCSource` | Android built and run through on the emulator: profile entered, connector filter works, arrival SoC shown. `CarHardwareSoCSource` is written but **never run with a vehicle** — that needs a head unit that actually supplies the data. |
| **M3** ✅ | BNetzA source, dedup/merge, offline tile cache | Both sources run merged, the local store survives process end (verified on-device in airplane mode). At the same location, the nearest charging stop moved from 16 km to 2.4 km, because the registry knows charging parks that OpenChargeMap is missing. |
| **M4** | Network filter, detail view, navigation hand-off, consumption learning | ready for everyday use. **Detail view, navigation hand-off, and network filter are in place and verified in the car**; consumption learning is still missing — it requires reliable SoC measurements and has no data basis with manual entry. |
| **M6** | Vehicle list as a preset, elevation profile and wind in the consumption model | estimate becomes robust instead of rough |
| **M5** | GoingElectric, Mobilithek, own routing backend | more sources, no more third-party demo server |

**To be started in parallel and immediately, because externally
blocked:** apply for the CarPlay entitlement with Apple · register the OCM
API key · request the BNetzA interface description by email.

---

## 2. The phone UI

Two decisions taken on 2026-09-05; **built on 2026-09-07** along the design
mockup in `docs/mockup/index.html` (design doc:
`docs/2026-09-07-phone-ui-design.md`). The map itself is still a placeholder
drawing — the map-SDK decision below remains open, everything else works:
map-first home, plan-a-route bottom sheet with charging stops from the shared
`TripPlanner`, "Jetzt laden" (best chargers nearby with an auto-relax filter
ladder), garage with vehicle presets and consumption slider, tariff
subscriptions with per-site price comparison (demo price table — a real
price API is open point 10), saved routes, and Google-Maps hand-off for the
whole route, a section, or a single stop. iOS has the core screens (home,
plan, charge now, trip, stop detail); garage and subscription views are
still Android-only.

**The start screen becomes a map view.** Today the phone shows the same
plain list as the car (`ChargeStopsPhoneScreen` in
`androidApp/.../phone/MainActivity.kt`) — it exists mostly to check that
location, API key, and the shared state actually work. That's the wrong
shape for the phone: on a map, corridor, route, and the charging stops
along it are grasped at a glance, and the phone is exactly the surface
where that's allowed. In the car it stays a list — see the design rule in
ARCHITECTURE.md §7: no information that needs two glances.

**Destination planning moves into a bottom sheet.** Today
`DestinationScreen` replaces the whole content, so the context is gone
while a destination is being entered. As a bottom sheet over the map, the
search sits above the map instead of replacing it: type, see the hits,
watch the route and its charging stops appear underneath.

Still open:

- **Which map — decided 2026-09-07: Google Maps Compose on Android.** The
  key exists (`googleMapsApiKey` in `local.properties` → manifest
  placeholder), and navigation hands off to Google Maps anyway. Without a
  key the app falls back to the labeled placeholder instead of Google's
  blank grey — same philosophy as the demo data. iOS still draws the
  placeholder; the Maps iOS SDK needs SPM wiring and a key in
  `Secrets.xcconfig`, and is the next iOS step.
- **What the map shows.** Charging stops as markers is obvious. Whether
  the corridor sector, the OSRM route line, and the reachability colors
  belong on it is not.
- **Price data.** The tariff comparison runs on a built-in demo table
  (`DemoTariffSource`), every quote labeled an estimate. Chargeprice vs.
  Eco-Movement is the same kind of decision as OCM was — keyed API,
  contract test, replaceable source behind the `TariffSource` port.
- **Charge-now availability.** "Only free right now" is deliberately not a
  filter yet: no connected source has live occupancy. The relax ladder's
  order (power → networks → price → distance) should later become
  user-configurable.

---

## 3. Open points

1. **Android Auto only verifiable on real hardware** — the projected UI
   doesn't run on any emulator: the preinstalled Android Auto there is a
   stub, and the Play Store reports "not compatible" for emulator
   hardware. With a real phone connected to the Desktop Head Unit, though,
   it's fully verifiable, and the DHU can be remote-controlled through its
   standard input while doing so (see docs/android-auto-testen.md).

   **Run through in the car for the first time on 2026-09-04** (Pixel 10
   Pro, Android Auto 17.4, DHU 2.0): list, detail view, and the hand-off to
   navigation work. Google Maps opened with a route to the selected
   charging station.
2. **CarPlay simulator without an entitlement** — whether the Xcode CarPlay
   simulator launches with a key entered locally into `.entitlements` but
   not approved by Apple is documented inconsistently. Needs practical
   verification once a Mac with Xcode is available. Affects the M0 schedule
   on the iOS side.
3. **Consumption model** — a constant value is enough for M2. What comes
   next, in order of expected benefit:

   - **Elevation profile.** The biggest systematic error on routes with
     grade. Over the Alps, the same car uses a multiple of the flat-ground
     consumption, and regeneration downhill gives some of it back — an
     estimate with constant consumption isn't just off there, it's off by
     an order of magnitude. Needs elevation data along the route, i.e.
     another data source.
   - **Headwind.** At highway speed, drag is by far the largest factor;
     30 km/h of headwind acts like driving noticeably faster. Needs a
     weather source and the direction of travel — the latter is already
     available via the heading from the corridor.
   - **Decay of the typed-in charge level.** A manually set value ages with
     every kilometer driven. Aging it forward over the distance driven
     would be the same calculation that already produces the arrival SoC —
     until then, the UI has to make visible how old the value is.
   - **Temperature and speed**, plus, from M4, the rolling average from
     actual SoC drop over distance.

   All of this hinges on the same point: the `ConsumptionModel` type has to
   know the route, not just its length. As long as `VehicleProfile` carries
   a scalar consumption value, none of this is possible.

4. **Vehicle list.** Deliberately not built in M2: the driver enters usable
   capacity and consumption themselves. That's honest — the numbers are in
   the spec sheet — but inconvenient, because few people know their *usable*
   capacity. A bundled list of common models as a preset, still
   overridable, is the next convenience step. The price is maintenance:
   every new model and every facelift is missing or wrong, and a wrong
   preset value is worse than none, because nobody checks it.
5. **Server: yes, planned.** Decided when route planning was moved up. A
   dedicated routing server (OSRM or Valhalla) will later also handle the
   API key proxy and, if needed, BNetzA preprocessing.

   **Until then the app runs against `router.project-osrm.org`** — the
   OSRM project's public demo server. That's explicitly not meant for
   production use and comes with no guarantee. It sits behind the
   `RouteEngine` port and is swappable for a dedicated instance without
   changing anything else. **It must be replaced before any release.**

6. **Destination search via Nominatim.** Chosen because it's the easiest to
   replace with a dedicated instance — its interface is identical to the
   public one, so the switch is a one-line base URL change. The public
   instance requires an identifiable user agent and at most one request per
   second; honored, because the search only runs on a destination entry.
   OpenStreetMap's data (ODbL) must be credited — the same applies to the
   routes from OSRM.

7. **Charging-network operator logos.** Making Ionity, EnBW, Aral pulse and
   the rest recognizable by their mark would be a real win in the car: a
   logo is grasped at a glance, a company name isn't. Three things need
   clarifying first, and none of them is technical:

   - **Trademark law.** Operator logos are protected. Shipping them needs
     permission or at least a solid assessment; that's not a question the
     code can decide.
   - **Mapping.** The sources spell the same operator differently: "IONITY
     GmbH", "Ionity", "E.ON Drive GmbH" and "E.ON Drive Infrastructure
     GmbH", "EnBW (D)", "Mer Germany GmbH". It needs a normalization table —
     with the same maintenance problem as the vehicle list, and a
     mis-mapped logo is worse than none.
   - **Delivery.** Android Auto doesn't load arbitrary image URLs; the marks
     would have to ship as vector resources in the app. That caps the count
     and turns every addition into an app update.

8. **Buffer is straight-line, not detour.** A charging station 1 km off the
   highway can cost a 12 km detour via the next-but-one exit, possibly on
   the wrong side. Real detour costs would need a second route computation
   per candidate. The buffer is the workable approximation; whether it's
   good enough is for practice to decide.
9. **Play Store approval** — POI apps go through a separate review against
   the Car App Quality Guidelines. Plan for this before M4.
