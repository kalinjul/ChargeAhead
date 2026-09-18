package org.julakali.chargeahead.android.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.SavedRoute
import org.julakali.chargeahead.shared.domain.SettingsStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The car's start screen: enter a destination, charge right now, or pick a favorite route. */
class CarHomeScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val settings: SettingsStore,
    private val permissions: CarPermissions,
) : Screen(carContext) {

    // onGetTemplate() is synchronous; changes are picked up via invalidate().
    private var favorites: List<SavedRoute> = emptyList()
    private val fineLocation = permissions.granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private val coarseLocation = permissions.granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Seeded synchronously so the first template doesn't flash the permission message.
    private var hasLocationPermission = fineLocation.value || coarseLocation.value
    private var permissionRequestPending = false

    init {
        lifecycleScope.launch {
            settings.savedRoutes.collect { updated ->
                favorites = updated
                invalidate()
            }
        }
        lifecycleScope.launch {
            combine(fineLocation, coarseLocation) { fine, coarse -> fine || coarse }
                .collect { granted ->
                    hasLocationPermission = granted
                    // Starting twice is a no-op.
                    if (granted) feature.startSensors()
                    invalidate()
                }
        }
    }

    override fun onGetTemplate(): Template {
        if (!hasLocationPermission) return permissionTemplate()

        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        itemList.addItem(searchRow())
        itemList.addItem(chargeNowRow())

        if (favorites.isEmpty()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_home_no_favorites))
                    .build(),
            )
        } else {
            favorites.take(contentLimit - 2).forEach { itemList.addItem(favoriteRow(it)) }
        }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header())
            .build()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(title())
        .setStartHeaderAction(Action.APP_ICON)
        // Manual state of charge, for cars that don't report their battery.
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(icon(R.drawable.ic_battery))
                .setOnClickListener { screenManager.push(SoCScreen(carContext, settings, permissions)) }
                .build(),
        )
        .build()

    private fun title(): String {
        val base = carContext.getString(R.string.app_name)
        return if (feature.currentState.isDemo) {
            carContext.getString(R.string.car_title_suffix_demo, base)
        } else {
            base
        }
    }

    // IMAGE_TYPE_ICON: only tintable icons get recolored by the host.
    private fun searchRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_home_enter_destination))
        .setImage(icon(R.drawable.ic_search), Row.IMAGE_TYPE_ICON)
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(DestinationSearchScreen(carContext, feature)) }
        .build()

    private fun chargeNowRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_home_charge_now))
        .setImage(icon(R.drawable.ic_bolt), Row.IMAGE_TYPE_ICON)
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(ChargeNowScreen(carContext, feature)) }
        .build()

    private fun favoriteRow(route: SavedRoute): Row {
        val row = Row.Builder()
            .setTitle(route.name)
            .setImage(icon(R.drawable.ic_heart_filled, CarColor.RED), Row.IMAGE_TYPE_ICON)
            .setBrowsable(true)
            .setOnClickListener {
                screenManager.push(RouteScreen(carContext, feature, route.destination, route.name))
            }
        route.summary?.let { row.addText(it) }
        return row.build()
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
