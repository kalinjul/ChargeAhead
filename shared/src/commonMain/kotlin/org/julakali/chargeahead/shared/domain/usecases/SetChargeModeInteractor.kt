package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.PreferencesRepository

/**
 * Puts the app into [Params.target]: AC mode, Stöbermodus or neither. The modes are a view on two
 * stored settings, so each is written only when it actually changes and nothing else is touched.
 */
class SetChargeModeInteractor(
    private val preferences: PreferencesRepository,
    private val updateChargeFilters: UpdateChargeFiltersInteractor,
    private val updateNetworks: UpdateNetworksInteractor,
) : Interactor<SetChargeModeInteractor.Params, Unit>() {

    data class Params(val target: ChargeMode)

    override suspend fun doWork(params: Params) {
        val slowMode = params.target == ChargeMode.AC
        val onlyPreferred = params.target != ChargeMode.BROWSE

        val filters = preferences.chargeFilters.first()
        if (filters.slowMode != slowMode) {
            updateChargeFilters(UpdateChargeFiltersInteractor.Params(filters.copy(slowMode = slowMode))).getOrThrow()
        }
        val networks = preferences.networks.first()
        if (networks.onlyPreferred != onlyPreferred) {
            updateNetworks(UpdateNetworksInteractor.Params(networks.copy(onlyPreferred = onlyPreferred))).getOrThrow()
        }
    }
}
