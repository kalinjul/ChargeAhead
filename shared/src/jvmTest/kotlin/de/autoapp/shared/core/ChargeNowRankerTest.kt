package de.autoapp.shared.core

import de.autoapp.shared.data.DemoTariffSource
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargeNowRankerTest {

    private val here = LatLon(52.37, 4.90)
    private val tariffs = DemoTariffSource()

    private fun site(
        id: String,
        operator: String,
        powerKw: Double,
        distanceKm: Double,
        connector: ConnectorType = ConnectorType.CCS2,
    ) = ChargeSite(
        id = "demo:$id",
        name = id,
        operator = operator,
        position = here.destination(bearingDeg = 90.0, distanceKm = distanceKm),
        connectors = listOf(Connector(connector, powerKw, 4)),
    )

    private fun rank(
        sites: List<ChargeSite>,
        filters: ChargeFilters = ChargeFilters(),
        networks: NetworkPreferences = NetworkPreferences(),
    ) = ChargeNowRanker.rank(sites, here, filters, networks, tariffs, activeTariffIds = emptySet())

    @Test
    fun `ranks the cheapest qualifying sites first`() {
        val result = rank(
            listOf(
                site("ionity", "Ionity", 350.0, 3.0),      // ad-hoc 0.79
                site("tesla", "Tesla", 250.0, 3.0),        // ad-hoc 0.55
                site("vattenfall", "Vattenfall", 150.0, 2.0), // ad-hoc 0.59
                site("fastned", "Fastned", 300.0, 1.0),    // ad-hoc 0.69
            ),
        )
        assertTrue(result.relaxed.isEmpty())
        assertEquals(
            listOf("demo:tesla", "demo:vattenfall", "demo:fastned"),
            result.candidates.map { it.site.id },
        )
    }

    @Test
    fun `AC posts never qualify`() {
        val result = rank(
            listOf(
                site("wallbox", "Stadtwerke", 22.0, 0.2),
                site("fastned", "Fastned", 300.0, 1.0),
                site("tesla", "Tesla", 250.0, 2.0),
            ),
        )
        assertTrue(result.candidates.none { it.site.id == "demo:wallbox" })
    }

    @Test
    fun `starving min power relaxes exactly that filter`() {
        // Only one site passes 150 kW; two 60 kW ones exist nearby.
        val result = rank(
            listOf(
                site("strong", "Fastned", 300.0, 1.0),
                site("weak-1", "Allego", 60.0, 1.5),
                site("weak-2", "EnBW", 60.0, 2.0),
            ),
        )
        assertEquals(listOf(RelaxedFilter.MIN_POWER), result.relaxed)
        assertEquals(3, result.candidates.size)
    }

    @Test
    fun `distance falls last`() {
        // Everything usable is far away AND weak AND expensive: the whole
        // ladder must give way, in its fixed order.
        val result = rank(
            listOf(
                site("far-1", "Ionity", 60.0, 20.0),
                site("far-2", "Ionity", 60.0, 22.0),
            ),
            filters = ChargeFilters(minPowerKw = 300.0, maxPriceEuroPerKwh = 0.50, maxDistanceKm = 5.0),
        )
        assertEquals(
            listOf(
                RelaxedFilter.MIN_POWER,
                RelaxedFilter.NETWORKS,
                RelaxedFilter.MAX_PRICE,
                RelaxedFilter.MAX_DISTANCE,
            ),
            result.relaxed,
        )
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `network filter is honored while it can be`() {
        val preferences = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("fastned", "tesla"))
        val result = rank(
            listOf(
                site("ionity", "Ionity", 350.0, 1.0),
                site("fastned", "Fastned", 300.0, 2.0),
                site("tesla", "Tesla", 250.0, 3.0),
            ),
            networks = preferences,
        )
        assertTrue(result.relaxed.isEmpty())
        assertTrue(result.candidates.none { it.site.id == "demo:ionity" })
    }

    @Test
    fun `the rest of the pool comes along, nearest first`() {
        val result = rank(
            listOf(
                site("ionity", "Ionity", 350.0, 3.0),         // ad-hoc 0.79 — priciest, misses top 3
                site("tesla", "Tesla", 250.0, 3.0),           // 0.55
                site("vattenfall", "Vattenfall", 150.0, 2.0), // 0.59
                site("fastned", "Fastned", 300.0, 1.0),       // 0.69
                site("wallbox", "Stadtwerke", 22.0, 0.2),     // AC — never appears anywhere
            ),
        )
        assertEquals(listOf("demo:ionity"), result.more.map { it.site.id })
    }

    @Test
    fun `more is sorted by distance, not by price`() {
        val result = rank(
            listOf(
                site("a", "Tesla", 250.0, 1.0),      // 0.55 — top 3
                site("b", "Vattenfall", 150.0, 1.0), // 0.59 — top 3
                site("c", "Fastned", 300.0, 1.0),    // 0.69 — top 3
                site("far-cheap", "Tesla", 250.0, 9.0),
                site("near-pricey", "Ionity", 350.0, 4.0),
            ),
        )
        assertEquals(listOf("demo:near-pricey", "demo:far-cheap"), result.more.map { it.site.id })
    }

    @Test
    fun `a generous distance filter must not crowd out the station next door`() {
        // The reported bug: max distance opened wide, and the 1.4 km station
        // matching every filter vanished behind cheap chargers half an hour out.
        val result = rank(
            listOf(
                site("next-door", "Vattenfall", 150.0, 1.4), // 0.59
                site("far-1", "Tesla", 250.0, 25.0),         // 0.55
                site("far-2", "Tesla", 250.0, 28.0),         // 0.55
                site("far-3", "Tesla", 250.0, 30.0),         // 0.55
            ),
            filters = ChargeFilters(maxDistanceKm = 35.0),
        )
        assertTrue(result.relaxed.isEmpty())
        assertEquals("demo:next-door", result.candidates.first().site.id)
    }

    @Test
    fun `a real saving still beats a short detour`() {
        // 2 ct/kWh per km: 3 km extra must be bought with >6 ct — Tesla brings 24.
        val result = rank(
            listOf(
                site("near-pricey", "Ionity", 350.0, 1.0), // 0.79 + 0.02
                site("far-cheaper", "Tesla", 250.0, 4.0),  // 0.55 + 0.08
                site("mid", "EnBW", 150.0, 2.0),           // 0.65 + 0.04
            ),
        )
        assertEquals(
            listOf("demo:far-cheaper", "demo:mid", "demo:near-pricey"),
            result.candidates.map { it.site.id },
        )
    }
}
