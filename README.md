# ChargeAhead

Charging-stop assistant for **Android Auto** and **Apple CarPlay**. Shows the
next reachable charging stations ahead while driving — sorted by distance,
filtered by vehicle and charging network, rated against the remaining range.

Shared logic in Kotlin Multiplatform, car UI native twice.

- **[ARCHITECTURE.md](ARCHITECTURE.md)** — design, platform constraints, data sources
- **[ROADMAP.md](ROADMAP.md)** — milestones, planned UI, open points
- **[AGENTS.md](AGENTS.md)** — conventions and the shared contract
- **[docs/android-auto-testen.md](docs/android-auto-testen.md)** — seeing the app in the Desktop Head Unit
- **[iosApp/README.md](iosApp/README.md)** — what to do on a Mac
- **[docs/ci-cd.md](docs/ci-cd.md)** — CI, releasing to the Play Store, secrets

## Status: M1 — corridor and real data

The app determines the location, spans a ±35° sector in the direction of
travel, queries the ChargeAhead backend for the enclosing area, and shows the
results sorted by distance. Recomputed after 2 km, after 60 s, or
immediately on a heading change over 45°; queried only when the corridor
leaves the most recently fetched area.

Not yet in place: vehicle profile and charge level. Reachability therefore
stays `UNKNOWN` throughout — guessing it would be worse than leaving it
open. That's coming in M2.

The mapping of the backend's answers can be checked against the real
backend with:

```bash
CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendChargeSiteLiveContractTest'
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

## Backend

The app gets its charging sites, networks, routes and destination search
from the ChargeAhead backend and does not run without it: it stops at
startup when address or token are missing. Neither belongs in the
repository:

- **Android** — in `local.properties`:
  ```properties
  chargeAheadBaseUrl=https://YOUR_HOST
  chargeAheadToken=YOUR_TOKEN
  ```
- **iOS** — in `iosApp/Secrets.xcconfig` (not checked in), see
  [iosApp/README.md](iosApp/README.md).
