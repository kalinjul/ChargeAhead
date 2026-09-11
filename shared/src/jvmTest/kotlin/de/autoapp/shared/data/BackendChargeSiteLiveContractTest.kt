package de.autoapp.shared.data

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkCatalog
import de.autoapp.shared.domain.PolylineArea
import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the deployed backend against the same assumptions
 * [OpenChargeMapLiveContractTest] checks the provider against — the app must
 * not lose anything by going through the server.
 *
 * **Does not run by default.** Needs network access and a token:
 *
 * ```bash
 * CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendChargeSiteLiveContractTest'
 * ```
 */
class BackendChargeSiteLiveContractTest {

    private val baseUrl: String? = localProperty("chargeAheadBaseUrl")
    private val token: String? = localProperty("chargeAheadToken")
    private val enabled: Boolean =
        System.getenv("CHARGEAHEAD_LIVE") == "1" && baseUrl != null && token != null

    /** On the A9 between Nürnberg and Ingolstadt — densely populated, many sources. */
    private val location = LatLon(48.95, 11.45)
    private val area = SectorArea.circle(location, radiusKm = 175.0)

    private fun source() = BackendChargeSiteSource(
        createHttpClient(),
        baseUrl = requireNotNull(baseUrl),
        token = requireNotNull(token),
    )

    @Test
    fun theQueryReturnsUsableSites() {
        if (skip()) return

        val sites = runBlocking { source().query(area, emptyList()) }

        assertTrue(sites.size > 20, "Only ${sites.size} sites in the metro area — too few")
        assertTrue(sites.all { it.name.isNotBlank() }, "Site without a name")
        assertTrue(sites.all { it.position in area }, "Site outside the requested area")
    }

    /** The reason the area travels as a shape rather than a rectangle. */
    @Test
    fun theNearestChargingStationsAreNotMissing() {
        if (skip()) return

        val sites = runBlocking { source().query(area, emptyList()) }
        val nearest = sites.minOf { location.distanceKmTo(it.position) }

        assertTrue(nearest < 10.0, "Nearest charging station only at ${nearest.toInt()} km")
    }

    /** One request for the whole corridor, where the app used to make a chain of them. */
    @Test
    fun aCorridorIsAnsweredInOneRequest() {
        if (skip()) return

        val corridor = PolylineArea(
            listOf(LatLon(49.45, 11.08), LatLon(49.2, 11.3), LatLon(48.77, 11.42)),
            bufferKm = 5.0,
        )

        val sites = runBlocking { source().query(corridor, emptyList()) }

        assertTrue(sites.size > 20, "Only ${sites.size} sites along the A9")
        assertTrue(sites.distinctBy { it.id }.size == sites.size, "The backend returned duplicates")
    }

    @Test
    fun connectorsArriveUsable() {
        if (skip()) return

        val connectors = runBlocking { source().query(area, emptyList()) }.flatMap { it.connectors }

        assertTrue(connectors.isNotEmpty())
        assertTrue(connectors.all { it.maxPowerKw > 0.0 }, "A connector without a power rating")
        assertTrue(
            connectors.count { it.type != ConnectorType.UNKNOWN } > connectors.size * 0.8,
            "Too many unrecognized connector types — the enum mapping has drifted",
        )
    }

    @Test
    fun networkFiltersAreAppliedServerSide() {
        if (skip()) return

        val enbw = NetworkCatalog.byKey("enbw")!!
        val sites = runBlocking {
            source().query(SectorArea.circle(LatLon(48.137, 11.575), 25.0), listOf(enbw))
        }

        assertTrue(sites.isNotEmpty(), "EnBW around Munich should return sites")
        assertTrue(sites.all { it.operatorId == 86L }, "Every returned site must be EnBW")
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("BackendChargeSiteLiveContractTest skipped (CHARGEAHEAD_LIVE!=1 or no configuration).")
        }
        return !enabled
    }

    /** The working directory is `shared/`, local.properties sits at the repo root. */
    private fun localProperty(name: String): String? {
        var directory: File? = File(".").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "local.properties")
            if (candidate.isFile) {
                val properties = Properties().apply { candidate.inputStream().use(::load) }
                return properties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
            }
            directory = directory.parentFile
        }
        return null
    }
}
