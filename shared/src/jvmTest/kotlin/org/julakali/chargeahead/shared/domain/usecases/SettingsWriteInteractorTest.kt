package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** The one-write interactors the phone and car screens go through. */
class SettingsWriteInteractorTest {

    private val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 17.8,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    @Test
    fun `selecting a vehicle puts it in the garage, and null clears the selection`() = runBlocking {
        val select = SelectVehicleInteractor(settings)

        select(SelectVehicleInteractor.Params(vehicle)).getOrThrow()
        assertEquals(vehicle.displayName, settings.vehicle.first()?.displayName)
        assertEquals(listOf(vehicle.displayName), settings.vehicles.first().map { it.displayName })

        select(SelectVehicleInteractor.Params(null)).getOrThrow()
        assertNull(settings.vehicle.first())
        // Clearing the selection keeps the garage.
        assertEquals(listOf(vehicle.displayName), settings.vehicles.first().map { it.displayName })
    }

    @Test
    fun `removing a vehicle takes it out of the garage`() = runBlocking {
        SelectVehicleInteractor(settings)(SelectVehicleInteractor.Params(vehicle)).getOrThrow()

        RemoveVehicleInteractor(settings)(RemoveVehicleInteractor.Params(vehicle.displayName)).getOrThrow()

        assertEquals(emptyList(), settings.vehicles.first())
        assertNull(settings.vehicle.first())
    }

    @Test
    fun `the charge level is stored, and null clears it`() = runBlocking {
        val update = UpdateManualSocInteractor(settings)

        update(UpdateManualSocInteractor.Params(55.0)).getOrThrow()
        assertEquals(55.0, settings.manualSocPercent.first())

        update(UpdateManualSocInteractor.Params(null)).getOrThrow()
        assertNull(settings.manualSocPercent.first())
    }

    @Test
    fun `the arrival level is stored`() = runBlocking {
        UpdateArrivalSocInteractor(settings)(UpdateArrivalSocInteractor.Params(25.0)).getOrThrow()

        assertEquals(25.0, settings.arrivalSocPercent.first())
    }

    @Test
    fun `charge filters are stored`() = runBlocking {
        val filters = ChargeFilters(minPowerKw = 100.0, maxDistanceKm = 25.0)

        UpdateChargeFiltersInteractor(settings)(UpdateChargeFiltersInteractor.Params(filters)).getOrThrow()

        assertEquals(filters, settings.chargeFilters.first())
    }

    @Test
    fun `network preferences are stored`() = runBlocking {
        val preferences = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("enbw"))

        UpdateNetworksInteractor(settings)(UpdateNetworksInteractor.Params(preferences)).getOrThrow()

        assertEquals(preferences, settings.networks.first())
    }

    @Test
    fun `a saved route can be renamed and removed`() = runBlocking {
        val destination = Destination("München", LatLon(48.14, 11.58))
        SaveRouteInteractor(settings)(SaveRouteInteractor.Params(destination)).getOrThrow()
        val id = settings.savedRoutes.first().single().id

        RenameSavedRouteInteractor(settings)(RenameSavedRouteInteractor.Params(id, "Heimweg")).getOrThrow()
        assertEquals("Heimweg", settings.savedRoutes.first().single().name)

        RemoveSavedRouteInteractor(settings)(RemoveSavedRouteInteractor.Params(id)).getOrThrow()
        assertEquals(emptyList(), settings.savedRoutes.first())
    }
}
