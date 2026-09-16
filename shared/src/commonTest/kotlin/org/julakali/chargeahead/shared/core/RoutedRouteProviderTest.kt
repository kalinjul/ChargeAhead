package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutedRouteProviderTest {

    private val a9 = listOf(
        LatLon(49.38, 11.10),
        LatLon(49.19, 11.19),
        LatLon(49.05, 11.35),
        LatLon(48.93, 11.46),
        LatLon(48.79, 11.47),
        LatLon(48.25, 11.65),
    )

    private val route = Route(a9, distanceKm = 170.0, durationMinutes = 108.0)
    private val provider = RoutedRouteProvider(route)

    private fun fix(position: LatLon) =
        Fix(position, bearingDeg = 180.0, speedMps = 30.0, timestampMillis = 0L)

    @Test
    fun onTheRoute_aRouteBufferIsCreated() {
        val area = assertIs<PolylineArea>(provider.searchArea(fix(a9[2]), rangeKm = 300.0))

        assertEquals(RoutedRouteProvider.DEFAULT_BUFFER_KM, area.bufferKm)
    }

    @Test
    fun theAreaContainsTheRouteAheadNotBehind() {
        val area = provider.searchArea(fix(a9[3]), rangeKm = 300.0)

        assertTrue(a9.last() in area, "The destination is missing from the search area")
        assertTrue(a9.first() !in area, "The start is still within the search area")
    }

    @Test
    fun theAreaShrinksWhileDriving() {
        val atTheStart = provider.searchArea(fix(a9.first()), rangeKm = 300.0).radiusKm
        val later = provider.searchArea(fix(a9[4]), rangeKm = 300.0).radiusKm

        assertTrue(later < atTheStart, "$later was not smaller than $atTheStart")
    }

    @Test
    fun aRestStopNextToTheRoute_stillCountsAsOnTheRoute() {
        val offToTheSide = a9[3].destination(bearingDeg = 90.0, distanceKm = 3.0)

        assertIs<PolylineArea>(provider.searchArea(fix(offToTheSide), rangeKm = 300.0))
    }

    @Test
    fun farOffRoute_itFallsBackToTheCorridor() {
        // Regensburg is about 60 km off the A9. At that point the route no
        // longer says anything about what lies ahead.
        val regensburg = LatLon(49.02, 12.10)

        assertIs<SectorArea>(provider.searchArea(fix(regensburg), rangeKm = 300.0))
    }

    @Test
    fun atTheDestination_itFallsBackToTheCorridor() {
        // Otherwise the list would be empty at the end of every trip.
        assertIs<SectorArea>(provider.searchArea(fix(a9.last()), rangeKm = 300.0))
    }

    @Test
    fun progressAdvancesAlongTheRoute() {
        val atTheStart = assertNotNull(provider.progressAt(fix(a9.first())))
        val later = assertNotNull(provider.progressAt(fix(a9[3])))

        assertEquals(0.0, atTheStart.kmFromStart, 1e-9)
        assertTrue(later.kmFromStart in 1.0..route.distanceKm, "km-from-start was ${later.kmFromStart}")
    }

    @Test
    fun whereTheAreaFallsBack_thereIsNoProgress() {
        // A list searched in the corridor must not be priced against the route.
        assertNull(provider.progressAt(fix(LatLon(49.02, 12.10))))
        assertNull(provider.progressAt(fix(a9.last())))
    }

    @Test
    fun theCorridorFallbackUsesTheRange() {
        val area = assertIs<SectorArea>(provider.searchArea(fix(a9.last()), rangeKm = 100.0))

        assertEquals(120.0, area.radiusKm)
    }
}
