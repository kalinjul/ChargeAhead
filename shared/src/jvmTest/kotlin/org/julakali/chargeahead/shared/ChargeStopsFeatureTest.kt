package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.data.CombinedSoCSource
import org.julakali.chargeahead.shared.data.ManualSoCSource
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.destination
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [Dispatchers.Unconfined] makes every emission run to completion before `emit` returns. */
class ChargeStopsFeatureTest {

    private val start = LatLon(48.9331, 11.4779)

    private class ControllableLocationSource(private val oneShot: Fix? = null) : LocationSource {
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
        override val updates: Flow<Fix> = fixes
        override suspend fun currentFix(): Fix? = oneShot
    }

    private class ControllableHardwareSoC : SoCSource {
        override val kind = SoCSourceKind.CAR_HARDWARE
        val states = MutableStateFlow<EnergyState?>(null)
        override val energy: Flow<EnergyState?> = states
    }

    private fun fix(position: LatLon = start, bearingDeg: Double? = 180.0) =
        Fix(position, bearingDeg, speedMps = 30.0, timestampMillis = 0L)

    private fun feature(location: LocationSource, socSource: SoCSource? = null) = ChargeStopsFeature(
        locationSource = location,
        socSource = socSource,
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `a fix is published once started`() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location)
        feature.start()
        assertNull(feature.currentFix.value)

        location.fixes.emit(fix())

        assertEquals(start, feature.currentFix.value?.position)
        feature.close()
    }

    /** Standing still, the course of the last movement is kept. */
    @Test
    fun `a fix without course keeps the last known one`() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location)
        feature.start()

        location.fixes.emit(fix(bearingDeg = 90.0))
        location.fixes.emit(Fix(start, bearingDeg = null, speedMps = 0.0, timestampMillis = 1_000L))

        assertEquals(90.0, feature.currentFix.value?.bearingDeg)
        feature.close()
    }

    /** Issue #36: the stream never emits; the one-shot fix must still arrive. */
    @Test
    fun `locate takes the one-shot fix instead of waiting for the stream`() {
        val feature = feature(ControllableLocationSource(oneShot = fix()))
        feature.start()

        feature.locate()

        assertEquals(start, feature.currentFix.value?.position)
        feature.close()
    }

    @Test
    fun `a failing location stream is reported`() {
        val location = object : LocationSource {
            override val updates: Flow<Fix> = flow { throw SecurityException("no permission") }
        }
        val feature = feature(location)

        feature.start()

        assertTrue(feature.locationFailed.value)
        feature.close()
    }

    @Test
    fun `starting twice keeps one stream`() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location)

        feature.start()
        feature.start()
        location.fixes.emit(fix())

        assertEquals(1, location.fixes.subscriptionCount.value)
        assertFalse(feature.locationFailed.value)
        feature.close()
    }

    @Test
    fun `after close further fixes are ignored`() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location)
        feature.start()
        location.fixes.emit(fix())

        feature.close()
        location.fixes.emit(fix(start.destination(180.0, 50.0)))

        assertEquals(start, feature.currentFix.value?.position)
    }

    @Test
    fun `the charge prefers the car over the driver`() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryPreferencesDataStore())
        settings.setManualSocPercent(50.0)
        val hardware = ControllableHardwareSoC()
        val feature = feature(
            ControllableLocationSource(),
            CombinedSoCSource(manual = ManualSoCSource(settings, TimeProvider { 0L }), hardware = hardware),
        )

        feature.start()

        // As long as the car stays silent, the manual entry applies.
        assertEquals(SoCSourceKind.MANUAL, feature.currentEnergy.value?.source)

        hardware.states.value = EnergyState(81.0, SoCSourceKind.CAR_HARDWARE, observedAtMillis = 0L)

        assertEquals(81.0, feature.currentEnergy.value?.socPercent)
        assertEquals(SoCSourceKind.CAR_HARDWARE, feature.currentEnergy.value?.source)
        feature.close()
    }
}
