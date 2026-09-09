package de.autoapp.android.car

import android.Manifest
import android.content.pm.PackageManager
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
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.domain.SavedRoute
import kotlinx.coroutines.launch

/**
 * The car's start screen: enter a destination, charge right now, or pick a
 * favorite route. Deliberately no map and no live list — the app is an
 * addition beside the host's map, designed for the narrow panel, and
 * everything heavy happens only once the driver asks for it.
 */
class CarHomeScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
) : Screen(carContext) {

    private val settings = ChargeStopsFeatureProvider.settingsStore(carContext)

    // onGetTemplate() is synchronous and therefore only reads the last
    // remembered state; changes are picked up via invalidate().
    private var favorites: List<SavedRoute> = emptyList()
    private var permissionRequestPending = false

    init {
        lifecycleScope.launch {
            settings.savedRoutes.collect { updated ->
                favorites = updated
                invalidate()
            }
        }
        if (hasLocationPermission()) feature.startSensors()
    }

    override fun onGetTemplate(): Template {
        if (!hasLocationPermission()) return permissionTemplate()

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

        // Deliberately no map template: the map is Google Maps' territory —
        // this app is the text panel beside it and hands every drive off.
        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header())
            .build()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(title())
        .setStartHeaderAction(Action.APP_ICON)
        // The state of charge is the value that changes while driving — it
        // stays within reach as the manual fallback for cars that don't
        // report their battery.
        .addEndHeaderAction(
            Action.Builder()
                .setIcon(icon(R.drawable.ic_battery))
                .setOnClickListener { screenManager.push(SoCScreen(carContext)) }
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

    // IMAGE_TYPE_ICON, not the SMALL default: only icons declared tintable
    // get recolored by the host — untinted ones stay black on a dark theme.
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

    private fun hasLocationPermission(): Boolean =
        carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * In projection, the head unit can't show the permission dialog itself —
     * the host instructs the driver to confirm it on the phone. That's why
     * the request sits behind a button instead of firing unprompted on open.
     */
    private fun requestLocationPermission() {
        if (permissionRequestPending) return
        permissionRequestPending = true

        carContext.requestPermissions(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        ) { granted, _ ->
            permissionRequestPending = false
            if (granted.isNotEmpty()) feature.startSensors()
            invalidate()
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
