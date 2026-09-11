# Provider abstraction for routing and destination search

*2026-09-10 · implementation plan. Not built yet — this document is the agreed design.*

## Context

Route calculation and destination search are hard-wired to two public demo
services today:

- `OsrmRouteEngine` → `https://router.project-osrm.org` — the OSRM project's
  demo server, whose operators explicitly rule out production use
  (ROADMAP.md, open point 5: *"it must be replaced before any release"*).
- `NominatimGeocoder` → `https://nominatim.openstreetmap.org` — at most one
  request per second, no bulk use (ROADMAP.md, open point 6).

Both already sit behind the `RouteEngine` and `Geocoder` ports, but they are
instantiated unconditionally in exactly one place
([ChargeStopsFeatureFactory.kt:99](../shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeatureFactory.kt)
and `:114`). Switching providers is therefore a code change, not a
configuration step.

Goal: routing and destination search become swappable. The first alternative
is Google (Routes API v2 + Places API New). The existing OSM stack stays and
later points at self-hosted OSRM/Nominatim or an own proxy — from then on
through base URLs only, without touching code.

### Decisions this plan rests on

1. **A backend is coming, but as a separate track.** The app does *not* get a
   second set of provider code for it: the backend mirrors the upstream paths
   1:1, so the same clients serve it with nothing but a different base URL and
   no vendor key. Every implementation therefore takes a `baseUrl` plus
   optional credentials. The sketch is further down; the backend is not built
   here.
2. **One switch for both.** A provider supplies routing *and* search as a
   pair — no mixed operation.
3. **Default from the build, override at runtime.** If a Google key is
   present, Google is the default; a value in the `SettingsStore` overrides
   it. The switch is a developer tool and lives in the existing debug section
   of the drawer, next to `CarData`.
4. **Two-phase geocoder** (`suggest` + `resolve`), because Google Places
   autocomplete returns no coordinates.

---

## Design pattern

**Abstract Factory** on top of the existing **Strategy** ports, plus a
**delegating proxy** for the runtime switch.

Why an abstract factory rather than two independent factories: routing and
search have to agree on what a place is. A Google place ID means nothing to
Nominatim; mixed operation would make exactly that mistake possible in the
first place. The pattern enforces the consistent pair.

This matches the house style: `ChargeSiteSource` is already staffed according
to key availability (`OpenChargeMapSource` vs. `DemoSiteSource`,
[ChargeStopsFeatureFactory.kt:80](../shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeatureFactory.kt)),
`RoutedRouteProvider` composes via `fallback`, `CombinedSoCSource` and
`MergingSiteRepository` are composites. Nothing foreign is introduced.

---

## New types

### `shared/.../domain/MapProvider.kt`

```kotlin
/** Where route and destination search come from. Always both from one vendor. */
enum class MapProvider { OSM, GOOGLE }
```

Only the enum lives in `domain` — it is persisted and shown in the UI. The
assembly logic belongs in `data`, so `domain` keeps knowing nothing
(AGENTS.md, dependency direction).

### `shared/.../data/MapServices.kt`

```kotlin
/** One vendor's routing and destination search. */
class MapServices(
    val provider: MapProvider,
    val routeEngine: RouteEngine,
    val geocoder: Geocoder,
)

interface MapServicesFactory {
    val provider: MapProvider
    /** `null` when the configuration doesn't carry what this provider needs. */
    fun create(httpClient: HttpClient, config: MapServicesConfig): MapServices?
}

/**
 * Where a service points and how the app identifies itself there.
 *
 * [apiKey] is the vendor's key, [proxyToken] the own backend's. At most one of
 * them is set: either the app talks to the vendor directly, or it talks to the
 * proxy, which holds the vendor key and never hands it out.
 */
data class EndpointConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val proxyToken: String? = null,
) {
    /** For services that demand any credential at all — OSRM and Nominatim don't. */
    val isAuthorized: Boolean get() = apiKey != null || proxyToken != null
}

data class MapServicesConfig(
    val provider: MapProvider = MapProvider.OSM,
    val osrm: EndpointConfig = EndpointConfig(OsrmRouteEngine.DEFAULT_BASE_URL),
    val nominatim: EndpointConfig = EndpointConfig(NominatimGeocoder.DEFAULT_BASE_URL),
    val googleRoutes: EndpointConfig = EndpointConfig(GoogleRoutesEngine.DEFAULT_BASE_URL),
    val googlePlaces: EndpointConfig = EndpointConfig(GooglePlacesGeocoder.DEFAULT_BASE_URL),
)

object MapServicesRegistry {
    private val factories = listOf(OsmMapServicesFactory, GoogleMapServicesFactory)

    /**
     * OSM is the guaranteed fallback: a missing Google key must not switch
     * route calculation off — the same decision as the missing OCM key that
     * falls back to DemoSiteSource.
     */
    fun create(provider: MapProvider, httpClient: HttpClient, config: MapServicesConfig): MapServices =
        factories.firstOrNull { it.provider == provider }?.create(httpClient, config)
            ?: OsmMapServicesFactory.create(httpClient, config)
}
```

