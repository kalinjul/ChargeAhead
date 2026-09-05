package de.autoapp.shared.core

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.VehicleProfile
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangeCalculatorTest {

    // 77 kWh usable, 18 kWh/100 km — roughly the size of a mid-range EV.
    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2, ConnectorType.TYPE2),
    )

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 0.05) {
        assertTrue(
            abs(expected - actual) < tolerance,
            "Expected approx. $expected, was $actual",
        )
    }

    @Test
    fun range_followsTheFormulaFromTheArchitecture() {
        // (80 - 10)% of 77 kWh = 53.9 kWh; / 18 * 100 = 299.4 km
        assertApprox(299.44, RangeCalculator.rangeKm(vehicle, socPercent = 80.0))
    }

    @Test
    fun range_accountsForTheReserve() {
        val withReserve = RangeCalculator.rangeKm(vehicle, socPercent = 50.0, reserveSocPercent = 10.0)
        val withoutReserve = RangeCalculator.rangeKm(vehicle, socPercent = 50.0, reserveSocPercent = 0.0)

        assertTrue(withReserve < withoutReserve)
        assertApprox(171.11, withReserve)
        assertApprox(213.89, withoutReserve)
    }

    @Test
    fun range_atTheReserve_isZero() {
        assertEquals(0.0, RangeCalculator.rangeKm(vehicle, socPercent = 10.0))
    }

    @Test
    fun range_belowTheReserve_isZeroNotNegative() {
        // A negative range would otherwise propagate through the classification
        // and into the list.
        assertEquals(0.0, RangeCalculator.rangeKm(vehicle, socPercent = 3.0))
    }

    @Test
    fun higherConsumption_shortensTheRange() {
        val efficient = RangeCalculator.rangeKm(vehicle, socPercent = 80.0)
        val thirsty = RangeCalculator.rangeKm(
            vehicle.copy(consumptionKwhPer100Km = 25.0),
            socPercent = 80.0,
        )

        assertTrue(thirsty < efficient)
    }

    @Test
    fun arrivalSoc_subtractsTheTripConsumption() {
        // 100 km at 18 kWh/100 km = 18 kWh = 23.4% of 77 kWh
        assertApprox(56.62, RangeCalculator.socOnArrivalPercent(vehicle, socPercent = 80.0, distanceKm = 100.0))
    }

    @Test
    fun arrivalSoc_atZeroDistance_isTheCurrentState() {
        assertApprox(80.0, RangeCalculator.socOnArrivalPercent(vehicle, socPercent = 80.0, distanceKm = 0.0))
    }

    @Test
    fun arrivalSoc_mayFallBelowTheReserve() {
        // The reserve limits the range, not the display: this is the number
        // the driver reads, matching what their car itself shows.
        val soc = RangeCalculator.socOnArrivalPercent(vehicle, socPercent = 20.0, distanceKm = 60.0)

        assertTrue(soc < 10.0 && soc > 0.0, "Was $soc")
    }

    @Test
    fun arrivalSoc_isNeverNegative() {
        assertEquals(0.0, RangeCalculator.socOnArrivalPercent(vehicle, socPercent = 20.0, distanceKm = 500.0))
    }

    @Test
    fun aVehicleWithoutBatteryOrConsumption_isRejected() {
        // Otherwise the range formula would divide by zero.
        val invalid = listOf(
            { vehicle.copy(usableBatteryKwh = 0.0) },
            { vehicle.copy(consumptionKwhPer100Km = 0.0) },
            { vehicle.copy(usableBatteryKwh = -1.0) },
        )
        invalid.forEach { create ->
            var thrown = false
            try {
                create()
            } catch (expected: IllegalArgumentException) {
                thrown = true
            }
            assertTrue(thrown, "Invalid profile got through")
        }
    }
}
