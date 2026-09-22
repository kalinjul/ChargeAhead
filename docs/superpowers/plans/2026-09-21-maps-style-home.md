# Maps-style Home Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the phone map into the single screen: a persistent search bar plans directly, the trip shows as a sheet over the same map, the drawer moves to the right.

**Architecture:** Shared KMP layer gets a `SearchViewModel` (replacing `PlanSheetViewModel`) and a `TripViewModel.clear()`. The Android `HomeScreen` grows the new chrome (bar, settings icon, control column, two pills, results panel, trip sheet); the `Trip` and `StopDetail` pages, `PlanSheet` and `TripGoogleMap` are deleted. The shell mode (browsing / searching / trip) is derived in `PhoneApp` from `TripUiState` and bar focus.

**Tech Stack:** Kotlin Multiplatform, Compose Material3 (`ModalNavigationDrawer`, `BottomSheetScaffold`), maps-compose 8.6, Koin, kotlin-test + `runBlocking` in `shared/src/jvmTest`.

**Spec:** `docs/superpowers/specs/2026-09-21-maps-style-home-design.md`

## Global Constraints

- User-visible text is German and comes only from `androidApp/src/main/res/values/strings.xml`.
- Identifiers, comments: English. Comments only for a non-obvious *why*, one line.
- Never touch `build.gradle.kts` or `gradle/libs.versions.toml`.
- `shared/commonMain` knows no Android/iOS frameworks.
- Coroutine tests live in `shared/src/jvmTest`, use `runBlocking` and `Dispatchers.setMain(Dispatchers.Unconfined)`.
- Gradle needs `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Run `./gradlew --console=plain --no-scan <task>` with the log redirected into the scratchpad and grep for `BUILD`/`FAILED`/`e: `.
- Commit style: `type: subject`, lowercase, ≤70 chars, no ticket, no co-author trailer.
- Android Auto (`CarAppService`) and iOS Swift are untouched. `IosEntryPoints.kt` is adapted only where the deleted ViewModel forces it.
- Raphael verifies on device; do not launch the app or emulator.

---

### Task 1: SearchViewModel replaces PlanSheetViewModel (shared)

**Files:**
- Create: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ui/SearchViewModel.kt`
- Delete: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ui/PlanSheetViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ui/SharedUiModule.kt`
- Modify: `shared/src/iosMain/kotlin/org/julakali/chargeahead/shared/IosEntryPoints.kt:19-20,123,163-176`
- Test: `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/ui/SearchViewModelTest.kt`
- Modify: `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/ui/PhoneViewModelTest.kt:155-190` (delete the two plan-sheet tests)

**Interfaces:**
- Consumes: `ObserveDestinationSearch`, `SettingsStore.recentDestinations`, `SettingsStore.vehicle`, `ChargeStopsFeature.currentFix`, `LatLon.distanceKmTo`, `Place.toDestination()`, `ChargeStopFormatter.label/detailLine`.
- Produces:
  ```kotlin
  data class SearchRow(val destination: Destination, val title: String, val detail: String?, val distanceKm: Double?, val recent: Boolean)
  data class SearchUiState(val query: String, val rows: List<SearchRow>, val searching: Boolean, val failed: Boolean, val hasVehicle: Boolean, val results: List<Place>?) {
      val isQueryTooShort: Boolean
      companion object { const val MIN_QUERY_LENGTH }
  }
  fun searchRows(query: String, results: List<Place>?, recent: List<Destination>, from: LatLon?): List<SearchRow>
  fun Double.asKmLabel(): String   // "< 1 km", "4,2 km" style one decimal below 10, integer above
  class SearchViewModel(feature, observeDestinationSearch, settings) : ViewModel {
      val uiState: StateFlow<SearchUiState>
      fun onOpened(prefill: Destination? = null)
      fun onQueryChanged(query: String)
      fun onClosed()
  }
  ```
  Planning itself stays in `TripViewModel.plan(destination)`; the UI calls that when a row is picked and `hasVehicle` is true.

- [ ] **Step 1: Write the failing tests**

```kotlin
package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDownMainDispatcher() = Dispatchers.resetMain()

    private val hamburg = Destination("Hamburg", LatLon(53.55, 9.99), "Hamburg")
    private val berlin = Place(
        name = "Berlin Hbf",
        description = "Berlin Hbf, Europaplatz 1, 10557 Berlin",
        position = LatLon(52.525, 13.369),
        address = Address(street = "Europaplatz 1", postalCode = "10557", town = "Berlin"),
    )
    private val frankfurt = LatLon(50.11, 8.68)

    /** An empty query lists what was searched before, nothing else. */
    @Test
    fun `an empty query shows recents as rows`() {
        val rows = searchRows(query = "", results = emptyList(), recent = listOf(hamburg), from = null)
        assertEquals(listOf(SearchRow(hamburg, "Hamburg", "Hamburg", distanceKm = null, recent = true)), rows)
    }

    /** Once the query is long enough the geocoder's hits replace the recents. */
    @Test
    fun `a typed query shows geocoder results with their address`() {
        val rows = searchRows(query = "Berlin", results = listOf(berlin), recent = listOf(hamburg), from = frankfurt)
        assertEquals(1, rows.size)
        assertEquals("Berlin Hbf", rows.single().title)
        assertEquals("Europaplatz 1, 10557 Berlin", rows.single().detail)
        assertEquals(false, rows.single().recent)
        assertEquals(hamburg.copy(name = "Berlin Hbf", position = berlin.position, address = "Europaplatz 1, 10557 Berlin"), rows.single().destination)
    }

    @Test
    fun `distance is measured from the fix and missing without one`() {
        val withFix = searchRows("", emptyList(), listOf(hamburg), from = frankfurt).single().distanceKm
        assertTrue(withFix!! in 390.0..400.0)
        assertNull(searchRows("", emptyList(), listOf(hamburg), from = null).single().distanceKm)
    }

    @Test
    fun `km labels round the way the list expects`() {
        assertEquals("< 1", 0.4.asKmLabel())
        assertEquals("4,2", 4.24.asKmLabel())
        assertEquals("42", 41.6.asKmLabel())
    }

    /** "Neu planen" reopens the search on the current destination as typed text. */
    @Test
    fun `opening with a prefill puts its label into the query`() = runBlocking<Unit> {
        val viewModel = SearchViewModel(stubFeature(), ObserveDestinationSearch(NoGeocoder, NoLocation), settings())
        viewModel.onOpened(hamburg)
        assertEquals("Hamburg, Hamburg", viewModel.uiState.await { it.query.isNotEmpty() }.query)
        viewModel.onClosed()
        assertEquals("", viewModel.uiState.await { it.query.isEmpty() }.query)
    }

    @Test
    fun `without a vehicle the state says so`() = runBlocking<Unit> {
        val viewModel = SearchViewModel(stubFeature(), ObserveDestinationSearch(NoGeocoder, NoLocation), settings())
        assertEquals(false, viewModel.uiState.await { true }.hasVehicle)
    }

    private fun settings() = PersistentSettingsStore(InMemoryPreferencesDataStore())

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T = withTimeout(5_000) { first(matching) }

    private fun stubFeature() = ChargeStopsFeature(
        locationSource = object : LocationSource { override val updates: Flow<Fix> = emptyFlow() },
        dispatcher = Dispatchers.Unconfined,
    )

    private object NoGeocoder : Geocoder {
        override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> = emptyList()
    }

    private object NoLocation : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --console=plain --no-scan :shared:jvmTest --tests '*SearchViewModelTest*' > "$SCRATCH/t1.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t1.log" | head`
Expected: compilation error, `Unresolved reference: SearchViewModel`.

- [ ] **Step 3: Write SearchViewModel.kt**

```kotlin
package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.toDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

