package org.julakali.chargeahead.android.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.ui.car.CarHomeViewModel
import org.koin.core.scope.Scope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The car's start screen: tiles for destination, charging now and the
 * charge level. A committed trip takes over: the route screen is pushed on
 * top of the tiles as soon as one exists, and back lands here.
 */
class CarHomeScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val session: Scope,
    private val permissions: CarPermissions,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarHomeViewModel>() }
    private val fineLocation = permissions.granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private val coarseLocation = permissions.granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Seeded synchronously so the first template doesn't flash the permission message.
    private var hasLocationPermission = fineLocation.value || coarseLocation.value
    private var permissionRequestPending = false

    /** The committed destination the route screen was last pushed for; pushed once per commit. */
    private var shownRouteTo: Destination? = null

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                invalidate()
                val destination = state.activeDestination
                if (destination != null && destination != shownRouteTo && hasLocationPermission) {
                    shownRouteTo = destination
                    openRoute(destination)
                } else if (destination == null) {
                    shownRouteTo = null
                }
            }
        }
        lifecycleScope.launch {
            combine(fineLocation, coarseLocation) { fine, coarse -> fine || coarse }
                .collect { granted ->
                    hasLocationPermission = granted
                    // Starting twice is a no-op.
                    if (granted) feature.start()
                    invalidate()
                }
        }
    }

    override fun onGetTemplate(): Template {
        if (!hasLocationPermission) return permissionTemplate()

        val state = viewModel.uiState.value
        val tiles = ItemList.Builder()
        state.activeDestination?.let { tiles.addItem(activeRouteTile(it)) }
        tiles.addItem(searchTile())
        tiles.addItem(chargeNowTile())
        tiles.addItem(socTile(state.socPercent))

        return GridTemplate.Builder()
            .setSingleList(tiles.build())
            // Four tiles have to fit one row on an 800px head unit; the default size wraps and scrolls.
            .setItemSize(GridTemplate.ITEM_SIZE_SMALL)
            .setHeader(header())
            .build()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(title())
        .setStartHeaderAction(Action.APP_ICON)
        // What the car reports, for debugging in the car itself.
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(icon(R.drawable.ic_info))
                .setOnClickListener { screenManager.push(CarDebugScreen(carContext, session)) }
                .build(),
        )
        .build()

    private fun title(): String = carContext.getString(R.string.app_name)

    // IMAGE_TYPE_ICON: tintable, so the tiles carry the brand colour.
    private fun tile(title: String, iconRes: Int, text: String? = null, onClick: () -> Unit): GridItem =
        GridItem.Builder()
            .setTitle(title)
            .apply { text?.let(::setText) }
            .setImage(icon(iconRes, CarColor.PRIMARY), GridItem.IMAGE_TYPE_ICON)
            .setOnClickListener(onClick)
            .build()

    private fun searchTile(): GridItem = tile(carContext.getString(R.string.car_home_enter_destination), R.drawable.ic_search) {
        screenManager.push(DestinationSearchScreen(carContext, session, permissions))
    }

    private fun chargeNowTile(): GridItem = tile(carContext.getString(R.string.car_home_charge_now), R.drawable.ic_bolt) {
        screenManager.push(ChargeNowScreen(carContext, session))
    }

    /** The level as a battery drawn to it, in steps of ten; empty when nothing is known. */
    private fun socTile(socPercent: Int?): GridItem = tile(
        carContext.getString(R.string.car_home_soc),
        batteryIcon(socPercent),
        text = socPercent?.let { carContext.getString(R.string.car_home_soc_percent, it) }
            ?: carContext.getString(R.string.car_home_soc_unset),
    ) {
        screenManager.push(SoCScreen(carContext, session, permissions))
    }

    private fun batteryIcon(socPercent: Int?): Int {
        val step = ((socPercent ?: 0).coerceIn(0, 100) + 5) / 10 * 10
        return BATTERY_ICONS.getValue(step)
    }

    private fun activeRouteTile(destination: Destination): GridItem =
        tile(carContext.getString(R.string.car_home_active_route), R.drawable.ic_route, text = destination.name) {
            openRoute(destination)
        }

    private fun openRoute(destination: Destination) {
        screenManager.push(RouteScreen(carContext, session, destination, activeRoute = true, permissions = permissions))
    }

    /** In projection, the driver confirms the permission on the phone, so it sits behind a button. */
    private fun requestLocationPermission() {
        if (permissionRequestPending) return
        permissionRequestPending = true

        // A grant arrives through the location collector in init.
        permissions.request(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        ) {
            permissionRequestPending = false
        }
    }

    private fun permissionTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_permission_message))
            .setHeader(
                Header.Builder()
                    .setTitle(title())
                    .setStartHeaderAction(Action.APP_ICON)
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_permission_action))
                    .setOnClickListener(::requestLocationPermission)
                    .build(),
            )
            .build()
}

private val BATTERY_ICONS = mapOf(
    0 to R.drawable.ic_battery_0,
    10 to R.drawable.ic_battery_10,
    20 to R.drawable.ic_battery_20,
    30 to R.drawable.ic_battery_30,
    40 to R.drawable.ic_battery_40,
    50 to R.drawable.ic_battery_50,
    60 to R.drawable.ic_battery_60,
    70 to R.drawable.ic_battery_70,
    80 to R.drawable.ic_battery_80,
    90 to R.drawable.ic_battery_90,
    100 to R.drawable.ic_battery_100,
)
