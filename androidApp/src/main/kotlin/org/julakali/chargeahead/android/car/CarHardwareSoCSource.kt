package org.julakali.chargeahead.android.car

import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * State of charge from the vehicle.
 *
 * **Expect it to deliver nothing**: in projection, very few head units
 * populate this data. Requires the `CAR_FUEL` permission.
 */
class CarHardwareSoCSource(
    /** The session's shared subscription. */
    private val energyLevels: CarEnergyLevels,
    private val time: TimeProvider,
    /** Where the result is written for the phone UI. */
    private val settingsStore: SettingsStore? = null,
) : SoCSource {

    override val kind: SoCSourceKind = SoCSourceKind.CAR_HARDWARE

    override val energy: Flow<EnergyState?> = energyLevels.readings
        .map { reading ->
            val (state, diagnostics) = evaluate(reading)
            settingsStore?.recordSoCDiagnostics(diagnostics)
            state
        }
        // Initial null, since combine() in CombinedSoCSource waits for every source.
        .onStart { emit(null) }
        .conflate()

    /** The charge state a reading yields, and what to report to the phone UI about it. */
    private fun evaluate(reading: CarEnergyLevels.Reading): Pair<EnergyState?, SoCDiagnostics> {
        val now = time.nowMillis()
        return when (reading) {
            is CarEnergyLevels.Reading.NoCarHardware ->
                null to SoCDiagnostics(now, SoCDiagnostics.Outcome.NO_CAR_HARDWARE, reading.cause)

            // The manual value takes over.
            is CarEnergyLevels.Reading.NoPermission ->
                null to SoCDiagnostics(now, SoCDiagnostics.Outcome.NO_PERMISSION, reading.message)

            is CarEnergyLevels.Reading.Level -> {
                val state = reading.level.toEnergyStateOrNull()
                val diagnostics = if (state == null) {
                    SoCDiagnostics(now, SoCDiagnostics.Outcome.NO_DATA, statusName(reading.level.batteryPercent.status))
                } else {
                    SoCDiagnostics(now, SoCDiagnostics.Outcome.AVAILABLE, "${state.socPercent.toInt()} %")
                }
                state to diagnostics
            }
        }
    }

    /** Status code as a word. */
    private fun statusName(status: Int): String = when (status) {
        CarValue.STATUS_SUCCESS -> "STATUS_SUCCESS"
        CarValue.STATUS_UNIMPLEMENTED -> "STATUS_UNIMPLEMENTED"
        CarValue.STATUS_UNAVAILABLE -> "STATUS_UNAVAILABLE"
        CarValue.STATUS_UNKNOWN -> "STATUS_UNKNOWN"
        else -> "Status $status"
    }

    /** Only `STATUS_SUCCESS` means something was actually measured. */
    private fun EnergyLevel.toEnergyStateOrNull(): EnergyState? {
        val percent = batteryPercent
        if (percent.status != CarValue.STATUS_SUCCESS) return null
        val value = percent.value?.toDouble() ?: return null
        if (value !in 0.0..100.0) return null

        return EnergyState(value, SoCSourceKind.CAR_HARDWARE, time.nowMillis())
    }
}
