package de.autoapp.android.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.EnergyLevel
import androidx.car.app.hardware.info.EnergyProfile
import androidx.car.app.hardware.info.Mileage
import androidx.car.app.hardware.info.Model
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import de.autoapp.shared.domain.CarDataKind
import de.autoapp.shared.domain.CarDataPoint
import de.autoapp.shared.domain.CarDataStatus
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * Captures everything the car hardware offers and persists it for the
 * phone's debug view — the generalization of what [CarHardwareSoCSource]
 * does for the charge level alone.
 *
 * Same expectations apply: most head units deliver `STATUS_UNIMPLEMENTED`
 * for most of this, and that is exactly what the debug view is for — seeing
 * per data point what this particular car actually provides. Verifiable only
 * with real hardware (ROADMAP open item 1); on the DHU everything reads
 * NO_DATA at best.
 */
class CarHardwareDebugRecorder(
    private val carContext: CarContext,
    private val time: TimeProvider,
    private val settingsStore: SettingsStore,
) {

    private var carInfo: CarInfo? = null
    private var energyListener: OnCarDataAvailableListener<EnergyLevel>? = null
    private var speedListener: OnCarDataAvailableListener<Speed>? = null
    private var mileageListener: OnCarDataAvailableListener<Mileage>? = null

    fun start() {
        val info = try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
        } catch (unavailable: Exception) {
            CarDataKind.entries.forEach { record(it, CarDataStatus.NO_CAR_HARDWARE) }
            return
        }
        carInfo = info
        val executor = ContextCompat.getMainExecutor(carContext)

        // One-shot values: no permission required.
        info.fetchModel(executor) { model -> record(CarDataKind.MODEL, model.toPoint()) }
        info.fetchEnergyProfile(executor) { profile ->
            record(CarDataKind.ENERGY_PROFILE, profile.toPoint())
        }

        // Streamed values, each behind its own car permission.
        energyListener = listenOrRecordDenied(CarDataKind.BATTERY_PERCENT, PERMISSION_ENERGY) {
            OnCarDataAvailableListener<EnergyLevel> { level ->
                record(CarDataKind.BATTERY_PERCENT, level.batteryPercent.toPoint { "${it.roundToInt()} %" })
                record(
                    CarDataKind.RANGE,
                    level.rangeRemainingMeters.toPoint { "${(it / 1000.0).roundToInt()} km" },
                )
                record(CarDataKind.ENERGY_IS_LOW, level.energyIsLow.toPoint { if (it) "ja" else "nein" })
            }.also { info.addEnergyLevelListener(executor, it) }
        }

        speedListener = listenOrRecordDenied(CarDataKind.SPEED, PERMISSION_SPEED) {
            OnCarDataAvailableListener<Speed> { speed ->
                record(
                    CarDataKind.SPEED,
                    speed.displaySpeedMetersPerSecond.toPoint { "${(it * 3.6).roundToInt()} km/h" },
                )
            }.also { info.addSpeedListener(executor, it) }
        }

        mileageListener = listenOrRecordDenied(CarDataKind.ODOMETER, PERMISSION_MILEAGE) {
            OnCarDataAvailableListener<Mileage> { mileage ->
                record(
                    CarDataKind.ODOMETER,
                    mileage.odometerMeters.toPoint { "${(it / 1000.0).roundToInt()} km" },
                )
            }.also { info.addMileageListener(executor, it) }
        }
    }

    fun stop() {
        val info = carInfo ?: return
        energyListener?.let(info::removeEnergyLevelListener)
        speedListener?.let(info::removeSpeedListener)
        mileageListener?.let(info::removeMileageListener)
        energyListener = null; speedListener = null; mileageListener = null
    }

    /**
     * Registers a listener if its permission is granted; otherwise records
     * NO_PERMISSION for [kind] so the debug view can say what to fix.
     * Registration itself may also throw SecurityException — same outcome.
     */
    private fun <T> listenOrRecordDenied(
        kind: CarDataKind,
        permission: String,
        register: () -> OnCarDataAvailableListener<T>,
    ): OnCarDataAvailableListener<T>? {
        if (carContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            record(kind, CarDataStatus.NO_PERMISSION)
            return null
        }
        return try {
            register()
        } catch (denied: SecurityException) {
            record(kind, CarDataStatus.NO_PERMISSION)
            null
        }
    }

    private fun Model.toPoint(): CarDataPoint {
        val parts = listOfNotNull(
            manufacturer.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value,
            name.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value,
            year.takeIf { it.status == CarValue.STATUS_SUCCESS }?.value?.toString(),
        )
        return if (parts.isEmpty()) {
            CarDataPoint(CarDataKind.MODEL, CarDataStatus.NO_DATA, null, time.nowMillis())
        } else {
            CarDataPoint(CarDataKind.MODEL, CarDataStatus.AVAILABLE, parts.joinToString(" "), time.nowMillis())
        }
    }

    private fun EnergyProfile.toPoint(): CarDataPoint {
        val connectors = evConnectorTypes.takeIf { it.status == CarValue.STATUS_SUCCESS }
            ?.value?.joinToString { connectorName(it) }
        return if (connectors.isNullOrEmpty()) {
            CarDataPoint(CarDataKind.ENERGY_PROFILE, CarDataStatus.NO_DATA, null, time.nowMillis())
        } else {
            CarDataPoint(CarDataKind.ENERGY_PROFILE, CarDataStatus.AVAILABLE, connectors, time.nowMillis())
        }
    }

    /**
     * Only `STATUS_SUCCESS` with a value counts as measured — everything else
     * is a polite "I don't know" (see CarHardwareSoCSource). The kind on the
     * returned point is a placeholder; [record] stamps the real one.
     */
    private fun <T : Any> CarValue<T>.toPoint(format: (T) -> String): CarDataPoint {
        val measured = value.takeIf { status == CarValue.STATUS_SUCCESS }
        return if (measured != null) {
            CarDataPoint(CarDataKind.MODEL, CarDataStatus.AVAILABLE, format(measured), time.nowMillis())
        } else {
            CarDataPoint(CarDataKind.MODEL, CarDataStatus.NO_DATA, null, time.nowMillis())
        }
    }

    private fun record(kind: CarDataKind, status: CarDataStatus) {
        record(kind, CarDataPoint(kind, status, null, time.nowMillis()))
    }

    private fun record(kind: CarDataKind, point: CarDataPoint) {
        // runBlocking in a host callback — same trade-off as in
        // CarHardwareSoCSource: a few characters into SharedPreferences.
        runBlocking { settingsStore.recordCarDataPoint(point.copy(kind = kind)) }
    }

    private fun connectorName(type: Int): String = when (type) {
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_1 -> "CCS1"
        EnergyProfile.EVCONNECTOR_TYPE_COMBO_2 -> "CCS2"
        EnergyProfile.EVCONNECTOR_TYPE_CHADEMO -> "CHAdeMO"
        EnergyProfile.EVCONNECTOR_TYPE_MENNEKES -> "Typ 2"
        EnergyProfile.EVCONNECTOR_TYPE_TESLA_SUPERCHARGER -> "Tesla"
        else -> "Typ $type"
    }

    private companion object {
        const val PERMISSION_ENERGY = "com.google.android.gms.permission.CAR_FUEL"
        const val PERMISSION_SPEED = "com.google.android.gms.permission.CAR_SPEED"
        const val PERMISSION_MILEAGE = "com.google.android.gms.permission.CAR_MILEAGE"
    }
}