`GoogleMapServicesFactory.create` returns `null` when `googleRoutes.isAuthorized`
or `googlePlaces.isAuthorized` is false. That is deliberately an explicit
statement rather than guessing from the base URL: whether Google or the own
backend sits behind an address is not visible in the address.

Each client sets its own credential: `apiKey` as `X-Goog-Api-Key`,
`proxyToken` as `Authorization: Bearer …`. OSRM and Nominatim normally carry
neither — behind the backend they carry the token.

### `shared/.../data/SwitchingMapServices.kt`

The runtime switch. `ChargeStopsFeature` is a process singleton and its engines
are built once — without delegation, changing provider would need a restart.

```kotlin
/**
 * Keeps exactly one instance per vendor. Not out of thrift: the Places session
 * token lives in the geocoder instance, and a geocoder rebuilt per keystroke
 * would bill every keystroke as a new session.
 */
class MapServicesHolder(
    private val httpClient: HttpClient,
    private val config: MapServicesConfig,
    private val override: Flow<MapProvider?>,
) {
    private val instances = mutableMapOf<MapProvider, MapServices>()
    suspend fun current(): MapServices { /* override.first() ?: config.provider */ }
}

internal class SwitchingRouteEngine(private val holder: MapServicesHolder) : RouteEngine {
    override suspend fun route(from: LatLon, to: LatLon) = holder.current().routeEngine.route(from, to)
}

internal class SwitchingGeocoder(private val holder: MapServicesHolder) : Geocoder { /* same shape */ }
```

`current()` may be `suspend`, because `route`/`suggest`/`resolve` are anyway —
that saves a `CoroutineScope` of its own and a `@Volatile` field.

Special case, switching provider mid-entry: Nominatim cannot resolve a Google
`PlaceRef`. `resolve` then returns `null` and the UI reports it like any other
resolution failure. The other way round, every geocoder resolves a
`PlaceRef.Known` without asking — it carries the coordinates itself.

---

## Port change: two-phase geocoder

`shared/.../domain/Geocoder.kt`:

```kotlin
/** A destination-search hit, before its coordinates are known. */
class PlaceSuggestion internal constructor(
    val name: String,
    val description: String,
    val address: Address? = null,
    /** Straight-line distance from the search origin, where the source reports one. */
    val distanceKm: Double? = null,
    internal val ref: PlaceRef,
)

internal sealed interface PlaceRef {
    /** Nominatim returns coordinates in the search response already. */
    data class Known(val position: LatLon) : PlaceRef
    /** Google Places: only the details call knows the coordinates. */
    data class Remote(val id: String, val sessionToken: String?) : PlaceRef
}

interface Geocoder {
    suspend fun suggest(query: String, near: LatLon? = null, limit: Int = DEFAULT_LIMIT): List<PlaceSuggestion>

    /** `null` when the suggestion can't be resolved (any more). */
    suspend fun resolve(suggestion: PlaceSuggestion): Place?

    companion object { const val DEFAULT_LIMIT = 5 }
}
```

