package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore

class RemoveSavedRouteInteractor(
    private val settings: SettingsStore,
) : Interactor<RemoveSavedRouteInteractor.Params, Unit>() {

    data class Params(val id: String)

    override suspend fun doWork(params: Params) {
        settings.removeSavedRoute(params.id)
    }
}
