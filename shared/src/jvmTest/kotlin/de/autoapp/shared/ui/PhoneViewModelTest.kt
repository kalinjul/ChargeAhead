package de.autoapp.shared.ui

import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LocationSource
import de.autoapp.shared.domain.Network
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.settings.InMemoryKeyValueStorage
import de.autoapp.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ViewModels are plain Kotlin: no Android, no Compose, no test rule —
 * a store on in-memory storage and a feature on stub ports is the whole
 * setup.
 *
 * The one thing they do need is a Main dispatcher: `viewModelScope` runs on
 * it, and a plain test JVM has none. `Dispatchers.setMain(Unconfined)` gives
 * it one that also keeps writes in the order they were issued, so the
 * assertions stay deterministic.
 *
 * `uiState` is shared with `WhileUiSubscribed`, so it only produces while
 * someone collects. [await] is that collector: it subscribes, waits for the
 * first state that matches, and gives up rather than hanging forever if it
 * never comes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `subscriptions filter the catalog and survive a toggle`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = SubscriptionsViewModel(settings)

        viewModel.onQueryChanged("ionity")
        val filtered = viewModel.uiState.await { it.query == "ionity" }
        assertTrue(filtered.matches.isNotEmpty(), "expected at least one Ionity tariff")
        assertTrue(filtered.matches.all { it.displayName.contains("Ionity", ignoreCase = true) })

        val id = filtered.matches.first().id
        viewModel.onTariffToggled(id)
        assertEquals(setOf(id), viewModel.uiState.await { it.activeIds.isNotEmpty() }.activeIds)
    }

    /**
     * The regression this pattern exists for. The old screen derived its
     * fields from the stored profile, so typing the comma in "17,8" wrote
     * 17.0, which came straight back as "17" and swallowed the comma — the
     * decimal was unreachable. The form is state now, and keeps what was
     * typed.
     */
    @Test
    fun `the form keeps what was typed while the store takes what parses`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = VehicleSettingsViewModel(settings, stubFeature())

        viewModel.onNameChanged("Testwagen")
        viewModel.onBatteryChanged("77")
        viewModel.onConsumptionChanged("17,")

        val state = viewModel.uiState.await { it.name == "Testwagen" }
        assertEquals("77", state.battery)
        assertEquals("17,", state.consumption, "the comma must survive being written through")
        assertEquals(17.0, settings.vehicle.first()?.consumptionKwhPer100Km)

        viewModel.onConsumptionChanged("17,8")
        assertEquals("17,8", viewModel.uiState.await { it.consumption == "17,8" }.consumption)
        assertEquals(17.8, settings.vehicle.first()?.consumptionKwhPer100Km)
    }

    /**
     * Half a capacity must never become the number the range calculation
     * runs on — without a usable one, no profile is stored at all.
     */
    @Test
    fun `an unparseable capacity stores no profile`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val viewModel = VehicleSettingsViewModel(settings, stubFeature())

        viewModel.onNameChanged("Testwagen")
        viewModel.onConsumptionChanged("17,8")
        viewModel.onBatteryChanged("sieben")

        assertTrue(viewModel.uiState.await { it.battery == "sieben" }.batteryInvalid)
        assertNull(settings.vehicle.first())
    }

    @Test
    fun `the garage reports what the settings hold`() = runBlocking<Unit> {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        val addCar = AddCarViewModel(settings)
        val garage = GarageViewModel(settings, stubFeature())

        val preset = addCar.uiState.await { it.matches.isNotEmpty() }.matches.first()
        addCar.onPresetAdded(preset)

        val state = garage.uiState.await { it.selected != null }
        assertEquals(preset.name, state.selected?.displayName)
        assertEquals(listOf(preset.name), state.vehicles.map { it.displayName })
        // Already owned, so the add screen stops offering it.
        assertTrue(addCar.uiState.await { preset !in it.matches }.matches.none { it.name == preset.name })
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(TIMEOUT_MILLIS) { first(matching) }

    private fun stubFeature() = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override val updates: Flow<Fix> = emptyFlow()
        },
        repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = emptyList()
        },
        dispatcher = Dispatchers.Unconfined,
    )

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
