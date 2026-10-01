package org.julakali.chargeahead.shared.ui.car

import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Destination
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One car session: the screens' ViewModels over the session's own feature, declared on the scope. */
val CarSession = named("car-session")

fun carUiModule(): Module = module {
    scope(CarSession) {
        factoryOf(::CarHomeViewModel)
        factoryOf(::CarDestinationSearchViewModel)
        factoryOf(::CarChargeNowViewModel)
        factoryOf(::CarSoCViewModel)
        factory { (destination: Destination, activeRoute: Boolean) ->
            CarRouteViewModel(get(), destination, activeRoute, get(), get(), get(), get())
        }
        factory { (site: ChargeSite) -> CarSiteDetailViewModel(get(), site, get()) }
    }
}

/** The scope a car session resolves its ViewModels from; close it with the session. */
@OptIn(ExperimentalUuidApi::class)
fun Koin.carSession(feature: ChargeStopsFeature): Scope =
    createScope(Uuid.random().toString(), CarSession).apply { declare(feature) }
