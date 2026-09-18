package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.flow.first

/** Saves a destination as a favourite route, or removes it again. `true` when it is saved now. */
class ToggleSavedRoute(
    private val settings: SettingsStore,
) : Interactor<ToggleSavedRoute.Params, Boolean>() {

    data class Params(val destination: Destination, val summary: String)

    override suspend fun doWork(params: Params): Boolean {
        val destination = params.destination
        val existing = settings.savedRoutes.first()
            .firstOrNull { it.destination.position == destination.position }
        if (existing != null) {
            settings.removeSavedRoute(existing.id)
            return false
        }
        settings.saveRoute(
            SavedRoute(
                id = destination.routeId(),
                name = destination.name,
                destination = destination,
                summary = params.summary,
            ),
        )
        return true
    }
}
