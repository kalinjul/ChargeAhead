package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.SettingsStore

class UpdateChargeFiltersInteractor(
    private val settings: SettingsStore,
) : Interactor<UpdateChargeFiltersInteractor.Params, Unit>() {

    data class Params(val filters: ChargeFilters)

    override suspend fun doWork(params: Params) {
        settings.setChargeFilters(params.filters)
    }
}
