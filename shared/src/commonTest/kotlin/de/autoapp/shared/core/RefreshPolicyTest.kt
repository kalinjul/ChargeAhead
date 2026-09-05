package de.autoapp.shared.core

import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.destination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshPolicyTest {

    private val policy = RefreshPolicy()
    private val start = LatLon(48.9331, 11.4779)

    private fun fix(
        position: LatLon = start,
        bearingDeg: Double? = 180.0,
        timestampMillis: Long = 0L,
    ) = Fix(position, bearingDeg, speedMps = 30.0, timestampMillis = timestampMillis)

    @Test
    fun withoutAPreviousFix_alwaysRecomputes() {
        assertTrue(policy.shouldRecompute(previous = null, current = fix()))
    }

    @Test
    fun shortDistanceAndShortTime_doesNotTrigger() {
        val before = fix(timestampMillis = 0L)
        val after = fix(position = start.destination(180.0, 1.0), timestampMillis = 30_000L)

        assertFalse(policy.shouldRecompute(before, after))
    }

    @Test
    fun overTwoKilometers_triggers() {
        val before = fix(timestampMillis = 0L)
        val after = fix(position = start.destination(180.0, 2.5), timestampMillis = 5_000L)

        assertTrue(policy.shouldRecompute(before, after))
    }

    @Test
    fun overOneMinute_triggersEvenWhileStationary() {
        val before = fix(timestampMillis = 0L)
        val after = fix(timestampMillis = 61_000L)

        assertTrue(policy.shouldRecompute(before, after))
    }

    @Test
    fun bearingChangeOver45Degrees_triggersImmediately() {
        // Leaving the highway: the entire previous corridor is now invalid.
        val before = fix(bearingDeg = 180.0, timestampMillis = 0L)
        val after = fix(bearingDeg = 260.0, timestampMillis = 1_000L)

        assertTrue(policy.shouldRecompute(before, after))
    }

    @Test
    fun bearingChangeAcrossTheZeroMark_isRecognizedAsSmall() {
        val before = fix(bearingDeg = 350.0, timestampMillis = 0L)
        val after = fix(bearingDeg = 10.0, timestampMillis = 1_000L)

        assertFalse(policy.shouldRecompute(before, after))
    }

    @Test
    fun backwardsRunningTimestamp_doesNotBlock() {
        // Fixes arriving late or timestamped after a clock correction must not
        // permanently block updates.
        val before = fix(timestampMillis = 500_000L)
        val after = fix(timestampMillis = 400_000L)

        assertTrue(policy.shouldRecompute(before, after))
    }

    @Test
    fun missingBearing_isNotCountedAsABearingChange() {
        val before = fix(bearingDeg = 180.0, timestampMillis = 0L)
        val after = fix(bearingDeg = null, timestampMillis = 1_000L)

        assertFalse(policy.shouldRecompute(before, after))
    }
}
