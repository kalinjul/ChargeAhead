package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.toSavedRoute

/** Saves a destination as a favourite route; an existing one is replaced. */
class SaveRouteInteractor(
    private val settings: SettingsStore,
) : Interactor<SaveRouteInteractor.Params, Unit>() {

    data class Params(val destination: Destination, val summary: String? = null)

    override suspend fun doWork(params: Params) {
        settings.saveRoute(params.destination.toSavedRoute(params.summary))
    }
}