`PlaceSuggestion` is deliberately **not** a `data class`: the internal
constructor and the internal `ref` would otherwise leak out through the
generated `copy()` (Kotlin refuses that). As a plain class, `ref` stays inside
`:shared` — the UI cannot use a suggestion behind `resolve`'s back. Tests in
`shared/src/jvmTest` still see `internal`.

`Place` stays unchanged: it is the *resolved* result.

### Call path — what changes where

| File | Change |
|---|---|
| [ChargeStopsFeature.kt:221](../shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeature.kt) | `searchDestinations` → `suggestDestinations(query): List<PlaceSuggestion>`; new `resolveDestination(suggestion): Destination?` (maps `Place` → `Destination`) |
| [PlanSheetViewModel.kt](../shared/src/commonMain/kotlin/de/autoapp/shared/ui/PlanSheetViewModel.kt) | `results: List<PlaceSuggestion>?`; new `resolving: Boolean` and `resolveFailed: Boolean`; `onSuggestionChosen(suggestion)` launches a coroutine instead of setting `chosen` right away |
| [PlanSheet.kt:199](../androidApp/src/main/kotlin/de/autoapp/android/phone/PlanSheet.kt) | `distanceKm = suggestion.distanceKm` instead of `uiState.from?.distanceKmTo(place.position)`; `detailLine()` moves onto `PlaceSuggestion`; rows locked while `resolving`; new string for the failure case |
| [DestinationSearchScreen.kt:99](../androidApp/src/main/kotlin/de/autoapp/android/car/DestinationSearchScreen.kt) | The click listener is synchronous: `lifecycleScope.launch { … }`, `setLoading(true)` until `resolveDestination` answers, push `RouteScreen` only on success, otherwise a `CarToast` |

**A trap that needs a test:** the debounce guard
`SearchInput(query, chosen != null)` suppresses a search as soon as a
destination is chosen. Between the tap and `resolve`'s answer, `chosen` is
still `null` while `query` already holds the suggestion's name — without an
additional `picking` flag in `InputState` (folded into `isSearchable`), a
search for the just-chosen destination fires in exactly that window.

Nominatim keeps its semantics completely: `suggest` returns `PlaceRef.Known`
including `distanceKm` (from `near` via `distanceKmTo`), `resolve` does no
network traffic. The `distinctBy { description }` deduplication stays.

---

## Google implementations

### `shared/.../data/GoogleRoutesEngine.kt`

```
POST {base}/directions/v2:computeRoutes
X-Goog-Api-Key: <key>
X-Goog-FieldMask: routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline
Content-Type: application/json

{ "origin":      { "location": { "latLng": { "latitude": …, "longitude": … } } },
  "destination": { "location": { "latLng": { … } } },
  "travelMode": "DRIVE", "routingPreference": "TRAFFIC_UNAWARE",
  "polylineQuality": "OVERVIEW", "polylineEncoding": "ENCODED_POLYLINE",
  "languageCode": "de-DE", "units": "METRIC" }
```

- `DEFAULT_BASE_URL = "https://routes.googleapis.com"`.
- `polylineQuality: OVERVIEW` corresponds to OSRM's `overview=simplified` —
  for a two-kilometer buffer the coarse shape is enough (see the KDoc on
  `OsrmRouteEngine`).
- Response: `distanceMeters` (Int), `duration` as a string like `"854s"`
  (needs parsing), `polyline.encodedPolyline`.
- An empty `routes` array → `null`, exactly like OSRM's `code != "Ok"`. Error
  statuses still throw, because the shared client sets `expectSuccess = true`
  ([HttpClientFactory.kt](../shared/src/commonMain/kotlin/de/autoapp/shared/data/HttpClientFactory.kt)).
- The project's first POST: don't forget
  `contentType(ContentType.Application.Json)`, or `ContentNegotiation` won't
  serialize the body.

### `shared/.../data/EncodedPolyline.kt`

Google returns the geometry in the *Encoded Polyline Algorithm Format*
(precision 5). Nothing in the repo handles that — a decoder of about 25 lines
is added, with its own test in `commonTest` against the canonical vector
`_p~iF~ps|U_ulLnnqC_mqNvxq`@` → `(38.5, -120.2)`, `(40.7, -120.95)`,
`(43.252, -126.453)`.

### `shared/.../data/GooglePlacesGeocoder.kt`

```
POST {base}/v1/places:autocomplete
X-Goog-Api-Key: <key>
{ "input": …, "languageCode": "de", "regionCode": "DE",
  "locationBias": { "circle": { "center": {…}, "radius": 50000.0 } },
  "origin": { "latitude": …, "longitude": … },
  "sessionToken": <token> }

