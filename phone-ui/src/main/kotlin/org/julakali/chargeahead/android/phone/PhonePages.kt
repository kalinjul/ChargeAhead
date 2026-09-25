package org.julakali.chargeahead.android.phone

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.julakali.chargeahead.android.phone.components.AppTopBar
import org.julakali.chargeahead.shared.domain.VehiclePreset

/**
 * The full-screen pages, a layer above the drawer. The empty [Home] slot lets
 * the map and drawer show through.
 */
@Composable
fun PhonePages(
    backStack: NavBackStack<NavKey>,
    librariesRes: Int,
    preferredNetworkCount: Int,
    onBack: () -> Unit,
    onCarAdded: (VehiclePreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        // Each page gets its own ViewModelStore, cleared when it leaves the
        // back stack. The chrome (drawer, map, sheets) is outside, so its
        // ViewModels stay activity-scoped.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            slideInHorizontally(tween(300)) { it } togetherWith fadeOut(tween(300))
        },
        popTransitionSpec = {
            fadeIn(tween(300)) togetherWith slideOutHorizontally(tween(300)) { it }
        },
        entryProvider = entryProvider {
            entry<Home> { }

            entry<Garage> {
                Page(title = stringResource(R.string.garage_title), onBack = onBack) { pagePadding ->
                    GarageRoute(
                        onOpenAdvanced = { backStack.add(VehicleEdit) },
                        onOpenAdd = { backStack.add(AddCar) },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<AddCar> {
                Page(title = stringResource(R.string.garage_add_title), onBack = onBack) { pagePadding ->
                    AddCarRoute(
                        onAdded = { preset ->
                            onBack()
                            onCarAdded(preset)
                        },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<VehicleEdit> {
                Page(title = stringResource(R.string.phone_settings_title), onBack = onBack) { pagePadding ->
                    VehicleSettingsRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Networks> {
                Page(
                    title = stringResource(R.string.phone_networks_title),
                    subtitle = networksSummary(preferredNetworkCount),
                    onBack = onBack,
                ) { pagePadding ->
                    NetworksRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<CarData> {
                Page(title = stringResource(R.string.cardata_title), onBack = onBack) { pagePadding ->
                    CarDataDebugRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Legal> {
                Page(title = stringResource(R.string.drawer_legal), onBack = onBack) { pagePadding ->
                    LegalScreen(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Licenses> {
                Page(title = stringResource(R.string.drawer_licenses), onBack = onBack) { pagePadding ->
                    LicensesRoute(librariesRes = librariesRes, modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * One page of the back stack: a full-screen, opaque Scaffold with its own top
 * bar, so predictive back scales the whole page.
 */
@Composable
private fun Page(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AppTopBar(title = title, onBack = onBack, subtitle = subtitle, actions = actions) },
        content = content,
    )
}
