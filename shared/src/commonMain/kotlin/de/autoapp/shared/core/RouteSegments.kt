package de.autoapp.shared.core

import de.autoapp.shared.domain.RouteSegment
import kotlin.math.abs
import kotlin.math.max

/** One stretch as a route service reported it, before coalescing. */
data class RawStep(
    val distanceKm: Double,
    val durationMinutes: Double,
)

/**
 * Turns per-maneuver steps into the coarse speed profile [de.autoapp.shared.domain.Route] carries.
 *
 * Only the direct OSRM engine needs this: the ChargeAhead backend coalesces
 * server-side and sends segments ready to use. The two implementations are
 * deliberate duplicates across the repository boundary — a contract module
 * carries data, not behaviour — and they have to stay in step.
 *
 * Maneuver steps in a town are twenty metres long; hundreds of them are detail
 * no consumption model can use. Adjacent steps are merged until a segment is
 * long enough to mean something, and split again where the implied speed
 * actually changes — so a motorway exit ends a segment while a roundabout does
 * not start one.
 */
object RouteSegments {

    /**
     * Empty when the steps cannot be trusted to describe this route: segments
     * are keyed by distance from the start, so a breakdown that does not add up
     * would silently shift every lookup.
     */
    fun coalesce(steps: List<RawStep>, routeDistanceKm: Double): List<RouteSegment> {
        if (routeDistanceKm <= 0.0) return emptyList()

        // A step without distance or without time carries no speed. Dropping it
        // is only safe because the sum is checked against the route afterwards.
        val usable = steps.filter { it.distanceKm > 0.0 && it.durationMinutes > 0.0 }
        if (usable.isEmpty()) return emptyList()

        val covered = usable.sumOf { it.distanceKm }
        val tolerance = max(ABSOLUTE_TOLERANCE_KM, routeDistanceKm * RELATIVE_TOLERANCE)
        if (abs(covered - routeDistanceKm) > tolerance) return emptyList()

        // Scaled with the route so a thousand-kilometre trip cannot produce a
        // segment list that dwarfs the geometry it describes.
        val minimumKm = max(MIN_SEGMENT_KM, routeDistanceKm / MAX_SEGMENTS)

        val merged = mutableListOf<RawStep>()
        for (step in usable) {
            val current = merged.lastOrNull()
            if (current == null) {
                merged += step
                continue
            }
            val breaks = current.distanceKm >= minimumKm &&
                abs(speedKmh(step) - speedKmh(current)) > SPEED_BREAK_KMH
            if (breaks) {
                merged += step
            } else {
                merged[merged.lastIndex] = current + step
            }
        }

        // A stub tail is an artefact of where the last maneuver happened to
        // fall, not a stretch anybody drives differently.
        if (merged.size > 1 && merged.last().distanceKm < minimumKm) {
            val tail = merged.removeAt(merged.lastIndex)
            merged[merged.lastIndex] = merged.last() + tail
        }

        var fromKm = 0.0
        return merged.map { segment ->
            RouteSegment(
                fromKm = fromKm,
                distanceKm = segment.distanceKm,
                durationMinutes = segment.durationMinutes,
            ).also { fromKm += segment.distanceKm }
        }
    }

    private operator fun RawStep.plus(other: RawStep) = RawStep(
        distanceKm = distanceKm + other.distanceKm,
        durationMinutes = durationMinutes + other.durationMinutes,
    )

    private fun speedKmh(step: RawStep): Double = step.distanceKm / step.durationMinutes * 60.0

    /** Shorter than this, a segment says more about the road layout than about the driving. */
    private const val MIN_SEGMENT_KM = 2.0

    private const val SPEED_BREAK_KMH = 15.0

    private const val MAX_SEGMENTS = 300.0

    private const val RELATIVE_TOLERANCE = 0.02

    private const val ABSOLUTE_TOLERANCE_KM = 1.0
}
