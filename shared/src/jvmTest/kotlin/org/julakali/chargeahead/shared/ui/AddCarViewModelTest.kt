package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.FakeVehicleCatalog
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.usecases.SelectVehicleInteractor
import org.julakali.chargeahead.shared.domain.usecases.VehiclePresetsObserver
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A car the catalog doesn't know, started from the search. */
class AddCarViewModelTest {

    private val main = TestMain()

    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val viewModel by lazy {
        main.track(AddCarViewModel(VehiclePresetsObserver(FakeVehicleCatalog(), vehicles, testDispatchers), SelectVehicleInteractor(vehicles)))
    }

    @BeforeTest
    fun setUpMainDispatcher() = main.setUp()

    @AfterTest
    fun tearDownMainDispatcher() = main.tearDown()

    @Test
    fun `an own car lands in the garage selected, under the name it was searched by`() = runBlocking<Unit> {
        viewModel.onCustomCarCreated("  Fiat 500e ")

        val car = withTimeout(5_000) { vehicles.vehicle.first { it != null } }!!
        assertEquals("Fiat 500e", car.displayName)
        assertNull(car.modelId)
        assertEquals(setOf(ConnectorType.CCS2, ConnectorType.TYPE2), car.acceptedConnectors)
        assertEquals(listOf(car.id), vehicles.vehicles.first().map { it.id })
    }
}
