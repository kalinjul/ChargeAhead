package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.RoadLoad

// The same constants the backend fits its curves with, so a catalog curve
// reproduces the catalog's WLTP figure here too.
// TODO take this from the shared vehicle-model module (kalinjul/ChargeAheadBackend#42, kalinjul/ChargeAheadBackend#43)

/** Wheels and drivetrain add inertia on top of the vehicle mass. */
internal const val ROTATING_MASS_FACTOR = 1.03

/** WLTP is measured at the plug. */
private const val CHARGING_LOSS = 0.12

/** The test cycle runs without heating or air conditioning. */
private const val WLTC_AUXILIARY_W = 300.0

private const val GENERIC_MASS_KG = 2000.0
private const val GENERIC_DRIVETRAIN_EFFICIENCY = 0.94
private const val GENERIC_AUXILIARY_KW = 1.0
private const val GENERIC_RECUPERATION_SHARE = 0.65

/** Shares of F(100) from f0, f1·v and f2·v² in a typical curve. */
private const val SHAPE_F0 = 0.29
private const val SHAPE_F1 = 0.12
private const val SHAPE_F2 = 0.59

private const val MAX_FORCE_AT_100_N = 3000.0

/** What [roadLoad] consumes over the WLTC, at the plug, in kWh/100 km. */
internal fun wltpKwhPer100Km(roadLoad: RoadLoad): Double {
    val trace = WLTC_CLASS_3B_DECI_KMH
    var joules = 0.0
    var metres = 0.0
    for (i in 0 until trace.size - 1) {
        val now = trace[i] / 10.0
        val next = trace[i + 1] / 10.0
        val speedKmh = (now + next) / 2.0
        val acceleration = (next - now) / 3.6
        val road = if (speedKmh > 0.0) roadLoad.forceN(speedKmh) else 0.0
        val power = (road + ROTATING_MASS_FACTOR * roadLoad.massKg * acceleration) * speedKmh / 3.6
        joules += if (power > 0.0) power / roadLoad.drivetrainEfficiency else power * roadLoad.recuperationShare
        metres += speedKmh / 3.6
    }
    joules += WLTC_AUXILIARY_W * (trace.size - 1)
    return joules / (1.0 - CHARGING_LOSS) / 3.6e6 / metres * 100_000.0
}

/** A typical curve for a car without one, scaled until it reproduces [wltpKwhPer100Km]. */
internal fun genericRoadLoad(wltpKwhPer100Km: Double): RoadLoad {
    var low = 0.0
    var high = MAX_FORCE_AT_100_N
    repeat(40) {
        val middle = (low + high) / 2.0
        if (wltpKwhPer100Km(typicalRoadLoad(middle)) < wltpKwhPer100Km) low = middle else high = middle
    }
    return typicalRoadLoad((low + high) / 2.0)
}

private fun typicalRoadLoad(forceAt100N: Double) = RoadLoad(
    f0 = forceAt100N * SHAPE_F0,
    f1 = forceAt100N * SHAPE_F1 / 100.0,
    f2 = forceAt100N * SHAPE_F2 / 10_000.0,
    massKg = GENERIC_MASS_KG,
    drivetrainEfficiency = GENERIC_DRIVETRAIN_EFFICIENCY,
    auxiliaryPowerKw = GENERIC_AUXILIARY_KW,
    recuperationShare = GENERIC_RECUPERATION_SHARE,
)
