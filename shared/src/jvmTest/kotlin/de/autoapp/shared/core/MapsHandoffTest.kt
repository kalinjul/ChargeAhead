package de.autoapp.shared.core

import de.autoapp.shared.domain.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapsHandoffTest {

    @Test
    fun `directions url carries origin, destination and waypoints`() {
        val url = MapsHandoff.directionsUrl(
            origin = LatLon(52.37, 4.9),
            destination = LatLon(48.14, 11.58),
            waypoints = listOf(LatLon(51.47, 6.85)),
        )
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&travelmode=driving"))
        assertTrue("destination=48.14%2C11.58" in url)
        assertTrue("origin=52.37%2C4.9" in url)
        assertTrue("waypoints=51.47%2C6.85" in url)
    }

    @Test
    fun `without origin Maps uses the current position`() {
        val url = MapsHandoff.navigateUrl(LatLon(48.14, 11.58))
        assertFalse("origin=" in url)
    }

    @Test
    fun `waypoints are capped at the Maps limit`() {
        val many = (1..15).map { LatLon(50.0 + it, 6.0) }
        val url = MapsHandoff.directionsUrl(origin = null, destination = LatLon(48.0, 11.0), waypoints = many)
        val encoded = url.substringAfter("waypoints=")
        assertEquals(MapsHandoff.MAX_WAYPOINTS, encoded.split("%7C").size)
    }
}
