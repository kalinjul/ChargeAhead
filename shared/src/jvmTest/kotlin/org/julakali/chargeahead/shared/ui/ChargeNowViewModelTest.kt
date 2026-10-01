package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.BoundingBox
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.MapFilter
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.usecases.ChargeNowObserver
import org.julakali.chargeahead.shared.domain.usecases.RefreshChargeNowInteractor
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.testDispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChargeNowViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDownMainDispatcher() = Dispatchers.resetMain()

    private val here = LatLon(48.0, 11.0)
    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val fixes = MutableSharedFlow<Fix>(replay = 1)
    private val feature = ChargeStopsFeature(
        locationSource = object : LocationSource { override val updates: Flow<Fix> = fixes },
        parentScope = CoroutineScope(Dispatchers.Unconfined),
    ).apply { start() }
    private val nearby = ChargeSite(
        id = "near",
        name = "near",
        operator = "Ionity",
        position = LatLon(here.lat + 0.01, here.lon),
        connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
    )
    private val store = MutableStateFlow<List<ChargeSite>>(emptyList())
    private val repository = object : SiteRepository {
        override suspend fun load(area: SearchArea, networkKeys: Set<String>): List<ChargeSite> {
            store.update { listOf(nearby) }
            return listOf(nearby)
        }

        override fun storedSitesIn(box: BoundingBox, filter: MapFilter): Flow<List<ChargeSite>> = store
    }

    private fun viewModel() = ChargeNowViewModel(
        feature,
        ChargeNowObserver(repository, preferences, testDispatchers),
        RefreshChargeNowInteractor(repository, preferences),
    )

    /** The modal sheet takes its height from the first frame: a tall skeleton, never a one-liner. */
    @Test
    fun `the sheet starts on the skeleton, not on no position`() {
        assertEquals(ChargeNowUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `opening the sheet ranks what is nearby from the current fix`() = runBlocking<Unit> {
        fixes.emit(Fix(here, null, null, 0L))
        val viewModel = viewModel()

        viewModel.onSheetOpened()

        val ready = withTimeout(5_000) { viewModel.uiState.first { it is ChargeNowUiState.Ready && !it.result.isEmpty } }
        assertEquals(listOf("near"), (ready as ChargeNowUiState.Ready).result.candidates.map { it.site.id })
    }
}
