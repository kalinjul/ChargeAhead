package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.julakali.chargeahead.shared.domain.Garage
import org.julakali.chargeahead.shared.domain.RangeCalculator
import org.julakali.chargeahead.shared.domain.SubjectInteractor
import org.julakali.chargeahead.shared.domain.VehicleCatalogRepository
import org.julakali.chargeahead.shared.domain.presetOf
import org.julakali.chargeahead.shared.domain.VehicleRepository

/** The garage: the cars, the selected one, and what the catalog and the range model say about it. */
class GarageObserver(
    private val vehicles: VehicleRepository,
    private val catalog: VehicleCatalogRepository,
) : SubjectInteractor<GarageObserver.Params, Garage>() {

    /** Nothing to ask for; the garage is what it is. */
    data class Params(val unused: Unit = Unit)

    override fun createObservable(params: Params): Flow<Garage> =
        combine(vehicles.vehicles, vehicles.vehicle, catalog.presets) { owned, selected, presets ->
            Garage(
                vehicles = owned,
                selected = selected,
                selectedFullRangeKm = selected?.let { RangeCalculator.rangeKm(it, socPercent = 100.0, reserveSocPercent = 0.0) },
                selectedPresetConsumption = selected?.let { presets.presetOf(it)?.consumptionKwhPer100Km },
            )
        }
}
