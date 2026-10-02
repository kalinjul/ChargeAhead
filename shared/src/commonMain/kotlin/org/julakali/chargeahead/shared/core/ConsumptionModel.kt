package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.RoadLoad
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.vehicle.ConsumptionModel as VehiclePhysics

/**
 * How much energy a stretch of a route costs.
 *
 * Everything is expressed against distance from the route's start, the same
 * frame [TripPlanner] plans in.
 */
interface ConsumptionModel {

    /** Energy for the stretch from [fromKm] to [toKm]. */
    fun energyKwh(route: Route, fromKm: Double, toKm: Double): Double

    /** The distance from the start at which [availableKwh] runs out, starting at [fromKm]. */
    fun reachKm(route: Route, fromKm: Double, availableKwh: Double): Double
}

/** Distance times a fixed rate — what the app did before it knew anything about speed. */
class ConstantConsumption(private val kwhPer100Km: Double) : ConsumptionModel {

    override fun energyKwh(route: Route, fromKm: Double, toKm: Double): Double =
        (toKm - fromKm).coerceAtLeast(0.0) / 100.0 * kwhPer100Km

    override fun reachKm(route: Route, fromKm: Double, availableKwh: Double): Double =
        fromKm + availableKwh.coerceAtLeast(0.0) / kwhPer100Km * 100.0
}

/**
 * Prices each stretch of the route from the car's road-load curve at that
 * stretch's speed. The driver's consumption is read as the WLTP figure: the
 * curve is scaled so the WLTP cycle reproduces it.
 */
class RoadLoadConsumption(vehicle: VehicleProfile) : ConsumptionModel {

    private val roadLoad = vehicle.roadLoad ?: genericRoadLoad(vehicle.consumptionKwhPer100Km)
    private val correction = vehicle.consumptionKwhPer100Km / roadLoad.wltpKwhPer100Km

    /** Battery energy per 100 km at a stretch averaging [speedKmh]. */
    fun kwhPer100KmAt(speedKmh: Double): Double {
        // A stretch averaging walking pace is a jam; the auxiliary term divides by the speed.
        val speed = speedKmh.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)
        val efficiency = roadLoad.drivetrainEfficiency
        val rolling = roadLoad.forceN(speed) / (36.0 * efficiency)
        val auxiliary = roadLoad.auxiliaryPowerKw * 100.0 / speed
        val acceleration = VehiclePhysics.ROTATING_MASS_FACTOR * roadLoad.massKg * kineticJoulesPerKgAndMetre(speed) *
            (1.0 / efficiency - roadLoad.recuperationShare) / 36.0
        return correction * (rolling + auxiliary + acceleration)
    }

    override fun energyKwh(route: Route, fromKm: Double, toKm: Double): Double {
        val from = fromKm.coerceIn(0.0, route.distanceKm)
        val to = toKm.coerceIn(from, route.distanceKm)
        if (to <= from) return 0.0

        var energy = 0.0
        forEachOverlap(route, from, to) { km, speedKmh ->
            energy += km / 100.0 * kwhPer100KmAt(speedKmh)
        }
        return energy
    }

    override fun reachKm(route: Route, fromKm: Double, availableKwh: Double): Double {
        val from = fromKm.coerceIn(0.0, route.distanceKm)
        var budget = availableKwh.coerceAtLeast(0.0)
        var reached = from

        forEachOverlap(route, from, route.distanceKm) { km, speedKmh ->
            if (budget > 0.0) {
                val perKm = kwhPer100KmAt(speedKmh) / 100.0
                val affordableKm = budget / perKm
                if (affordableKm >= km) {
                    reached += km
                    budget -= km * perKm
                } else {
                    reached += affordableKm
                    budget = 0.0
                }
            }
        }

        // Past the end of the route there are no segments left to price, so what
        // is left of the budget is spent at the route's own average. Without
        // this the planner would think every route ends exactly at the range
        // limit and would plan a stop for a trip that comfortably makes it.
        if (budget > 0.0) {
            reached += budget / (kwhPer100KmAt(averageSpeedKmh(route)) / 100.0)
        }
        return reached
    }

    /**
     * Walks the part of the route between [from] and [to], handing out the
     * length of each piece and the speed that applies to it. A route without
     * segments is one single piece at the route's average speed.
     */
    private inline fun forEachOverlap(route: Route, from: Double, to: Double, action: (Double, Double) -> Unit) {
        if (to <= from) return
        if (route.segments.isEmpty()) {
            action(to - from, averageSpeedKmh(route))
            return
        }

        var covered = from
        for (segment in route.segments) {
            val start = maxOf(segment.fromKm, from)
            val end = minOf(segment.fromKm + segment.distanceKm, to)
            if (end <= start) continue
            action(end - start, segment.averageSpeedKmh)
            covered = end
            if (covered >= to) return
        }

        // Segments that stop short of the route's length — the contract rules it
        // out, but a gap must not silently cost nothing.
        if (covered < to) action(to - covered, averageSpeedKmh(route))
    }

    private fun averageSpeedKmh(route: Route): Double =
        if (route.durationMinutes <= 0.0) FALLBACK_SPEED_KMH
        else route.distanceKm / route.durationMinutes * 60.0

    private companion object {
        const val MIN_SPEED_KMH = 10.0
        const val MAX_SPEED_KMH = 200.0
        const val FALLBACK_SPEED_KMH = 100.0
    }
}

/** The backend's typical curve for a car without one, scaled until the WLTC reproduces [wltpKwhPer100Km]. */
internal fun genericRoadLoad(wltpKwhPer100Km: Double): RoadLoad {
    val curve = VehiclePhysics.generic(wltpKwhPer100Km, VehiclePhysics.TYPICAL_TEST_MASS_KG)
    return RoadLoad(
        f0 = curve.f0,
        f1 = curve.f1,
        f2 = curve.f2,
        massKg = VehiclePhysics.TYPICAL_TEST_MASS_KG,
        drivetrainEfficiency = VehiclePhysics.DRIVETRAIN_EFFICIENCY,
        auxiliaryPowerKw = VehiclePhysics.DRIVING_AUXILIARY_KW,
        recuperationShare = VehiclePhysics.RECUPERATION_SHARE,
        wltpKwhPer100Km = wltpKwhPer100Km,
    )
}

/**
 * Kinetic energy put in per metre driven, in J/kg, for a stretch averaging
 * [speedKmh]. Up to 92 km/h these are the WLTC phases; the drop towards
 * steady motorway driving above that is an assumption.
 */
internal fun kineticJoulesPerKgAndMetre(speedKmh: Double): Double {
    val points = KINETIC_BY_SPEED
    if (speedKmh <= points.first().first) return points.first().second
    for (i in 1 until points.size) {
        val (upperSpeed, upperValue) = points[i]
        if (speedKmh <= upperSpeed) {
            val (lowerSpeed, lowerValue) = points[i - 1]
            return lowerValue + (upperValue - lowerValue) * (speedKmh - lowerSpeed) / (upperSpeed - lowerSpeed)
        }
    }
    return points.last().second
}

private val KINETIC_BY_SPEED = listOf(
    18.9 to 0.207,
    39.5 to 0.199,
    56.7 to 0.134,
    92.0 to 0.125,
    120.0 to 0.06,
)
