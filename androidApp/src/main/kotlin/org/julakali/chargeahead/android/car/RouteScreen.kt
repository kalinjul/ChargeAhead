package org.julakali.chargeahead.android.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.CarText
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.PhoneUiVisibility
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.MapsHandoff
import org.julakali.chargeahead.shared.domain.OperatorShortName
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.distanceKmTo
import org.julakali.chargeahead.shared.ui.car.CarRouteUiState
import org.julakali.chargeahead.shared.ui.car.CarRouteViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The route on the host's map: every charging stop as a numbered marker in
 * the brand colour, the car's position, the destination as the anchor.
 * Navigation goes one stop at a time through the host, and starting it
 * commits a plan made here. A stop opens its detail; the last row still
 * sends the whole route through the phone, for those who want Maps to hold
 * every waypoint.
 */
class RouteScreen(
    carContext: CarContext,
    private val session: Scope,
    private val destination: Destination,
    private val title: String = destination.name,
    /** Show the active route as the phone keeps it, instead of planning here; leaves when it ends. */
    private val activeRoute: Boolean = false,
    private val permissions: CarPermissions,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarRouteViewModel> { parametersOf(destination, activeRoute) } }

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state == CarRouteUiState.Ended) screenManager.pop() else invalidate()
            }
        }
        // Distances are measured from the fix; a new one moves every row.
        lifecycleScope.launch { viewModel.feature.currentFix.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template = when (val state = viewModel.uiState.value) {
        is CarRouteUiState.Loading -> loadingTemplate(state.waitingForLocation)
        // Leaving already.
        CarRouteUiState.Ended -> loadingTemplate(waitingForLocation = false)
        is CarRouteUiState.Failed -> failureTemplate(state.result)
        is CarRouteUiState.Ready ->
            if (state.plan.stops.isEmpty()) directTemplate() else stopsTemplate(state.plan)
    }

    private fun failureTemplate(failure: TripPlanResult): Template = when (failure) {
        is TripPlanResult.Planned -> loadingTemplate(waitingForLocation = false)
        TripPlanResult.NoVehicle -> messageTemplate(carContext.getString(R.string.car_route_no_vehicle))
        TripPlanResult.NoRoute -> messageTemplate(carContext.getString(R.string.car_route_no_route))
        is TripPlanResult.NoChargerInReach -> messageTemplate(
            carContext.getString(
                R.string.car_route_no_charger,
                ChargeStopFormatter.distanceLabel(failure.afterKm),
            ),
        )
    }

    private fun loadingTemplate(waitingForLocation: Boolean): Template {
        val waitingText = if (waitingForLocation) {
            R.string.car_waiting_for_location
        } else {
            R.string.car_route_planning
        }
        return PlaceListMapTemplate.Builder()
            .setLoading(true)
            .setTitle(titleText(carContext.getString(waitingText)))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun stopsTemplate(plan: TripPlan): Template {
        // The row count is dictated by the host.
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        val here = viewModel.feature.currentFix.value?.position

        // Every stop is a marker; the whole-route send takes the last slot.
        val itemList = ItemList.Builder()
        plan.stops.take(contentLimit - 1).forEachIndexed { index, stop ->
            itemList.addItem(stopRow(index + 1, stop, here))
        }
        itemList.addItem(sendAllRow(plan))

        return PlaceListMapTemplate.Builder()
            .setItemList(itemList.build())
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setCurrentLocationEnabled(true)
            .setAnchor(destinationAnchor())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(navigateAction(plan))
                    .addAction(replanAction())
                    .build(),
            )
            .build()
    }

    /** The destination, flagged on the map so the stops read as a line towards it. */
    private fun destinationAnchor(): Place =
        Place.Builder(CarLocation.create(destination.position.lat, destination.position.lon))
            .setMarker(PlaceMarker.Builder().setIcon(icon(R.drawable.ic_destination), PlaceMarker.TYPE_ICON).build())
            .build()

    /**
     * One line, title only: "EnBW · 73 km · laden bis 68 %". The host
     * numbers the row after its marker and renders the title in its own
     * size (there is no bold span in the car text model), and it frames its
     * map around the rows on screen, so the shorter the rows, the more of
     * the route is on the map. Arrival level and charge time live in the detail.
     */
    private fun stopRow(ordinal: Int, stop: PlannedStop, here: LatLon?): Row {
        val name = OperatorShortName.of(stop.site.operator) ?: stop.site.operator ?: stop.site.name
        val charge = carContext.getString(R.string.car_route_charge, stop.departureSocPercent.toInt())
        val title = if (here != null) {
            distanceLine(here.distanceKmTo(stop.site.position), suffix = charge, leading = name)
        } else {
            CarText.create("$name · $charge")
        }
        return Row.Builder()
            .setTitle(title)
            .setMetadata(placeMetadata(stop.site.position, ordinal.toString()))
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(SiteDetailScreen(carContext, session, stop.site, stop)) }
            .build()
    }

    /** Strip actions render icon-only on most hosts. */
    private fun navigateAction(plan: TripPlan): Action = Action.Builder()
        .setIcon(icon(R.drawable.ic_destination))
        .setOnClickListener { startNavigation(plan) }
        .build()

    /**
     * The next stop goes to the host's navigation, which is the one hand-off
     * that keeps the driver in the car. A plan made here becomes the active
     * route first and the grid takes over, so coming back lands on it.
     */
    private fun startNavigation(plan: TripPlan) {
        val target = plan.stops.firstOrNull()?.site?.let { it.name to it.position } ?: (destination.name to destination.position)
        if (!activeRoute) {
            viewModel.onNavigationStarted()
            screenManager.popToRoot()
        }
        navigateTo(carContext, target.first, target.second)
    }

    private fun sendAllRow(plan: TripPlan): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_route_send_all))
        .addText(carContext.getString(R.string.car_route_send_all_hint))
        // IMAGE_TYPE_ICON: only tintable icons get recolored by the host.
        .setImage(icon(R.drawable.ic_send, CarColor.PRIMARY), Row.IMAGE_TYPE_ICON)
        .setOnClickListener { sendRouteToMaps(plan) }
        .build()

    /**
     * The whole route with every stop as a waypoint, sent to Maps on the phone
     * as `google.navigation:` (the host's ACTION_NAVIGATE drops waypoints).
     *
     * Android only allows that launch while the app is visible on the phone.
     */
    private fun sendRouteToMaps(plan: TripPlan) {
        if (PhoneUiVisibility.isVisible.value) {
            handOffRoute(plan)
        } else {
            screenManager.push(
                OpenPhoneScreen(
                    carContext,
                    onPhoneOpened = { handOffRoute(plan) },
                    onNextStopOnly = { plan.stops.first().site.let { navigateTo(carContext, it.name, it.position) } },
                ),
            )
        }
    }

    /**
     * First the host's own hand-off to the first stop, which moves Maps into
     * the car's main pane. Once Maps has taken over, the whole route follows
     * from the phone and replaces the single stop.
     */
    private fun handOffRoute(plan: TripPlan) {
        val first = plan.stops.first().site
        val waypoints = plan.stops.map { it.site.position }
        val navigation = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(MapsHandoff.navigationUri(plan.destination.position, waypoints)),
        ).setPackage(GOOGLE_MAPS_PACKAGE)
        // Without Google Maps, any app that handles the directions URL.
        val directions = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(MapsHandoff.directionsUrl(origin = null, plan.destination.position, waypoints)),
        )
        lifecycleScope.launch {
            // Coming back from OpenPhoneScreen, this screen isn't started yet.
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            navigateTo(carContext, first.name, first.position)
            // The phone's route must arrive after the host's single stop.
            withTimeoutOrNull(MAPS_TAKEOVER_TIMEOUT_MS) {
                lifecycle.currentStateFlow.first { !it.isAtLeast(Lifecycle.State.STARTED) }
            }
            if (!startOnPhone(navigation) && !startOnPhone(directions)) {
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.car_no_navigation_app),
                    CarToast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun startOnPhone(intent: Intent): Boolean = try {
        carContext.applicationContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (notFound: ActivityNotFoundException) {
        false
    } catch (denied: SecurityException) {
        // A blocked launch must not crash the car UI.
        false
    }

    /**
     * "Neu planen": with the car's own reading right away, otherwise the
     * percent picker first, and its pick re-plans.
     */
    private fun replanAction(): Action = Action.Builder()
        .setIcon(icon(R.drawable.ic_refresh))
        .setOnClickListener {
            if (!viewModel.onReplanRequested()) {
                screenManager.push(SoCScreen(carContext, session, permissions, onPicked = viewModel::onReplanWith))
            }
        }
        .build()

    /** Destination in reach without charging: no list to show, just the handoff. */
    private fun directTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_route_direct))
            .setHeader(header())
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_route_navigate))
                    .setBackgroundColor(CarColor.PRIMARY)
                    .setOnClickListener { (viewModel.uiState.value as? CarRouteUiState.Ready)?.plan?.let(::startNavigation) }
                    .build(),
            )
            .addAction(chargeNowTitledAction())
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setHeader(header())
            .addAction(chargeNowTitledAction())
            .build()

    /** Body actions may carry titles — unlike the strip's icons. */
    private fun chargeNowTitledAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.car_home_charge_now))
        .setOnClickListener { screenManager.push(ChargeNowScreen(carContext, session)) }
        .build()

    private fun header(): Header = Header.Builder()
        .setTitle(title)
        .setStartHeaderAction(Action.BACK)
        // Re-plans with the freshest position and charge state.
        .addEndHeaderAction(replanAction())
        .build()

    private fun titleText(subtitle: String?): String = subtitle?.let { "$title · $it" } ?: title
}

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

private const val MAPS_TAKEOVER_TIMEOUT_MS = 3_000L
