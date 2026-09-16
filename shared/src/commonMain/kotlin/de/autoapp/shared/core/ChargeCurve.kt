package de.autoapp.shared.core

import de.autoapp.shared.domain.VehicleProfile
import kotlin.math.abs
import kotlin.math.ln

/** The share of its DC peak a car still accepts at a given charge level. */
fun interface ChargeCurve {
    fun fractionOfPeakAt(socPercent: Double): Double

    /**
     * The charge levels between which the curve is a straight line, 0 and 100
     * included. Charge times are integrated exactly between them, so a curve
     * that is not piecewise linear on the default 1 % grid has to say where it
     * bends.
     */
    fun breakpoints(): List<Double> = ONE_PERCENT_GRID
}

private val ONE_PERCENT_GRID = (0..100).map { it.toDouble() }

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

    private val BREAKPOINTS = listOf(0.0, PLATEAU_END_SOC, TAPER_KNEE_SOC, 100.0)

    override fun breakpoints(): List<Double> = BREAKPOINTS

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
 */
fun chargeMinutes(
    vehicle: VehicleProfile,
    sitePowerKw: Double,
    fromSocPercent: Double,
    toSocPercent: Double,
    curve: ChargeCurve = GenericChargeCurve,
): Double = chargeTimeTable(vehicle, sitePowerKw, curve).minutesBetween(fromSocPercent, toSocPercent)

/** The charge-time table for [vehicle] at a site offering [sitePowerKw]. */
internal fun chargeTimeTable(
    vehicle: VehicleProfile,
    sitePowerKw: Double,
    curve: ChargeCurve = GenericChargeCurve,
): ChargeTimeTable = ChargeTimeTable(
    usableBatteryKwh = vehicle.usableBatteryKwh,
    peakKw = if (sitePowerKw <= 0.0) 0.0 else acceptedPeakKw(vehicle, sitePowerKw),
    curve = curve,
)

/**
 * Cumulative charge time F(s): minutes from 0 % to s at an accepted peak of
 * [peakKw]. Differences of F are additive, t(a→b) + t(b→c) = t(a→c), which the
 * stop optimizer relies on and a stepwise sum starting at `from` is not.
 *
 * Between two breakpoints the power is linear in the charge level,
 * p(s) = p₀ + m·s, so each piece integrates in closed form. Off-grid levels are
 * evaluated exactly within their piece, never rounded to the table.
 */
class ChargeTimeTable(
    private val usableBatteryKwh: Double,
    val peakKw: Double,
    private val curve: ChargeCurve = GenericChargeCurve,
) {
    private val breakpoints = (curve.breakpoints() + listOf(0.0, 100.0))
        .filter { it in 0.0..100.0 }
        .distinct()
        .sorted()

    private val atPercent = DoubleArray(101).also { table ->
        for (percent in 1..100) {
            table[percent] = table[percent - 1] + integrate((percent - 1).toDouble(), percent.toDouble())
        }
    }

    // The optimizer evaluates F at millions of off-grid levels, so each percent
    // keeps its own straight line unless a breakpoint falls inside it.
    private val percentStartKw = DoubleArray(100) { powerAt(it.toDouble()) }
    private val percentSlope = DoubleArray(100) { powerAt(it + 1.0) - percentStartKw[it] }
    private val percentIsStraight = BooleanArray(100) { percent ->
        breakpoints.none { it > percent && it < percent + 1 }
    }

    fun minutesTo(socPercent: Double): Double {
        if (peakKw <= 0.0) return 0.0
        val soc = socPercent.coerceIn(0.0, 100.0)
        val whole = soc.toInt().coerceAtMost(100)
        val part = soc - whole
        if (whole == 100 || part == 0.0) return atPercent[whole]
        if (!percentIsStraight[whole]) return atPercent[whole] + integrate(whole.toDouble(), soc)
        val fromKw = percentStartKw[whole]
        val toKw = fromKw + percentSlope[whole] * part
        return atPercent[whole] + closedForm(fromKw, toKw, percentSlope[whole], part)
    }

    fun minutesBetween(fromSocPercent: Double, toSocPercent: Double): Double {
        val from = fromSocPercent.coerceIn(0.0, 100.0)
        val to = toSocPercent.coerceIn(0.0, 100.0)
        if (to <= from || peakKw <= 0.0) return 0.0
        return minutesTo(to) - minutesTo(from)
    }

    private fun integrate(from: Double, to: Double): Double {
        if (to <= from || peakKw <= 0.0) return 0.0
        var minutes = 0.0
        var pieceIndex = breakpoints.indexOfLast { it <= from }.coerceIn(0, breakpoints.size - 2)
        var soc = from
        while (soc < to && pieceIndex < breakpoints.size - 1) {
            val pieceStart = breakpoints[pieceIndex]
            val pieceEnd = breakpoints[pieceIndex + 1]
            val end = minOf(to, pieceEnd)
            if (end > soc) minutes += integratePiece(pieceStart, pieceEnd, soc, end)
            soc = end
            pieceIndex++
        }
        return minutes
    }

    private fun integratePiece(pieceStart: Double, pieceEnd: Double, from: Double, to: Double): Double {
        val startKw = powerAt(pieceStart)
        val slope = (powerAt(pieceEnd) - startKw) / (pieceEnd - pieceStart)
        val fromKw = startKw + slope * (from - pieceStart)
        val toKw = startKw + slope * (to - pieceStart)
        return closedForm(fromKw, toKw, slope, to - from)
    }

    /** Minutes across [spanPercent] while the power runs linearly from [fromKw] to [toKw]. */
    private fun closedForm(fromKw: Double, toKw: Double, slopeKwPerPercent: Double, spanPercent: Double): Double {
        // 60 min/h over 100 %/battery.
        val scale = 0.6 * usableBatteryKwh
        return if (abs(toKw - fromKw) <= FLAT_TOLERANCE * fromKw) {
            scale * spanPercent / ((fromKw + toKw) / 2.0)
        } else {
            scale / slopeKwPerPercent * ln(toKw / fromKw)
        }
    }

    private fun powerAt(socPercent: Double): Double =
        peakKw * curve.fractionOfPeakAt(socPercent).coerceAtLeast(MIN_FRACTION)
}

/**
 * What the car can actually pull at the top of the curve.
 *
 * The catalog's peak is often a figure held for seconds on a preconditioned
 * pack; a small battery cannot sustain it whatever the spec sheet says, so it
 * is capped at a plausible C-rate. Without that, a 54 kWh car claiming 140 kW
 * would be planned as if it held 140 kW through the whole plateau.
 */
internal fun acceptedPeakKw(vehicle: VehicleProfile, sitePowerKw: Double): Double {
    val claimed = vehicle.dcPeakPowerKw ?: sitePowerKw
    return minOf(sitePowerKw, claimed, vehicle.usableBatteryKwh * MAX_C_RATE)
}

private const val MAX_C_RATE = 2.5

private const val FLAT_TOLERANCE = 1e-9

/** A curve that reaches zero would make the last percent take forever. */
private const val MIN_FRACTION = 1e-3
