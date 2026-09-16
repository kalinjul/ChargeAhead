package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.PolylineArea
import org.julakali.chargeahead.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the assumptions about OSRM against the real service. **Does not run
 * by default** — see [OpenChargeMapLiveContractTest]:
 *
 * ```bash
 * OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OsrmLiveContractTest'
 * ```
 *
 * It also checks that the full geometry, once simplified, stays small.
 */
class OsrmLiveContractTest {

    private val enabled: Boolean = System.getenv("OCM_LIVE") == "1"

    private val nuremberg = LatLon(49.4521, 11.0767)
    private val munich = LatLon(48.1351, 11.5820)

    private fun engine() = OsrmRouteEngine(createHttpClient())

    @Test
    fun computesAPlausibleRoute() {
        if (skip()) return

        val route = assertNotNull(runBlocking { engine().route(nuremberg, munich) })

        // Nuremberg to Munich via the A9 is a good 170 km.
        assertTrue(route.distanceKm in 150.0..220.0, "Was ${route.distanceKm} km")
        assertTrue(route.durationMinutes in 60.0..240.0, "Was ${route.durationMinutes} min")
        assertTrue(route.points.first().distanceKmTo(nuremberg) < 5.0, "Does not start in Nuremberg")
        assertTrue(route.points.last().distanceKmTo(munich) < 5.0, "Does not end in Munich")
    }

    @Test
    fun theSimplifiedRouteStaysSmall() {
        if (skip()) return

        val route = assertNotNull(runBlocking { engine().route(nuremberg, munich) })

        // Unsimplified, this trip is about 2,000 points; at 100 m about 100 (#57).
        assertTrue(route.points.size in 2..400, "Was ${route.points.size} waypoints")
    }

    @Test
    fun theRouteWorksAsASearchArea() {
        if (skip()) return

        val route = assertNotNull(runBlocking { engine().route(nuremberg, munich) })
        val area = PolylineArea(route.points, bufferKm = 2.0)

        // Denkendorf sits right on the A9 and must be inside the buffer.
        assertTrue(LatLon(48.93, 11.46) in area, "The A9 itself is not inside the buffer")
        // Regensburg is a good 70 km off to the side.
        assertTrue(LatLon(49.02, 12.10) !in area, "Regensburg is inside the buffer")
    }

    private fun skip(): Boolean {
        if (!enabled) println("OsrmLiveContractTest skipped (OCM_LIVE!=1).")
        return !enabled
    }
}
