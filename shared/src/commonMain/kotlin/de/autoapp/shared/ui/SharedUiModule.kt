package de.autoapp.shared.ui

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Every phone-screen ViewModel, declared once for both platforms. The
 * platform module supplies SettingsStore, ChargeStopsFeature and
 * PlanningFeature; nothing here knows where those come from.
 */
fun sharedUiModule(): Module = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::TripViewModel)
    viewModelOf(::PlanSheetViewModel)
    viewModelOf(::ChargeNowViewModel)
    viewModelOf(::RoutesViewModel)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::GarageViewModel)
    viewModelOf(::AddCarViewModel)
    viewModelOf(::VehicleSettingsViewModel)
    viewModelOf(::SubscriptionsViewModel)
    viewModelOf(::NetworksViewModel)
    viewModelOf(::CarDataViewModel)
}
