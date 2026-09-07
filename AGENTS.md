# AGENTS.md — Working rules for this repository

Applies to all agents and humans writing code here. Architecture and
rationale live in [ARCHITECTURE.md](ARCHITECTURE.md), milestones and open
points in [ROADMAP.md](ROADMAP.md) — this document only says *how* work
happens here.

## What the project is

Charging-stop assistant for **Android Auto** and **Apple CarPlay**. Shows the
next reachable charging stations ahead while driving. Shared logic in Kotlin
Multiplatform, car UI native twice.

Current state: **search along the route.** The app determines the
location, spans a sector in the direction of travel, queries OpenChargeMap,
and classifies the results against the remaining range. The driver enters
the vehicle profile and charge level themselves — deliberately without a
vehicle list (ROADMAP.md, open point 4). Without both, reachability
stays `UNKNOWN` and the list shows only distances; that's a valid state, not
an error.

## Layout

```
shared/      Kotlin Multiplatform — domain, formatting, later data layer
androidApp/  Android app: CarAppService (Android Auto) + Compose phone UI
iosApp/      Swift: CarPlay scene + SwiftUI phone UI
tools/       Helper scripts (Swift syntax check)
```

Dependency direction: `androidApp`/`iosApp` → `shared`. Never the reverse.
`shared` knows neither Android nor iOS frameworks in `commonMain`.

## Language

- **Identifiers, types, filenames: English.** `ChargeSite`, not `Ladesaeule`.
- **Comments and documentation: English.** Write a comment only when it's
  truly needed — not a blind comment on every function. A comment explains
  *why*, not *what*: a hidden constraint, a subtle invariant, a workaround, a
  reason that would surprise a reader. No comment that just retells the line
  below it.
- **User-visible text: German**, and exclusively from resources
  (`strings.xml`, `Localizable.strings`) — never as a literal in code. This
  is a deliberate product decision for the German market; it does not extend
  to code, comments, or documentation.
- **No secrets in the code or in the repository** — no API keys, tokens, or
  credentials, not even as an example value that looks real. See "API keys"
  below for where they actually go.

## Build

```bash
./gradlew :androidApp:assembleDebug     # build Android
./gradlew :shared:jvmTest               # tests for the shared logic (JVM target)
./gradlew :androidApp:installDebug      # onto a device/emulator
./gradlew :shared:compileKotlinIosSimulatorArm64   # compile iosMain (works on Linux!)
tools/check-swift.sh                    # Swift syntax check (see below)
```

Tests that need to run coroutines live in `shared/src/jvmTest` and use
`runBlocking`. That avoids the `kotlinx-coroutines-test` dependency;
`runBlocking` doesn't exist in `commonTest`.

Versions live exclusively in `gradle/libs.versions.toml`. **Never write a
version number directly into a `build.gradle.kts`.** Anyone who needs a new
dependency adds it to the catalog.

The `build.gradle.kts` files, `settings.gradle.kts`, and the version catalog
are maintained centrally. Agents contributing source code don't change them
without being asked — if a dependency is missing, report it instead of
adding it yourself.

## The shared contract (as of M1)

`shared` provides exactly the following. Android and iOS build on top of it
and duplicate none of it.

### Model

```kotlin
package de.autoapp.shared.domain

data class LatLon(val lat: Double, val lon: Double)

enum class ConnectorType { CCS2, TYPE2, CHADEMO, TESLA_NACS, SCHUKO, UNKNOWN }

// UNKNOWN applies as long as no vehicle profile exists — so throughout M1.
enum class Reachability  { REACHABLE, MARGINAL, UNREACHABLE, UNKNOWN }

// count is nullable: OCM omits the unit count in about half the cases.
data class Connector(val type: ConnectorType, val maxPowerKw: Double, val count: Int?)

data class ChargeSite(
    val id: String,                  // source-qualified: "ocm:12345", "demo:…"
    val name: String,
    val operator: String?,
    val position: LatLon,
    val connectors: List<Connector>,
)

data class ChargeStop(
    val site: ChargeSite,
    val distanceKm: Double,          // straight-line distance x ROUTE_DETOUR_FACTOR
    val reachability: Reachability,
    val socOnArrivalPercent: Double?,
    // The connector shown on the first line: the strongest one THIS vehicle
    // can use. Chosen by the planner, not the formatter — it depends on the
    // profile and is thus a decision, not formatting.
    val primaryConnector: Connector? = null,
)

// From M2. Throws in the constructor for capacity or consumption <= 0 —
// otherwise the range formula divides by zero.
data class VehicleProfile(
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val acceptedConnectors: Set<ConnectorType>,   // empty = no filtering
    val dcPeakPowerKw: Double? = null,            // null = unknown; charge-time estimates use site power alone
)

enum class SoCSourceKind { MANUAL, CAR_HARDWARE, OEM_CLOUD }

data class EnergyState(val socPercent: Double, val source: SoCSourceKind, val observedAtMillis: Long)

data class Fix(
    val position: LatLon,
    val bearingDeg: Double?,         // null when stationary — not guessed
    val speedMps: Double?,
    val timestampMillis: Long,
)

data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)

sealed interface SearchArea { val origin: LatLon; val radiusKm: Double; val boundingBox: BoundingBox }
// SectorArea  = fan in the direction of travel, when no destination is set.
// PolylineArea = strip along the route, once one is set.

data class Destination(val name: String, val position: LatLon)
data class Route(val points: List<LatLon>, val distanceKm: Double, val durationMinutes: Double)
data class SectorArea(...) : SearchArea   // halfAngleDeg = 180.0 is the full circle
```

