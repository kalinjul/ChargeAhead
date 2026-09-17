package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.Reachability

/**
 * Classifies a distance against the remaining range.
 *
 * | Classification | Condition |
 * |---|---|
 * | `REACHABLE` | `distance ≤ range × 0.85` |
 * | `MARGINAL` | `distance ≤ range` |
 * | `UNREACHABLE` | otherwise |
 *
 * The 15% gap absorbs the imprecision of the estimate itself; the reserve is
 * already part of the range.
 */
object ReachabilityClassifier {

    const val COMFORTABLE_FRACTION = 0.85

    fun classify(distanceKm: Double, rangeKm: Double): Reachability = when {
        distanceKm <= rangeKm * COMFORTABLE_FRACTION -> Reachability.REACHABLE
        distanceKm <= rangeKm -> Reachability.MARGINAL
        else -> Reachability.UNREACHABLE
    }
}
