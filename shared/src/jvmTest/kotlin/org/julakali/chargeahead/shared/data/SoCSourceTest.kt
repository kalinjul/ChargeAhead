package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoCSourceTest {

    private val clock = TimeProvider { 1_000L }

    private class FixedSource(
        override val kind: SoCSourceKind,
        value: EnergyState?,
    ) : SoCSource {
        val state = MutableStateFlow(value)
        override val energy: Flow<EnergyState?> = state
    }

    private fun fromTheCar(socPercent: Double) =
        EnergyState(socPercent, SoCSourceKind.CAR_HARDWARE, observedAtMillis = 0L)

    @Test
    fun withoutInput_theManualSourceReturnsNothing() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())

        assertNull(ManualSoCSource(store, clock).energy.first())
    }

    @Test
    fun withInput_theManualSourceReturnsTheValue() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(64.0)

        val state = ManualSoCSource(store, clock).energy.first()

        assertEquals(64.0, state?.socPercent)
        assertEquals(SoCSourceKind.MANUAL, state?.source)
        assertEquals(1_000L, state?.observedAtMillis)
    }

    @Test
    fun withoutVehicleSource_theInputApplies() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(50.0)

        val combined = CombinedSoCSource(ManualSoCSource(store, clock), hardware = null)

        assertEquals(50.0, combined.energy.first()?.socPercent)
    }

    @Test
    fun theCarBeatsTheInput() = runBlocking {
        // A value from the vehicle wins.
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(50.0)
        val carSource = FixedSource(SoCSourceKind.CAR_HARDWARE, fromTheCar(73.0))

        val combined = CombinedSoCSource(ManualSoCSource(store, clock), carSource)

        val state = combined.energy.first()
        assertEquals(73.0, state?.socPercent)
        assertEquals(SoCSourceKind.CAR_HARDWARE, state?.source)
    }

    @Test
    fun ifTheCarIsSilent_theInputAppliesAgain() = runBlocking {
        // The head unit delivers nothing.
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(50.0)
        val carSource = FixedSource(SoCSourceKind.CAR_HARDWARE, value = null)

        val combined = CombinedSoCSource(ManualSoCSource(store, clock), carSource)

        val state = combined.energy.first()
        assertEquals(50.0, state?.socPercent)
        assertEquals(SoCSourceKind.MANUAL, state?.source)
    }

    @Test
    fun ifBothAreSilent_thereIsNoChargeLevel() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        val combined = CombinedSoCSource(
            ManualSoCSource(store, clock),
            FixedSource(SoCSourceKind.CAR_HARDWARE, value = null),
        )

        assertNull(combined.energy.first())
    }

    @Test
    fun aCarReading_isStoredForAfterTheDisconnect() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(90.0)
        val car = FixedSource(SoCSourceKind.CAR_HARDWARE, fromTheCar(42.4))

        RememberingSoCSource(car, store).energy.first()

        assertEquals(42.4, store.manualSocPercent.value)
    }

    @Test
    fun noCarReading_keepsTheStoredLevel() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(90.0)
        val car = FixedSource(SoCSourceKind.CAR_HARDWARE, null)

        RememberingSoCSource(car, store).energy.first()

        assertEquals(90.0, store.manualSocPercent.value)
    }

    @Test
    fun aReadingWithinTheSamePercent_isNotRewritten() = runBlocking {
        val store = PersistentSettingsStore(InMemoryKeyValueStorage())
        store.setManualSocPercent(42.0)
        val car = FixedSource(SoCSourceKind.CAR_HARDWARE, fromTheCar(42.3))

        RememberingSoCSource(car, store).energy.first()

        assertEquals(42.0, store.manualSocPercent.value)
    }
}
