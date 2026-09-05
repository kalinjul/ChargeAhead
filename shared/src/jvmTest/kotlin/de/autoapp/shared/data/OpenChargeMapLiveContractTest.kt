package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.distanceKmTo
import de.autoapp.shared.domain.ConnectorType
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the assumptions about OpenChargeMap against the real service.
 *
 * **Does not run by default.** It needs network access and a valid key, and
 * neither belongs in an ordinary test run — a test that goes red just because
 * of a dead connection or an expired key says nothing about the code. It is
 * enabled explicitly:
 *
 * ```bash
 * OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OpenChargeMapLiveContractTest'
 * ```
 *
 * What it's good for then: [OpenChargeMapSourceTest] checks the mapping against
 * canned responses and wouldn't notice if OCM changes its response. This one does.
 */
class OpenChargeMapLiveContractTest {

    private val apiKey: String? = readApiKey()
    private val enabled: Boolean = System.getenv("OCM_LIVE") == "1" && apiKey != null

    /** On the A9 between Nürnberg and Ingolstadt — densely populated, many sources. */
    private val location = LatLon(48.95, 11.45)
    private val area = SectorArea.circle(location, radiusKm = 175.0)

    private fun source() = OpenChargeMapSource(createHttpClient(), requireNotNull(apiKey))

    @Test
    fun theQueryReturnsUsableSites() {
        if (skip()) return

        val sites = runBlocking { source().query(area) }

        assertTrue(sites.size > 20, "Only ${sites.size} sites in the metro area — too few")
        assertTrue(sites.all { it.id.startsWith("${OpenChargeMapSource.SOURCE_ID}:") })
        assertTrue(sites.all { it.name.isNotBlank() }, "Site without a name")
        assertTrue(sites.all { it.position in area }, "Site outside the requested area")
    }

    @Test
    fun theNearestChargingStationsAreNotMissing() {
        if (skip()) return

        val sites = runBlocking { source().query(area) }
        val nearest = sites.minOf { location.distanceKmTo(it.position) }

        // The actual point of the radial search. With a rectangular query, the
        // nearest hit here came back at 70 km, because OCM responds unsorted
        // and truncates at maxresults — the app was missing exactly the
        // charging stations it exists to find.
        assertTrue(nearest < 10.0, "Nearest charging station only at ${nearest.toInt()} km")
    }

    @Test
    fun operatorNamesAreNotPlaceholders() {
        if (skip()) return

        val sites = runBlocking { source().query(area) }

        val placeholders = sites.mapNotNull { it.operator }.filter { it.startsWith("(") }
        assertTrue(placeholders.isEmpty(), "Placeholders slipped through: ${placeholders.distinct()}")
    }

    @Test
    fun mostConnectorTypesAreRecognized() {
        if (skip()) return

        val connectors = runBlocking { source().query(area) }.flatMap { it.connectors }
        val recognized = connectors.count { it.type != ConnectorType.UNKNOWN }

        // No claim to completeness: CEE and Type 1 variants are deliberately
        // left unmapped (see OpenChargeMapSource.connectorTypeOf). If the ratio
        // drops well below this, OCM has changed its numbering.
        assertTrue(
            recognized > connectors.size * 0.8,
            "Only $recognized of ${connectors.size} connector types recognized",
        )
    }

    @Test
    fun everyConnectorHasAPowerRating() {
        if (skip()) return

        val connectors = runBlocking { source().query(area) }.flatMap { it.connectors }

        // Connectors without a power rating are dropped by the source — what
        // remains must have one, or the car UI would show "0 kW".
        assertTrue(connectors.all { it.maxPowerKw > 0.0 })
        assertTrue(connectors.isNotEmpty())
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("OpenChargeMapLiveContractTest skipped (OCM_LIVE!=1 or no key).")
        }
        return !enabled
    }

    private fun readApiKey(): String? {
        // The test run's working directory is shared/; local.properties lives
        // at the repo root. Search upward instead of hardcoding the path.
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "local.properties")
            if (candidate.isFile) {
                val properties = Properties().apply { candidate.inputStream().use(::load) }
                return properties.getProperty("openChargeMapApiKey")?.trim()?.takeIf { it.isNotEmpty() }
            }
            directory = directory.parentFile
        }
        return null
    }
}