GET {base}/v1/places/{placeId}?sessionToken=<the same token>
X-Goog-Api-Key: <key>
X-Goog-FieldMask: id,location,formattedAddress,displayName,addressComponents
```

- `DEFAULT_BASE_URL = "https://places.googleapis.com"`.
- `origin` is set so that `placePrediction.distanceMeters` comes back — that
  is the only way the distance column in `PlaceRow` stays filled while the
  coordinates are still unknown.
- Mapping: `name` = `structuredFormat.mainText.text` (falling back to
  `text.text`), `description` = `mainText, secondaryText`, `distanceKm` =
  `distanceMeters / 1000`.
- `limit`: the API has no parameter for it and returns up to five
  suggestions → `.take(limit)`.
- **Session token:** one token per typing session; the details call closes it
  and is billed more cheaply because of that. The token is created on the
  first `suggest` after a `resolve`, travels in `PlaceRef.Remote`, and is
  consumed and reset in `resolve`. Generated with `kotlin.uuid.Uuid.random()`
  (opt-in `@ExperimentalUuidApi`; if the Kotlin version in
  `libs.versions.toml` doesn't allow it, a hex string from `Random.nextLong()`
  — Google only requires uniqueness).
- `addressComponents` (`route`, `street_number`, `postal_code`, `locality`) →
  `Address`, analogous to `NominatimAddress.toAddress()`.

---

## Build and configuration plumbing

`androidApp/build.gradle.kts`:

```kotlin
/**
 * Separate from the Maps SDK key on purpose: that one is restricted to this
 * app's package and signature, and Google rejects such a key on the web
 * services (Routes, Places REST) — those only know IP restrictions.
 */
val googleWebApiKey: String = secret("googleWebApiKey", "GOOGLE_WEB_API_KEY")
```

plus `buildConfigField("String", "GOOGLE_WEB_API_KEY", …)` with the same
escaping as the OCM key (lines 77–81). The escaping expression then exists
three times — worth pulling into a small local function while there.

`ChargeStopsFeatureFactory.create(…)` gains a parameter
`mapServicesConfig: MapServicesConfig = MapServicesConfig()` and replaces
lines 99 and 114 with the `MapServicesHolder` and its switching delegates. The
`TripPlanner` (line 118) receives the same `SwitchingRouteEngine`.

`AppModule.kt` derives the build default. As long as no backend runs, the
Google key sits in the app directly; once it runs, the backend URL plus token
takes its place and `apiKey` stays `null`:

```kotlin
// Without a backend: the app talks to Google directly.
val googleKey = BuildConfig.GOOGLE_WEB_API_KEY.takeIf { it.isNotEmpty() }
val backend = BuildConfig.BACKEND_BASE_URL.takeIf { it.isNotEmpty() }
val backendToken = BuildConfig.BACKEND_TOKEN.takeIf { it.isNotEmpty() }

