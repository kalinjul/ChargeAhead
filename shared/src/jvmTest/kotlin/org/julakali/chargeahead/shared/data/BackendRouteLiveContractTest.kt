package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.BackendConfig

import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks route calculation against the deployed backend.
 * The 204 case is covered by [BackendRouteEngineTest].
 *
 * **Does not run by default:**
 *
 * ```bash
 * CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendRouteLiveContractTest'
 * ```
 */
class BackendRouteLiveContractTest {

    private val baseUrl: String? = localProperty("chargeAheadBaseUrl")
    private val token: String? = localProperty("chargeAheadToken")
    private val enabled: Boolean =
        System.getenv("CHARGEAHEAD_LIVE") == "1" && baseUrl != null && token != null

    /** Ingolstadt to Nürnberg, along the A9. */
    private val from = LatLon(48.7665, 11.4257)
    private val to = LatLon(49.4521, 11.0767)

    private fun engine() = BackendRouteEngine(
        createHttpClient(backend = BackendConfig(requireNotNull(baseUrl), requireNotNull(token))),
    )

    @Test
    fun aRouteIsPlausible() {
        if (skip()) return

        val route = runBlocking { engine().route(from, to) }!!

        val straightLine = from.distanceKmTo(to)
        assertTrue(route.points.size >= 2, "A route of ${route.points.size} points")
        assertTrue(
            route.distanceKm > straightLine && route.distanceKm < straightLine * 1.6,
            "${route.distanceKm} km against ${straightLine.toInt()} km straight line",
        )
        assertTrue(route.durationMinutes in 30.0..150.0, "${route.durationMinutes} minutes")
    }

    /** Ingolstadt lies about 60 m above Nürnberg; the A9 between them rolls over the Jura. */
    @Test
    fun everySegmentCarriesAPlausibleElevation() {
        if (skip()) return

        val route = runBlocking { engine().route(from, to) }!!

        assertTrue(route.segments.isNotEmpty(), "A route without segments")
        val ascent = route.segments.sumOf { it.ascentM }
        val descent = route.segments.sumOf { it.descentM }
        assertTrue(route.segments.all { it.ascentM >= 0.0 && it.descentM >= 0.0 }, "Negative elevation in ${route.segments}")
        assertTrue(ascent in 50.0..1500.0, "$ascent m up")
        assertTrue(descent - ascent in -100.0..250.0, "$ascent m up, $descent m down")
    }

    /** The polyline is what the charge-site corridor is built from. */
    @Test
    fun theRouteStartsAndEndsWhereItWasAsked() {
        if (skip()) return

        val route = runBlocking { engine().route(from, to) }!!

        assertTrue(from.distanceKmTo(route.points.first()) < 2.0, "Start is off the requested point")
        assertTrue(to.distanceKmTo(route.points.last()) < 2.0, "End is off the requested point")
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("BackendRouteLiveContractTest skipped (CHARGEAHEAD_LIVE!=1 or no configuration).")
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
}
