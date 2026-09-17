package org.julakali.chargeahead.android.car

import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn

/**
 * The session's one subscription to the vehicle's energy level, shared by the
 * SoC source and the debug recorder: only the first listener of a data type
 * gets an initial value from the host.
 *
 * A CAR_FUEL grant mid-session registers the listener right away.
 */
class CarEnergyLevels(
    private val carContext: CarContext,
    permissions: CarPermissions,
    /** Session-lived and on the main thread. */
    scope: CoroutineScope,
) {

    /** What the energy subscription currently yields. */
    sealed interface Reading {
        /** No CarHardware on this host. */
        data class NoCarHardware(val cause: String?) : Reading

        /** CAR_FUEL not granted (yet). */
        data class NoPermission(val message: String? = null) : Reading

        /** The host answered — which may still be a status other than success. */
        data class Level(val level: EnergyLevel) : Reading
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val readings: SharedFlow<Reading> = permissions.granted(CAR_FUEL_PERMISSION)
        .flatMapLatest(::subscribe)
        .conflate()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    private fun subscribe(permitted: Boolean): Flow<Reading> = callbackFlow {
        val carInfo = try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
        } catch (unavailable: Exception) {
            trySend(Reading.NoCarHardware(unavailable::class.simpleName))
            awaitClose { }
            return@callbackFlow
        }

        if (!permitted) {
            trySend(Reading.NoPermission())
            awaitClose { }
            return@callbackFlow
        }

        val listener = OnCarDataAvailableListener<EnergyLevel> { level ->
            trySend(Reading.Level(level))
        }
        try {
            carInfo.addEnergyLevelListener(ContextCompat.getMainExecutor(carContext), listener)
        } catch (missingPermission: SecurityException) {
            trySend(Reading.NoPermission(missingPermission.message))
            awaitClose { }
            return@callbackFlow
        }

        awaitClose { carInfo.removeEnergyLevelListener(listener) }
    }

    companion object {
        /** Without this permission, the head unit delivers no energy data. */
        const val CAR_FUEL_PERMISSION = "com.google.android.gms.permission.CAR_FUEL"
    }
}
