package de.autoapp.android.car

import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.EnergyLevel
import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCDiagnostics
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * State of charge from the vehicle — the opportunistic upgrade over manual
 * input (ARCHITECTURE.md 1.2).
 *
 * **Expect it to deliver nothing.** In projection, very few head units
 * populate this data; `STATUS_UNIMPLEMENTED` is the normal case, not the
 * exception. Also required is the `com.google.android.gms.permission.CAR_FUEL`
 * permission, which the driver can deny — or grant later, mid-session.
 *
 * Lives in `androidApp` rather than `shared/androidMain`, even though
 * ARCHITECTURE.md section 3 places it there: the data comes from a
 * `CarContext`, which only exists within a `Session`. The shared module
 * couldn't get at one anyway and would have to have it passed in — so the
 * class might as well live right where the context is created, and `shared`
 * stays free of the Car App Library.
 */
class CarHardwareSoCSource(
    /** The session's shared subscription — see [CarEnergyLevels] for why it's shared. */
    private val energyLevels: CarEnergyLevels,
    private val time: TimeProvider,
    /**
     * Where the result is written so the phone UI can display it — it has
     * no `CarContext` there and thus no way to look it up itself.
     */
    private val settingsStore: SettingsStore? = null,
) : SoCSource {

    override val kind: SoCSourceKind = SoCSourceKind.CAR_HARDWARE

    override val energy: Flow<EnergyState?> = energyLevels.readings
        .map { reading ->
            val (state, diagnostics) = evaluate(reading)
            settingsStore?.recordSoCDiagnostics(diagnostics)
            state
        }
        // FIRST null, and unconditionally so: CombinedSoCSource combines this
        // flow via combine(), and combine waits until EVERY source has
        // delivered once. Without this initial null, the driver's manual
        // state of charge would go unused until the host answers — or forever
        // if it doesn't.
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
