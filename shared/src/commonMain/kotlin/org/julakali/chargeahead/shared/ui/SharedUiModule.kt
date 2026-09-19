package org.julakali.chargeahead.shared.ui

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Every phone-screen ViewModel, declared once for both platforms. */
fun sharedUiModule(): Module = module {
    // Not viewModelOf: it would try to inject the defaulted timeout parameter.
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get()) }
    viewModelOf(::TripViewModel)
    viewModelOf(::PlanSheetViewModel)
    viewModelOf(::ChargeNowViewModel)
    viewModelOf(::RoutesViewModel)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::GarageViewModel)
    viewModelOf(::AddCarViewModel)
    viewModelOf(::VehicleSettingsViewModel)
    viewModelOf(::NetworksViewModel)
    viewModelOf(::CarDataViewModel)
    viewModelOf(::LicensesViewModel)
}