### Ports

They live in `domain` and know no one; every implementation lives outside.

```kotlin
interface LocationSource   { val updates: Flow<Fix> }
// query() gets the whole area, not just its rectangle: sources with a
// result cap must be able to query radially, or exactly the nearest
// charging stations go missing. See OpenChargeMapSource.query.
interface ChargeSiteSource { val id: String; suspend fun query(area: SearchArea): List<ChargeSite> }
// Implemented as TiledSiteRepository (SQLDelight). Always reads from the
// database; if refilling fails, the existing stock is still returned. Only
// if that is also empty is the error thrown.
interface SiteRepository   { suspend fun sitesIn(area: SearchArea): List<ChargeSite>; suspend fun invalidate() }
interface RouteProvider    { fun searchArea(fix: Fix, rangeKm: Double): SearchArea }
interface RouteEngine      { suspend fun route(from: LatLon, to: LatLon): Route? }
interface Geocoder         { suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> }
fun interface TimeProvider { fun nowMillis(): Long }

// From M2. null in the stream means "this source currently knows nothing" —
// for CAR_HARDWARE that's the normal case, not the exception.
interface SoCSource     { val kind: SoCSourceKind; val energy: Flow<EnergyState?> }
interface SettingsStore {
    val vehicle: Flow<VehicleProfile?>
    val vehicles: Flow<List<VehicleProfile>>      // the garage; setVehicle selects AND adds
    val manualSocPercent: Flow<Double?>
    val chargeFilters: Flow<ChargeFilters>        // phone flows: min power, max price, max distance
    val activeTariffIds: Flow<Set<String>>        // the driver's tariffs, see TariffCatalog
    val savedRoutes: Flow<List<SavedRoute>>
    suspend fun setVehicle(profile: VehicleProfile?)
    suspend fun removeVehicle(displayName: String)
    suspend fun setManualSocPercent(socPercent: Double?)
    suspend fun setChargeFilters(filters: ChargeFilters)
    suspend fun setActiveTariffIds(ids: Set<String>)
    suspend fun saveRoute(route: SavedRoute); suspend fun renameSavedRoute(id: String, name: String)
    suspend fun removeSavedRoute(id: String)
}

// The phone's planning flows, reachable as ChargeStopsFeature.planning.
// Swift goes through PlanningBridge (iosMain) — same reasoning as the watcher.
class PlanningFeature {
    suspend fun planTrip(from, destination, socOverridePercent = null): TripPlanResult
    suspend fun chargeNow(position): ChargeNowResult   // best 3, relax ladder: power → networks → price → distance
    suspend fun quote(site): PriceQuote                // always isEstimate until a real price API exists
}
```

**The route is computed once per destination, not once per location
update.** `PolylineArea.aheadOf()` trims it at the front as the drive
progresses — the route itself doesn't change during the drive, only the
section still ahead does. `RoutedRouteProvider` falls back to the corridor
when the driver leaves the route or reaches the destination; without that
fallback the list would sit empty at the end of every drive.

**One `SettingsStore` instance per process.** The phone UI writes into it,
the feature reads the same flows; two instances over the same storage would
keep changes from each other. In Android Auto, the phone and car UI run in
the same process — see `ChargeStopsFeatureProvider.settingsStore`.

### State and assembly

```kotlin
package de.autoapp.shared

// Single source of state for both car UIs.
class ChargeStopsFeature(locationSource, repository, ...) {
    val state: StateFlow<ChargeStopsState>
    val stops: StateFlow<List<ChargeStop>>   // shorthand for state.stops
    val currentState: ChargeStopsState       // snapshot for Swift without SKIE
    fun start(); fun refresh(); fun close()
}

// List AND status. Flat instead of sealed, so the type crosses to Swift
// losslessly.
data class ChargeStopsState(
    val stops: List<ChargeStop>,
    val phase: Phase,          // WAITING_FOR_LOCATION | LOADING | READY | FAILED
    val failure: FailureReason?,   // LOCATION_UNAVAILABLE | SITES_UNAVAILABLE
    val isDemo: Boolean,
)

// Decides once for both platforms what happens without an API key.
object ChargeStopsFeatureFactory {
    fun create(locationSource: LocationSource, openChargeMapKey: String?, ...): ChargeStopsFeature
}

// So Android and iOS are guaranteed to show the same lines.
object ChargeStopFormatter {
    fun primaryLine(stop: ChargeStop): String    // "12 km · CCS 150 kW"
    fun secondaryLine(stop: ChargeStop): String  // "Arrival ~34%" | "6 charging points"
}

expect fun platformName(): String
expect fun currentTimeMillis(): Long
```

