package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiteMergerTest {

    private val location = LatLon(48.9331, 11.4779)

    private fun site(
        id: String,
        position: LatLon = location,
        name: String = "Ladepark",
        operator: String? = "TestNetz",
        connectors: List<Connector> = listOf(Connector(ConnectorType.CCS2, 150.0, 4)),
    ) = ChargeSite(
        id = id,
        name = name,
        operator = operator,
        position = position,
        connectors = connectors,
        sources = setOf(id.substringBefore(":")),
    )

    @Test
    fun individualSites_remainUnchanged() {
        val one = site("ocm:1")

        assertEquals(listOf(one), SiteMerger.merge(listOf(one)))
    }

    @Test
    fun theSameSiteFromTwoSources_becomesOne() {
        val merged = SiteMerger.merge(listOf(site("ocm:1"), site("bnetza:2")))

        assertEquals(1, merged.size)
        assertEquals(setOf("ocm", "bnetza"), merged.single().sources)
    }

    @Test
    fun multipleRegisterReports_areMerged() {
        // The register reports per charging device: "mblty Denkendorf" appeared
        // seven times at the same coordinate.
        val sevenTimes = (1..7).map { site("bnetza:$it") }

        assertEquals(1, SiteMerger.merge(sevenTimes).size)
    }

    @Test
    fun beyondTheThreshold_remainsSeparate() {
        val farther = site("bnetza:2", position = location.destination(90.0, 0.1))

        assertEquals(2, SiteMerger.merge(listOf(site("ocm:1"), farther)).size)
    }

    @Test
    fun withoutOverlappingConnectors_remainsSeparate() {
        // Two devices can share a parking lot and still be different charging stations.
        val acOnly = site("bnetza:2", connectors = listOf(Connector(ConnectorType.TYPE2, 22.0, 2)))

        assertEquals(2, SiteMerger.merge(listOf(site("ocm:1"), acOnly)).size)
    }

    @Test
    fun withoutConnectorInfo_distanceAloneDecides() {
        // A missing connector spec is not evidence of being a different site.
        val withoutData = site("ocm:1", connectors = emptyList())

        assertEquals(1, SiteMerger.merge(listOf(withoutData, site("bnetza:2"))).size)
    }

    @Test
    fun positionAndOperatorComeFromTheRegister() {
        val fromRegister = site(
            "bnetza:2",
            position = location.destination(90.0, 0.015),
            operator = "Amtlich GmbH",
        )
        val fromOcm = site("ocm:1", operator = "Ungenau e.V.")

        val merged = SiteMerger.merge(listOf(fromOcm, fromRegister)).single()

        assertEquals(fromRegister.position, merged.position)
        assertEquals("Amtlich GmbH", merged.operator)
    }

    @Test
    fun theNameComesFromOpenChargeMap() {
        // There, people name what they found on site; the register uses
        // administrative designations.
        val merged = SiteMerger.merge(
            listOf(
                site("ocm:1", name = "Raststätte Köschinger Forst West"),
                site("bnetza:2", name = "Denkendorf - Am Limes AC/ Varusstraße"),
            ),
        ).single()

        assertEquals("Raststätte Köschinger Forst West", merged.name)
    }

    @Test
    fun withoutAnOcmName_theRegistersNameIsKept() {
        val merged = SiteMerger.merge(listOf(site("bnetza:2", name = "Amtlicher Name"))).single()

        assertEquals("Amtlicher Name", merged.name)
    }

    @Test
    fun chargePointsFromTheSameSourceAreAdded() {
        val merged = SiteMerger.merge(
            listOf(
                site("bnetza:1", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2))),
                site("bnetza:2", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 4))),
            ),
        ).single()

        assertEquals(6, merged.connectors.single().count)
    }

    @Test
    fun chargingPointsFromDifferentSourcesAreNotAdded() {
        // OCM and the register describe the same devices. Adding both would
        // suddenly double the site's reported charging points.
        val merged = SiteMerger.merge(
            listOf(
                site("ocm:1", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 4))),
                site("bnetza:2", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 4))),
            ),
        ).single()

        assertEquals(4, merged.connectors.single().count)
    }

    @Test
    fun anUnknownCount_makesTheSumUnknown() {
        val merged = SiteMerger.merge(
            listOf(
                site("bnetza:1", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2))),
                site("bnetza:2", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, null))),
            ),
        ).single()

        assertNull(merged.connectors.single().count)
    }

    @Test
    fun differentConnectorTypesRemainSideBySide() {
        val merged = SiteMerger.merge(
            listOf(
                site("bnetza:1", connectors = listOf(Connector(ConnectorType.CCS2, 150.0, 2))),
                site(
                    "bnetza:2",
                    connectors = listOf(
                        Connector(ConnectorType.CCS2, 150.0, 2),
                        Connector(ConnectorType.TYPE2, 22.0, 1),
                    ),
                ),
            ),
        ).single()

        assertEquals(2, merged.connectors.size)
        // Strongest first — the car UI's headline reads that entry.
        assertEquals(150.0, merged.connectors.first().maxPowerKw)
    }

    @Test
    fun theIdComesFromTheLeadingSource() {
        val merged = SiteMerger.merge(listOf(site("ocm:1"), site("bnetza:2"))).single()

        assertEquals("bnetza:2", merged.id)
    }

    @Test
    fun theResultDoesNotDependOnOrder() {
        // Otherwise the list would depend on the response order of two network calls.
        val input = listOf(site("ocm:1"), site("bnetza:2"), site("bnetza:3"))

        assertEquals(SiteMerger.merge(input), SiteMerger.merge(input.reversed()))
    }

    @Test
    fun widelySeparatedSites_allRemain() {
        val scattered = (0..20).map { site("ocm:$it", position = location.destination(90.0, it * 5.0)) }

        assertEquals(21, SiteMerger.merge(scattered).size)
    }

    @Test
    fun manySites_areMergedInReasonableTime() {
        // A route buffer brings back a few thousand entries per query.
        // Without a grid index this would be quadratic.
        val many = (0 until 4000).map {
            site("ocm:$it", position = location.destination(90.0, it * 0.05))
        }

        assertEquals(4000, SiteMerger.merge(many).size)
    }

    @Test
    fun anEmptyInput_producesAnEmptyList() {
        assertTrue(SiteMerger.merge(emptyList()).isEmpty())
    }
}
