package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ShellSheet { NONE, CHARGE_NOW, ROUTES }

enum class TripListLayout { LIST, TILES }

data class ShellUiState(
    /** The search bar has focus and the results panel is open. */
    val searching: Boolean = false,
    val sheet: ShellSheet = ShellSheet.NONE,
    val tripLayout: TripListLayout = TripListLayout.LIST,
    /** `null` until the platform has been asked once. */
    val hasLocationPermission: Boolean? = null,
) {
    // Tiles fit the peek and never expand.
    val tripExpandable: Boolean get() = tripLayout == TripListLayout.LIST
}

/** What the platform has to do next in the location handshake. */
sealed interface ShellEvent {

    data object RequestLocationPermission : ShellEvent

    data object CheckLocationSettings : ShellEvent
}

/** The phone shell around the map: search mode, open sheet, trip layout, location handshake. */
class ShellViewModel : ViewModel() {

    private val state = MutableStateFlow(ShellUiState())
    private val events = MutableStateFlow<ShellEvent?>(null)

    // Set when the location button had to ask for the permission first.
    private var locateAfterPermission = false

    val uiState: StateFlow<ShellUiState> = state.asStateFlow()

    val event: StateFlow<ShellEvent?> = events.asStateFlow()

    /** The grant can change outside the app, so the platform reports it on every resume. */
    fun onLocationPermissionChecked(granted: Boolean) {
        state.update { it.copy(hasLocationPermission = granted) }
    }

    fun onLocationPermissionRequested() {
        events.value = ShellEvent.RequestLocationPermission
    }

    fun onLocationPermissionResult(granted: Boolean) {
        state.update { it.copy(hasLocationPermission = granted) }
        if (granted && locateAfterPermission) events.value = ShellEvent.CheckLocationSettings
        locateAfterPermission = false
    }

    /** The location button: permission first, then the device settings. */
    fun onLocateRequested() {
        if (state.value.hasLocationPermission == true) {
            events.value = ShellEvent.CheckLocationSettings
        } else {
            locateAfterPermission = true
            events.value = ShellEvent.RequestLocationPermission
        }
    }

    fun onSearchOpened() {
        state.update { it.copy(searching = true) }
    }

    fun onSearchClosed() {
        state.update { it.copy(searching = false) }
    }

    fun onSheetOpened(sheet: ShellSheet) {
        state.update { it.copy(sheet = sheet) }
    }

    fun onSheetDismissed() {
        state.update { it.copy(sheet = ShellSheet.NONE) }
    }

    fun onTripLayoutChanged(layout: TripListLayout) {
        state.update { it.copy(tripLayout = layout) }
    }

    fun onEventHandled() {
        events.value = null
    }
}
