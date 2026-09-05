package de.autoapp.shared.data

import de.autoapp.shared.domain.SectorArea
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.distanceKmTo
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoSiteSourceTest {

    private val source = DemoSiteSource()

    @Test
    fun returnsSitesRelativeToTheAreaOrigin() = runBlocking {
        // Fixed coordinates would only be visible in one spot on Earth, and
        // empty of all places on the test rig.
        val nurembergArea = SectorArea.circle(LatLon(49.2, 11.2), radiusKm = 150.0)
        val berlinArea = SectorArea.circle(LatLon(52.5, 13.4), radiusKm = 150.0)

        val nurembergSites = source.query(nurembergArea)
        val berlinSites = source.query(berlinArea)

        assertEquals(nurembergSites.size, berlinSites.size)
        assertTrue(nurembergSites.first().position.distanceKmTo(berlinSites.first().position) > 300.0)
    }

    @Test
    fun allSitesLieWithinTheSearchRadius() = runBlocking {
        val center = LatLon(48.9, 11.45)
        val area = SectorArea.circle(center, radiusKm = 150.0)

        source.query(area).forEach { site ->
            val distance = center.distanceKmTo(site.position)
            assertTrue(distance < 150.0, "${site.name} was $distance km away")
        }
    }

    @Test
    fun idsAreRecognizableAsDemoAndUnique() = runBlocking {
        val sites = source.query(SectorArea.circle(LatLon(48.9, 11.45), radiusKm = 150.0))

        assertTrue(sites.all { it.id.startsWith("demo:") })
        assertEquals(sites.size, sites.map { it.id }.toSet().size)
    }

    @Test
    fun everySiteHasANameOperatorAndAtLeastOneConnector() = runBlocking {
        source.query(SectorArea.circle(LatLon(48.9, 11.45), radiusKm = 150.0)).forEach { site ->
            assertTrue(site.name.isNotBlank())
            assertTrue(!site.operator.isNullOrBlank())
            assertTrue(site.connectors.isNotEmpty())
        }
    }
}