/** One line of the results panel: a recent destination or a geocoder hit. */
data class SearchRow(
    val destination: Destination,
    val title: String,
    val detail: String?,
    /** Straight-line from the current fix; `null` without one. */
    val distanceKm: Double?,
    val recent: Boolean,
)

data class SearchUiState(
    val query: String = "",
    val rows: List<SearchRow> = emptyList(),
    val searching: Boolean = false,
    /** The geocoder itself failed. */
    val failed: Boolean = false,
    val hasVehicle: Boolean = false,
    /** Raw hits, for the iOS bridge. */
    val results: List<Place>? = emptyList(),
) {
    val isQueryTooShort: Boolean get() = query.trim().length < MIN_QUERY_LENGTH

    companion object {
        const val MIN_QUERY_LENGTH = ObserveDestinationSearch.MIN_QUERY_LENGTH
    }
}

/** Recents while the query is too short to search on, hits once it is. */
fun searchRows(query: String, results: List<Place>?, recent: List<Destination>, from: LatLon?): List<SearchRow> =
    if (query.trim().length < SearchUiState.MIN_QUERY_LENGTH) {
        recent.map { SearchRow(it, it.name, it.address, from?.distanceKmTo(it.position), recent = true) }
    } else {
        results.orEmpty().map { place ->
            val destination = place.toDestination()
            SearchRow(destination, place.name, destination.address, from?.distanceKmTo(place.position), recent = false)
        }
    }

/** "< 1", "4,2", "42" — the unit is the caller's resource string. */
fun Double.asKmLabel(): String = when {
    this < 1 -> "< 1"
    this < 10 -> {
        val tenths = (this * 10).roundToInt()
        "${tenths / 10},${tenths % 10}"
    }
    else -> roundToInt().toString()
}

class SearchViewModel(
    private val feature: ChargeStopsFeature,
    private val observeDestinationSearch: ObserveDestinationSearch,
    settings: SettingsStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        observeDestinationSearch.flow,
        settings.recentDestinations,
        settings.vehicle,
        feature.currentFix,
    ) { query, search, recent, vehicle, fix ->
        SearchUiState(
            query = query,
            rows = searchRows(query, search.results, recent, fix?.position),
            searching = search.searching,
            failed = search.results == null,
            hasVehicle = vehicle != null,
            results = search.results,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, SearchUiState())

    init {
        search("")
    }

    /** The bar took focus; [prefill] is the current destination when re-planning. */
    fun onOpened(prefill: Destination? = null) {
        onQueryChanged(prefill?.let(ChargeStopFormatter::label) ?: "")
    }

    fun onQueryChanged(query: String) {
        this.query.value = query
        search(query)
    }

    fun onClosed() {
        onQueryChanged("")
    }

    private fun search(query: String) {
        observeDestinationSearch(ObserveDestinationSearch.Params(query.trim()))
    }
}
```

Note `SOC_PERCENT_RANGE` from the deleted file: grep for it; if anything else uses it, move the one-liner `internal val SOC_PERCENT_RANGE = 1..100` into `TripViewModel.kt` next to `ARRIVAL_SOC_RANGE`.

- [ ] **Step 4: Delete PlanSheetViewModel.kt, update the Koin module and the iOS bridge**

In `SharedUiModule.kt` replace `viewModelOf(::PlanSheetViewModel)` with `viewModelOf(::SearchViewModel)`.

In `IosEntryPoints.kt`: imports `PlanSheetUiState`/`PlanSheetViewModel` → `SearchUiState`/`SearchViewModel`; field `planSheetViewModel = viewModels.get { SearchViewModel(feature, koin.get(), koin.get()) }` renamed to `searchViewModel`; `watchDestinationSearch` and `searchDestinations` use `searchViewModel.uiState`/`searchViewModel.onQueryChanged`; KDoc reference `[SearchUiState.MIN_QUERY_LENGTH]`.

In `PhoneViewModelTest.kt` delete the two tests `the plan sheet opens pre-filled with a destination` and `a picked search result fills the field with its address` (they moved here in a new form) and any now-unused imports (`PlanSheetViewModel`, `Address`, `Place`, `Geocoder` — check with the compiler).

- [ ] **Step 5: Run tests and the iOS compile**

Run: `./gradlew --console=plain --no-scan :shared:jvmTest :shared:compileKotlinIosSimulatorArm64 > "$SCRATCH/t1b.log" 2>&1; grep -E "BUILD|e: |FAILED" "$SCRATCH/t1b.log" | head`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add shared/
git commit -m "feat: search view model without the plan sheet baggage"
```

