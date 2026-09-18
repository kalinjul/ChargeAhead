package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.data.ManualSoCSource
import org.julakali.chargeahead.shared.settings.InMemoryKeyValueStorage
import org.julakali.chargeahead.shared.settings.PersistentSettingsStore
import org.julakali.chargeahead.shared.domain.destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the chain location -> corridor -> source -> list as a whole.
 *
 * [Dispatchers.Unconfined] makes every emission run to completion before
 * `emit` returns, as long as the sources don't suspend.
 */
class ChargeStopsFeatureTest {

    private val start = LatLon(48.9331, 11.4779)

    private class FixedSiteRepository(private val sites: List<ChargeSite>) : SiteRepository {
        var queries = 0
            private set

        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            return sites
        }
    }

    private class BrokenSiteRepository : SiteRepository {
        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> =
            throw IllegalStateException("Funkloch")
    }

    private class ControllableLocationSource : LocationSource {
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
        override val updates: Flow<Fix> = fixes
    }

    private fun site(id: String, bearingDeg: Double, distanceKm: Double) = ChargeSite(
        id = id,
        name = "Ladepark $id",
        operator = "TestNetz",
        position = start.destination(bearingDeg, distanceKm),
        connectors = emptyList(),
    )

    private fun fix(
        position: LatLon = start,
        bearingDeg: Double? = 180.0,
        timestampMillis: Long = 0L,
    ) = Fix(position, bearingDeg, speedMps = 30.0, timestampMillis = timestampMillis)

    private fun feature(
        location: LocationSource,
        repository: SiteRepository,
        isDemo: Boolean = false,
        settingsStore: SettingsStore? = null,
    ) = ChargeStopsFeature(
        locationSource = location,
        repository = repository,
        settingsStore = settingsStore,
        socSource = settingsStore?.let { ManualSoCSource(it, TimeProvider { 0L }) },
        isDemo = isDemo,
        dispatcher = Dispatchers.Unconfined,
    )

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    private fun settingsStore() = PersistentSettingsStore(InMemoryKeyValueStorage())

    @Test
    fun beforeFirstFix_waitsForLocation() {
        val feature = feature(ControllableLocationSource(), FixedSiteRepository(emptyList()))
        feature.start()

        assertEquals(ChargeStopsState.Phase.WAITING_FOR_LOCATION, feature.state.value.phase)
        assertTrue(feature.state.value.stops.isEmpty())
        assertNull(feature.state.value.failure)

        feature.close()
    }

    @Test
    fun afterFirstFix_theListIsSortedByDistance() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(
            location,
            FixedSiteRepository(listOf(site("fern", 180.0, 60.0), site("nah", 180.0, 10.0))),
        )
        feature.start()

        location.fixes.emit(fix())

        assertEquals(ChargeStopsState.Phase.READY, feature.state.value.phase)
        assertEquals(listOf("nah", "fern"), feature.state.value.stops.map { it.site.id })

        feature.close()
    }

    @Test
    fun stopsMirrorsTheState() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location, FixedSiteRepository(listOf(site("a", 180.0, 10.0))))
        feature.start()

        location.fixes.emit(fix())

        assertEquals(feature.state.value.stops, feature.stops.value)

        feature.close()
    }

    @Test
    fun smallMovement_doesNotTriggerRecomputation() = runBlocking {
        val location = ControllableLocationSource()
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 10.0)))
        val feature = feature(location, repository)
        feature.start()

        location.fixes.emit(fix(timestampMillis = 0L))
        location.fixes.emit(fix(position = start.destination(180.0, 0.5), timestampMillis = 20_000L))

        assertEquals(1, repository.queries)

        feature.close()
    }

    @Test
    fun movementOverTwoKilometers_triggersRecomputation() = runBlocking {
        val location = ControllableLocationSource()
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 10.0)))
        val feature = feature(location, repository)
        feature.start()

        location.fixes.emit(fix(timestampMillis = 0L))
        location.fixes.emit(fix(position = start.destination(180.0, 3.0), timestampMillis = 20_000L))

        assertEquals(2, repository.queries)

        feature.close()
    }

    @Test
    fun repositoryFailure_leavesTheOldListStanding() = runBlocking {
        // The last known list stays when the source fails.
        val location = ControllableLocationSource()
        var broken = false
        val repository = object : SiteRepository {
            override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
                if (broken) throw IllegalStateException("Funkloch")
                return listOf(site("a", 180.0, 10.0))
            }
        }
        val feature = feature(location, repository)
        feature.start()

        location.fixes.emit(fix(timestampMillis = 0L))
        broken = true
        location.fixes.emit(fix(position = start.destination(180.0, 3.0), timestampMillis = 20_000L))

        assertEquals(ChargeStopsState.Phase.FAILED, feature.state.value.phase)
        assertEquals(ChargeStopsState.FailureReason.SITES_UNAVAILABLE, feature.state.value.failure)
        assertEquals(listOf("a"), feature.state.value.stops.map { it.site.id })

        feature.close()
    }

    @Test
    fun locationStreamFailure_isReportedAsLocationUnavailable() {
        val location = object : LocationSource {
            override val updates: Flow<Fix> = flow { throw SecurityException("no permission") }
        }
        val feature = feature(location, FixedSiteRepository(emptyList()))

        feature.start()

        assertEquals(ChargeStopsState.Phase.FAILED, feature.state.value.phase)
        assertEquals(ChargeStopsState.FailureReason.LOCATION_UNAVAILABLE, feature.state.value.failure)

        feature.close()
    }

    @Test
    fun demoMode_isPropagatedToTheState() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location, FixedSiteRepository(listOf(site("a", 180.0, 10.0))), isDemo = true)
        feature.start()

        location.fixes.emit(fix())

        assertTrue(feature.state.value.isDemo)

        feature.close()
    }

    @Test
    fun refresh_withoutLocation_isDeferredToTheFirstFix() = runBlocking {
        val location = ControllableLocationSource()
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 10.0)))
        val feature = feature(location, repository)
        feature.start()

        feature.refresh()
        location.fixes.emit(fix())

        assertEquals(ChargeStopsState.Phase.READY, feature.state.value.phase)

        feature.close()
    }

    @Test
    fun refresh_withLocation_recomputesImmediately() = runBlocking {
        val location = ControllableLocationSource()
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 10.0)))
        val feature = feature(location, repository)
        feature.start()
        location.fixes.emit(fix())

        feature.refresh()

        assertEquals(2, repository.queries)

        feature.close()
    }

    @Test
    fun afterClose_furtherFixesAreIgnored() = runBlocking {
        val location = ControllableLocationSource()
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 10.0)))
        val feature = feature(location, repository)
        feature.start()
        location.fixes.emit(fix(timestampMillis = 0L))

        feature.close()
        location.fixes.emit(fix(position = start.destination(180.0, 50.0), timestampMillis = 300_000L))

        assertEquals(1, repository.queries)
    }

    @Test
    fun persistentFailure_doesNotResultInAnEmptyList() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location, BrokenSiteRepository())
        feature.start()

        location.fixes.emit(fix())

        assertEquals(ChargeStopsState.Phase.FAILED, feature.state.value.phase)
        assertTrue(feature.state.value.stops.isEmpty())

        feature.close()
    }

    // --- vehicle profile and charge state ---

    @Test
    fun withoutProfile_reachabilityStaysUnknown() = runBlocking {
        val location = ControllableLocationSource()
        val feature = feature(location, FixedSiteRepository(listOf(site("a", 180.0, 10.0))))
        feature.start()

        location.fixes.emit(fix())

        assertEquals(Reachability.UNKNOWN, feature.state.value.stops.single().reachability)

        feature.close()
    }

    @Test
    fun withProfileAndStateOfCharge_reachabilityIsClassified() = runBlocking {
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        settingsStore.setVehicle(vehicle)
        settingsStore.setManualSocPercent(80.0)

        val feature = feature(
            location,
            FixedSiteRepository(listOf(site("a", 180.0, 10.0))),
            settingsStore = settingsStore,
        )
        feature.start()
        location.fixes.emit(fix())

        val stop = feature.state.value.stops.single()
        assertEquals(Reachability.REACHABLE, stop.reachability)
        assertTrue(stop.socOnArrivalPercent != null, "Arrival SoC is missing")

        feature.close()
    }

    @Test
    fun aChangeInStateOfCharge_recomputesImmediately() = runBlocking {
        // Must take effect without waiting for the next location fix.
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        settingsStore.setVehicle(vehicle)
        settingsStore.setManualSocPercent(80.0)

        // 25 km falls within the corridor whether the battery is full or nearly
        // empty: at 15% the radius shrinks to 30 km (minimum search radius 25 km x 1.2).
        val repository = FixedSiteRepository(listOf(site("a", 180.0, 25.0)))
        val feature = feature(location, repository, settingsStore = settingsStore)
        feature.start()
        location.fixes.emit(fix())
        assertEquals(Reachability.REACHABLE, feature.state.value.stops.single().reachability)

        // 15% - 10% reserve = 5% of 77 kWh = 3.85 kWh -> about 21 km of range.
        // Distance: 25 km straight-line x 1.25 detour factor = 31 km.
        settingsStore.setManualSocPercent(15.0)

        assertEquals(Reachability.UNREACHABLE, feature.state.value.stops.single().reachability)

        feature.close()
    }

    @Test
    fun aNewProfile_recomputesImmediately() = runBlocking {
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        settingsStore.setManualSocPercent(80.0)

        val feature = feature(
            location,
            FixedSiteRepository(listOf(site("a", 180.0, 10.0))),
            settingsStore = settingsStore,
        )
        feature.start()
        location.fixes.emit(fix())
        assertEquals(Reachability.UNKNOWN, feature.state.value.stops.single().reachability)

        settingsStore.setVehicle(vehicle)

        assertEquals(Reachability.REACHABLE, feature.state.value.stops.single().reachability)

        feature.close()
    }

    @Test
    fun emptyBattery_doesNotMakeTheListDisappear() = runBlocking {
        // The minimum search radius applies below the reserve.
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        settingsStore.setVehicle(vehicle)
        settingsStore.setManualSocPercent(2.0)

        val feature = feature(
            location,
            FixedSiteRepository(listOf(site("a", 180.0, 5.0))),
            settingsStore = settingsStore,
        )
        feature.start()
        location.fixes.emit(fix())

        assertEquals(1, feature.state.value.stops.size)
        assertEquals(Reachability.UNREACHABLE, feature.state.value.stops.single().reachability)

        feature.close()
    }

    @Test
    fun justUnreachableStaysVisible_farAwayDoesNot() {
        // The 1.2x margin: a band just beyond range is still fetched and flagged.
        runBlocking {
            val location = ControllableLocationSource()
            val settingsStore = settingsStore()
            settingsStore.setVehicle(vehicle)
            // 20% - 10% = 10% of 77 kWh = 7.7 kWh -> just under 43 km of range,
            // so a search radius of about 51 km.
            settingsStore.setManualSocPercent(20.0)

            val feature = feature(
                location,
                FixedSiteRepository(listOf(site("knapp", 180.0, 45.0), site("weitweg", 180.0, 120.0))),
                settingsStore = settingsStore,
            )
            feature.start()
            location.fixes.emit(fix())

            assertEquals(listOf("knapp"), feature.state.value.stops.map { it.site.id })
            assertEquals(Reachability.UNREACHABLE, feature.state.value.stops.single().reachability)

            feature.close()
        }
    }

    // --- network filter threading ---

    private class RecordingSiteRepository : SiteRepository {
        val capturedNetworks = mutableListOf<List<Network>>()
        override suspend fun load(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            capturedNetworks += networks
            return emptyList()
        }
    }

    @Test
    fun `active network filter passes resolved selection to sitesIn`() = runBlocking {
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        val ionityKey = "ionity"
        settingsStore.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf(ionityKey)))

        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore = settingsStore)
        feature.start()
        location.fixes.emit(fix())

        val called = repository.capturedNetworks.last()
        assertTrue(called.isNotEmpty(), "expected non-empty selection when filter is active")
        assertEquals(listOf(NetworkCatalog.byKey(ionityKey)), called)

        feature.close()
    }

    @Test
    fun `inactive network filter passes empty list to sitesIn`() = runBlocking {
        val location = ControllableLocationSource()
        val settingsStore = settingsStore()
        // onlyPreferred = false → isActive = false
        settingsStore.setNetworks(NetworkPreferences(onlyPreferred = false, preferredOperators = setOf("ionity")))

        val repository = RecordingSiteRepository()
        val feature = feature(location, repository, settingsStore = settingsStore)
        feature.start()
        location.fixes.emit(fix())

        assertTrue(repository.capturedNetworks.last().isEmpty(), "expected empty list when filter is inactive")

        feature.close()
    }
}
