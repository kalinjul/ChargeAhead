package de.autoapp.shared.data

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the assumptions about the charging station register against the real service.
 *
 * **Does not run by default** — like [OpenChargeMapLiveContractTest]. It needs
 * network access, and a test that goes red just because of a dead connection
 * says nothing about the code. Unlike OpenChargeMap, it needs no key:
 *
 * ```bash
 * OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*BnetzaLiveContractTest'
 * ```
 *
 * What it's good for: [BnetzaSourceTest] checks against canned responses and
 * won't notice if the service changes. And it has changed before — the endpoint
 * documented at `ladestationen.api.bund.dev` now requires a token.
 */
class BnetzaLiveContractTest {

    private val enabled: Boolean = System.getenv("OCM_LIVE") == "1"

    /** On the A9 between Nürnberg and Ingolstadt. */
    private val location = LatLon(48.95, 11.45)

    /**
     * 25 km. Deliberately small: ArcGIS doesn't sort by distance, and the full
     * corridor contains 33,508 charging devices.
     */
    private val area = SectorArea.circle(location, radiusKm = 25.0)

    private fun source() = BnetzaSource(createHttpClient())

    @Test
    fun theQueryReturnsUsableSites() {
        if (skip()) return

        val sites = runBlocking { source().query(area) }

        assertTrue(sites.size > 100, "Only ${sites.size} charging devices — too few for 25 km")
        assertTrue(sites.all { it.id.startsWith("${BnetzaSource.SOURCE_ID}:") })
        assertTrue(sites.all { it.name.isNotBlank() }, "Charging device without a name")
        assertTrue(sites.all { it.position in area.boundingBox }, "Result outside the rectangle")
    }

    @Test
    fun theNearestChargingStationsAreNotMissing() {
        if (skip()) return

        val sites = runBlocking { source().query(area) }
        val nearest = sites.minOf { location.distanceKmTo(it.position) }

        assertTrue(nearest < 10.0, "Nearest charging device only at ${nearest.toInt()} km")
    }

    @Test
    fun theConnectorLabelsAreStillRecognized() {
        if (skip()) return

        val connectors = runBlocking { source().query(area) }.flatMap { it.connectors }
        val recognized = connectors.count { it.type != ConnectorType.UNKNOWN }

        // Only "AC CEE 3-polig" stays unrecognized, and that's rare. If the
        // ratio drops well below this, the register has changed its labels.
        assertTrue(
            recognized > connectors.size * 0.9,
            "Only $recognized of ${connectors.size} connector types recognized",
        )
    }

    @Test
    fun theUnitCountIsKnown() {
        if (skip()) return

        // The actual advantage over OpenChargeMap, where this field is missing
        // in about half the cases.
        val connectors = runBlocking { source().query(area) }.flatMap { it.connectors }

        assertTrue(connectors.isNotEmpty())
        assertTrue(connectors.all { it.count != null }, "Charging device without a unit count")
        assertTrue(connectors.all { it.maxPowerKw > 0.0 })
    }

    @Test
    fun theDocumentedEndpointIsStillDead() {
        if (skip()) return

        // If it starts responding without a token again, check the reasoning in
        // BnetzaSource — that's where it explains why a different one is used.
        val deprecatedSource = BnetzaSource(
            createHttpClient(),
            baseUrl = "https://services6.arcgis.com/6jU7RmJig2Wwo1b0/ArcGIS/rest/services/" +
                "Ladesaeulenregister/FeatureServer/7/query",
        )

        val result = runCatching { runBlocking { deprecatedSource.query(area) } }

        assertTrue(result.isFailure, "The documented endpoint is responding again — check the comment")
    }

    @Test
    fun aRealRouteYieldsAMuchSmallerResult() {
        if (skip()) return

        val route = requireNotNull(
            runBlocking {
                OsrmRouteEngine(createHttpClient())
                    .route(LatLon(49.4521, 11.0767), LatLon(48.1351, 11.5820))
            },
        ) { "No route Nürnberg–München" }

        val corridor = PolylineArea(route.points, bufferKm = 2.0)
        val alongRoute = runBlocking { source().query(corridor) }

        // This is the number the whole switch-over is based on: the sector
        // corridor over the same route contains about 33,000 charging devices
        // (ARCHITECTURE.md section 1.1).
        assertTrue(
            alongRoute.size in 200..3000,
            "Along the route there were ${alongRoute.size} charging devices",
        )

        // And they really are along the route, not just in the bounding box around
        // it. ArcGIS buffers server-side; the generous tolerance accounts for the
        // planar approximation and the fact that the service buffers geodetically.
        val outliers = alongRoute.filter { corridor.distanceKmTo(it.position) > 3.0 }
        assertTrue(outliers.isEmpty(), "${outliers.size} results were more than 3 km off the route")
    }

    private fun skip(): Boolean {
        if (!enabled) println("BnetzaLiveContractTest skipped (OCM_LIVE!=1).")
        return !enabled
    }
}
