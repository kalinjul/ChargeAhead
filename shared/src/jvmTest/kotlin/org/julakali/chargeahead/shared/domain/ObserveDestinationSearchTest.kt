package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserveDestinationSearchTest {

    private val nuremberg = LatLon(49.4521, 11.0767)
    private val hit = Place("Treffer", "Treffer", LatLon(48.0, 11.0))
    private val queries = mutableListOf<String>()
    private var seenNear: LatLon? = null

    private val geocoder = object : Geocoder {
        override suspend fun search(query: String, near: LatLon?, limit: Int): List<Place> {
            queries += query
            seenNear = near
            if (query == "Funkloch") error("offline")
            return listOf(hit)
        }
    }

    private val location = object : LocationSource {
        override val updates: Flow<Fix> = emptyFlow()
        override suspend fun currentFix() = Fix(nuremberg, bearingDeg = null, speedMps = null, timestampMillis = 0L)
    }

    private val observe = ObserveDestinationSearch(geocoder, location)

    private suspend fun await(matching: (DestinationSearch) -> Boolean): DestinationSearch =
        withTimeout(5_000) { observe.flow.first(matching) }

    /** "Hauptbahnhof" (main station) is ambiguous without a nearby location. */
    @Test
    fun `the search is biased toward the current position`() = runBlocking {
        observe(ObserveDestinationSearch.Params("Hauptbahnhof", debounce = false))

        assertEquals(listOf(hit), await { !it.searching }.results)
        assertEquals(nuremberg, seenNear)
    }

    @Test
    fun `a query too short to search on finds nothing and asks no one`() = runBlocking {
        observe(ObserveDestinationSearch.Params("Ul"))

        val search = await { true }
        assertTrue(search.results.orEmpty().isEmpty() && !search.searching)
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `a failed search is told apart from an empty one`() = runBlocking {
        observe(ObserveDestinationSearch.Params("Funkloch", debounce = false))

        assertNull(await { !it.searching }.results)
    }

    @Test
    fun `typing on only searches for the text it settled on`() = runBlocking {
        observe(ObserveDestinationSearch.Params("Mün"))
        observe(ObserveDestinationSearch.Params("Münc"))
        observe(ObserveDestinationSearch.Params("München"))

        assertEquals("München", await { !it.searching && it.query == "München" }.query)
        assertEquals(listOf("München"), queries)
    }

    @Test
    fun `the previous results stay while the next query is searched`() = runBlocking {
        observe(ObserveDestinationSearch.Params("München", debounce = false))
        await { !it.searching }

        observe(ObserveDestinationSearch.Params("Hamburg"))

        assertEquals(listOf(hit), await { it.searching && it.query == "Hamburg" }.results)
    }
}
