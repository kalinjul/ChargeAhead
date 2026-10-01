package org.julakali.chargeahead.shared.db

import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `connectors survive the round trip, unit count and unknown type included`() {
        val connectors = listOf(
            Connector(ConnectorType.CCS2, 350.0, 6),
            Connector(ConnectorType.TYPE2, 22.0, null),
            Connector(ConnectorType.UNKNOWN, 50.0, 1),
        )

        assertEquals(connectors, converters.connectorsFromJson(converters.connectorsToJson(connectors)))
    }

    @Test
    fun `points survive the round trip in order`() {
        val points = listOf(LatLon(53.55, 9.99), LatLon(51.31, 9.49), LatLon(48.137, 11.575))

        assertEquals(points, converters.pointsFromJson(converters.pointsToJson(points)))
    }

    @Test
    fun `string sets survive the round trip`() {
        val sources = setOf("bnetza", "datex", "ocm")

        assertEquals(sources, converters.stringsFromJson(converters.stringsToJson(sources)))
    }

    @Test
    fun `empty collections come back empty`() {
        assertEquals(emptyList(), converters.connectorsFromJson(converters.connectorsToJson(emptyList())))
        assertEquals(emptyList(), converters.pointsFromJson(converters.pointsToJson(emptyList())))
        assertEquals(emptySet(), converters.stringsFromJson(converters.stringsToJson(emptySet())))
    }

    /** A connector type this build doesn't know reads as UNKNOWN; the row stays. */
    @Test
    fun `an unknown connector type in the stored json is coerced, not fatal`() {
        val stored = """[{"type":"PLUG_FROM_THE_FUTURE","maxPowerKw":400.0,"count":2},{"type":"CCS2","maxPowerKw":150.0}]"""

        assertEquals(
            listOf(Connector(ConnectorType.UNKNOWN, 400.0, 2), Connector(ConnectorType.CCS2, 150.0, null)),
            converters.connectorsFromJson(stored),
        )
    }

    @Test
    fun `fields a newer build adds are ignored`() {
        val stored = """[{"type":"CCS2","maxPowerKw":150.0,"count":1,"colour":"blue"}]"""

        assertEquals(listOf(Connector(ConnectorType.CCS2, 150.0, 1)), converters.connectorsFromJson(stored))
    }
}
