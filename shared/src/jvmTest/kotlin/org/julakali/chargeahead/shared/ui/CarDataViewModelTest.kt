package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.settings.DataStoreCarDiagnosticsRepository
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The debug page: what the car delivered, and the charge level the planner starts from. */
class CarDataViewModelTest {

    private val main = TestMain()

    private val diagnostics = DataStoreCarDiagnosticsRepository(InMemoryPreferencesDataStore())
    private val vehicles = DataStoreVehicleRepository(InMemoryPreferencesDataStore())
    private val feature = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override suspend fun currentFix(): Fix? = null
            override val updates: Flow<Fix> = emptyFlow()
        },
        parentScope = CoroutineScope(Dispatchers.Unconfined),
    )

    @BeforeTest
    fun setUpMainDispatcher() = main.setUp()

    @AfterTest
    fun tearDownMainDispatcher() = main.tearDown()

    @Test
    fun `the stored charge level and the last hardware check show up`() = runBlocking<Unit> {
        val check = SoCDiagnostics(checkedAtMillis = 1_000, outcome = SoCDiagnostics.Outcome.NO_CAR_HARDWARE)
        diagnostics.recordSoCDiagnostics(check)
        vehicles.setManualSocPercent(64.0)

        val state = withTimeout(5_000) {
            main.track(CarDataViewModel(diagnostics, vehicles, feature)).uiState.first { it.socPercent != null && it.socDiagnostics != null }
        }

        assertEquals(64.0, state.socPercent)
        assertFalse(state.socFromCar)
        assertEquals(check, state.socDiagnostics)
    }
}