---

### Task 2: TripViewModel.clear() (shared)

**Files:**
- Modify: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/domain/TripStore.kt`
- Modify: `shared/src/commonMain/kotlin/org/julakali/chargeahead/shared/ui/TripViewModel.kt`
- Test: `shared/src/jvmTest/kotlin/org/julakali/chargeahead/shared/ui/TripStoreTest.kt`

**Interfaces:**
- Produces: `TripStore.clear()` (internal), `TripViewModel.clear()` — drops the plan so `uiState` returns to `TripUiState.NoPlan`.

- [ ] **Step 1: Write the failing test**

```kotlin
package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.TripStore
import kotlin.test.Test
import kotlin.test.assertNull

class TripStoreTest {
    @Test
    fun `clearing drops the stored plan`() {
        val store = TripStore()
        store.clear()
        assertNull(store.plan.value)
    }
}
```

(Constructing a `TripPlan` needs a `Route` and stops; `store(plan)` is exercised by `ChargeStopsFeatureTest` already. This test only pins the new method compiles and nulls.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew --console=plain --no-scan :shared:jvmTest --tests '*TripStoreTest*' > "$SCRATCH/t2.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t2.log" | head`
Expected: `Unresolved reference: clear`.

- [ ] **Step 3: Implement**

`TripStore.kt`, after `store`:
```kotlin
    internal fun clear() {
        current.value = null
    }
```

`TripViewModel.kt`, after `plan(...)`:
```kotlin
    /** The driver dismissed the trip; the map goes back to browsing. */
    fun clear() {
        selection.value = SectionSelection()
        socEditor.value = null
        arrivalSocEditor.value = null
        tripStore.clear()
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew --console=plain --no-scan :shared:jvmTest > "$SCRATCH/t2b.log" 2>&1; grep -E "BUILD|FAILED" "$SCRATCH/t2b.log"`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add shared/
git commit -m "feat: let the trip be dismissed"
```

---

### Task 3: HomeGoogleMap draws the route and hands out its camera (Android)

**Files:**
- Modify: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/ChargeMap.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class RouteOverlay(val points: List<LatLon>, val stops: List<Pair<Int, LatLon>>, val destination: LatLon)

  @Composable fun HomeGoogleMap(
      position: LatLon?, chargers: List<MapCharger>, route: RouteOverlay?,
      hasLocationPermission: Boolean, cameraPositionState: CameraPositionState,
      onViewportChanged: (BoundingBox?) -> Unit, onChargerTapped: (MapCharger) -> Unit,
      modifier: Modifier = Modifier,
  )
  @Composable fun rememberHomeCamera(position: LatLon?): CameraPositionState
  const val HOME_ZOOM = 11f   // was private
  ```
  Locate/compass buttons and the loading spinner are gone from this file; Task 4 rebuilds them in `HomeScreen`. `TripGoogleMap` is deleted.

- [ ] **Step 1: Rewrite HomeGoogleMap**

Replace the `HomeGoogleMap` function (lines 87–249) and `TripGoogleMap` (265–319) with:

```kotlin
/** What the trip draws over the browsing map. */
data class RouteOverlay(
    val points: List<LatLon>,
    /** 1-based stop index to position. */
    val stops: List<Pair<Int, LatLon>>,
    val destination: LatLon,
)

/** The home camera, owned by the screen so its controls can drive it. */
@Composable
fun rememberHomeCamera(position: LatLon?): CameraPositionState = rememberCameraPositionState {
    this.position = CameraPosition.fromLatLngZoom((position ?: FALLBACK_CENTER).toLatLng(), HOME_ZOOM)
}

fun LatLon.toLatLng() = LatLng(lat, lon)

/**
 * Home map: viewport-driven. The map reports every settled camera position
 * upward (`null` below [MIN_CHARGER_ZOOM]); the caller passes the chargers back down.
 */
@Composable
fun HomeGoogleMap(
    position: LatLon?,
    chargers: List<MapCharger>,
    route: RouteOverlay?,
    hasLocationPermission: Boolean,
    cameraPositionState: CameraPositionState,
    onViewportChanged: (org.julakali.chargeahead.shared.domain.BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, cameraPositionState.isMoving) {
        if (!mapLoaded || cameraPositionState.isMoving) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        if (cameraPositionState.position.zoom < MIN_CHARGER_ZOOM) {
            onViewportChanged(null)
            return@LaunchedEffect
        }
        val bounds = cameraPositionState.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
        onViewportChanged(
            org.julakali.chargeahead.shared.domain.BoundingBox(
                south = bounds.southwest.latitude,
                west = bounds.southwest.longitude,
                north = bounds.northeast.latitude,
                east = bounds.northeast.longitude,
            ),
        )
    }

    // Follow the first fix, then leave the camera to the user.
    var followedFirstFix by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(position != null) {
        val target = position ?: return@LaunchedEffect
        if (!followedFirstFix) {
            followedFirstFix = true
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM))
        }
    }

    val routeLatLngs = remember(route) { route?.points.orEmpty().map { it.toLatLng() } }

    // Fit once per new route; afterwards the driver may pan freely.
    LaunchedEffect(mapLoaded, routeLatLngs) {
        if (!mapLoaded || routeLatLngs.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder().apply { routeLatLngs.forEach { include(it) } }.build()
        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDS_PADDING_PX))
    }

    val pillIcons = rememberPillIcons()

    GoogleMap(
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        onMapLoaded = { mapLoaded = true },
        // The SDK's own buttons would sit inside the status bar; ours replace them.
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false),
        modifier = modifier,
    ) {
        chargers.forEach { charger ->
            key(charger.site.id) {
                ChargerMarker(charger = charger, icons = pillIcons, onClick = onChargerTapped)
            }
        }
        if (route != null) {
            if (routeLatLngs.size >= 2) {
                Polyline(points = routeLatLngs, color = Color(0xFF1A73E8), width = 14f)
            }
            route.stops.forEach { (index, stopPosition) ->
                key(index) {
                    MarkerComposable(
                        keys = arrayOf(index),
                        state = rememberMarkerState(position = stopPosition.toLatLng()),
                        anchor = Offset(0.5f, 0.5f),
                    ) {
                        ChargeBadge(color = Color(0xFF1A73E8)) {
                            Text(text = "$index", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Marker(state = rememberMarkerState(position = route.destination.toLatLng()))
        }
    }
}
```

