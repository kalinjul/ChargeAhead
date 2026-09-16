package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.Network
import org.julakali.chargeahead.shared.domain.SearchArea
import org.julakali.chargeahead.shared.domain.SiteRepository
import org.julakali.chargeahead.shared.domain.destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What issue #36 was about: the phone map kept reporting "no location" on a
 * device that had one. Three separate causes, one test each.
 *
 * [Dispatchers.Unconfined] for the same reason as in [ChargeStopsFeatureTest]:
 * every emission runs to completion before `emit` returns.
 */
class ChargeStopsFeatureLocationTest {

    private val start = LatLon(48.9331, 11.4779)

    private fun fix(position: LatLon = start, timestampMillis: Long = 0L) =
        Fix(position, bearingDeg = 180.0, speedMps = 30.0, timestampMillis = timestampMillis)

    private class CountingSiteRepository(private val fail: Boolean = false) : SiteRepository {
        var queries = 0
            private set

        override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> {
            queries++
            if (fail) throw IllegalStateException("Funkloch")
            return emptyList()
        }
    }

    private class ControllableLocationSource(private val oneShot: Fix? = null) : LocationSource {
        val fixes = MutableSharedFlow<Fix>(extraBufferCapacity = 8)
        override val updates: Flow<Fix> = fixes
        override suspend fun currentFix(): Fix? = oneShot
    }

    private fun feature(location: LocationSource, repository: SiteRepository) = ChargeStopsFeature(
        locationSource = location,
        repository = repository,
        dispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun failingSiteQuery_stillPublishesThePosition() = runBlocking {
        // The position used to be written only by a successful recompute, so a
        // backend hiccup on the very first fix left the map — and its location
        // button — without a location the device demonstrably had.
        val location = ControllableLocationSource()
        val feature = feature(location, CountingSiteRepository(fail = true))
        feature.start()

        location.fixes.emit(fix())

        assertEquals(ChargeStopsState.Phase.FAILED, feature.state.value.phase)
        assertEquals(ChargeStopsState.FailureReason.SITES_UNAVAILABLE, feature.state.value.failure)
        assertEquals(start, feature.state.value.position)

        feature.close()
    }

    @Test
    fun start_upgradesARunningSensorsOnlySession() = runBlocking {
        // Phone and car share one app-scoped instance. Whichever came second
        // used to be a silent no-op — with the car first, the phone's pipeline
        // never ran at all.
        val location = ControllableLocationSource()
        val repository = CountingSiteRepository()
        val feature = feature(location, repository)

        feature.startSensors()
        location.fixes.emit(fix(timestampMillis = 0L))
        // Sensors-only queries nothing, but it does publish the position —
        // the car surface needs it too.
        assertEquals(0, repository.queries)
        assertEquals(start, feature.state.value.position)

        feature.start()
        location.fixes.emit(fix(position = start.destination(180.0, 5.0), timestampMillis = 90_000L))

        assertTrue(repository.queries > 0)
        assertEquals(ChargeStopsState.Phase.READY, feature.state.value.phase)

        feature.close()
    }

    @Test
    fun startSensors_doesNotDisplaceTheRunningPipeline() = runBlocking {
        val location = ControllableLocationSource()
        val repository = CountingSiteRepository()
        val feature = feature(location, repository)

        feature.start()
        feature.startSensors()
        location.fixes.emit(fix())

        assertEquals(1, repository.queries)
        assertEquals(ChargeStopsState.Phase.READY, feature.state.value.phase)

        feature.close()
    }

    @Test
    fun locate_takesTheOneShotFixInsteadOfWaitingForTheStream() = runBlocking {
        // What the map's location button needs: the stream here never emits,
        // exactly like a device with no sky in view and network location off.
        val location = ControllableLocationSource(oneShot = fix())
        val repository = CountingSiteRepository()
        val feature = feature(location, repository)
        feature.start()

        assertEquals(ChargeStopsState.Phase.WAITING_FOR_LOCATION, feature.state.value.phase)

        feature.locate()

        assertEquals(start, feature.state.value.position)
        assertEquals(ChargeStopsState.Phase.READY, feature.state.value.phase)

        feature.close()
    }
}
