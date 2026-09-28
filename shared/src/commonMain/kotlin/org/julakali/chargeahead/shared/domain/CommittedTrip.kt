package org.julakali.chargeahead.shared.domain

import kotlinx.serialization.Serializable

/** The trip that went to Maps, kept as it was sent until the driver ends it or re-plans. */
@Serializable
data class CommittedTrip(
    val plan: TripPlan,
    /** The level the plan was made from; null when it was unknown at the time. */
    val startSocPercent: Double?,
    val committedAtEpochMillis: Long,
)
