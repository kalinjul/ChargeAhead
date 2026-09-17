package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.Route

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
 * Scales the driver's consumption with the speed each stretch of the route
 * implies.
 */
class SpeedAwareConsumption(
    private val kwhPer100Km: Double,
    private val referenceSpeedKmh: Double = REFERENCE_SPEED_KMH,
) : ConsumptionModel {

    override fun energyKwh(route: Route, fromKm: Double, toKm: Double): Double {
        val from = fromKm.coerceIn(0.0, route.distanceKm)
        val to = toKm.coerceIn(from, route.distanceKm)
        if (to <= from) return 0.0

        var energy = 0.0
        forEachOverlap(route, from, to) { km, speedKmh ->
            energy += km / 100.0 * kwhPer100Km * factorAt(speedKmh)
        }
        return energy
    }

    override fun reachKm(route: Route, fromKm: Double, availableKwh: Double): Double {
        val from = fromKm.coerceIn(0.0, route.distanceKm)
        var budget = availableKwh.coerceAtLeast(0.0)
        var reached = from

        forEachOverlap(route, from, route.distanceKm) { km, speedKmh ->
            if (budget > 0.0) {
                val perKm = kwhPer100Km * factorAt(speedKmh) / 100.0
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
            val perKm = kwhPer100Km * factorAt(averageSpeedKmh(route)) / 100.0
            reached += budget / perKm
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

    private fun factorAt(speedKmh: Double): Double {
        // A segment averaging walking pace is a traffic light, not a driving
        // style, and the auxiliary term divides by this.
        val speed = speedKmh.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)
        val ratio = speed / referenceSpeedKmh
        val factor = ROLLING_SHARE + DRAG_SHARE * ratio * ratio + AUXILIARY_SHARE / ratio
        return factor.coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    private fun averageSpeedKmh(route: Route): Double =
        if (route.durationMinutes <= 0.0) referenceSpeedKmh
        else route.distanceKm / route.durationMinutes * 60.0

    companion object {

        const val REFERENCE_SPEED_KMH = 100.0

        /**
         * Shares of the three terms at the reference speed for a mid-size EV.
         * They add up to one, which is what makes the model return the
         * configured value there unchanged.
         */
        private const val ROLLING_SHARE = 0.45
        private const val DRAG_SHARE = 0.45
        private const val AUXILIARY_SHARE = 0.10

        private const val MIN_SPEED_KMH = 30.0
        private const val MAX_SPEED_KMH = 200.0

        /** The shape is an approximation; these keep a bad segment from becoming an absurd plan. */
        private const val MIN_FACTOR = 0.7
        private const val MAX_FACTOR = 1.6
    }
}
