package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneAppViewModelTest {

    private val savedState = SavedStateHandle()
    private val viewModel = PhoneAppViewModel(savedState)

    @Test
    fun `locating without the permission asks for it, then checks the settings once granted`() {
        viewModel.onLocationPermissionChecked(granted = false)

        viewModel.onLocateRequested()
        assertEquals(PhoneAppEvent.RequestLocationPermission, viewModel.event.value)
        viewModel.onEventHandled()

        viewModel.onLocationPermissionResult(granted = true)
        assertEquals(PhoneAppEvent.CheckLocationSettings, viewModel.event.value)
        assertEquals(true, viewModel.uiState.value.hasLocationPermission)
    }

    @Test
    fun `locating with the permission goes straight to the settings check`() {
        viewModel.onLocationPermissionChecked(granted = true)

        viewModel.onLocateRequested()

        assertEquals(PhoneAppEvent.CheckLocationSettings, viewModel.event.value)
    }

    @Test
    fun `a grant from the banner does not start locating`() {
        viewModel.onLocationPermissionRequested()
        viewModel.onEventHandled()

        viewModel.onLocationPermissionResult(granted = true)

        assertNull(viewModel.event.value)
    }

    @Test
    fun `a denied request drops the pending locate`() {
        viewModel.onLocateRequested()
        viewModel.onEventHandled()
        viewModel.onLocationPermissionResult(granted = false)

        viewModel.onLocationPermissionResult(granted = true)

        assertNull(viewModel.event.value)
    }

    @Test
    fun `tiles are the only layout that cannot expand`() {
        viewModel.onTripLayoutChanged(TripListLayout.TILES)
        assertEquals(false, viewModel.uiState.value.tripExpandable)

        viewModel.onTripLayoutChanged(TripListLayout.LIST)
        assertEquals(true, viewModel.uiState.value.tripExpandable)
    }

    @Test
    fun `sheets open and close`() {
        viewModel.onSheetOpened(PhoneAppSheet.ROUTES)
        assertEquals(PhoneAppSheet.ROUTES, viewModel.uiState.value.sheet)

        viewModel.onSheetDismissed()
        assertEquals(PhoneAppSheet.NONE, viewModel.uiState.value.sheet)
    }

    /** #133: search mode, open sheet and trip layout come back after process death. */
    @Test
    fun `the saved state restores search mode, sheet and layout`() {
        viewModel.onSearchOpened()
        viewModel.onSheetOpened(PhoneAppSheet.CHARGE_NOW)
        viewModel.onTripLayoutChanged(TripListLayout.TILES)

        val restored = PhoneAppViewModel(savedState).uiState.value

        assertEquals(true, restored.searching)
        assertEquals(PhoneAppSheet.CHARGE_NOW, restored.sheet)
        assertEquals(TripListLayout.TILES, restored.tripLayout)
    }

    @Test
    fun `the location grant is asked for again instead of being restored`() {
        viewModel.onLocationPermissionChecked(granted = true)

        assertNull(PhoneAppViewModel(savedState).uiState.value.hasLocationPermission)
    }

    @Test
    fun `an unknown persisted name falls back to the default`() {
        val stale = SavedStateHandle(mapOf("sheet" to "GONE", "tripLayout" to "GONE"))

        val restored = PhoneAppViewModel(stale).uiState.value

        assertEquals(PhoneAppSheet.NONE, restored.sheet)
        assertEquals(TripListLayout.LIST, restored.tripLayout)
    }
}
