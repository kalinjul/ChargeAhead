package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.MapCharger
import org.julakali.chargeahead.shared.PlanningFeature
import org.julakali.chargeahead.shared.data.SiteFetchActivity
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.distanceKmTo
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

/** The map screen. */
data class HomeUiState(
    val position: LatLon? = null,
    /** The corridor list. */
    val stops: List<ChargeStop> = emptyList(),
    val isDemo: Boolean = false,
    val chargers: List<MapCharger> = emptyList(),
    /** Zoomed out too far for markers. */
    val belowMinZoom: Boolean = false,
    /** The charger the driver tapped, as a detail dialog; `null` = no dialog. */
    val selectedStop: ChargeStop? = null,
    val filtersCustomized: Boolean = false,
    /** A filter/network change is re-querying the map. */
    val applyingFilters: Boolean = false,
    /** Location is running but has not delivered a position yet. */
    val searchingLocation: Boolean = false,
    /** Long enough without a fix to tell the driver; stays alongside [searchingLocation]. */
    val locationUnavailable: Boolean = false,
    /** A charger source is being asked over the network. */
    val loadingSites: Boolean = false,
)

/**
 * State holder for the map screen. The location pipeline itself lives in the
 * app-scoped [ChargeStopsFeature].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
    settings: SettingsStore,
    fetchActivity: SiteFetchActivity,
    /** How long the button may spin before the map says something. */
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
            // Gated on the position, so a late fix clears both.
            searchingLocation = attempt.running && state.position == null,
            locationUnavailable = attempt.timedOut && state.position == null,
            loadingSites = loadingSites,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState())

    /** Just the "applying filters" flag, so the drawer doesn't recompose on every map change. */
    val applyingFilters: StateFlow<Boolean> =
        uiState.map { it.applyingFilters }.stateIn(viewModelScope, WhileUiSubscribed, false)

    init {
        // One pipeline drives both the markers and the "applying filters" flag,
        // so the flag can't race itself. Only the filters the map applies are keyed on.
        var lastFilterSig: Pair<NetworkPreferences, Pair<Double, Boolean>>? = null
        combine(
            viewport,
            settings.networks,
            settings.chargeFilters.map { it.minPowerKw to it.slowMode }.distinctUntilChanged(),
        ) { viewport, networks, filterKey -> Triple(viewport, networks, filterKey) }
            // mapLatest cancels a superseded query.
            .mapLatest { (viewport, networks, filterKey) ->
                val sig = networks to filterKey
                val filterChanged = lastFilterSig != null && sig != lastFilterSig
                lastFilterSig = sig
                if (filterChanged) map.update { it.copy(applyingFilters = true) }
                val chargers = withContext(Dispatchers.Default) {
                    viewport?.let { planning.chargersIn(it) }.orEmpty()
                }
                map.update { it.copy(chargers = chargers, applyingFilters = false) }
                // Markers first, live data after: the status request must not hold them back.
                if (chargers.any { it.site.liveStatusId != null }) {
                    val withStatus = planning.withAvailability(chargers)
                    map.update { it.copy(chargers = withStatus) }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Location permission granted: the pipeline may run. Calling it twice is harmless. */
    fun onLocationPermissionGranted() {
        feature.start()
        beginLocating()
    }

    /** The location button was tapped with no position to center on. */
    fun onLocateRequested() {
        feature.start()
        feature.locate()
        beginLocating()
    }

    /** Marks an attempt as under way and gives it a deadline. A running attempt is left alone. */
    private fun beginLocating() {
        if (attemptJob?.isActive == true) return
        attempt.value = LocationAttempt(running = true)
        attemptJob = viewModelScope.launch {
            val position = withTimeoutOrNull(locationTimeoutMillis) {
                feature.state.first { it.position != null }
            }
            if (position == null) attempt.update { it.copy(timedOut = true) }
        }
    }

    /** `null` means: zoomed out past the point where markers are useful. */
    fun onViewportChanged(viewport: BoundingBox?) {
        // Set before the query runs.
        map.update { it.copy(belowMinZoom = viewport == null) }
        this.viewport.value = viewport
    }

    /** A tapped map marker becomes the same detail dialog the corridor list uses. */
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
