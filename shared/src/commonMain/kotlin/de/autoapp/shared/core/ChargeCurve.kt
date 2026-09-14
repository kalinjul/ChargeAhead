package de.autoapp.shared.core

import de.autoapp.shared.domain.VehicleProfile

/** The share of its DC peak a car still accepts at a given charge level. */
fun interface ChargeCurve {
    fun fractionOfPeakAt(socPercent: Double): Double
}

/**
 * One shape for every car, because per-model curves are data the app does not
 * have. A DC session holds close to its peak while the battery is empty and
 * tapers hard once it is not — flat to [PLATEAU_END_SOC], then falling in two
 * straight pieces. That is the behaviour a flat average factor cannot
 * reproduce: it charges the taper far too fast and a near-empty arrival too
 * slowly.
 *
 * Per-model curves can replace this later without any caller changing.
 */
object GenericChargeCurve : ChargeCurve {

    override fun fractionOfPeakAt(socPercent: Double): Double {
        val soc = socPercent.coerceIn(0.0, 100.0)
        return when {
            soc <= PLATEAU_END_SOC -> 1.0
            soc <= TAPER_KNEE_SOC -> interpolate(soc, PLATEAU_END_SOC, 1.0, TAPER_KNEE_SOC, KNEE_FRACTION)
            else -> interpolate(soc, TAPER_KNEE_SOC, KNEE_FRACTION, 100.0, FULL_FRACTION)
        }
    }

    private fun interpolate(at: Double, fromSoc: Double, fromValue: Double, toSoc: Double, toValue: Double): Double =
        fromValue + (toValue - fromValue) * (at - fromSoc) / (toSoc - fromSoc)

    private const val PLATEAU_END_SOC = 50.0
    private const val TAPER_KNEE_SOC = 80.0
    private const val KNEE_FRACTION = 0.35
    private const val FULL_FRACTION = 0.15
}

/**
 * Minutes to charge [fromSocPercent] up to [toSocPercent] at a site offering
 * [sitePowerKw], integrated over the curve rather than averaged across it.
 *
 * One percent per step: the curve is piecewise linear, so a finer grid buys
 * nothing, and a hundred steps cost nothing either.
 */
fun chargeMinutes(
    vehicle: VehicleProfile,
    sitePowerKw: Double,
    fromSocPercent: Double,
    toSocPercent: Double,
    curve: ChargeCurve = GenericChargeCurve,
): Double {
    val from = fromSocPercent.coerceIn(0.0, 100.0)
    val to = toSocPercent.coerceIn(0.0, 100.0)
    if (to <= from || sitePowerKw <= 0.0) return 0.0

    val peakKw = acceptedPeakKw(vehicle, sitePowerKw)
    if (peakKw <= 0.0) return 0.0

    var minutes = 0.0
    var soc = from
    while (soc < to) {
        val step = minOf(STEP_PERCENT, to - soc)
        val powerKw = peakKw * curve.fractionOfPeakAt(soc + step / 2.0)
        minutes += vehicle.usableBatteryKwh * step / 100.0 / powerKw * 60.0
        soc += step
    }
    return minutes
}

/**
 * What the car can actually pull at the top of the curve.
 *
 * The catalog's peak is often a figure held for seconds on a preconditioned
 * pack; a small battery cannot sustain it whatever the spec sheet says, so it
 * is capped at a plausible C-rate. Without that, a 54 kWh car claiming 140 kW
 * would be planned as if it held 140 kW through the whole plateau.
 */
private fun acceptedPeakKw(vehicle: VehicleProfile, sitePowerKw: Double): Double {
    val claimed = vehicle.dcPeakPowerKw ?: sitePowerKw
    return minOf(sitePowerKw, claimed, vehicle.usableBatteryKwh * MAX_C_RATE)
}

private const val STEP_PERCENT = 1.0

private const val MAX_C_RATE = 2.5
