# autoapp

Charging-stop assistant for **Android Auto** and **Apple CarPlay**. Shows the
next reachable charging stations ahead while driving — sorted by distance,
filtered by vehicle and charging network, rated against the remaining range.

Shared logic in Kotlin Multiplatform, car UI native twice.

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — design, platform constraints, data sources
- **[ROADMAP.md](ROADMAP.md)** — milestones, planned UI, open points
- **[AGENTS.md](AGENTS.md)** — conventions and the shared contract
- **[docs/android-auto-testen.md](docs/android-auto-testen.md)** — seeing the app in the Desktop Head Unit
- **[iosApp/README.md](iosApp/README.md)** — what to do on a Mac

## Status: M1 — corridor and real data

The app determines the location, spans a ±35° sector in the direction of
travel, queries OpenChargeMap for the enclosing rectangle, and shows the
results sorted by distance. Recomputed after 2 km, after 60 s, or
immediately on a heading change over 45°; queried only when the corridor
leaves the most recently fetched area.

Not yet in place: vehicle profile and charge level. Reachability therefore
stays `UNKNOWN` throughout — guessing it would be worse than leaving it
open. That's coming in M2.

The OpenChargeMap mapping has been verified against the real service (123
sites along the A9). Repeatable with:

```bash
OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OpenChargeMapLiveContractTest'
```

What **can't** be checked on this development machine (Linux): anything
needing the generated Objective-C header or a device. The Swift code parses
(`tools/check-swift.sh`) but isn't type-checked — see
[iosApp/README.md](iosApp/README.md).

## Building

```bash
./gradlew :androidApp:assembleDebug
```

```bash
./gradlew :shared:jvmTest
```

```bash
./gradlew :androidApp:installDebug
```

Requires JDK 17 and an Android SDK with Platform 36 and Build-Tools 36. The
path to the SDK lives in `local.properties` (not in the repository).

## OpenChargeMap key

Without a key, OpenChargeMap responds with HTTP 403. The app doesn't abort
because of this — it shows labeled **demo data** instead: fabricated
charging parks around the current location, so the UI can be checked even
without a key.

A free key is available after registering with OpenChargeMap. It does not
belong in the repository:

- **Android** — in `local.properties`:
  ```properties
  openChargeMapApiKey=YOUR_KEY
  ```
- **iOS** — in `iosApp/Secrets.xcconfig` (not checked in):
  ```
  OPEN_CHARGE_MAP_API_KEY = YOUR_KEY
  ```
