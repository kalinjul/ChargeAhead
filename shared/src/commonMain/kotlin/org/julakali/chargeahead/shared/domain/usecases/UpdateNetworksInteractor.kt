package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class UpdateNetworksInteractor(
    private val settings: SettingsStore,
) : Interactor<UpdateNetworksInteractor.Params, Unit>() {

    data class Params(val preferences: NetworkPreferences)

    override suspend fun doWork(params: Params) {
        // Once begun, finish: a half-cancelled write leaves memory and disk apart.
        withContext(NonCancellable) { settings.setNetworks(params.preferences) }
    }
}
