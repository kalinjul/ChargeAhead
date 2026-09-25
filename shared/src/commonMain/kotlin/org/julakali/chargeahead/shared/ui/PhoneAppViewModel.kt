package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

enum class PhoneAppSheet { NONE, CHARGE_NOW, ROUTES }

enum class TripListLayout { LIST, TILES }

data class PhoneAppUiState(
    /** The search bar has focus and the results panel is open. */
    val searching: Boolean = false,
    val sheet: PhoneAppSheet = PhoneAppSheet.NONE,
    val tripLayout: TripListLayout = TripListLayout.LIST,
    /** `null` until the platform has been asked once. */
    val hasLocationPermission: Boolean? = null,
) {
    // Tiles fit the peek and never expand.
    val tripExpandable: Boolean get() = tripLayout == TripListLayout.LIST
}

/** What the platform has to do next in the location handshake. */
sealed interface PhoneAppEvent {

    data object RequestLocationPermission : PhoneAppEvent

    data object CheckLocationSettings : PhoneAppEvent
}

/** Everything around the map: search mode, open sheet, trip layout, location handshake. */
class PhoneAppViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    private val state = MutableStateFlow(savedState.restoredUiState())
    private val events = MutableStateFlow<PhoneAppEvent?>(null)

    // Set when the location button had to ask for the permission first.
    private var locateAfterPermission = false

    val uiState: StateFlow<PhoneAppUiState> = state.asStateFlow()

    val event: StateFlow<PhoneAppEvent?> = events.asStateFlow()

    /** The grant can change outside the app, so the platform reports it on every resume. */
    fun onLocationPermissionChecked(granted: Boolean) {
        update { it.copy(hasLocationPermission = granted) }
    }

    fun onLocationPermissionRequested() {
        events.value = PhoneAppEvent.RequestLocationPermission
    }

    fun onLocationPermissionResult(granted: Boolean) {
        update { it.copy(hasLocationPermission = granted) }
        if (granted && locateAfterPermission) events.value = PhoneAppEvent.CheckLocationSettings
        locateAfterPermission = false
    }

    /** The location button: permission first, then the device settings. */
    fun onLocateRequested() {
        if (state.value.hasLocationPermission == true) {
            events.value = PhoneAppEvent.CheckLocationSettings
        } else {
            locateAfterPermission = true
            events.value = PhoneAppEvent.RequestLocationPermission
        }
    }

    fun onSearchOpened() {
        update { it.copy(searching = true) }
    }

    fun onSearchClosed() {
        update { it.copy(searching = false) }
    }

    fun onSheetOpened(sheet: PhoneAppSheet) {
        update { it.copy(sheet = sheet) }
    }

    fun onSheetDismissed() {
        update { it.copy(sheet = PhoneAppSheet.NONE) }
    }

    fun onTripLayoutChanged(layout: TripListLayout) {
        update { it.copy(tripLayout = layout) }
    }

    fun onEventHandled() {
        events.value = null
    }

    private fun update(transform: (PhoneAppUiState) -> PhoneAppUiState) {
        savedState.store(state.updateAndGet(transform))
    }
}

private const val KEY_SEARCHING = "searching"
private const val KEY_SHEET = "sheet"
private const val KEY_TRIP_LAYOUT = "tripLayout"

// The location grant stays out: the platform re-checks it on every resume,
// and a stale "granted" would skip the check.
private fun SavedStateHandle.restoredUiState() = PhoneAppUiState(
    searching = get<Boolean>(KEY_SEARCHING) ?: false,
    sheet = enum(KEY_SHEET, PhoneAppSheet.NONE),
    tripLayout = enum(KEY_TRIP_LAYOUT, TripListLayout.LIST),
)

private fun SavedStateHandle.store(state: PhoneAppUiState) {
    this[KEY_SEARCHING] = state.searching
    this[KEY_SHEET] = state.sheet.name
    this[KEY_TRIP_LAYOUT] = state.tripLayout.name
}

// Enums travel as their name: the multiplatform SavedState only carries
// primitives, and an unknown name falls back instead of throwing.
private inline fun <reified T : Enum<T>> SavedStateHandle.enum(key: String, default: T): T =
    get<String>(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
