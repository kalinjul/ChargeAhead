package org.julakali.chargeahead.android.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.ui.car.CarHomeViewModel
import org.koin.core.scope.Scope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The car's start screen: enter a destination, charge right now, or open the active route. */
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

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch { viewModel.uiState.collect { invalidate() } }
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

        val itemList = ItemList.Builder()
        itemList.addItem(searchRow())
        itemList.addItem(chargeNowRow())

        viewModel.uiState.value.activeDestination?.let { itemList.addItem(activeRouteRow(it)) }

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
                .setOnClickListener { screenManager.push(SoCScreen(carContext, session, permissions)) }
                .build(),
        )
        .build()

    private fun title(): String = carContext.getString(R.string.app_name)

    // IMAGE_TYPE_ICON: only tintable icons get recolored by the host.
    private fun searchRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_home_enter_destination))
        .setImage(icon(R.drawable.ic_search), Row.IMAGE_TYPE_ICON)
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(DestinationSearchScreen(carContext, session)) }
        .build()

    private fun chargeNowRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_home_charge_now))
        .setImage(icon(R.drawable.ic_bolt), Row.IMAGE_TYPE_ICON)
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(ChargeNowScreen(carContext, session)) }
        .build()

    private fun activeRouteRow(destination: Destination): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_home_active_route))
        .addText(destination.name)
        .setImage(icon(R.drawable.ic_route), Row.IMAGE_TYPE_ICON)
        .setBrowsable(true)
        .setOnClickListener {
            screenManager.push(RouteScreen(carContext, session, destination, activeRoute = true))
        }
        .build()

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
