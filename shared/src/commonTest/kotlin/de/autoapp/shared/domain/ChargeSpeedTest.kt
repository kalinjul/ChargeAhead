package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChargeSpeedTest {

    @Test
    fun acPowerIsSlow() {
        assertEquals(ChargeSpeed.SLOW, ChargeSpeed.of(11.0))
        assertEquals(ChargeSpeed.SLOW, ChargeSpeed.of(22.0))
    }

    @Test
    fun theGapBelowFiftyStaysSlow() {
        // No class exists between the AC ceiling and the 50 kW step; a 43 kW
        // site is closer to slow than to a DC charger and must not look green.
        assertEquals(ChargeSpeed.SLOW, ChargeSpeed.of(43.0))
    }

    @Test
    fun thresholdsAreInclusive() {
        assertEquals(ChargeSpeed.MEDIUM, ChargeSpeed.of(50.0))
        assertEquals(ChargeSpeed.FAST, ChargeSpeed.of(100.0))
        assertEquals(ChargeSpeed.ULTRA, ChargeSpeed.of(150.0))
        assertEquals(ChargeSpeed.HYPER, ChargeSpeed.of(300.0))
    }

    @Test
    fun boltsGrowWithPower() {
        assertEquals(1, ChargeSpeed.of(75.0).bolts)
        assertEquals(2, ChargeSpeed.of(200.0).bolts)
        assertEquals(3, ChargeSpeed.of(400.0).bolts)
    }
}
