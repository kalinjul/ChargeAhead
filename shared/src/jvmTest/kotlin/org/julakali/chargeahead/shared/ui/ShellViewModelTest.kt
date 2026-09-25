package org.julakali.chargeahead.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShellViewModelTest {

    private val viewModel = ShellViewModel()

    @Test
    fun `locating without the permission asks for it, then checks the settings once granted`() {
        viewModel.onLocationPermissionChecked(granted = false)

        viewModel.onLocateRequested()
        assertEquals(ShellEvent.RequestLocationPermission, viewModel.event.value)
        viewModel.onEventHandled()

        viewModel.onLocationPermissionResult(granted = true)
        assertEquals(ShellEvent.CheckLocationSettings, viewModel.event.value)
        assertEquals(true, viewModel.uiState.value.hasLocationPermission)
    }

    @Test
    fun `locating with the permission goes straight to the settings check`() {
        viewModel.onLocationPermissionChecked(granted = true)

        viewModel.onLocateRequested()

        assertEquals(ShellEvent.CheckLocationSettings, viewModel.event.value)
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
        viewModel.onSheetOpened(ShellSheet.ROUTES)
        assertEquals(ShellSheet.ROUTES, viewModel.uiState.value.sheet)

        viewModel.onSheetDismissed()
        assertEquals(ShellSheet.NONE, viewModel.uiState.value.sheet)
    }
}
