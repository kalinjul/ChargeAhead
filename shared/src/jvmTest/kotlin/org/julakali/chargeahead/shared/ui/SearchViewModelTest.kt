package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Address
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.Geocoder
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.ObserveDestinationSearch
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun tearDownMainDispatcher() = Dispatchers.resetMain()

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

    /** "Neu planen" reopens the search on the current destination as typed text. */
    @Test
    fun `opening with a prefill puts its label into the query`() = runBlocking<Unit> {
        val viewModel = SearchViewModel(stubFeature(), ObserveDestinationSearch(NoGeocoder, NoLocation), settings())
        viewModel.onOpened(hamburg)
        assertEquals("Hamburg, Hamburg", viewModel.uiState.await { it.query.isNotEmpty() }.query)
        viewModel.onClosed()
        assertEquals("", viewModel.uiState.await { it.query.isEmpty() }.query)
    }

    @Test
    fun `without a vehicle the state says so`() = runBlocking<Unit> {
        val viewModel = SearchViewModel(stubFeature(), ObserveDestinationSearch(NoGeocoder, NoLocation), settings())
        assertFalse(viewModel.uiState.await { true }.hasVehicle)
    }

    private fun settings() = PersistentSettingsStore(InMemoryPreferencesDataStore())

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T = withTimeout(5_000) { first(matching) }

    private fun stubFeature() = ChargeStopsFeature(
        locationSource = object : LocationSource { override val updates: Flow<Fix> = emptyFlow() },
        dispatcher = Dispatchers.Unconfined,
    )

    private object NoGeocoder : Geocoder {
        override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> = emptyList()
    }

    private object NoLocation : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
    }
}
