# Send whole route to Maps — without touching the phone

Goal: the driver taps "Ganze Route an Maps senden" in the car and ends up in
Google Maps navigation with all stops — no phone interaction. Verify whether
that is possible, and if not, get as close as the platform allows.

## Where we left off (2026-09-08)

- Single stop works fully in-car: `startCarApp(ACTION_NAVIGATE, geo:…)`.
- Whole route: `geo:`/`ACTION_NAVIGATE` carries no waypoints, so
  `RouteScreen.sendRouteToMaps` opens the Maps directions URL
  (`MapsHandoff.directionsUrl`, up to 9 waypoints) **on the phone** via
  `applicationContext.startActivity` — the driver must press start there.
- Careful: `carContext.startActivity` targets the car's virtual display and
  dies with `SecurityException: … launchDisplayId=…`. Already fixed; don't
  reintroduce it.
- Still unconfirmed: whether the phone launch even survives Android's
  background-activity rules on every Android version, or only worked because
  the phone screen was on during testing. Verify first (logcat:
  "Background activity start … blocked").

## To investigate

- `google.navigation:` scheme via `startCarApp` — officially no waypoints;
  check whether current Maps accepts any multi-stop extras anyway.
- Leg-by-leg chaining as the in-car fallback: send stop 1 via `geo:`, and on
  return/arrival offer stop 2 ("nächster Ladestopp") — MapsHandoff's docs
  already promise this pattern, nobody implemented it.
- If the phone launch is BAL-blocked: notification with the directions URL as
  the reliable path (needs POST_NOTIFICATIONS on 13+ — request like CAR_FUEL,
  only where it's needed).

## Done when

Either the route lands in Maps from the car alone, or the README-honest
version: in-car leg-by-leg + one documented phone tap for the full preview.