Change `private const val HOME_ZOOM = 11f` to `const val HOME_ZOOM = 11f`. Keep `ChargeBadge`, `MissingMapsKeyNotice`, `FALLBACK_CENTER`, `MIN_CHARGER_ZOOM`, `BOUNDS_PADDING_PX`. Remove now-unused imports (`SmallFloatingActionButton`, `Icons`, `MyLocation`, `Navigation`, `AnimatedVisibility`, `fadeIn`, `fadeOut`, `Arrangement`, `Column`, `Row`, `statusBarsPadding`, `graphicsLayer`, `semantics`, `contentDescription`, `rememberCoroutineScope`, `launch`, `CircularProgressIndicator`, `SideEffect`, `stringResource` if unused). Add `import com.google.maps.android.compose.CameraPositionState`.

- [ ] **Step 2: Compile**

Run: `./gradlew --console=plain --no-scan :androidApp:compileDebugKotlin > "$SCRATCH/t3.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t3.log" | head -20`
Expected: errors only in `HomeScreen.kt` and `TripPlanScreen.kt` (callers not yet adapted). No error inside `ChargeMap.kt`.

- [ ] **Step 3: Commit**

```bash
git add androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/ChargeMap.kt
git commit -m "feat: home map draws the route and lends out its camera"
```

---

### Task 4: New home chrome — bar, settings, control column, pills, bottom hint (Android)

**Files:**
- Modify: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/HomeScreen.kt`
- Create: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/HomeChrome.kt`
- Modify: `androidApp/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `HomeGoogleMap`, `rememberHomeCamera`, `RouteOverlay`, `HOME_ZOOM` (Task 3).
- Produces (used by Task 5/6/7):
  ```kotlin
  enum class HomeMode { BROWSING, SEARCHING, TRIP }

  @Composable fun HomeRoute(
      hasPermission: Boolean, planningInProgress: Boolean, mode: HomeMode, route: RouteOverlay?,
      onRequestPermission: () -> Unit, onLocate: () -> Unit, onSettings: () -> Unit,
      onChargeNow: () -> Unit, onRoutes: () -> Unit,
      topBar: @Composable () -> Unit,          // search bar or destination header, from Task 5/6
      topPanel: @Composable () -> Unit,        // results panel, from Task 5
      modifier: Modifier = Modifier, viewModel: HomeViewModel = koinViewModel(),
  )
  // HomeChrome.kt
  @Composable fun RoundIconButton(icon: Painter | ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false, tint: Color = onSurfaceVariant, content: (@Composable () -> Unit)? = null)
  @Composable fun HintChip(text: String, color: Color = onSurface)
  @Composable fun HomePill(text: String?, icon: Painter, containerColor: Color, contentColor: Color, onClick: () -> Unit, iconTint: Color = contentColor, contentDescription: String? = null)
  ```

- [ ] **Step 1: Strings**

Add to `strings.xml` next to the `home_` block; remove `home_pill_plan`:
```xml
    <string name="home_pill_favorites">Favoriten</string>
    <string name="home_settings">Einstellungen</string>
    <string name="home_search_hint">Wohin?</string>
    <string name="home_search_clear">Eingabe löschen</string>
    <string name="home_trip_clear">Route verwerfen</string>
    <string name="plan_vehicle_missing_action">Auto anlegen</string>
```

- [ ] **Step 2: Create HomeChrome.kt**

```kotlin
package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 46dp floating circle; [badge] is the "filters customized" dot. */
@Composable
fun RoundIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.size(46.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(11.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}

@Composable
fun RoundIcon(icon: Painter, contentDescription: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
fun HintChip(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Fully round, floating pill. */
@Composable
fun HomePill(
    text: String?,
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    iconTint: Color = contentColor,
    contentDescription: String? = null,
) {
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, contentColor = contentColor, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            modifier = Modifier.padding(horizontal = if (text != null) 21.dp else 15.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(18.dp))
            text?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)) }
        }
    }
}
```

- [ ] **Step 3: Rewrite HomeScreen.kt**

Replace the whole file body from `HomeRoute` down (keep the package line; fix imports as the compiler demands):

```kotlin
enum class HomeMode { BROWSING, SEARCHING, TRIP }