The iOS framework is called **`Shared`** (`import Shared`). Swift goes
through `IosEntryPointsKt.createChargeStopsFeature(openChargeMapKey:)` and
`ChargeStopsWatcher` — both in `iosMain`, because Kotlin's default arguments
don't reach the Objective-C header and a `StateFlow` isn't subscribable from
Swift without SKIE.

### Verifying data sources

Each source's mapping is checked twice, and both are necessary:

- `OpenChargeMapSourceTest` (always runs) checks against **fabricated**
  responses. Fast and network-free, but doesn't notice when the source
  changes.
- `OpenChargeMapLiveContractTest` checks against the **real** service. Runs
  only on explicit request, because a test that goes red on a dead spot says
  nothing about the code:

  ```bash
  OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OpenChargeMapLiveContractTest'
  ```

Whoever changes a source's mapping runs both.

### API keys

The OpenChargeMap key does **not** go into the repository.

- Android: `openChargeMapApiKey` in `local.properties` -> `BuildConfig`
- iOS: build setting `OPEN_CHARGE_MAP_API_KEY` in a local
  `iosApp/Secrets.xcconfig` -> `Info.plist`

If it's missing, `DemoSiteSource` stands in for the real source and
`ChargeStopsState.isDemo` is set. **Both UIs must make that visible** —
passing off invented charging stations as real would, in an app for the car,
be not just sloppy but dangerous.

## Rules for the car UI

Both platforms only translate — they don't compute and don't format
themselves. Every number shown in the car comes from `ChargeStopFormatter`.

**Android Auto**

- The category is `androidx.car.app.category.POI`. **Not** `CHARGING` or
  `PARKING` — both have been deprecated since Car App Library 1.3.
- The list's row count isn't free to choose. Always query it via
  `carContext.getCarService(ConstraintManager::class.java)
   .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)` and trim the
  list to that.
- Reachability via `CarColor.GREEN` / `YELLOW` / `RED`. Color is a
  supplement, never the sole carrier of the information — the text must name
  the state too.
- Header via `Header` and `setHeader(...)`. `setTitle`, `setHeaderAction`,
  and `setActionStrip` directly on the template have been deprecated since
  1.7.0.
- Actions at the right edge of the header must carry an icon and must not
  carry their own title (`ActionsConstraints.ACTIONS_CONSTRAINTS_MULTI_HEADER`).
  A violation only throws at runtime.
- An empty list is never the outcome. If there's nothing to show, a
  `MessageTemplate` says why — the states for that live in
  `ChargeStopsState`.

**CarPlay**

- The entitlement is `com.apple.developer.carplay-charging`. Without
  approval from Apple, no build is possible; the code is still written in
  full.
- `CPTemplateApplicationSceneDelegate`, `CPListTemplate` with `CPListItem`.

## The iOS side on this machine

Development happens on Linux. The iOS side splits here into two parts that
can be checked to **very different** degrees — conflating them means
reporting unverified work as verified.

### Kotlin (`shared/src/iosMain`) — compiles fully

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Kotlin/Native compiles the Apple targets on Linux too. This checks
`iosMain` against the **real** cinterop bindings of CoreLocation, Foundation,
and UIKit — wrong property names, wrong delegate signatures, and missing
`actual` declarations show up here, not only on a Mac.

What does **not** work on Linux is linking: `linkDebugFrameworkIos*` is
silently skipped by the Kotlin plugin (SKIPPED, not an error). That means no
`Shared.framework` and no Objective-C header are produced.

### Swift (`iosApp/`) — syntax at best

`import CarPlay`, `import UIKit`, `import SwiftUI`, and `import Shared`
can't be resolved here; an actual compile is impossible. What does work is a
**syntax check**, provided a Swift compiler is installed:

```bash
tools/check-swift.sh
```

The script runs `swiftc -parse` over every Swift file. It finds syntax
errors, **not** type errors. A green result means "parses cleanly", not
"compiles". If `swiftc` is missing, the script exits with code 127 — then
the Swift code is **also syntactically unverified**, and should be reported
as such.

Swift 6.1.3 is installed on this machine (`sudo apt install swiftlang` on
Ubuntu 26.04), so the script runs.

Because the Objective-C header doesn't get produced here, everything that
depends on its shape stays unverified: whether Kotlin `object`s show up as
`.shared`, whether `operator` becomes `operator_`, what Kotlin `enum`s are
called in Swift.

## Before reporting something as done

1. `./gradlew :androidApp:assembleDebug` completes — show the output, don't
   just claim it.
2. `./gradlew :shared:jvmTest` completes.
3. `./gradlew :shared:compileKotlinIosSimulatorArm64` completes, if
   `shared/` was touched. This is mandatory, not optional: otherwise
   `commonMain` is only checked against JVM and Android, and Kotlin/Native
   is stricter.
4. `tools/check-swift.sh` completes, if Swift files were touched. If
   `swiftc` isn't installed, the script exits with code 127 — then the
   Swift files are **also not** syntactically checked, and that must be
   reported as such.
5. No file outside the assigned directory was touched.
6. Anything that couldn't be built or checked is named explicitly as
   unverified.

Don't claim something works when it was only written.
