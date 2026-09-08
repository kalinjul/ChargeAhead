package de.autoapp.shared

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Fix
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.LocationSource
import de.autoapp.shared.domain.SearchArea
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Reachability
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SiteRepository
import de.autoapp.shared.domain.TimeProvider
import de.autoapp.shared.domain.VehicleProfile
import de.autoapp.shared.data.ManualSoCSource
import de.autoapp.shared.data.OperatorCatalog
import de.autoapp.shared.db.DatabaseDriverFactory
import de.autoapp.shared.db.createChargeSiteDatabase
import de.autoapp.shared.settings.InMemoryKeyValueStorage
import de.autoapp.shared.settings.PersistentSettingsStore
import de.autoapp.shared.domain.destination
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
 * [Dispatchers.Unconfined] instead of a test dispatcher: this makes every
 * emission run to completion before `emit` returns, so the test doesn't need
 * kotlinx-coroutines-test. This requires that the source used doesn't actually
 * suspend — none here does.
 */
class ChargeStopsFeatureTest {

    private val start = LatLon(48.9331, 11.4779)

    private class FixedSiteRepository(private val sites: List<ChargeSite>) : SiteRepository {
        var queries = 0
            private set

        override suspend fun sitesIn(area: SearchArea): List<ChargeSite> {
            queries++
            return sites
        }
    }

    private class BrokenSiteRepository : SiteRepository {
        override suspend fun sitesIn(area: SearchArea): List<ChargeSite> =
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
        // When out of signal range, the last known list is worth more than an empty one.
        val location = ControllableLocationSource()
        var broken = false
        val repository = object : SiteRepository {
            override suspend fun sitesIn(area: SearchArea): List<ChargeSite> {
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

    // --- from M2: vehicle profile and charge state ---

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
        // It changes range, classification, and corridor size. Waiting for the
        // next location fix would mean up to two more kilometers of driving.
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
        // The corridor would otherwise be zero kilometers wide — exactly when
        // the driver needs the list the most.
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
        // The 1.2x margin on the search radius exists exactly for this: a band
        // just beyond range is still fetched and flagged (ARCHITECTURE.md 5.2),
        // while anything far beyond that would just be noise.
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

    @Test
    fun `seeds the network picker from the local store before the first fix`() = runBlocking {
        val database = createChargeSiteDatabase(DatabaseDriverFactory())
        database.chargeSitesQueries.upsertSite("a", "test", "Ladepark a", "IONITY GmbH", 48.9, 11.4, "CCS2:150.0:4", null, null, null)
        database.chargeSitesQueries.upsertSite("b", "test", "Ladepark b", "Ionity", 48.8, 11.3, "CCS2:150.0:4", null, null, null)

        val feature = ChargeStopsFeature(
            locationSource = ControllableLocationSource(),
            repository = FixedSiteRepository(emptyList()),
            operatorCatalog = OperatorCatalog(database),
            dispatcher = Dispatchers.Unconfined,
        )
        feature.start()

        assertEquals(listOf("Ionity"), feature.currentState.availableOperators.map { it.displayName })
        assertEquals(2, feature.currentState.availableOperators.single().siteCount)
        feature.close()
    }
}