/** The map screen with its state holder attached. */
@Composable
fun HomeRoute(
    hasPermission: Boolean,
    planningInProgress: Boolean,
    mode: HomeMode,
    route: RouteOverlay?,
    onRequestPermission: () -> Unit,
    /** The location button with nothing to center on. Owned by the activity. */
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    topBar: @Composable () -> Unit,
    topPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The pipeline may only run once the permission is there.
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.onLocationPermissionGranted()
    }

    HomeScreen(
        uiState = uiState,
        hasPermission = hasPermission,
        planningInProgress = planningInProgress,
        mode = mode,
        route = route,
        onViewportChanged = viewModel::onViewportChanged,
        onChargerTapped = viewModel::onChargerSelected,
        onRequestPermission = onRequestPermission,
        onLocate = onLocate,
        onSettings = onSettings,
        onChargeNow = onChargeNow,
        onRoutes = onRoutes,
        topBar = topBar,
        topPanel = topPanel,
        modifier = modifier,
    )

    uiState.selectedStop?.let { stop ->
        ChargeStopDetailSheet(stop = stop, live = uiState.selectedStopLive, onDismiss = viewModel::onSelectedStopDismissed)
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    hasPermission: Boolean,
    planningInProgress: Boolean,
    mode: HomeMode,
    route: RouteOverlay?,
    onViewportChanged: (BoundingBox?) -> Unit,
    onChargerTapped: (MapCharger) -> Unit,
    onRequestPermission: () -> Unit,
    onLocate: () -> Unit,
    onSettings: () -> Unit,
    onChargeNow: () -> Unit,
    onRoutes: () -> Unit,
    topBar: @Composable () -> Unit,
    topPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val camera = rememberHomeCamera(uiState.position)
    val scope = rememberCoroutineScope()
    val inTrip = mode == HomeMode.TRIP

    Box(modifier = modifier) {
        if (hasGoogleMapsKey) {
            HomeGoogleMap(
                position = uiState.position,
                // The route replaces the browsing markers.
                chargers = if (inTrip) emptyList() else uiState.chargers,
                route = route,
                hasLocationPermission = hasPermission,
                cameraPositionState = camera,
                onViewportChanged = onViewportChanged,
                onChargerTapped = onChargerTapped,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MissingMapsKeyNotice(Modifier.fillMaxSize())
        }

        // Bar + settings on one line, controls hanging under the settings icon.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { topBar() }
                RoundIconButton(onClick = onSettings, badge = uiState.filtersCustomized) {
                    RoundIcon(painterResource(R.drawable.ic_filter), stringResource(R.string.home_settings))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == HomeMode.SEARCHING) {
                        topPanel()
                    } else if (uiState.locationUnavailable) {
                        HintChip(stringResource(R.string.phone_status_location_unavailable), MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (camera.position.bearing != 0f) {
                        RoundIconButton(onClick = {
                            scope.launch {
                                camera.animate(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder(camera.position).bearing(0f).tilt(0f).build(),
                                    ),
                                )
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Navigation,
                                contentDescription = stringResource(R.string.map_compass),
                                tint = Color(0xFFD93025),
                                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = -camera.position.bearing },
                            )
                        }
                    }
                    RoundIconButton(onClick = {
                        val target = uiState.position
                        if (target == null) {
                            onLocate()
                        } else {
                            scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), HOME_ZOOM)) }
                        }
                    }) {
                        if (uiState.searchingLocation) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Filled.MyLocation, stringResource(R.string.map_my_location), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (uiState.loadingSites) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (!hasPermission) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.phone_permission_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRequestPermission) { Text(stringResource(R.string.phone_permission_action)) }
                }
            }
        }

        if (planningInProgress) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.plan_planning))
                }
            }
        }

        if (mode == HomeMode.BROWSING) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
            ) {
                if (uiState.belowMinZoom) HintChip(stringResource(R.string.map_zoom_hint))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomePill(
                        text = stringResource(R.string.home_pill_charge_now),
                        icon = painterResource(R.drawable.ic_battery),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onChargeNow,
                    )
                    HomePill(
                        text = stringResource(R.string.home_pill_favorites),
                        icon = painterResource(R.drawable.ic_heart),
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = onRoutes,
                    )
                }
            }
        }
    }
}
```

Delete the old private `HomePill` at the bottom of `HomeScreen.kt` (it moved to `HomeChrome.kt`). Imports needed: `Icons`, `Icons.Filled.MyLocation`, `Icons.Filled.Navigation`, `graphicsLayer`, `rememberCoroutineScope`, `launch`, `CameraUpdateFactory`, `CameraPosition`, `Spacer`, `width`, `fillMaxWidth`.

- [ ] **Step 4: Compile**

Run: `./gradlew --console=plain --no-scan :androidApp:compileDebugKotlin > "$SCRATCH/t4.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t4.log" | head -20`
Expected: remaining errors only in `MainActivity.kt` (old `HomeRoute` call, `home_pill_plan`) and `TripPlanScreen.kt` (`TripGoogleMap`). None in `HomeScreen.kt`/`HomeChrome.kt`.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/HomeScreen.kt androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/HomeChrome.kt androidApp/src/main/res/values/strings.xml
git commit -m "feat: maps-style home chrome (bar slot, settings, controls, two pills)"
```

---

### Task 5: Search bar, destination header and results panel (Android)

**Files:**
- Create: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/HomeSearch.kt`
- Delete: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/PlanSheet.kt`
- Modify: `androidApp/src/main/res/values/strings.xml` (remove `plan_title`, `plan_from`, `plan_from_current`, `plan_to`, `plan_cta`, `plan_soc_value` if unused elsewhere — grep first)

**Interfaces:**
- Consumes: `SearchUiState`, `SearchRow`, `asKmLabel` (Task 1); `RoundIconButton`, `RoundIcon` (Task 4).
- Produces:
  ```kotlin
  @Composable fun HomeSearchBar(query: String, searching: Boolean, focused: Boolean, onFocused: () -> Unit, onQueryChange: (String) -> Unit, onClear: () -> Unit, focusRequester: FocusRequester)
  @Composable fun DestinationHeader(title: String, subtitle: String, onClear: () -> Unit)
  @Composable fun SearchResultsPanel(uiState: SearchUiState, onPick: (SearchRow) -> Unit)
  ```

