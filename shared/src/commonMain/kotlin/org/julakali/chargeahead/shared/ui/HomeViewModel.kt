package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.ObserveLiveConnectors
import org.julakali.chargeahead.shared.domain.ObserveMapChargers
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.RefreshChargerAvailability
import org.julakali.chargeahead.shared.domain.RefreshLiveConnectors
import org.julakali.chargeahead.shared.domain.RefreshMapChargers
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.distanceKmTo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The map screen. */
data class HomeUiState(
    val position: LatLon? = null,
    val chargers: List<MapCharger> = emptyList(),
    /** Zoomed out too far for markers. */
    val belowMinZoom: Boolean = false,
    /** The charger the driver tapped, as a detail dialog; `null` = no dialog. */
    val selectedStop: ChargeStop? = null,
    /** The selected site's live charge points; `null` without live data. */
    val selectedStopLive: List<LiveConnectorGroup>? = null,
    val filtersCustomized: Boolean = false,
    /** Location is running but has not delivered a position yet. */
    val searchingLocation: Boolean = false,
    /** Long enough without a fix to tell the driver; stays alongside [searchingLocation]. */
    val locationUnavailable: Boolean = false,
    /** The map's chargers are being refilled. */
    val loadingSites: Boolean = false,
)

/**
 * State holder for the map screen. The location pipeline itself lives in the
 * app-scoped [ChargeStopsFeature].
 */
class HomeViewModel(
    private val feature: ChargeStopsFeature,
    private val observeMapChargers: ObserveMapChargers,
    private val refreshMapChargers: RefreshMapChargers,
    private val refreshChargerAvailability: RefreshChargerAvailability,
    private val observeLiveConnectors: ObserveLiveConnectors,
    private val refreshLiveConnectors: RefreshLiveConnectors,
    settings: SettingsStore,
    /** How long the button may spin before the map says something. */
    private val locationTimeoutMillis: Long = DEFAULT_LOCATION_TIMEOUT_MILLIS,
) : ViewModel() {

    private val map = MutableStateFlow(MapState())

    private val attempt = MutableStateFlow(LocationAttempt())
    private var attemptJob: Job? = null
    private var refreshJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(
        // combine tops out at five typed flows.
        feature.currentFix,
        combine(settings.chargeFilters, settings.networks, ::Pair),
        combine(map, observeLiveConnectors.flow, ::Pair),
        observeMapChargers.flow,
        combine(attempt, refreshMapChargers.inProgress, ::Pair),
    ) { fix, (filters, networks), (mapState, selectedStopLive), mapChargers, (attempt, loadingSites) ->
        val position = fix?.position
        HomeUiState(
            position = position,
            chargers = mapChargers,
            belowMinZoom = mapState.belowMinZoom,
            selectedStop = mapState.selectedStop,
            selectedStopLive = selectedStopLive.takeIf { mapState.selectedStop?.site?.liveStatusId != null },
            filtersCustomized = !filters.isDefault || networks.isActive,
            // Gated on the position, so a late fix clears both.
            searchingLocation = attempt.running && position == null,
            locationUnavailable = attempt.timedOut && position == null,
            loadingSites = loadingSites,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState())

    init {
        observeMapChargers(ObserveMapChargers.Params(viewport = null))
        observeLiveConnectors(ObserveLiveConnectors.Params(liveStatusId = null))
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
                feature.currentFix.first { it != null }
            }
            if (position == null) attempt.update { it.copy(timedOut = true) }
        }
    }

    /** `null` means: zoomed out past the point where markers are useful. */
    fun onViewportChanged(viewport: BoundingBox?) {
        map.update { it.copy(belowMinZoom = viewport == null) }
        observeMapChargers(ObserveMapChargers.Params(viewport))
        // Latest viewport wins: a pan supersedes the fetch for the previous one.
        refreshJob?.cancel()
        // A failed refill leaves the stored markers and statuses; nothing to tell the driver.
        refreshJob = viewport?.let {
            viewModelScope.launch {
                // Stored sites get their status right away, the ones the refill adds after it.
                launch { refreshChargerAvailability(RefreshChargerAvailability.Params(it)) }
                refreshMapChargers(RefreshMapChargers.Params(it))
                refreshChargerAvailability(RefreshChargerAvailability.Params(it))
            }
        }
    }

    /** A tapped map marker becomes the same detail dialog the corridor list uses. */
    fun onChargerSelected(charger: MapCharger) = onSiteSelected(charger.site)

    /** Same dialog for a planned stop, tapped in the trip list or on its route marker. */
    fun onSiteSelected(site: ChargeSite) {
        val position = feature.currentFix.value?.position
        map.update {
            it.copy(
                selectedStop = ChargeStop(
                    site = site,
                    distanceKm = position?.distanceKmTo(site.position) ?: 0.0,
                    reachability = Reachability.UNKNOWN,
                    socOnArrivalPercent = null,
                ),
            )
        }
        val liveStatusId = site.liveStatusId
        observeLiveConnectors(ObserveLiveConnectors.Params(liveStatusId))
        // The viewport's refresh may be a minute old by now; a failure keeps what is shown.
        if (liveStatusId != null) {
            viewModelScope.launch { refreshLiveConnectors(RefreshLiveConnectors.Params(liveStatusId)) }
        }
    }

    fun onSelectedStopDismissed() {
        map.update { it.copy(selectedStop = null) }
        observeLiveConnectors(ObserveLiveConnectors.Params(liveStatusId = null))
    }

    private data class LocationAttempt(
        val running: Boolean = false,
        val timedOut: Boolean = false,
    )

    private data class MapState(
        val belowMinZoom: Boolean = false,
        val selectedStop: ChargeStop? = null,
    )

    companion object {
        const val DEFAULT_LOCATION_TIMEOUT_MILLIS = 20_000L
    }
}
