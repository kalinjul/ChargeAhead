package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.SectorArea
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.domain.ConnectorType
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the assumptions about OpenChargeMap against the real service.
 *
 * **Does not run by default**; needs network access and a valid key:
 *
 * ```bash
 * OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OpenChargeMapLiveContractTest'
 * ```
 */
class OpenChargeMapLiveContractTest {

    private val apiKey: String? = readApiKey()
    private val enabled: Boolean = System.getenv("OCM_LIVE") == "1" && apiKey != null

    /** On the A9 between Nürnberg and Ingolstadt. */
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

        // The radial search returns the nearest sites first.
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

        // If the ratio drops well below this, OCM has changed its numbering.
        assertTrue(
            recognized > connectors.size * 0.8,
            "Only $recognized of ${connectors.size} connector types recognized",
        )
    }

    @Test
    fun everyConnectorHasAPowerRating() {
        if (skip()) return

        val connectors = runBlocking { source().query(area) }.flatMap { it.connectors }

        // Connectors without a power rating are dropped by the source.
        assertTrue(connectors.all { it.maxPowerKw > 0.0 })
        assertTrue(connectors.isNotEmpty())
    }

    @Test
    fun operatorid_filters_server_side() {
        if (skip()) return

        val enbw = NetworkCatalog.byKey("enbw")!!
        val sites = runBlocking { source().query(SectorArea.circle(LatLon(48.137, 11.575), 25.0), networks = listOf(enbw)) }

        assertTrue(sites.isNotEmpty(), "EnBW around Munich should return sites")
        assertTrue(sites.all { it.operatorId == 86L }, "every returned site must be EnBW (operatorId 86)")
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("OpenChargeMapLiveContractTest skipped (OCM_LIVE!=1 or no key).")
        }
        return !enabled
    }

    private fun readApiKey(): String? {
        // local.properties lives at the repo root; search upward.
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
