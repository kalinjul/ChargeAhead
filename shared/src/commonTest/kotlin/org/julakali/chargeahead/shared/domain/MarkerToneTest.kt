package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkerToneTest {

    @Test
    fun slowIsBadMediumAWarningFastAndUpGood() {
        assertEquals(MarkerTone.BAD, ChargeSpeed.SLOW.tone)
        assertEquals(MarkerTone.WARN, ChargeSpeed.MEDIUM.tone)
        assertEquals(listOf(MarkerTone.GOOD, MarkerTone.GOOD, MarkerTone.GOOD), listOf(ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER).map { it.tone })
    }

    @Test
    fun plentyFreeIsGoodFewAWarningNoneBad() {
        assertEquals(MarkerTone.GOOD, AvailabilityLevel.GOOD.tone)
        assertEquals(MarkerTone.WARN, AvailabilityLevel.LOW.tone)
        assertEquals(MarkerTone.BAD, AvailabilityLevel.NONE.tone)
    }

    @Test
    fun boltsShowTheSpeedUnlessTheSiteIsOutOfOrder() {
        assertEquals(MarkerTone.GOOD, MarkerTone.bolts(ChargeSpeed.ULTRA, availability = null))
        assertEquals(MarkerTone.GOOD, MarkerTone.bolts(ChargeSpeed.ULTRA, SiteAvailability.Live(free = 0, total = 4)))
        assertEquals(MarkerTone.MUTED, MarkerTone.bolts(ChargeSpeed.ULTRA, SiteAvailability.OutOfOrder))
    }

    @Test
    fun theDotShowsLiveAvailabilityFirstThenTheSpeed() {
        assertEquals(MarkerTone.BAD, MarkerTone.dot(ChargeSpeed.HYPER, SiteAvailability.Live(free = 0, total = 2)))
        assertEquals(MarkerTone.MUTED, MarkerTone.dot(ChargeSpeed.HYPER, SiteAvailability.OutOfOrder))
        assertEquals(MarkerTone.WARN, MarkerTone.dot(ChargeSpeed.MEDIUM, availability = null))
    }
}
