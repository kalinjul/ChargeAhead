package org.julakali.chargeahead.shared.domain.usecases

import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.Interactor
import org.julakali.chargeahead.shared.domain.PreferencesRepository

class UpdateChargeFiltersInteractor(
    private val preferences: PreferencesRepository,
) : Interactor<UpdateChargeFiltersInteractor.Params, Unit>() {

    data class Params(val filters: ChargeFilters)

    override suspend fun doWork(params: Params) {
        preferences.setChargeFilters(params.filters)
    }
}
