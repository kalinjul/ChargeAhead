package de.autoapp.shared.data

import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests destination search against the real service. **Does not run by default**:
 *
 * ```bash
 * OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*NominatimLiveContractTest'
 * ```
 *
 * Deliberately kept minimal — the public instance allows one request per
 * second, and this test should not push that limit.
 */
class NominatimLiveContractTest {

    private val enabled: Boolean = System.getenv("OCM_LIVE") == "1"

    @Test
    fun findsAKnownDestination() {
        if (!enabled) {
            println("NominatimLiveContractTest skipped (OCM_LIVE!=1).")
            return
        }

        val results = runBlocking {
            NominatimGeocoder(createHttpClient()).search("München Hauptbahnhof", limit = 3)
        }

        assertTrue(results.isNotEmpty(), "No results for a known destination")

        val firstResult = results.first()
        assertTrue(firstResult.name.isNotBlank())
        assertTrue(
            firstResult.position.distanceKmTo(LatLon(48.1407, 11.5569)) < 5.0,
            "Result was at ${firstResult.position}",
        )
    }
}
