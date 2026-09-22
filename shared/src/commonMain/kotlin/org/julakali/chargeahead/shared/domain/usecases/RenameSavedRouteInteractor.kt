package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore

class RenameSavedRouteInteractor(
    private val settings: SettingsStore,
) : Interactor<RenameSavedRouteInteractor.Params, Unit>() {

    data class Params(val id: String, val name: String)

    override suspend fun doWork(params: Params) {
        settings.renameSavedRoute(params.id, params.name)
    }
}