mapServicesConfig = if (backend != null) {
    MapServicesConfig(
        provider = MapProvider.GOOGLE,
        osrm = EndpointConfig("$backend/osrm/route/v1/driving", proxyToken = backendToken),
        nominatim = EndpointConfig("$backend/nominatim", proxyToken = backendToken),
        googleRoutes = EndpointConfig("$backend/google/routes", proxyToken = backendToken),
        googlePlaces = EndpointConfig("$backend/google/places", proxyToken = backendToken),
    )
} else {
    MapServicesConfig(
        provider = if (googleKey != null) MapProvider.GOOGLE else MapProvider.OSM,
        googleRoutes = EndpointConfig(GoogleRoutesEngine.DEFAULT_BASE_URL, apiKey = googleKey),
        googlePlaces = EndpointConfig(GooglePlacesGeocoder.DEFAULT_BASE_URL, apiKey = googleKey),
    )
}
```

`backendBaseUrl` and `backendToken` arrive through the same `secret(…)`
mechanism as the other keys. When they are missing, the build behaves exactly
as it did before the backend — that is the condition for both tracks to make
progress independently.

`shared/src/iosMain/.../IosEntryPoints.kt`: `createChargeStopsFeature` gains
the same values as parameters (Swift doesn't see default arguments) and builds
the config identically — there the key comes from `Secrets.xcconfig` →
`Info.plist`, like the OCM key (AGENTS.md, "API keys").

`SettingsStore` (`domain/Ports.kt`) and `PersistentSettingsStore`:

```kotlin
/** Debug override; `null` = whatever the build configured. */
val mapProviderOverride: Flow<MapProvider?>
suspend fun setMapProviderOverride(provider: MapProvider?)
```

Persisted as the enum name; unknown values read back as `null`.

---

## Debug UI

A new screen in the drawer's existing debug section
([DrawerContent.kt:122](../androidApp/src/main/kotlin/de/autoapp/android/phone/DrawerContent.kt)),
as a sibling of `CarData`:

- `shared/.../ui/MapProviderViewModel.kt` — `MapProviderUiState(buildDefault,
  override, active, googleAvailable, routingEndpoint, searchEndpoint)`,
  registered in `SharedUiModule.kt` via `viewModelOf`. Takes `SettingsStore`
  and `ChargeStopsFeature`; the feature exposes build default and Google
  availability for it (per AGENTS.md, ViewModels may take nothing else).
- `androidApp/.../phone/MapProviderDebugScreen.kt` — stateless, two choice
  rows plus "build default", and the active endpoints in plain text below.
- `MapProviderDebug : PhoneDestination` in `Destinations.kt`, an entry in
  `MainActivity.kt` next to `entry<CarData>`, German strings in `strings.xml`.

The endpoints in the state are technical values, not wording — the
`CarDataDebugScreen` shows raw values just the same.

---

## Tests

| File | Content |
|---|---|
| `shared/src/commonTest/.../data/EncodedPolylineTest.kt` | Canonical Google vector, empty string, single point |
| `shared/src/jvmTest/.../data/GoogleRoutesEngineTest.kt` | MockEngine: POST path, `X-Goog-Api-Key`, field mask, `latLng` order in the body; `"854s"` → minutes; empty `routes` → `null` |
| `shared/src/jvmTest/.../data/GooglePlacesGeocoderTest.kt` | Mapping from `structuredFormat`; `distanceMeters` → `distanceKm`; the same session token in autocomplete *and* details; a new token after `resolve`; `resolve(Known)` makes no request |
| `shared/src/jvmTest/.../data/MapServicesRegistryTest.kt` | GOOGLE without a key → the OSM pair; GOOGLE with a key → the Google pair; a proxy base URL with a token is accepted |
| `shared/src/jvmTest/.../data/NominatimGeocoderTest.kt` | Moved to `suggest`/`resolve`; `resolve` against a MockEngine that fails every request |
| `shared/src/jvmTest/.../ui/PhoneViewModelTest.kt` | Tapping resolves and sets `chosen`; **no** search in the window between tap and answer; a `resolve` failure becomes visible |
| `shared/src/jvmTest/.../ChargeStopsFeatureRouteTest.kt` | Hand-written `Geocoder` fakes moved to the new port |
| `shared/src/jvmTest/.../data/GoogleLiveContractTest.kt` | Opt-in like `OsrmLiveContractTest`, but additionally bound to a key from the environment — **these calls cost money**, so exactly one each |

Verification (every call through the `gradle-run` wrapper, AGENTS.md "Build"):

```bash
./gradlew :shared:jvmTest
```

```bash
./gradlew :androidApp:assembleDebug
```

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Then in the app (skill `app-laufen-lassen`): open the plan sheet, type
"Münch", check the suggestions, pick a destination — a route must appear. Then
switch to the other provider in the debug screen and repeat the same entry;
the route must come from the other service without a restart. Cross-check the
car side through the `dhu` skill, because resolution happens inside the
synchronous click listener there.

---

## Backend — sketch

Not part of this plan and deliberately only outlined here. It is the reason
the endpoints are configurable, so its shape belongs in this document.

**Role: key holder and access point, not an API of its own.** The backend
mirrors the upstream paths unchanged. Only that keeps it at *one* provider
integration — the app clients don't know the difference, they get a different
base URL and a token instead of the vendor key. The moment the backend invents
a response format of its own, the Google integration exists twice: once in the
server, once in the app.

| Path | Target | What the backend does |
|---|---|---|
| `/osrm/*` | own OSRM, the demo server for now | pass through |
| `/nominatim/*` | own Nominatim | pass through, set the User-Agent |
| `/google/routes/*` | `routes.googleapis.com` | inject `X-Goog-Api-Key` |
| `/google/places/*` | `places.googleapis.com` | inject `X-Goog-Api-Key` |

**It must be able to**

- **Control access.** Without it the proxy is an open relay that runs other
  people's traffic against the very bill it is meant to protect. One token per
  release in the `Authorization` header is enough to start. That, too, is
  extractable from an APK — the difference is that it can be rotated and
  throttled, and the Google key cannot.
- **Throttle and cap quota per token**, so a leaked token does bounded damage.
- **Honor Nominatim's usage policy centrally** (one request per second,
  recognizable User-Agent) — today that hangs on the debounce interval in the
  app.
- **Offer a health endpoint**, because the app has to be able to fall back to
  OSM on an outage instead of hanging.

**It must not** store search terms or destination coordinates permanently.
Google's terms forbid it for Places data, and it is a movement profile in the
literal sense. Access logs without query parameters.

**Technology open.** As long as nothing is rewritten, a slim reverse proxy
with header injection is enough. Only when provider selection or response
normalization is added does it become a service of its own — Ktor Server is
then the obvious choice, because the DTOs from `:shared` could be reused.
Hosting, deployment, token rotation and cost model are not decided here.

---

## Order of work

1. **Port rework** — two-phase `Geocoder`, Nominatim moved over, all four call
   sites followed up. Behavior unchanged, tests green.
2. **Abstraction** — `MapProvider`, `MapServicesConfig`, `MapServicesFactory`,
   registry, `MapServicesHolder` and the switching delegates; factory wiring.
   Still OSM only.
3. **Google** — polyline decoder, `GoogleRoutesEngine`,
   `GooglePlacesGeocoder`, key plumbing in Gradle, `AppModule`, iOS entry
   point.
4. **Override and debug screen** — `SettingsStore`, ViewModel, screen, drawer.
5. **Documentation** — ARCHITECTURE.md sections 4 and 6, ROADMAP.md open
   points 5 and 6, AGENTS.md "API keys" extended with the second Google key.

Steps 1–2 are a pure refactor without behavior change and make a cleanly
reviewable first PR; 3–5 the second.

The **backend runs alongside**, not afterwards: it blocks nothing and is
blocked by nothing. Once it stands, moving over is a configuration step —
backend URL and token in `local.properties`, `googleWebApiKey` goes away. A
build without those values keeps behaving like today, so development doesn't
hang on running a server.

---

## Risks and open points

- **Until the backend stands, the Google key sits in the APK and is
  extractable.** Routes and Places REST keys cannot be restricted to an
  Android app. While working against Google directly: set quota caps and
  billing alerts, and keep the key out of a published build. Google as the
  default in a release presupposes the backend.
- **Google's terms of service.** Places results may only be stored to a
  limited extent (place IDs permanently, other fields for a limited time).
  `SettingsStore.recentDestinations` and `SavedRoute` store name and
  coordinates indefinitely — that has to be settled with Google as the source
  before Google becomes the default. With OSM data it is unproblematic.
- **Attribution.** ODbL requires the OSM credit, Google requires "Powered by
  Google" wherever Places results are shown. The notice has to follow the
  active provider.
- **Cost.** Places autocomplete is billed per session, not per keystroke — the
  600 ms debounce and the session-token handling are therefore not cosmetics
  but cost control.
- Whether `RouteEngine` will need legs and maneuvers in the medium term
  (Google supplies them, `Route` doesn't know them) is deliberately not
  decided here — the buffer along the line doesn't need them.
