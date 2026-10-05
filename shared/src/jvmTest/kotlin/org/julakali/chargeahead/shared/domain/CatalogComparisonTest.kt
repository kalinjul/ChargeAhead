package org.julakali.chargeahead.shared.domain

import org.julakali.chargeahead.shared.testPresets
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whether a catalog car has values of its own: compared by value, the name aside. */
class CatalogComparisonTest {

    private val preset = testPresets.first()
    private val asAdded = preset.toProfile()

    @Test
    fun `a car as the catalog has it does not differ`() {
        assertFalse(asAdded.differsFrom(preset))
    }

    @Test
    fun `an own name alone does not count`() {
        assertFalse(asAdded.copy(displayName = "Familienkutsche", ownName = true).differsFrom(preset))
    }

    @Test
    fun `an own consumption alone counts`() {
        assertTrue(asAdded.copy(consumptionKwhPer100Km = preset.consumptionKwhPer100Km + 1).differsFrom(preset))
    }

    @Test
    fun `battery and charging power count`() {
        assertTrue(asAdded.copy(usableBatteryKwh = preset.usableBatteryKwh - 1).differsFrom(preset))
        assertTrue(asAdded.copy(dcPeakPowerKw = 11.0).differsFrom(preset))
    }
}
