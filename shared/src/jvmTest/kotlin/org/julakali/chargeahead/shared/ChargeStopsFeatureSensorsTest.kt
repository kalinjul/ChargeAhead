package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.data.CombinedSoCSource
import org.julakali.chargeahead.shared.data.ManualSoCSource
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sensors-only mode for the car UI: location and charge state are
 * tracked, but no charging stops are computed.
 */
class ChargeStopsFeatureSensorsTest {

    private val start = LatLon(48.9331, 11.4779)

    private class CountingSiteRepository : SiteRepository {
        var queries = 0
            private set

        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            return emptyList()
        }

        override suspend fun invalidate() = Unit
    }

    private class ControllableLocationSource : LocationSource {
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
        override val updates: Flow<Fix> = fixes
    }

    private class ControllableHardwareSoC : SoCSource {
        override val kind = SoCSourceKind.CAR_HARDWARE
        val states = MutableStateFlow<EnergyState?>(null)
        override val energy: Flow<EnergyState?> = states
    }

    private fun fix() = Fix(start, bearingDeg = 180.0, speedMps = 30.0, timestampMillis = 0L)

    @Test
    fun startSensors_tracksTheFix_withoutQueryingSites() = runBlocking {
        val location = ControllableLocationSource()
        val repository = CountingSiteRepository()
        val feature = ChargeStopsFeature(
            locationSource = location,
            repository = repository,
            dispatcher = Dispatchers.Unconfined,
        )

        feature.startSensors()
        assertNull(feature.currentFix.value)

        location.fixes.emit(fix())

        assertEquals(start, feature.currentFix.value?.position)
        assertEquals(0, repository.queries)

        feature.close()
    }

    @Test
    fun startSensors_energyPrefersTheCarOverTheDriver() = runBlocking {
        val settings = PersistentSettingsStore(InMemoryKeyValueStorage())
        settings.setManualSocPercent(50.0)
        val hardware = ControllableHardwareSoC()
        val feature = ChargeStopsFeature(
            locationSource = ControllableLocationSource(),
            repository = CountingSiteRepository(),
            settingsStore = settings,
            socSource = CombinedSoCSource(
                manual = ManualSoCSource(settings, TimeProvider { 0L }),
                hardware = hardware,
            ),
            dispatcher = Dispatchers.Unconfined,
        )

        feature.startSensors()

        // As long as the car stays silent, the manual entry applies.
        assertEquals(50.0, feature.currentEnergy.value?.socPercent)
        assertEquals(SoCSourceKind.MANUAL, feature.currentEnergy.value?.source)

        hardware.states.value = EnergyState(81.0, SoCSourceKind.CAR_HARDWARE, observedAtMillis = 0L)

        assertEquals(81.0, feature.currentEnergy.value?.socPercent)
        assertEquals(SoCSourceKind.CAR_HARDWARE, feature.currentEnergy.value?.source)

        feature.close()
    }
}
