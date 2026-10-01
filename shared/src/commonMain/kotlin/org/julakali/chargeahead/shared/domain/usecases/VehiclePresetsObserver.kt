package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.VehicleCatalog
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.VehicleRepository

/** The catalog cars one can still add: not in the garage yet, matching the query. */
class VehiclePresetsObserver(
    private val vehicles: VehicleRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : SubjectInteractor<VehiclePresetsObserver.Params, List<VehiclePreset>>() {

    data class Params(val query: String)

    override fun createObservable(params: Params): Flow<List<VehiclePreset>> {
        val needle = params.query.trim()
        return vehicles.vehicles.map { owned ->
            val ownedNames = owned.mapTo(HashSet()) { it.displayName }
            VehicleCatalog.all.filter { it.name !in ownedNames && it.name.contains(needle, ignoreCase = true) }
        }.flowOn(dispatchers.computation)
    }
}