- [ ] **Step 1: Write HomeSearch.kt**

```kotlin
package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState
import org.julakali.chargeahead.shared.ui.asKmLabel

/** The always-present search pill. Focus flips the shell into searching. */
@Composable
fun HomeSearchBar(
    query: String,
    searching: Boolean,
    onFocused: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 16.dp, end = 6.dp).fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.home_search_hint), style = MaterialTheme.typography.bodyLarge, color = ChargeAheadColors.faint)
                    }
                    inner()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() },
            )
            when {
                searching -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp).padding(end = 10.dp))
                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.home_search_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Replaces the bar while a trip is shown. */
@Composable
fun DestinationHeader(title: String, subtitle: String, onClear: () -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_route), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.home_trip_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Recents or hits under the bar; the caller sizes it. */
@Composable
fun SearchResultsPanel(uiState: SearchUiState, onPick: (SearchRow) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        when {
            uiState.failed -> Message(stringResource(R.string.plan_search_failed), error = true)
            uiState.rows.isEmpty() && !uiState.isQueryTooShort && !uiState.searching -> Message(stringResource(R.string.plan_no_results))
            uiState.rows.isEmpty() -> Unit
            else -> LazyColumn(modifier = Modifier.fillMaxHeight(0.55f)) {
                items(uiState.rows, key = { it.destination.position.toString() + it.title }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onPick(row) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painterResource(if (row.recent) R.drawable.ic_refresh else R.drawable.ic_destination),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.detail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                        row.distanceKm?.let {
                            Text(stringResource(R.string.plan_result_distance, it.asKmLabel()), style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(14.dp),
    )
}
```

`ic_refresh` stands in for a clock icon; if a clock drawable exists by the time of implementation, use it. `fillMaxHeight(0.55f)` inside a column of unbounded height does nothing useful: instead cap with `Modifier.heightIn(max = 360.dp)` on the `LazyColumn`. Use `heightIn`, drop `fillMaxHeight`.

- [ ] **Step 2: Delete PlanSheet.kt; prune strings**

```bash
git rm androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/PlanSheet.kt
grep -rn "plan_title\|plan_from\b\|plan_from_current\|plan_to\b\|plan_cta\|plan_soc_value\|plan_recent" androidApp/src/main/kotlin
```
Remove from `strings.xml` every one of those the grep no longer finds in Kotlin. Also check whether `Formatting.kt`'s `oneDecimal()` still has callers; delete it if not.

- [ ] **Step 3: Compile**

Run: `./gradlew --console=plain --no-scan :androidApp:compileDebugKotlin > "$SCRATCH/t5.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t5.log" | head -20`
Expected: errors only in `MainActivity.kt` and `TripPlanScreen.kt`.

- [ ] **Step 4: Commit**

```bash
git add -A androidApp/src/main
git commit -m "feat: search bar, results panel and destination header for the map"
```

---

### Task 6: TripSheet from TripPlanScreen (Android)

**Files:**
- Create: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/TripSheet.kt`
- Delete: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/TripPlanScreen.kt`
- Delete: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/StopDetailScreen.kt`
- Modify: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/Destinations.kt` (drop `Trip`, `StopDetail`)

**Interfaces:**
- Produces:
  ```kotlin
  @Composable fun TripSheetContent(
      plan: TripPlan, startPosition: LatLon?, startSocPercent: Double?, isSaved: Boolean,
      selection: SectionSelection, socInput: String?, arrivalSocInput: String?,
      onToggleSelecting: () -> Unit, onPickPoint: (Int) -> Unit, onSectionSent: () -> Unit,
      onSendToMaps: (String) -> Unit, onToggleSave: () -> Unit, onReplan: () -> Unit,
      onEditStartSoc: () -> Unit, onSocInputChange: (String) -> Unit, onSocConfirm: () -> Unit, onSocDismiss: () -> Unit,
      onEditArrivalSoc: () -> Unit, onArrivalSocInputChange: (String) -> Unit, onArrivalSocConfirm: () -> Unit, onArrivalSocDismiss: () -> Unit,
      modifier: Modifier = Modifier,
  )
  @Composable fun TripSummary(plan: TripPlan, onReplan: () -> Unit)   // public now: the collapsed peek
  val TRIP_PEEK_HEIGHT = 120.dp
  internal fun minutesText(minutes: Double): String; internal fun etaText(minutesFromStart: Double): String  // unchanged
  ```

- [ ] **Step 1: Create TripSheet.kt from TripPlanScreen.kt**

```bash
git mv androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/TripPlanScreen.kt androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/TripSheet.kt
```
Then edit `TripSheet.kt`:
- Rename `TripPlanScreen` → `TripSheetContent`; remove parameters `hasLocationPermission` and `onOpenStop`.
- Delete the `if (hasGoogleMapsKey) { ... TripGoogleMap(...) } else { MissingMapsKeyNotice(...) }` block at the top of the `Column`.
- Replace the `TripSummary(plan, onReplan = onReplan)` call with nothing: the summary is rendered by the sheet's peek (see Task 7), and the content starts right after it. Keep the function but make it `fun TripSummary(...)` (drop `private`) and drop the top `HorizontalDivider` inside it (the drag handle sits above).
- In the `StationCard` call, `onClick = { if (selecting) onPickPoint(index + 1) }` (stops not tappable this iteration).
- Change `Column(modifier = modifier.fillMaxSize())` to `Column(modifier = modifier)`; `LazyColumn` keeps `weight(1f)`.
- Add at file bottom: `val TRIP_PEEK_HEIGHT = 120.dp`.
- Update the file KDoc: "The planned trip as sheet content: section hint, then the stops as a list with the send actions at its end."
- Remove unused imports (`height`, `fillMaxSize`, `remember`).

- [ ] **Step 2: Delete StopDetailScreen.kt and the two destinations**

