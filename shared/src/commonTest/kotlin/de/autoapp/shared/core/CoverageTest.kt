package de.autoapp.shared.core

import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.ViewportArea
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageTest {

    private val start = LatLon(48.9331, 11.4779)

    @Test
    fun aCircle_recordsOnlyTilesFullyInside_notItsBoxCorners() {
        val circle = SectorArea.circle(start, 40.0)

        val recorded = Coverage.tilesToRecord(circle)

        assertTrue(recorded.isNotEmpty())
        assertTrue(recorded.size < Tiles.covering(circle.boundingBox).size, "The box's corners must not count")
        assertFalse(Tiles.of(start.destination(45.0, 50.0)) in recorded, "A tile ~50 km out lies beyond the 40 km circle")
    }

    @Test
    fun aViewport_recordsTheTilesItFullyContains() {
        val viewport = ViewportArea(BoundingBox(south = 48.05, west = 11.05, north = 48.35, east = 11.35))

        val recorded = Coverage.tilesToRecord(viewport).toSet()

        assertEquals(setOf(Tiles.Tile(481, 111), Tiles.Tile(481, 112), Tiles.Tile(482, 111), Tiles.Tile(482, 112)), recorded)
    }

    @Test
    fun aNarrowRouteBuffer_recordsNoTiles() {
        val route = PolylineArea(listOf(start, start.destination(135.0, 80.0)), bufferKm = 3.0)

        assertEquals(emptyList(), Coverage.tilesToRecord(route))
    }

    @Test
    fun aCircleQuery_isCoveredByTheTilesAWiderCircleRecorded() {
        val fresh = Coverage.tilesToRecord(SectorArea.circle(start, 65.0)).toSet()

        assertTrue(Coverage.isCovered(SectorArea.circle(start, 40.0), fresh, emptyList()))
        assertFalse(Coverage.isCovered(SectorArea.circle(start, 60.0), fresh, emptyList()))
    }

    @Test
    fun theRouteAhead_isCoveredByTheRouteFetchedBefore() {
        val route = PolylineArea(
            listOf(start, start.destination(180.0, 80.0), start.destination(170.0, 170.0)),
            bufferKm = 2.0,
        )
        val ahead = route.aheadOf(start.destination(180.0, 40.0))!!

        assertTrue(Coverage.isCovered(ahead, emptySet(), listOf(route)))
    }

    @Test
    fun aWiderBuffer_isNotCoveredByANarrowerOne() {
        val route = listOf(start, start.destination(180.0, 80.0))

        assertFalse(
            Coverage.isCovered(PolylineArea(route, 3.0), emptySet(), listOf(PolylineArea(route, 2.0))),
        )
    }

    @Test
    fun aRouteFetchedInChunks_isCoveredAsAWhole() {
        // TripPlanner fetches in chunks that share their boundary point; the
        // car list later asks for the whole route at once.
        val a = start
        val b = start.destination(135.0, 80.0)
        val c = b.destination(180.0, 80.0)
        val chunks = listOf(PolylineArea(listOf(a, b), 3.0), PolylineArea(listOf(b, c), 3.0))

        assertTrue(Coverage.isCovered(PolylineArea(listOf(a, b, c), 2.0), emptySet(), chunks))
    }

    @Test
    fun aRouteLeavingTheFetchedCorridor_isNotCovered() {
        val fetched = PolylineArea(listOf(start, start.destination(180.0, 80.0)), 3.0)
        val detour = PolylineArea(listOf(start, start.destination(180.0, 40.0), start.destination(160.0, 80.0)), 3.0)

        assertFalse(Coverage.isCovered(detour, emptySet(), listOf(fetched)))
    }

    @Test
    fun aRoute_isCoveredByFreshTilesAroundIt() {
        // Planned inside an area a big "charge now" circle already fetched.
        val fresh = Coverage.tilesToRecord(SectorArea.circle(start, 150.0)).toSet()
        val route = PolylineArea(listOf(start, start.destination(135.0, 60.0)), 3.0)

        assertTrue(Coverage.isCovered(route, fresh, emptyList()))
    }
}
