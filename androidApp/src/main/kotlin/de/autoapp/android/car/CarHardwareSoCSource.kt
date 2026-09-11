package de.autoapp.android.car

import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.EnergyLevel
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCDiagnostics
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * State of charge from the vehicle — the opportunistic upgrade over manual
 * input (ARCHITECTURE.md 1.2).
 *
 * **Expect it to deliver nothing.** In projection, very few head units
 * populate this data; `STATUS_UNIMPLEMENTED` is the normal case, not the
 * exception. Also required is the `com.google.android.gms.permission.CAR_FUEL`
 * permission, which the driver can deny.
 *
 * Lives in `androidApp` rather than `shared/androidMain`, even though
 * ARCHITECTURE.md section 3 places it there: the source needs a
 * `CarContext`, which only exists within a `Session`. The shared module
 * couldn't get at one anyway and would have to have it passed in — so the
 * class might as well live right where the context is created, and `shared`
 * stays free of the Car App Library.
 */
class CarHardwareSoCSource(
    private val carContext: CarContext,
    private val time: TimeProvider,
    /**
     * Where the result is written so the phone UI can display it — it has
     * no `CarContext` there and thus no way to look it up itself.
     */
    private val settingsStore: SettingsStore? = null,
) : SoCSource {

    companion object {
        /** Without this permission, the head unit delivers nothing. */
        const val CAR_FUEL_PERMISSION = "com.google.android.gms.permission.CAR_FUEL"
    }

    override val kind: SoCSourceKind = SoCSourceKind.CAR_HARDWARE

    override val energy: Flow<EnergyState?> = callbackFlow {
        // FIRST null, and unconditionally so: CombinedSoCSource combines this
        // flow via combine(), and combine waits until EVERY source has
        // delivered once. Without this initial null, the driver's manual
        // state of charge would go unused as long as the car stays silent —
        // which is almost always.
        trySend(null)

        val carInfo = try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
        } catch (unavailable: Exception) {
            // No CarHardware on this host. Not an error, just no value.
            record(SoCDiagnostics.Outcome.NO_CAR_HARDWARE, unavailable::class.simpleName)
            awaitClose { }
            return@callbackFlow
        }

        if (carContext.checkSelfPermission(CAR_FUEL_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            record(SoCDiagnostics.Outcome.NO_PERMISSION)
            awaitClose { }
            return@callbackFlow
        }

        val listener = OnCarDataAvailableListener<EnergyLevel> { level ->
            val state = level.toEnergyStateOrNull()
            record(
                outcome = if (state == null) {
                    SoCDiagnostics.Outcome.NO_DATA
                } else {
                    SoCDiagnostics.Outcome.AVAILABLE
                },
                detail = state?.let { "${it.socPercent.toInt()} %" }
                    ?: statusName(level.batteryPercent.status),
            )
            trySend(state)
        }

        try {
            carInfo.addEnergyLevelListener(ContextCompat.getMainExecutor(carContext), listener)
        } catch (missingPermission: SecurityException) {
            // CAR_FUEL not granted. The manual value takes over.
            record(SoCDiagnostics.Outcome.NO_PERMISSION, missingPermission.message)
            awaitClose { }
            return@callbackFlow
        }

        awaitClose { carInfo.removeEnergyLevelListener(listener) }
    }.conflate()

    /**
     * Persists the result for the phone UI. Fire-and-forget on the flow's own
     * scope so the car host's main-thread callback never blocks on the write
     * (the store serializes it off-Main); cancelled when the flow closes.
     */
    private fun CoroutineScope.record(outcome: SoCDiagnostics.Outcome, detail: String? = null) {
        val store = settingsStore ?: return
        launch { store.recordSoCDiagnostics(SoCDiagnostics(time.nowMillis(), outcome, detail)) }
    }

    /** Status code as a word — "STATUS_UNIMPLEMENTED" says more than "2". */
    private fun statusName(status: Int): String = when (status) {
        CarValue.STATUS_SUCCESS -> "STATUS_SUCCESS"
        CarValue.STATUS_UNIMPLEMENTED -> "STATUS_UNIMPLEMENTED"
        CarValue.STATUS_UNAVAILABLE -> "STATUS_UNAVAILABLE"
        CarValue.STATUS_UNKNOWN -> "STATUS_UNKNOWN"
        else -> "Status $status"
    }

    /**
     * Every value arrives as a [CarValue] with a status code. Only
     * `STATUS_SUCCESS` means something was actually measured — everything
     * else is a polite "I don't know" and must not pass as 0%.
     */
    private fun EnergyLevel.toEnergyStateOrNull(): EnergyState? {
        val percent = batteryPercent
        if (percent.status != CarValue.STATUS_SUCCESS) return null
        val value = percent.value?.toDouble() ?: return null
        if (value !in 0.0..100.0) return null

        return EnergyState(value, SoCSourceKind.CAR_HARDWARE, time.nowMillis())
    }
}
