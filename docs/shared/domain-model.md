# Domain model

The plain Kotlin types in `org.julakali.chargeahead.shared.domain`. Android
and iOS build on them and duplicate none of them. The source is the
authority; this page is the overview.

## Sites and stops

```kotlin
data class LatLon(val lat: Double, val lon: Double)

enum class ConnectorType { CCS2, TYPE2, CHADEMO, TESLA_NACS, SCHUKO, UNKNOWN }

// UNKNOWN as long as there is no vehicle profile or no charge level.
enum class Reachability { REACHABLE, MARGINAL, UNREACHABLE, UNKNOWN }

data class Connector(val type: ConnectorType, val maxPowerKw: Double, val count: Int?)   // count may be unknown

data class ChargeSite(
    val id: String,                  // source-qualified, e.g. "ocm:12345"; for a merged site, the leading source's
    val name: String,
    val operator: String?,
    val position: LatLon,
    val connectors: List<Connector>,
    ...                              // address, sources, liveStatusId
    val networkKey: String? = null,  // assigned by the backend
)

data class ChargeStop(
    val site: ChargeSite,
    val distanceKm: Double,          // on a route: road km along it; in the corridor: straight line x ROUTE_DETOUR_FACTOR
    val reachability: Reachability,
    val socOnArrivalPercent: Double?,
    // The strongest connector THIS vehicle can use. Chosen by the planner,
    // not the formatter: it depends on the profile.
    val primaryConnector: Connector? = null,
)
```

## Vehicles

```kotlin
// Throws in the constructor for capacity or consumption <= 0.
data class VehicleProfile(
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val acceptedConnectors: Set<ConnectorType>,   // empty = no filtering
    val dcPeakPowerKw: Double? = null,            // null = unknown; charge-time estimates use site power alone
    val modelId: String? = null,                  // the linked catalog model; null when typed in by hand
    val roadLoad: RoadLoad? = null,               // the catalog's curve, attached when planning; not stored with the garage
    val customized: Boolean = false,              // catalog values edited by the driver
    val ownConsumption: Boolean = false,          // consumption set with the garage slider
    val id: String = newVehicleId(),              // the garage entry
)

// A model from the backend's catalog; toProfile() makes it a garage car.
data class VehiclePreset(val id: String, val name: String, ..., val roadLoad: RoadLoad?)
```

**The garage.** A car is either linked to the catalog (`modelId` set) or
typed in by hand (`modelId == null`). Every car has its own local `id`, which
stays the same through every edit and rename; the garage finds, updates and
removes cars by it, never by name.

**Following the catalog.** After each catalog refresh, every linked car that
is not `customized` takes the catalog's current values, name included
(`followingCatalog()`). Editing a linked car on the advanced vehicle screen
sets `customized`; from then on it keeps its values until the driver chooses
"Katalogwerte übernehmen" (`RestoreCatalogValuesInteractor`). The garage's
consumption slider does not count as customizing: it sets `ownConsumption`,
and that one value survives catalog updates. The planner reads `roadLoad`
through `modelId` alone.

## Charge level and location

```kotlin
enum class SoCSourceKind { MANUAL, CAR_HARDWARE, OEM_CLOUD }

data class EnergyState(val socPercent: Double, val source: SoCSourceKind, val observedAtMillis: Long)

data class Fix(
    val position: LatLon,
    val bearingDeg: Double?,         // null when stationary, never guessed
    val speedMps: Double?,
    val timestampMillis: Long,
)
```

## Search areas and routes

```kotlin
data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double)

sealed interface SearchArea { val origin: LatLon; val radiusKm: Double; val boundingBox: BoundingBox }
data class SectorArea(...) : SearchArea     // fan in the direction of travel; halfAngleDeg = 180.0 is the full circle
data class PolylineArea(...) : SearchArea   // strip along the route, once a destination is set

data class Destination(val name: String, val position: LatLon)
data class Route(val points: List<LatLon>, val distanceKm: Double, val durationMinutes: Double)
```

**The route is computed once per destination, not once per location
update.** A planned or committed trip brings its route along
(`TripRepository`), so `ChargeStopsObserver` only asks the `RouteEngine`
when there is no plan to the destination. `PolylineArea.aheadOf()` trims it
at the front as the drive progresses. `RoutedRouteProvider` falls back to the
corridor when the driver leaves the route or reaches the destination;
otherwise the list would sit empty at the end of every drive.
