package de.autoapp.shared.core

import de.autoapp.shared.domain.Reachability

/**
 * Classifies a distance against the remaining range
 * (ARCHITECTURE.md section 5.2).
 *
 * | Classification | Condition |
 * |---|---|
 * | `REACHABLE` | `distance ≤ range × 0.85` |
 * | `MARGINAL` | `distance ≤ range` |
 * | `UNREACHABLE` | otherwise |
 *
 * The 15% gap between "reachable" and "marginal" is not a safety margin on
 * top of the reserve — that's already baked into the range. It absorbs the
 * inherent imprecision of the estimate itself: the detour factor, the
 * constant consumption rate, the manually entered charge level.
 */
object ReachabilityClassifier {

    const val COMFORTABLE_FRACTION = 0.85

    fun classify(distanceKm: Double, rangeKm: Double): Reachability = when {
        distanceKm <= rangeKm * COMFORTABLE_FRACTION -> Reachability.REACHABLE
        distanceKm <= rangeKm -> Reachability.MARGINAL
        else -> Reachability.UNREACHABLE
    }
}
