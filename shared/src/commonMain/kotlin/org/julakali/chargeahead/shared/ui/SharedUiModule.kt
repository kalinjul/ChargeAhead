package org.julakali.chargeahead.shared.ui

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.koin.core.scope.Scope
import org.koin.core.qualifier.named
import org.koin.core.Koin
import org.julakali.chargeahead.shared.ChargeStopsFeature

/** Every phone-screen ViewModel, declared once for both platforms. */
fun sharedUiModule(): Module = module {
    // Not viewModelOf: it would try to inject the defaulted timeout parameter.
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::PhoneAppViewModel)
    viewModelOf(::TripViewModel)
    viewModelOf(::CommittedTripViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::ChargeNowViewModel)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::GarageViewModel)
    viewModelOf(::AddCarViewModel)
    viewModelOf(::VehicleSettingsViewModel)
    viewModelOf(::NetworksViewModel)
    viewModelOf(::CarDataViewModel)
    viewModelOf(::LicensesViewModel)
    viewModelOf(::CorridorViewModel)
}

/** A caller that brings its own feature (iOS) resolves the phone ViewModels through this scope. */
val PhoneSession = named("phone-session")

@OptIn(ExperimentalUuidApi::class)
fun Koin.phoneSession(feature: ChargeStopsFeature): Scope =
    createScope(Uuid.random().toString(), PhoneSession).apply { declare(feature) }
