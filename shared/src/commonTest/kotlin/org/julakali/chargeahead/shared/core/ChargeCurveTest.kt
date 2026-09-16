package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargeCurveTest {

    private val id4 = VehicleProfile(
        displayName = "VW ID.4 Pro",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 19.5,
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = 135.0,
    )

    @Test
    fun `an empty battery charges at the full peak`() {
        assertEquals(1.0, GenericChargeCurve.fractionOfPeakAt(0.0), 1e-9)
        assertEquals(1.0, GenericChargeCurve.fractionOfPeakAt(50.0), 1e-9)
    }

    @Test
    fun `the curve never rises again once it has started tapering`() {
        var previous = 1.1
        for (soc in 0..100) {
            val fraction = GenericChargeCurve.fractionOfPeakAt(soc.toDouble())
            assertTrue(fraction <= previous + 1e-9, "bei $soc % stieg die Kurve wieder: $fraction nach $previous")
            assertTrue(fraction > 0.0, "bei $soc % nimmt das Auto gar nichts mehr")
            previous = fraction
        }
    }

    /**
     * The whole reason a flat average factor had to go: the same twenty points
     * of charge cost far more time at the top of the battery than at the
     * bottom, and a plan that says otherwise sends the driver off too early.
     */
    @Test
    fun `the same band costs more time higher up the battery`() {
        val low = chargeMinutes(id4, sitePowerKw = 300.0, fromSocPercent = 10.0, toSocPercent = 30.0)
        val high = chargeMinutes(id4, sitePowerKw = 300.0, fromSocPercent = 60.0, toSocPercent = 80.0)

        assertTrue(high > low * 1.5, "oben muss es deutlich länger dauern: $high gegen $low")
    }

    @Test
    fun `a weak site caps the power, not the car`() {
        val fast = chargeMinutes(id4, sitePowerKw = 300.0, fromSocPercent = 10.0, toSocPercent = 40.0)
        val slow = chargeMinutes(id4, sitePowerKw = 50.0, fromSocPercent = 10.0, toSocPercent = 40.0)

        assertTrue(slow > fast, "50 kW muss länger dauern als 300 kW: $slow gegen $fast")
        // Below the plateau the car takes everything the site offers, so the
        // ratio is the power ratio.
        assertEquals(135.0 / 50.0, slow / fast, 1e-6)
    }

    /**
     * A catalogue peak is often a figure held for seconds on a preconditioned
     * pack. A small battery cannot sustain it whatever the spec sheet claims.
     */
    @Test
    fun `a small battery cannot hold a big claimed peak`() {
        val optimistic = id4.copy(displayName = "Klein", usableBatteryKwh = 40.0, dcPeakPowerKw = 150.0)

        val minutes = chargeMinutes(optimistic, sitePowerKw = 300.0, fromSocPercent = 10.0, toSocPercent = 40.0)

        // 12 kWh at the C-rate ceiling of 100 kW, all inside the plateau.
        assertEquals(12.0 / 100.0 * 60.0, minutes, 1e-6)
    }

    /** Without a known peak the site's power is all there is to go on. */
    @Test
    fun `an unknown peak falls back to the site`() {
        val unknown = id4.copy(dcPeakPowerKw = null)

        val minutes = chargeMinutes(unknown, sitePowerKw = 60.0, fromSocPercent = 0.0, toSocPercent = 20.0)

        assertEquals(77.0 * 0.2 / 60.0 * 60.0, minutes, 1e-6)
    }

    @Test
    fun `a band that goes nowhere takes no time`() {
        assertEquals(0.0, chargeMinutes(id4, 300.0, 50.0, 50.0))
        assertEquals(0.0, chargeMinutes(id4, 300.0, 60.0, 40.0))
        assertEquals(0.0, chargeMinutes(id4, 0.0, 10.0, 80.0))
    }

    /** Charging to full is the exception the planner allows, and it must stay finite. */
    @Test
    fun `charging to a hundred percent still terminates`() {
        val minutes = chargeMinutes(id4, sitePowerKw = 300.0, fromSocPercent = 10.0, toSocPercent = 100.0)

        assertTrue(minutes > 0.0 && minutes < 600.0, "unplausible Ladezeit: $minutes")
    }

    /** The stop optimizer splits a charge at arbitrary levels; the pieces must add up. */
    @Test
    fun `charge times are additive`() {
        val table = chargeTimeTable(id4, sitePowerKw = 300.0)
        val levels = listOf(3.7, 22.25, 50.0, 64.1, 80.0, 93.33, 100.0)
        for (a in levels) for (b in levels) for (c in levels) {
            if (a > b || b > c) continue
            assertEquals(
                table.minutesBetween(a, c),
                table.minutesBetween(a, b) + table.minutesBetween(b, c),
                1e-9,
                "$a → $b → $c",
            )
        }
    }

    @Test
    fun `the closed form matches a fine numeric integration`() {
        val table = chargeTimeTable(id4, sitePowerKw = 300.0)
        val peakKw = 135.0
        fun numeric(from: Double, to: Double): Double {
            val steps = ((to - from) / 0.001).toInt()
            val step = (to - from) / steps
            var minutes = 0.0
            for (n in 0 until steps) {
                val powerKw = peakKw * GenericChargeCurve.fractionOfPeakAt(from + (n + 0.5) * step)
                minutes += id4.usableBatteryKwh * step / 100.0 / powerKw * 60.0
            }
            return minutes
        }
        for ((from, to) in listOf(0.0 to 100.0, 12.3 to 47.9, 45.5 to 81.25, 79.9 to 99.1)) {
            assertEquals(numeric(from, to), table.minutesBetween(from, to), 1e-3, "$from → $to")
        }
    }

    /** What `chargeMinutes` returned before it became additive: a 1 % midpoint sum from `from`. */
    private fun legacyChargeMinutes(peakKw: Double, from: Double, to: Double): Double {
        var minutes = 0.0
        var soc = from
        while (soc < to) {
            val step = minOf(1.0, to - soc)
            minutes += id4.usableBatteryKwh * step / 100.0 /
                (peakKw * GenericChargeCurve.fractionOfPeakAt(soc + step / 2.0)) * 60.0
            soc += step
        }
        return minutes
    }

    @Test
    fun `the new charge times stay within a minute of the old ones`() {
        for ((from, to) in listOf(10.0 to 80.0, 5.5 to 62.3, 37.4 to 91.7, 60.0 to 100.0, 0.0 to 100.0)) {
            assertEquals(
                legacyChargeMinutes(135.0, from, to),
                chargeMinutes(id4, sitePowerKw = 300.0, fromSocPercent = from, toSocPercent = to),
                1.0,
                "$from → $to",
            )
        }
    }
}
