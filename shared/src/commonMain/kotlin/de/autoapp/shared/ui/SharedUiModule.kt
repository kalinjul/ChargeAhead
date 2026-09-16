package de.autoapp.shared.ui

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every phone-screen ViewModel, declared once for both platforms. The
 * platform module supplies SettingsStore, ChargeStopsFeature and
 * PlanningFeature; nothing here knows where those come from.
 */
fun sharedUiModule(): Module = module {
    // Spelled out, not viewModelOf: HomeViewModel's last parameter is a
    // timeout with a default, and viewModelOf binds every parameter from the
    // container — including that Long, which nothing provides. It compiles and
    // then crashes on first composition. Leave this one explicit.
    viewModel { HomeViewModel(get(), get(), get(), get()) }
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
}
