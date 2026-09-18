package org.julakali.chargeahead.shared.domain

/**
 * Stores how full the battery should be at the destination and re-plans the
 * stored trip with it. `null` when there was no trip to re-plan.
 */
class UpdateArrivalSoc(
    private val settings: SettingsStore,
    private val store: TripStore,
    private val planTrip: PlanTrip,
) : Interactor<UpdateArrivalSoc.Params, TripPlanResult?>() {

    /** [from] is where the re-planned trip starts. */
    data class Params(val socPercent: Double, val from: LatLon)

    override suspend fun doWork(params: Params): TripPlanResult? {
        settings.setArrivalSocPercent(params.socPercent)
        val destination = store.plan.value?.destination ?: return null
        return planTrip(PlanTrip.Params(params.from, destination)).getOrThrow()
    }
}
