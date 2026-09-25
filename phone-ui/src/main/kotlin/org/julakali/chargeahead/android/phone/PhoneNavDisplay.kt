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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.julakali.chargeahead.android.phone.components.AppTopBar
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.VehiclePreset

/**
 * Everything the navigator can open: full-screen pages, and the sheets that
 * float over the map. The empty [Home] slot lets the map and drawer show through.
 */
@Composable
fun PhoneNavDisplay(
    navigator: PhoneNavigator,
    librariesRes: Int,
    preferredNetworkCount: Int,
    onCarAdded: (VehiclePreset) -> Unit,
    onNavigateTo: (LatLon) -> Unit,
    onOpenRoute: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = navigator.backStack,
        onBack = navigator::back,
        // Each destination gets its own ViewModelStore, cleared when it leaves
        // the back stack. The map screen around it — drawer, search, trip sheet
        // — is outside, so its ViewModels stay activity-scoped.
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Held across recompositions: NavDisplay recalculates every scene when
        // the list changes, and strategies compare by identity.
        sceneStrategies = remember { listOf(SheetSceneStrategy()) },
        transitionSpec = {
            slideInHorizontally(tween(300)) { it } togetherWith fadeOut(tween(300))
        },
        popTransitionSpec = {
            fadeIn(tween(300)) togetherWith slideOutHorizontally(tween(300)) { it }
        },
        entryProvider = entryProvider {
            entry<Home> { }

            entry<Garage> {
                Page(title = stringResource(R.string.garage_title), onBack = navigator::back) { pagePadding ->
                    GarageRoute(
                        onOpenAdvanced = { navigator.open(VehicleEdit) },
                        onOpenAdd = { navigator.open(AddCar) },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<AddCar> {
                Page(title = stringResource(R.string.garage_add_title), onBack = navigator::back) { pagePadding ->
                    AddCarRoute(
                        onAdded = { preset ->
                            navigator.back()
                            onCarAdded(preset)
                        },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<VehicleEdit> {
                Page(title = stringResource(R.string.phone_settings_title), onBack = navigator::back) { pagePadding ->
                    VehicleSettingsRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Networks> {
                Page(
                    title = stringResource(R.string.phone_networks_title),
                    subtitle = networksSummary(preferredNetworkCount),
                    onBack = navigator::back,
                ) { pagePadding ->
                    NetworksRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<CarData> {
                Page(title = stringResource(R.string.cardata_title), onBack = navigator::back) { pagePadding ->
                    CarDataDebugRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Legal> {
                Page(title = stringResource(R.string.drawer_legal), onBack = navigator::back) { pagePadding ->
                    LegalScreen(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Licenses> {
                Page(title = stringResource(R.string.drawer_licenses), onBack = navigator::back) { pagePadding ->
                    LicensesRoute(librariesRes = librariesRes, modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<ChargeNow>(metadata = SheetSceneStrategy.sheet()) {
                ChargeNowRoute(onNavigate = { candidate -> onNavigateTo(candidate.site.position) })
            }

            entry<Routes>(metadata = SheetSceneStrategy.sheet()) {
                RoutesRoute(onOpen = onOpenRoute)
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
