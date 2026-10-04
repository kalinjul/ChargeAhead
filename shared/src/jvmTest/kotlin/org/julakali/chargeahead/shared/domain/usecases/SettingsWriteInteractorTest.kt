package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The one-write interactors the phone and car screens go through. */
class SettingsWriteInteractorTest {

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 17.8,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    @Test
    fun `selecting a vehicle puts it in the garage, and null clears the selection`() = runBlocking {
        val select = SelectVehicleInteractor(vehicles)

        select(SelectVehicleInteractor.Params(vehicle)).getOrThrow()
        assertEquals(vehicle.displayName, vehicles.vehicle.first()?.displayName)
        assertEquals(listOf(vehicle.displayName), vehicles.vehicles.first().map { it.displayName })

        select(SelectVehicleInteractor.Params(null)).getOrThrow()
        assertNull(vehicles.vehicle.first())
        // Clearing the selection keeps the garage.
        assertEquals(listOf(vehicle.displayName), vehicles.vehicles.first().map { it.displayName })
    }

    @Test
    fun `removing a vehicle takes it out of the garage`() = runBlocking {
        SelectVehicleInteractor(vehicles)(SelectVehicleInteractor.Params(vehicle)).getOrThrow()

        RemoveVehicleInteractor(vehicles)(RemoveVehicleInteractor.Params(vehicle.id)).getOrThrow()

        assertEquals(emptyList(), vehicles.vehicles.first())
        assertNull(vehicles.vehicle.first())
    }

    @Test
    fun `the charge level is stored, and null clears it`() = runBlocking {
        val update = UpdateManualSocInteractor(vehicles)

        update(UpdateManualSocInteractor.Params(55.0)).getOrThrow()
        assertEquals(55.0, vehicles.manualSocPercent.first())

        update(UpdateManualSocInteractor.Params(null)).getOrThrow()
        assertNull(vehicles.manualSocPercent.first())
    }

    @Test
    fun `the arrival level is stored`() = runBlocking {
        UpdateArrivalSocInteractor(vehicles)(UpdateArrivalSocInteractor.Params(25.0)).getOrThrow()

        assertEquals(25.0, vehicles.arrivalSocPercent.first())
    }

    @Test
    fun `charge filters are stored`() = runBlocking {
        val filters = ChargeFilters(minPowerKw = 100.0)

        UpdateChargeFiltersInteractor(preferences)(UpdateChargeFiltersInteractor.Params(filters)).getOrThrow()

        assertEquals(filters, preferences.chargeFilters.first())
    }

    @Test
    fun `network preferences are stored`() = runBlocking {
        val networks = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("enbw"))

        UpdateNetworksInteractor(preferences)(UpdateNetworksInteractor.Params(networks)).getOrThrow()

        assertEquals(networks, preferences.networks.first())
    }
}
