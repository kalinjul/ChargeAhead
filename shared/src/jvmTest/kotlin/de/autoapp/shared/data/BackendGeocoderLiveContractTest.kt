package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Checks the destination search against the deployed backend.
 *
 * **Does not run by default:**
 *
 * ```bash
 * CHARGEAHEAD_LIVE=1 ./gradlew :shared:jvmTest --tests '*BackendGeocoderLiveContractTest'
 * ```
 */
class BackendGeocoderLiveContractTest {

    private val baseUrl: String? = localProperty("chargeAheadBaseUrl")
    private val token: String? = localProperty("chargeAheadToken")
    private val enabled: Boolean =
        System.getenv("CHARGEAHEAD_LIVE") == "1" && baseUrl != null && token != null

    private val munich = LatLon(48.137, 11.575)

    private fun geocoder() = BackendGeocoder(
        createHttpClient(),
        baseUrl = requireNotNull(baseUrl),
        token = requireNotNull(token),
    )

    /** The reason for the switch: a geocoder answers this with a street of that name. */
    @Test
    fun aHalfTypedCityIsFound() {
        if (skip()) return

        val places = runBlocking { geocoder().search("Münch", near = munich, limit = 5) }

        assertTrue(places.isNotEmpty(), "Nothing found for a half-typed input")
        assertTrue(
            places.any { it.name.startsWith("München") },
            "München is not among the hits: ${places.map { it.name }}",
        )
    }

    @Test
    fun hitsCarryTheirCoordinatesAndAreDistinguishable() {
        if (skip()) return

        val places = runBlocking { geocoder().search("Hauptbahnhof", near = munich, limit = 5) }

        assertTrue(places.isNotEmpty())
        assertTrue(places.all { it.name.isNotBlank() }, "A hit without a name")
        // There is no second step to resolve a hit; the position must be there.
        assertTrue(places.all { it.position.lat != 0.0 || it.position.lon != 0.0 })
        assertTrue(
            places.distinctBy { it.description }.size == places.size,
            "Duplicate labels: ${places.map { it.description }}",
        )
    }

    /** Nearby first, or "Hauptbahnhof" is a lottery. */
    @Test
    fun theOriginBiasesTheResult() {
        if (skip()) return

        val places = runBlocking { geocoder().search("Hauptbahnhof", near = munich, limit = 5) }
        val nearest = places.minOf { munich.distanceKmTo(it.position) }

        assertTrue(nearest < 50.0, "Nearest 'Hauptbahnhof' only at ${nearest.toInt()} km")
    }

    @Test
    fun aBlankQueryReturnsNothing() {
        if (skip()) return

        assertTrue(runBlocking { geocoder().search("  ") }.isEmpty())
    }

    private fun skip(): Boolean {
        if (!enabled) {
            println("BackendGeocoderLiveContractTest skipped (CHARGEAHEAD_LIVE!=1 or no configuration).")
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
