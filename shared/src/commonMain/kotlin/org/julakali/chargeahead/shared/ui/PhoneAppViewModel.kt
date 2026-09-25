package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TripListLayout { LIST, TILES }

data class PhoneAppUiState(
    /** The search bar has focus and the results panel is open. */
    val searching: Boolean = false,
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

/** Everything around the map: search mode, trip layout, location handshake. */
class PhoneAppViewModel : ViewModel() {

    private val state = MutableStateFlow(PhoneAppUiState())
    private val events = MutableStateFlow<PhoneAppEvent?>(null)

    // Set when the location button had to ask for the permission first.
    private var locateAfterPermission = false

    val uiState: StateFlow<PhoneAppUiState> = state.asStateFlow()

    val event: StateFlow<PhoneAppEvent?> = events.asStateFlow()

    /** The grant can change outside the app, so the platform reports it on every resume. */
    fun onLocationPermissionChecked(granted: Boolean) {
        state.update { it.copy(hasLocationPermission = granted) }
    }

    fun onLocationPermissionRequested() {
        events.value = PhoneAppEvent.RequestLocationPermission
    }

    fun onLocationPermissionResult(granted: Boolean) {
        state.update { it.copy(hasLocationPermission = granted) }
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
        state.update { it.copy(searching = true) }
    }

    fun onSearchClosed() {
        state.update { it.copy(searching = false) }
    }

    fun onTripLayoutChanged(layout: TripListLayout) {
        state.update { it.copy(tripLayout = layout) }
    }

    fun onEventHandled() {
        events.value = null
    }
}
