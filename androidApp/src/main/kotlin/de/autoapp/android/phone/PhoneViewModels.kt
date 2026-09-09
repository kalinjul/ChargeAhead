package de.autoapp.android.phone

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.shared.ui.AddCarViewModel
import de.autoapp.shared.ui.CarDataViewModel
import de.autoapp.shared.ui.ChargeNowViewModel
import de.autoapp.shared.ui.DrawerViewModel
import de.autoapp.shared.ui.GarageViewModel
import de.autoapp.shared.ui.HomeViewModel
import de.autoapp.shared.ui.NetworksViewModel
import de.autoapp.shared.ui.PlanSheetViewModel
import de.autoapp.shared.ui.RoutesViewModel
import de.autoapp.shared.ui.SubscriptionsViewModel
import de.autoapp.shared.ui.TripViewModel
import de.autoapp.shared.ui.VehicleSettingsViewModel

/**
 * Hand-wired dependency injection for the phone's ViewModels.
 *
 * The ViewModels themselves live in `shared` and know nothing about Android;
 * this is the one place that knows where their dependencies come from. When
 * Koin arrives (plans/technical-debts.md) this whole object is replaced by a
 * module — the ViewModels stay as they are, which is the point of keeping
 * the wiring in exactly one file.
 */
internal object PhoneViewModels {

    @Volatile
    private var cached: ViewModelProvider.Factory? = null

    fun factory(context: Context): ViewModelProvider.Factory =
        cached ?: synchronized(this) { cached ?: build(context).also { cached = it } }

    private fun build(context: Context): ViewModelProvider.Factory {
        val app = context.applicationContext
        val feature = ChargeStopsFeatureProvider.phoneFeature(app)
        val planning = ChargeStopsFeatureProvider.planning(app)
        val settings = ChargeStopsFeatureProvider.settingsStore(app)
        return viewModelFactory {
            initializer { HomeViewModel(feature, planning, settings) }
            initializer { TripViewModel(feature, planning, settings) }
            initializer { PlanSheetViewModel(feature, settings) }
            initializer { ChargeNowViewModel(feature, planning) }
            initializer { RoutesViewModel(settings) }
            initializer { DrawerViewModel(settings) }
            initializer { GarageViewModel(settings, feature) }
            initializer { AddCarViewModel(settings) }
            initializer { VehicleSettingsViewModel(settings, feature) }
            initializer { SubscriptionsViewModel(settings) }
            initializer { NetworksViewModel(settings, feature) }
            initializer { CarDataViewModel(settings) }
        }
    }
}

/**
 * The shared ViewModel for this screen, scoped to the activity.
 *
 * Activity scope, not screen scope, because navigation is still the enum
 * page switch in [PhoneApp]: there is no per-destination store to scope to
 * yet. Navigation3 (plans/technical-debts.md) brings one, and only this
 * function changes.
 */
@Composable
internal inline fun <reified VM : ViewModel> phoneViewModel(): VM =
    viewModel(factory = PhoneViewModels.factory(LocalContext.current))
