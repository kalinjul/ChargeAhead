package org.julakali.chargeahead.shared.ui.car

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.koin.core.Koin

/**
 * Creates the car screens' ViewModels over one car session's [feature]. The
 * caller owns each ViewModel and clears it when its screen ends.
 */
class CarViewModels(
    private val koin: Koin,
    private val feature: ChargeStopsFeature,
) {
    fun home(): CarHomeViewModel = CarHomeViewModel(koin.get())

    fun destinationSearch(): CarDestinationSearchViewModel =
        CarDestinationSearchViewModel(koin.get(), koin.get())

    fun route(destination: Destination, activeRoute: Boolean): CarRouteViewModel =
        CarRouteViewModel(feature, destination, activeRoute, koin.get(), koin.get(), koin.get())

    fun chargeNow(): CarChargeNowViewModel = CarChargeNowViewModel(feature, koin.get(), koin.get())

    fun soc(): CarSoCViewModel = CarSoCViewModel(koin.get(), koin.get())
}
