package org.julakali.chargeahead.android.phone

import androidx.compose.animation.ContentTransform
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
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.julakali.chargeahead.android.phone.components.AppTopBar
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.active_route_title
import org.julakali.chargeahead.shared.resources.cardata_title
import org.julakali.chargeahead.shared.resources.drawer_legal
import org.julakali.chargeahead.shared.resources.drawer_licenses
import org.julakali.chargeahead.shared.resources.garage_add_title
import org.julakali.chargeahead.shared.resources.garage_title
import org.julakali.chargeahead.shared.resources.phone_networks_title
import org.julakali.chargeahead.shared.resources.vehicle_title

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
    onOpenStop: (PlannedStop) -> Unit,
    /** A charger tapped in "charge now": the same detail sheet a trip stop gets. */
    onOpenSite: (ChargeSite) -> Unit,
    onSendToMaps: (String) -> Unit,
    onTripEnded: () -> Unit,
    /** The committed destination's name, the active route page's title. */
    activeRouteTitle: String?,
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
            slideInHorizontally(ChargeAheadMotion.page()) { it } togetherWith fadeOut(ChargeAheadMotion.page())
        },
        popTransitionSpec = { pageSlideOut() },
        // Navigation 3 scales and fades on a back gesture by default; the page should just slide, as on a tap.
        predictivePopTransitionSpec = { _ -> pageSlideOut() },
        entryProvider = entryProvider {
            entry<Home> { }

            entry<Garage> {
                Page(title = stringResource(Res.string.garage_title), onBack = navigator::back) { pagePadding ->
                    GarageRoute(
                        onOpenVehicle = { navigator.open(VehicleEdit) },
                        onOpenAdd = { navigator.open(AddCar) },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<AddCar> {
                Page(title = stringResource(Res.string.garage_add_title), onBack = navigator::back) { pagePadding ->
                    AddCarRoute(
                        onAdded = { preset ->
                            navigator.back()
                            onCarAdded(preset)
                        },
                        // Back from its values lands in the garage, not the search.
                        onCustomCreated = {
                            navigator.back()
                            navigator.open(VehicleEdit)
                        },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<VehicleEdit> {
                Page(title = stringResource(Res.string.vehicle_title), onBack = navigator::back) { pagePadding ->
                    VehicleSettingsRoute(onRemoved = navigator::back, modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Networks> {
                Page(
                    title = stringResource(Res.string.phone_networks_title),
                    subtitle = networksSummary(preferredNetworkCount),
                    onBack = navigator::back,
                ) { pagePadding ->
                    NetworksRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<CarData> {
                Page(title = stringResource(Res.string.cardata_title), onBack = navigator::back) { pagePadding ->
                    CarDataDebugRoute(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<ActiveRoute> {
                Page(title = activeRouteTitle ?: stringResource(Res.string.active_route_title), onBack = navigator::back) { pagePadding ->
                    ActiveRouteRoute(
                        onOpenStop = onOpenStop,
                        onSendToMaps = onSendToMaps,
                        onEnded = {
                            navigator.back()
                            onTripEnded()
                        },
                        onOpenGarage = { navigator.open(Garage) },
                        modifier = Modifier.fillMaxSize().padding(pagePadding),
                    )
                }
            }

            entry<Legal> {
                Page(title = stringResource(Res.string.drawer_legal), onBack = navigator::back) { pagePadding ->
                    LegalScreen(modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<Licenses> {
                Page(title = stringResource(Res.string.drawer_licenses), onBack = navigator::back) { pagePadding ->
                    LicensesRoute(librariesRes = librariesRes, modifier = Modifier.fillMaxSize().padding(pagePadding))
                }
            }

            entry<ChargeNow>(metadata = SheetSceneStrategy.sheet()) {
                ChargeNowRoute(
                    onNavigate = { candidate -> onNavigateTo(candidate.site.position) },
                    onOpen = { candidate -> onOpenSite(candidate.site) },
                )
            }
        },
        modifier = modifier,
    )
}

/**
 * One page of the back stack: a full-screen, opaque Scaffold with its own top
 * bar, so a back gesture moves the whole page.
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

/** The page slides off to the right while what was under it shows again. */
private fun pageSlideOut(): ContentTransform =
    fadeIn(ChargeAheadMotion.page()) togetherWith slideOutHorizontally(ChargeAheadMotion.page()) { it }
