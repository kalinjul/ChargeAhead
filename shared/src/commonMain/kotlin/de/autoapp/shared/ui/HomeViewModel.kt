package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.MapCharger
import de.autoapp.shared.PlanningFeature
import de.autoapp.shared.data.SiteFetchActivity
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The map screen: where the driver is, which chargers are in view, and
 * whether a filter is narrowing what they see.
 */
data class HomeUiState(
    val position: LatLon? = null,
    /** The corridor list — the placeholder map draws its pins from it. */
    val stops: List<ChargeStop> = emptyList(),
    val isDemo: Boolean = false,
    val chargers: List<MapCharger> = emptyList(),
    /** Zoomed out too far for markers; the map says so instead of showing nothing. */
    val belowMinZoom: Boolean = false,
    /** The charger the driver tapped, as a detail dialog; `null` = no dialog. */
    val selectedStop: ChargeStop? = null,
    val filtersCustomized: Boolean = false,
    /** A filter/network change is re-querying the map — the overlay says so. */
    val applyingFilters: Boolean = false,
    /**
     * Location is running but has not delivered a position yet — the
     * location button spins instead of looking idle.
     */
    val searchingLocation: Boolean = false,
    /**
     * Long enough without a fix that a spinner alone is no longer honest.
     * Stays alongside [searchingLocation]: the request really is still
     * running, it just isn't getting anywhere, and saying so is the point.
     */
    val locationUnavailable: Boolean = false,
    /** A charger source is being asked over the network — a spinner under the compass. */
    val loadingSites: Boolean = false,
)

