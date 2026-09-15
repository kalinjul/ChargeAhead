package de.autoapp.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncodedPolylineTest {

    /** The example from Google's description of the format. */
    @Test
    fun theCanonicalVector() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-9)
        assertEquals(-120.2, points[0].lon, 1e-9)
        assertEquals(40.7, points[1].lat, 1e-9)
        assertEquals(-120.95, points[1].lon, 1e-9)
        assertEquals(43.252, points[2].lat, 1e-9)
        assertEquals(-126.453, points[2].lon, 1e-9)
    }

    @Test
    fun aTruncatedTailIsDropped() {
        assertEquals(2, decodePolyline("_p~iF~ps|U_ulLnnqC").size)
        assertEquals(1, decodePolyline("_p~iF~ps|U_ulLnnq").size)
        assertTrue(decodePolyline("_p~iF").isEmpty())
        assertTrue(decodePolyline("").isEmpty())
    }
}
