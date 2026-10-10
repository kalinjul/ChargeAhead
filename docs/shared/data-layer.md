# Data sources and repositories

`shared` reaches the outside world through two kinds of interfaces, both
declared in `org.julakali.chargeahead.shared.domain` and implemented outside
it (`shared/data`, `shared/settings`, or the platform):

- A **data source** fetches or streams something the app doesn't keep: the
  backend, the location, the car's battery, the clock.
- A **repository** owns stored data. The UI and the use cases read its
  flows; a `refresh()` or `load()` refills it from a data source.

Most live in `Sources.kt` and `Repositories.kt`; a few sit next to their
types (`ChargePointStatus.kt`, `Route.kt`, `Geocoder.kt`, `TripRepository.kt`).

## Data sources

```kotlin
interface LocationSource { val updates: Flow<Fix>; suspend fun currentFix(): Fix? }

// Gets the whole area, not just its rectangle: a source with a result cap
// must be able to query radially, or the nearest sites go missing.
// Empty networkKeys means every network. Throws on network or server errors.
interface ChargeSiteSource { val id: String; suspend fun query(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> }

fun interface NetworkListSource      { suspend fun networks(): List<Network> }
fun interface VehicleCatalogSource   { suspend fun presets(): List<VehiclePreset> }
fun interface ChargePointStatusSource { suspend fun status(siteIds: List<String>): Map<String, List<ChargePointStatus>> }

// null in the stream means "this source knows nothing right now";
// for CAR_HARDWARE that's the normal case.
interface SoCSource { val kind: SoCSourceKind; val energy: Flow<EnergyState?> }

interface RouteEngine { suspend fun route(from: LatLon, to: LatLon): Route? }
interface Geocoder    { suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> }
fun interface TimeProvider { fun nowMillis(): Long }
```

`RouteProvider` (in `SearchArea.kt`) builds the search area from a fix: the
corridor without a destination, the route strip with one.

## Repositories

```kotlin
// TiledSiteRepository (Room). load() refills the stock; the storedSitesIn
// flows emit again whenever the stock changes.
interface SiteRepository {
    suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite>
    fun storedSitesIn(area: SearchArea): Flow<List<ChargeSite>>
    fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>>
    suspend fun invalidate()
}
fun interface SiteCache { suspend fun prune(preferredNetworkKeys: Set<String>) }   // run once at app start

// The backend's lists, kept in Room. refresh() keeps what is stored when the
// backend fails or answers with an empty list.
interface NetworkRepository        { val networks: Flow<List<Network>>; suspend fun refresh() }
interface VehicleCatalogRepository { val presets: Flow<List<VehiclePreset>>; suspend fun refresh() }

// Live availability, keyed by ChargeSite.liveStatusId; refresh() fetches what is missing or stale.
interface ChargePointStatusRepository { val statuses: Flow<Map<String, List<ChargePointStatus>>>; suspend fun refresh(ids: Collection<String>) }

// The driver's settings, one repository per kind of data, all over one
// DataStore file. Depend only on the one you use.
// VehicleRepository keeps the garage in the database (garageVehicle) and
// only the selected car's id and the charge levels in the file.
interface VehicleRepository {
    val vehicle: Flow<VehicleProfile?>
    val vehicles: Flow<List<VehicleProfile>>      // the garage; setVehicle selects AND adds
    val manualSocPercent: Flow<Double?>
    val arrivalSocPercent: Flow<Double>
    suspend fun setVehicle(profile: VehicleProfile?)                           // adds, or updates the car with its id
    suspend fun updateVehicles(transform: (VehicleProfile) -> VehicleProfile)   // the whole garage in one write
    suspend fun removeVehicle(id: String)
    suspend fun setManualSocPercent(socPercent: Double?)
    suspend fun setArrivalSocPercent(socPercent: Double)
}
interface PreferencesRepository {
    val networks: Flow<NetworkPreferences>
    val chargeFilters: Flow<ChargeFilters>        // phone flows: min power, max distance; slowMode is not persisted
    suspend fun setNetworks(preferences: NetworkPreferences)
    suspend fun setChargeFilters(filters: ChargeFilters)
}
interface DestinationHistory {
    val recentDestinations: Flow<List<Destination>>
    suspend fun addRecentDestination(destination: Destination)
}
interface CarDiagnosticsRepository {              // what the car last reported, for the debug views
    val socDiagnostics: Flow<SoCDiagnostics?>
    val carDebugData: Flow<List<CarDataPoint>>
    suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics)
    suspend fun recordCarDataPoint(point: CarDataPoint)
}

// Destination, planned trip (on the map) and committed trip (sent to Maps),
// one per process, in its own DataStore file. Every transition is one write
// of the whole state; only the trip interactors call update().
data class TripState(val destination: Destination?, val planned: TripPlan?, val committed: CommittedTrip?, val unreachable: UnreachableTrip?)
class TripRepository(storage: TripStorage) { val state: StateFlow<TripState>; suspend fun restore() }
```

**One settings file per process.** The settings repositories all read and
write the same DataStore file, and DataStore refuses a second active
instance on it. `settingsModule { … }` opens it once and declares each
repository as a singleton; the platform module passes in how to open the
file. In Android Auto, the phone and car UI run in the same process and
share them.

**The garage is in the database.** `RoomVehicleRepository` stores the cars
in the `garageVehicle` table of `ChargeSiteDatabase` and the selected car's
id under `vehicle.id` in the settings file. The database still falls back to
a destructive migration, so a schema bump empties the garage along with the
caches.

## The ChargeAhead backend

Charging sites, live status, charging networks, the vehicle catalog, the
destination search and route calculation all come from the ChargeAhead
backend. The app stops at startup when it is not configured.

- Android: `chargeAheadBaseUrl` and `chargeAheadToken` in `local.properties`
  → `BuildConfig`
- iOS: build settings `CHARGEAHEAD_BASE_URL` and `CHARGEAHEAD_TOKEN` in a
  local `iosApp/Secrets.xcconfig` → `Info.plist`

The backend assigns each site its network (`ChargeSite.networkKey`) and lists
the networks worth offering (`/v1/networks`). `NetworkCatalog` is the old
shipped list and is no longer used.

The contract module `org.julakali.chargeahead:api-model` comes from the
backend's own Maven repository, which needs `chargeahead.maven.user` and
`chargeahead.maven.password` in `~/.gradle/gradle.properties`.

## Verifying a data source

Each source's mapping is checked twice:

- `BackendChargeSiteSourceTest` (always runs) checks against **fabricated**
  responses. Fast and network-free, but it doesn't notice when the backend
  changes.
- `BackendChargeSiteLiveContractTest` checks against the **real** backend.
  It runs only on explicit request, because a test that goes red on a dead
  spot says nothing about the code:

  ```bash
  CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendChargeSiteLiveContractTest'
  ```
