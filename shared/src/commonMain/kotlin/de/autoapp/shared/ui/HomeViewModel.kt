package de.autoapp.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.MapCharger
import de.autoapp.shared.PlanningFeature
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
) : ViewModel() {

    private val map = MutableStateFlow(MapState())

    /** The section of the world the map shows; `null` = zoomed out past the marker threshold. */
    private val viewport = MutableStateFlow<BoundingBox?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        feature.state,
        settings.chargeFilters,
        settings.networks,
        map,
    ) { state, filters, networks, mapState ->
        HomeUiState(
            position = state.position,
            stops = state.stops,
            isDemo = state.isDemo,
            chargers = mapState.chargers,
            belowMinZoom = mapState.belowMinZoom,
            selectedStop = mapState.selectedStop,
            filtersCustomized = !filters.isDefault || networks.isActive,
            applyingFilters = mapState.applyingFilters,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, HomeUiState())

    init {
        // The marker query reads the filters, so the query has to re-run when
        // they change — not only when the driver happens to pan. The minimum
        // power is picked out of the filters on purpose: price and distance
        // are ranking concerns that the map does not apply, and the drawer's
        // sliders would otherwise fire a query per pixel dragged.
        combine(
            viewport,
            settings.chargeFilters.map { it.minPowerKw to it.slowMode }.distinctUntilChanged(),
            settings.networks,
        ) { viewport, _, _ -> viewport }
            // Panning fires viewport changes far faster than the query
            // answers. Without cancelling the previous one, a slow early
            // response can land after a fast later one and put markers from a
            // different part of the country on the map.
            .mapLatest { viewport -> viewport?.let { planning.chargersIn(it) }.orEmpty() }
            .onEach { chargers -> map.update { it.copy(chargers = chargers, applyingFilters = false) } }
            .launchIn(viewModelScope)

        // A filter or network change re-queries the map, which can take a
        // while; flag it so the map shows "applying filters" instead of just
        // appearing to hang. Panning is not a filter change, so it stays out.
        combine(
            settings.networks,
            settings.chargeFilters.map { it.minPowerKw to it.slowMode }.distinctUntilChanged(),
        ) { _, _ -> }
            .drop(1) // the first combination is the initial load, not a change
            .onEach { map.update { it.copy(applyingFilters = true) } }
            .launchIn(viewModelScope)
    }

    /** Location permission granted: the pipeline may run. Calling it twice is harmless. */
    fun onLocationPermissionGranted() {
        feature.start()
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

    private data class MapState(
        val chargers: List<MapCharger> = emptyList(),
        val belowMinZoom: Boolean = false,
        val selectedStop: ChargeStop? = null,
        val applyingFilters: Boolean = false,
    )
}
