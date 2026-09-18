package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Plans [Params.from] → [Params.destination] with the selected vehicle,
 * puts a successful plan into [TripStore] and makes the destination the
 * app-wide one.
 */
class PlanTrip(
    private val planner: TripPlanning,
    private val settings: SettingsStore,
    private val store: TripStore,
) : Interactor<PlanTrip.Params, TripPlanResult>() {

    /**
     * [startSocPercent] plans with a charge level that isn't persisted;
     * `null` takes the stored manual one, and without one
     * [DEFAULT_ASSUMED_SOC_PERCENT].
     */
    data class Params(
        val from: LatLon,
        val destination: Destination,
        val startSocPercent: Double? = null,
    )

    override suspend fun doWork(params: Params): TripPlanResult {
        val result = plan(params)
        if (result is TripPlanResult.Planned) store.store(result.plan)
        // After the store, so the corridor takes the plan's route instead of fetching its own.
        settings.setDestination(params.destination)
        return result
    }

    private suspend fun plan(params: Params): TripPlanResult {
        val vehicle = settings.vehicle.first() ?: return TripPlanResult.NoVehicle
        val soc = params.startSocPercent
            ?: settings.manualSocPercent.first()
            ?: DEFAULT_ASSUMED_SOC_PERCENT
        return withContext(Dispatchers.Default) {
            planner.plan(
                from = params.from,
                destination = params.destination,
                vehicle = vehicle,
                startSocPercent = soc,
                arrivalSocPercent = settings.arrivalSocPercent.first(),
                filters = settings.chargeFilters.first(),
                networks = settings.networks.first(),
            )
        }
    }

    companion object {
        /** Assumed start charge when the driver never entered one. */
        const val DEFAULT_ASSUMED_SOC_PERCENT = 80.0
    }
}