```bash
git rm androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/StopDetailScreen.kt
```
In `Destinations.kt` delete the `Trip` and `StopDetail` declarations and shorten the KDoc to `/** The phone's destinations. */`. Grep `detail_title`, `detail_stop_x_of_y`, `trip_topbar_sub` in Kotlin afterwards; `trip_topbar_sub` stays (used by the header in Task 7), remove the other two from `strings.xml` if no Kotlin caller remains.

- [ ] **Step 3: Compile**

Run: `./gradlew --console=plain --no-scan :androidApp:compileDebugKotlin > "$SCRATCH/t6.log" 2>&1; grep -E "BUILD|e: " "$SCRATCH/t6.log" | head -20`
Expected: errors only in `MainActivity.kt`.

- [ ] **Step 4: Commit**

```bash
git add -A androidApp/src/main
git commit -m "feat: trip content becomes sheet content, stop detail page retires"
```

---

### Task 7: Wire the shell — right drawer, modes, trip sheet, back (Android)

**Files:**
- Modify: `androidApp/src/main/kotlin/org/julakali/chargeahead/android/phone/MainActivity.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–6.

- [ ] **Step 1: Imports and ViewModels**

Replace `PlanSheetViewModel` import/usage with `SearchViewModel`; add imports: `androidx.compose.material3.BottomSheetScaffold`, `rememberBottomSheetScaffoldState`, `rememberStandardBottomSheetState`, `SheetValue`, `androidx.compose.runtime.CompositionLocalProvider`, `androidx.compose.ui.platform.LocalLayoutDirection`, `androidx.compose.ui.unit.LayoutDirection`, `androidx.compose.ui.focus.FocusRequester`, `androidx.compose.ui.platform.LocalFocusManager`, `androidx.compose.material3.SnackbarResult`, `org.julakali.chargeahead.shared.ui.SearchViewModel`, `org.julakali.chargeahead.shared.domain.Destination`.

`private enum class Sheet { NONE, CHARGE_NOW, ROUTES }` (drop `PLAN`).

- [ ] **Step 2: Shell state inside PhoneApp**

After `val planned = tripUi as? TripUiState.Planned` add:

```kotlin
    val searchViewModel: SearchViewModel = koinViewModel()
    val searchUi by searchViewModel.uiState.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    fun closeSearch() {
        searching = false
        focusManager.clearFocus()
        searchViewModel.onClosed()
    }

    val mode = when {
        searching -> HomeMode.SEARCHING
        planned != null -> HomeMode.TRIP
        else -> HomeMode.BROWSING
    }

    val routeOverlay = remember(planned?.plan) {
        planned?.plan?.let { plan ->
            RouteOverlay(
                points = plan.route.points,
                stops = plan.stops.mapIndexed { index, stop -> (index + 1) to stop.site.position },
                destination = plan.destination.position,
            )
        }
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
```

- [ ] **Step 3: Events**

In `LaunchedEffect(tripEvent)`: `TripEvent.PlanReady -> { closeSearch(); scope.launch { sheetState.partialExpand() } }`. `TripEvent.VehicleMissing ->` becomes:
```kotlin
            TripEvent.VehicleMissing -> scope.launch {
                val result = snackbar.showSnackbar(
                    message = context.getString(R.string.plan_vehicle_missing),
                    actionLabel = context.getString(R.string.plan_vehicle_missing_action),
                )
                if (result == SnackbarResult.ActionPerformed) openFromRoot(Garage)
            }
```
Picking a row without a vehicle must not even start planning, so the pick handler checks `searchUi.hasVehicle` first and fires the same snackbar (factor it into a local `fun vehicleMissing()` used by both).

- [ ] **Step 4: Right-hand drawer and the scaffold body**

Wrap the existing `ModalNavigationDrawer` call:

```kotlin
    // Material's drawer only knows the start edge; in RTL that edge is the right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                        DrawerContent(uiState = drawerUi, onOpen = { target -> openFromRoot(target) }, onFilters = drawerViewModel::onFiltersChanged)
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = if (planned != null) TRIP_PEEK_HEIGHT else 0.dp,
                    sheetSwipeEnabled = planned != null,
                    sheetContainerColor = MaterialTheme.colorScheme.surface,
                    snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
                    sheetContent = {
                        val trip = planned
                        if (trip != null) {
                            Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                                TripSummary(trip.plan, onReplan = {
                                    searchViewModel.onOpened(trip.plan.destination)
                                    searching = true
                                    focusRequester.requestFocus()
                                })
                                TripSheetContent(
                                    plan = trip.plan,
                                    startPosition = trip.startPosition,
                                    startSocPercent = trip.startSocPercent,
                                    isSaved = trip.isSaved,
                                    selection = trip.selection,
                                    socInput = trip.socInput,
                                    arrivalSocInput = trip.arrivalSocInput,
                                    onToggleSelecting = tripViewModel::onSectionSelectingToggled,
                                    onPickPoint = tripViewModel::onSectionPointPicked,
                                    onSectionSent = tripViewModel::onSectionSent,
                                    onSendToMaps = ::sendToMaps,
                                    onToggleSave = { tripViewModel.toggleSaved(trip.plan.summaryLine(context)) },
                                    onReplan = { /* handled by the summary above */ },
                                    onEditStartSoc = tripViewModel::onStartSocEditRequested,
                                    onSocInputChange = tripViewModel::onStartSocInputChanged,
                                    onSocConfirm = tripViewModel::onStartSocConfirmed,
                                    onSocDismiss = tripViewModel::onStartSocEditDismissed,
                                    onEditArrivalSoc = tripViewModel::onArrivalSocEditRequested,
                                    onArrivalSocInputChange = tripViewModel::onArrivalSocInputChanged,
                                    onArrivalSocConfirm = tripViewModel::onArrivalSocConfirmed,
                                    onArrivalSocDismiss = tripViewModel::onArrivalSocEditDismissed,
                                    modifier = Modifier.weight(1f).navigationBarsPadding(),
                                )
                            }
                        }
                    },
                ) { _ ->
                    HomeRoute(
                        hasPermission = hasPermission,
                        planningInProgress = tripUi is TripUiState.Planning,
                        mode = mode,
                        route = routeOverlay,
                        onRequestPermission = ::requestLocationPermission,
                        onLocate = ::onLocate,
                        onSettings = { scope.launch { drawerState.open() } },
                        onChargeNow = { sheet = Sheet.CHARGE_NOW; chargeNowViewModel.onSheetOpened() },
                        onRoutes = { sheet = Sheet.ROUTES },
                        topBar = {
                            val trip = planned
                            if (trip != null && !searching) {
                                DestinationHeader(
                                    title = ChargeStopFormatter.label(trip.plan.destination),
                                    subtitle = pluralStringResource(R.plurals.trip_topbar_sub, trip.plan.stops.size, trip.plan.stops.size),
                                    onClear = tripViewModel::clear,
                                )
                            } else {
                                HomeSearchBar(
                                    query = searchUi.query,
                                    searching = searchUi.searching,
                                    onFocused = { searching = true },
                                    onQueryChange = searchViewModel::onQueryChanged,
                                    onClear = { if (searchUi.query.isEmpty()) closeSearch() else searchViewModel.onQueryChanged("") },
                                    focusRequester = focusRequester,
                                )
                            }
                        },
                        topPanel = {
                            SearchResultsPanel(
                                uiState = searchUi,
                                onPick = { row ->
                                    if (!searchUi.hasVehicle) {
                                        vehicleMissing()
                                    } else {
                                        closeSearch()
                                        tripViewModel.plan(row.destination)
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
```

`TripSheetContent`'s `onReplan` parameter is now unused by the sheet since `TripSummary` is rendered separately; remove the parameter from `TripSheetContent` in `TripSheet.kt` and drop the line here.

The zoom hint / pills already hide in `HomeScreen` when `mode != BROWSING`.

- [ ] **Step 5: Remove Trip/StopDetail entries and the PLAN sheet**

Delete the `entry<Trip>` and `entry<StopDetail>` blocks in the `entryProvider`. Delete the `Sheet.PLAN ->` branch. Remove `planSheetViewModel` and the `sendToMaps`-unrelated leftovers (`TripPlan.summaryLine` stays).

- [ ] **Step 6: Back handling**

Replace the final `BackHandler`:

```kotlin
    val sheetExpanded = planned != null && sheetState.currentValue == SheetValue.Expanded
    BackHandler(
        enabled = sheet != Sheet.NONE || searching || sheetExpanded || planned != null ||
            (drawerState.isOpen && backStack.size == 1),
    ) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            drawerState.isOpen -> scope.launch { drawerState.close() }
            searching -> closeSearch()
            sheetExpanded -> scope.launch { sheetState.partialExpand() }
            planned != null -> tripViewModel.clear()
        }
    }
```

Note: `BackHandler` inside `PhoneApp` sits below `NavDisplay`'s own back handling only when `backStack.size == 1`; keep the `drawerState.isOpen && backStack.size == 1` guard, and guard `searching`/trip branches the same way (`backStack.size == 1`), otherwise a page's back would clear the trip underneath. Compute `val atRoot = backStack.size == 1` and AND it into `enabled` for every branch except `sheet != Sheet.NONE`.

- [ ] **Step 7: Build**

Run: `./gradlew --console=plain --no-scan :androidApp:assembleDebug > "$SCRATCH/t7.log" 2>&1; grep -E "BUILD|e: |w: .*unused" "$SCRATCH/t7.log" | head -20`
Expected: `BUILD SUCCESSFUL`. Fix any leftover unused imports the compiler flags.

- [ ] **Step 8: Commit**

```bash
git add -A androidApp/src/main
git commit -m "feat: one-screen shell: right drawer, inline search, trip as sheet"
```

---

### Task 8: Cleanup, docs, full verification

**Files:**
- Modify: `plans/todo.md`, `ROADMAP.md` (only if they mention the plan sheet / trip page as current UI — grep `Plan sheet|PlanSheet|TripPlanScreen|Planen`)
- Modify: `ARCHITECTURE.md` (same grep; update the phone-UI paragraph to: map is the single screen, search bar plans directly, trip is a bottom sheet)
- Modify: `docs/superpowers/specs/2026-09-21-maps-style-home-design.md` — no change unless implementation deviated; if it did, record the deviation in one line.

- [ ] **Step 1: Dead code sweep**

```bash
grep -rn "PlanSheet\|TripPlanScreen\|TripGoogleMap\|StopDetail\|home_pill_plan\|onMenu\b" androidApp/src shared/src docs plans ARCHITECTURE.md ROADMAP.md AGENTS.md | grep -v "docs/superpowers"
```
Fix every Kotlin hit; update every prose hit that describes current behaviour (leave historical spec/plan docs alone).

- [ ] **Step 2: Full build and tests**

```bash
./gradlew --console=plain --no-scan :androidApp:assembleDebug :shared:jvmTest :shared:compileKotlinIosSimulatorArm64 > "$SCRATCH/t8.log" 2>&1
grep -E "BUILD|FAILED|e: " "$SCRATCH/t8.log" | head
```
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: sweep the plan sheet and trip page out of the docs"
```

- [ ] **Step 4: Hand over for device verification**

Report to Raphael the on-device checklist (he runs it): bar tap → keyboard + recents; typing → hits with km; pick → spinner → header + sheet peek + route on map; expand sheet → stops, SoC chips, section picker, save; X → browsing restored, pills back; settings icon → drawer from the right; compass appears after rotating the map; zoom out → hint above pills; Favoriten → load → trip state.
