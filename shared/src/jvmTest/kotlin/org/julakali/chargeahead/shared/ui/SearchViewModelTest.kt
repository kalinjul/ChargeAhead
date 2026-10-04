package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.SavedStateHandle
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.usecases.DestinationSearchObserver
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.DataStoreDestinationHistory
import org.julakali.chargeahead.shared.settings.DataStoreVehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchViewModelTest {

    private val main = TestMain()

    @BeforeTest
    fun setUpMainDispatcher() = main.setUp()

    @AfterTest
    fun tearDownMainDispatcher() = main.tearDown()

    private val hamburg = Destination("Hamburg", LatLon(53.55, 9.99), "Hamburg")
    private val berlin = Place(
        name = "Berlin Hbf",
        description = "Berlin Hbf, Europaplatz 1, 10557 Berlin",
        position = LatLon(52.525, 13.369),
        address = Address(street = "Europaplatz 1", postalCode = "10557", town = "Berlin"),
    )
    private val frankfurt = LatLon(50.11, 8.68)

    /** An empty query lists what was searched before, nothing else. */
    @Test
    fun `an empty query shows recents as rows`() {
        val rows = searchRows(query = "", results = emptyList(), recent = listOf(hamburg), from = null)
        assertEquals(listOf(SearchRow(hamburg, "Hamburg", "Hamburg", distanceKm = null, recent = true)), rows)
    }

    /** Once the query is long enough the geocoder's hits replace the recents. */
    @Test
    fun `a typed query shows geocoder results with their address`() {
        val rows = searchRows(query = "Berlin", results = listOf(berlin), recent = listOf(hamburg), from = frankfurt)
        val row = rows.single()
        assertEquals("Berlin Hbf", row.title)
        assertEquals("Europaplatz 1, 10557 Berlin", row.detail)
        assertFalse(row.recent)
        assertEquals(Destination("Berlin Hbf", berlin.position, "Europaplatz 1, 10557 Berlin"), row.destination)
    }

    @Test
    fun `distance is measured from the fix and missing without one`() {
        val withFix = searchRows("", emptyList(), listOf(hamburg), from = frankfurt).single().distanceKm
        assertTrue(withFix!! in 390.0..400.0, "was $withFix")
        assertNull(searchRows("", emptyList(), listOf(hamburg), from = null).single().distanceKm)
    }

    @Test
    fun `km labels round the way the list expects`() {
        assertEquals("< 1", 0.4.asKmLabel())
        assertEquals("4,2", 4.24.asKmLabel())
        assertEquals("42", 41.6.asKmLabel())
    }

    /** "Neu planen" reopens the search on the current destination as a pick; typing discards it. */
    @Test
    fun `opening with a prefill shows it as the committed pick`() = runBlocking<Unit> {
        val viewModel = searchViewModel()
        viewModel.onOpened(hamburg)
        val opened = viewModel.uiState.await { it.query.isNotEmpty() }
        assertEquals("Hamburg, Hamburg", opened.query)
        assertEquals(listOf(hamburg), opened.rows.map { it.destination })
        viewModel.onQueryChanged("Ha")
        assertTrue(viewModel.uiState.await { it.query == "Ha" }.rows.isEmpty())
        viewModel.onClosed()
        assertEquals("", viewModel.uiState.await { it.query.isEmpty() }.query)
    }

    @Test
    fun `without a vehicle the state says so`() = runBlocking<Unit> {
        val viewModel = searchViewModel()
        assertFalse(viewModel.uiState.await { true }.hasVehicle)
    }

    @Test
    fun `opening and closing toggles the panel`() = runBlocking<Unit> {
        val viewModel = searchViewModel()
        viewModel.onOpened()
        viewModel.uiState.await { it.expanded }
        viewModel.onClosed()
        viewModel.uiState.await { !it.expanded }
    }

    /** The field reports focus again while the panel is open. */
    @Test
    fun `reopening an open panel keeps the query`() = runBlocking<Unit> {
        val viewModel = searchViewModel()
        viewModel.onOpened()
        viewModel.onQueryChanged("Ham")
        viewModel.onOpened()
        assertEquals("Ham", viewModel.uiState.await { it.expanded }.query)
    }

    /** #133 */
    @Test
    fun `an open panel comes back after process death`() = runBlocking<Unit> {
        val savedState = SavedStateHandle()
        searchViewModel(savedState).onOpened()
        assertTrue(searchViewModel(savedState).uiState.await { true }.expanded)
    }

    private fun searchViewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        main.track(SearchViewModel(stubFeature(), DestinationSearchObserver(NoGeocoder, NoLocation), vehicles(), history(), savedState))

    private fun vehicles() = DataStoreVehicleRepository(InMemoryPreferencesDataStore())

    private fun history() = DataStoreDestinationHistory(InMemoryPreferencesDataStore())

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T = withTimeout(5_000) { first(matching) }

    private fun stubFeature() = ChargeStopsFeature(
        locationSource = object : LocationSource {
            override suspend fun currentFix(): Fix? = null
            override val updates: Flow<Fix> = emptyFlow()
        },
        parentScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private object NoGeocoder : Geocoder {
        override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> = emptyList()
    }

    private object NoLocation : LocationSource {
        override suspend fun currentFix(): Fix? = null
        override val updates: Flow<Fix> = emptyFlow()
    }
}