/**
 * State holder for the map screen.
 *
 * The location pipeline itself lives in [ChargeStopsFeature] and is
 * app-scoped — this ViewModel only starts it once the permission is there
 * and projects its state. Everything the map adds on top (the markers for
 * the current viewport, the tapped charger) is owned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
    settings: SettingsStore,
    fetchActivity: SiteFetchActivity,
    /**
     * How long the button may spin before the map says something.
     *
     * A GPS cold start under open sky can legitimately take this long, so the
     * hint is worded as advice, not as a verdict — and it disappears by itself
     * the moment a fix lands. A parameter so the timeout path is testable
     * without a twenty-second test.
     */
    private val locationTimeoutMillis: Long = DEFAULT_LOCATION_TIMEOUT_MILLIS,
) : ViewModel() {

    private val map = MutableStateFlow(MapState())

    private val attempt = MutableStateFlow(LocationAttempt())
    private var attemptJob: Job? = null

    /** The section of the world the map shows; `null` = zoomed out past the marker threshold. */
    private val viewport = MutableStateFlow<BoundingBox?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        feature.state,
        settings.chargeFilters,
        settings.networks,
        map,
        // combine tops out at five typed flows.
        combine(attempt, fetchActivity.isFetching, ::Pair),
    ) { state, filters, networks, mapState, (attempt, loadingSites) ->
        HomeUiState(
            position = state.position,
            stops = state.stops,
            isDemo = state.isDemo,
            chargers = mapState.chargers,
            belowMinZoom = mapState.belowMinZoom,
            selectedStop = mapState.selectedStop,
            filtersCustomized = !filters.isDefault || networks.isActive,
            applyingFilters = mapState.applyingFilters,
            // Both are gated on the position rather than being cleared by
            // hand: a fix that arrives late — after the timeout, from the
            // stream — silences spinner and hint on its own.
            searchingLocation = attempt.running && state.position == null,
            locationUnavailable = attempt.timedOut && state.position == null,
            loadingSites = loadingSites,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState())

    /**
     * Just the "applying filters" flag. The drawer reads only this, so an
     * unrelated map-state change (new markers, a position update) doesn't
     * recompose the whole drawer.
     */
    val applyingFilters: StateFlow<Boolean> =
        uiState.map { it.applyingFilters }.stateIn(viewModelScope, WhileUiSubscribed, false)

    init {
        // One pipeline drives both the markers and the "applying filters" flag,
        // so the flag can't race itself: a filter/network change (not a pan)
        // marks applying = true, the query runs off the main thread, and the
        // true always precedes the false because they share one ordered flow.
        // Minimum power is picked out of the filters on purpose — price and
        // distance are ranking concerns the map doesn't apply, and the drawer's
        // sliders would otherwise fire a query per pixel dragged.
        var lastFilterSig: Pair<NetworkPreferences, Pair<Double, Boolean>>? = null
        combine(
            viewport,
            settings.networks,
            settings.chargeFilters.map { it.minPowerKw to it.slowMode }.distinctUntilChanged(),
        ) { viewport, networks, filterKey -> Triple(viewport, networks, filterKey) }
            // Panning fires viewport changes far faster than the query answers;
            // mapLatest cancels a superseded query so late markers from another
            // viewport can't land on the current one.
            .mapLatest { (viewport, networks, filterKey) ->
                val sig = networks to filterKey
                val filterChanged = lastFilterSig != null && sig != lastFilterSig
                lastFilterSig = sig
                if (filterChanged) map.update { it.copy(applyingFilters = true) }
                // Room read + filter + sort off the main thread — a filter
                // toggle (AC mode especially) mustn't freeze the UI.
                val chargers = withContext(Dispatchers.Default) {
                    viewport?.let { planning.chargersIn(it) }.orEmpty()
                }
                map.update { it.copy(chargers = chargers, applyingFilters = false) }
            }
            .launchIn(viewModelScope)
    }

    /** Location permission granted: the pipeline may run. Calling it twice is harmless. */
    fun onLocationPermissionGranted() {
        feature.start()
        beginLocating()
    }

    /**
     * The location button was tapped with no position to center on. Starting
     * the pipeline again is deliberate and harmless: if it never came up —
     * because the permission arrived late, or the car surface had claimed the
     * location stream — this is the tap that fixes it. [ChargeStopsFeature.locate]
     * then asks for a fix now instead of waiting out the stream.
     */
    fun onLocateRequested() {
        feature.start()
        feature.locate()
        beginLocating()
    }

    /**
     * Marks an attempt as under way and gives it a deadline. A running
     * attempt is left alone — the deadline belongs to the first tap, not to
     * every repeat of it.
     */
    private fun beginLocating() {
        if (attemptJob?.isActive == true) return
        attempt.value = LocationAttempt(running = true)
        attemptJob = viewModelScope.launch {
            val position = withTimeoutOrNull(locationTimeoutMillis) {
                feature.state.first { it.position != null }
            }
            // The request stays subscribed either way; only the hint changes.
            if (position == null) attempt.update { it.copy(timedOut = true) }
        }
    }

    /** `null` means: zoomed out past the point where markers are useful. */
    fun onViewportChanged(viewport: BoundingBox?) {
        // Set before the query runs, not after: otherwise the zoom hint stays
        // up for the whole request although the map is long since close enough.
        map.update { it.copy(belowMinZoom = viewport == null) }
        this.viewport.value = viewport
    }

    /**
     * A tapped map marker becomes the same detail dialog the corridor list
     * uses. Reachability is honestly [Reachability.UNKNOWN]: the map shows
     * what is there, it does not rate it against the remaining range.
     */
    fun onChargerSelected(charger: MapCharger) {
        val position = feature.currentState.position
        map.update {
            it.copy(
                selectedStop = ChargeStop(
                    site = charger.site,
                    distanceKm = position?.distanceKmTo(charger.site.position) ?: 0.0,
                    reachability = Reachability.UNKNOWN,
                    socOnArrivalPercent = null,
                ),
            )
        }
    }

    fun onSelectedStopDismissed() {
        map.update { it.copy(selectedStop = null) }
    }

    private data class LocationAttempt(
        val running: Boolean = false,
        val timedOut: Boolean = false,
    )

    private data class MapState(
        val chargers: List<MapCharger> = emptyList(),
        val belowMinZoom: Boolean = false,
        val selectedStop: ChargeStop? = null,
        val applyingFilters: Boolean = false,
    )

    companion object {
        const val DEFAULT_LOCATION_TIMEOUT_MILLIS = 20_000L
    }
}
