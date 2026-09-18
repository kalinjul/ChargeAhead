package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.core.CourseTracker
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.LocationSource
import org.julakali.chargeahead.shared.domain.SoCSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the vehicle is, which way it's heading and how full its battery is —
 * one per location source: the phone's, and one per car session.
 *
 * Lifecycle: [start] begins listening, [close] ends it for good.
 */
class ChargeStopsFeature(
    private val locationSource: LocationSource,
    private val socSource: SoCSource? = null,
    /** The sites come from [org.julakali.chargeahead.shared.data.DemoSiteSource], not real data. */
    val isDemo: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Called by [close]. */
    private val onClose: () -> Unit = {},
    /** A one-shot task run on the feature's own scope at creation. */
    private val onStart: (suspend () -> Unit)? = null,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        onStart?.let { task -> scope.launch { task() } }
    }

    private val courseTracker = CourseTracker()

    // Guards the course tracker against locate() running alongside the stream.
    private val fixMutex = Mutex()

    private val mutableFix = MutableStateFlow<Fix?>(null)

    /** Last location fix, with the best course known. */
    val currentFix: StateFlow<Fix?> = mutableFix.asStateFlow()

    private val mutableEnergy = MutableStateFlow<EnergyState?>(null)

    /** Latest combined charge state: the car's measurement when it delivers one, otherwise the manual entry. */
    val currentEnergy: StateFlow<EnergyState?> = mutableEnergy.asStateFlow()

    private val mutableLocationFailed = MutableStateFlow(false)

    /** The location stream ended with an error — usually missing permission or location services off. */
    val locationFailed: StateFlow<Boolean> = mutableLocationFailed.asStateFlow()

    private var locationJob: Job? = null
    private var sensorJob: Job? = null

    /** Starts tracking location and charge state. Idempotent. */
    fun start() {
        if (sensorJob?.isActive != true) {
            sensorJob = scope.launch {
                socSource?.energy?.collect { mutableEnergy.value = it }
            }
        }
        if (locationJob?.isActive != true) {
            mutableLocationFailed.value = false
            locationJob = scope.launch {
                locationSource.updates
                    .catch { error ->
                        logWarning("Location stream ended", error)
                        mutableLocationFailed.value = true
                    }
                    .collect(::onFix)
            }
        }
    }

    /**
     * Asks the location source for a fix right now instead of waiting for the
     * stream's next update.
     */
    fun locate() {
        scope.launch {
            val fix = try {
                locationSource.currentFix()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logWarning("Could not read the current location", error)
                null
            }
            fix?.let { onFix(it) }
        }
    }

    /** Ends processing for good. The instance is unusable afterward. */
    fun close() {
        scope.cancel()
        locationJob = null
        sensorJob = null
        onClose()
    }

    private suspend fun onFix(rawFix: Fix) = fixMutex.withLock {
        mutableFix.value = courseTracker.update(rawFix)
    }
}
