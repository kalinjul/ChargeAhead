package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.distanceKmTo
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
 * It also measures the assumption the whole switch from sector to route is
 * based on: that a simplified route stays small.
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

        // This is what the switch is based on: a couple dozen points are
        // enough for a 2 km buffer. With overview=full it would be thousands.
        assertTrue(route.points.size in 2..200, "Was ${route.points.size} waypoints")
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
