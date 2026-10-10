package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.BackendConfig
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the route with its charge sites against the deployed backend.
 * The 204 case is covered by [BackendRouteSiteSourceTest].
 *
 * **Does not run by default:**
 *
 * ```bash
 * CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendRouteSiteLiveContractTest'
 * ```
 */
class BackendRouteSiteLiveContractTest {

    private val baseUrl: String? = localProperty("chargeAheadBaseUrl")
    private val token: String? = localProperty("chargeAheadToken")
    private val enabled: Boolean =
        System.getenv("CHARGEAHEAD_LIVE") == "1" && baseUrl != null && token != null

    /** Ingolstadt to Nürnberg, along the A9. */
    private val from = LatLon(48.7665, 11.4257)
    private val to = LatLon(49.4521, 11.0767)

    private fun found() = runBlocking {
        BackendRouteSiteSource(createHttpClient(backend = BackendConfig(requireNotNull(baseUrl), requireNotNull(token))))
            .routeWithSites(from, to, bufferKm = BUFFER_KM, networkKeys = emptySet(), minPowerKw = MIN_POWER_KW)
    }!!

    @Test
    fun theRouteIsPlausible() {
        if (skip()) return

        val route = found().route

        val straightLine = from.distanceKmTo(to)
        assertTrue(
            route.distanceKm > straightLine && route.distanceKm < straightLine * 1.6,
            "${route.distanceKm} km against ${straightLine.toInt()} km straight line",
        )
        assertTrue(route.durationMinutes in 30.0..150.0, "${route.durationMinutes} minutes")
        assertTrue(from.distanceKmTo(route.points.first()) < 2.0, "Start is off the requested point")
        assertTrue(to.distanceKmTo(route.points.last()) < 2.0, "End is off the requested point")
    }

    /** Ingolstadt lies about 60 m above Nürnberg; the A9 between them rolls over the Jura. */
    @Test
    fun everySegmentCarriesAPlausibleElevation() {
        if (skip()) return

        val segments = found().route.segments

        assertTrue(segments.isNotEmpty(), "A route without segments")
        val ascent = segments.sumOf { it.ascentM }
        val descent = segments.sumOf { it.descentM }
        assertTrue(segments.all { it.ascentM >= 0.0 && it.descentM >= 0.0 }, "Negative elevation in $segments")
        assertTrue(ascent in 50.0..1500.0, "$ascent m up")
        assertTrue(descent - ascent in -100.0..250.0, "$ascent m up, $descent m down")
    }

    @Test
    fun theSitesLieAlongTheRouteInDrivingOrder() {
        if (skip()) return

        val found = found()

        assertTrue(found.sites.size >= 3, "Only ${found.sites.size} fast chargers along the A9")
        assertEquals(found.sites.sortedBy { it.kmFromStart }, found.sites)
        assertTrue(found.sites.all { it.kmFromStart in 0.0..found.route.distanceKm }, "A site off the route's scale")
        assertTrue(found.sites.all { it.distanceFromRouteKm <= BUFFER_KM }, "A site outside the buffer")
        assertTrue(
            found.sites.all { site -> site.site.connectors.any { it.maxPowerKw >= MIN_POWER_KW } },
            "A site below the requested power",
        )
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("BackendRouteSiteLiveContractTest skipped (CHARGEAHEAD_LIVE!=1 or no configuration).")
        }
        return !enabled
    }

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

    private companion object {
        const val BUFFER_KM = 3.0
        const val MIN_POWER_KW = 50.0
    }
}
