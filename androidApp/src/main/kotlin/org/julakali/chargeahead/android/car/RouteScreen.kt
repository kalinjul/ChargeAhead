package org.julakali.chargeahead.android.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.PhoneUiVisibility
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.core.MapsHandoff
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.PlanTrip
import org.julakali.chargeahead.shared.domain.TripStore
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The committed route: its planned charging stops, numbered like an
 * itinerary. Tapping a stop hands it straight to the navigation app.
 *
 * Plans with the live charge state.
 */
class RouteScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val destination: Destination,
    private val title: String = destination.name,
) : Screen(carContext), KoinComponent {

    private val planTrip: PlanTrip = get()
    private val tripStore: TripStore = get()

    // onGetTemplate() is synchronous; changes are picked up via invalidate().
    private var plan: TripPlan? = null
    private var planning = false

    /** Why the last planning attempt found no plan; `null` after a success. */
    private var failure: TripPlanResult? = null

    init {
        lifecycleScope.launch {
            // Also a trip the phone plans to the same destination.
            tripStore.plan.collect { stored ->
                plan = stored?.takeIf { it.destination.position == destination.position }
                invalidate()
            }
        }
        lifecycleScope.launch {
            planTrip.inProgress.collect {
                planning = it
                invalidate()
            }
        }
        lifecycleScope.launch {
            plan(feature.currentFix.filterNotNull().first())
        }
    }

    private suspend fun plan(fix: Fix) {
        val result = planTrip(
            PlanTrip.Params(
                from = fix.position,
                destination = destination,
                startSocPercent = feature.currentEnergy.value?.socPercent,
            ),
        ).getOrDefault(TripPlanResult.NoRoute)
        failure = result.takeUnless { it is TripPlanResult.Planned }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val current = plan
        val failed = failure
        return when {
            planning -> loadingTemplate()
            failed != null -> failureTemplate(failed)
            current == null -> loadingTemplate()
            current.stops.isEmpty() -> directTemplate()
            else -> stopsTemplate(current)
        }
    }

    private fun failureTemplate(failure: TripPlanResult): Template = when (failure) {
        is TripPlanResult.Planned -> loadingTemplate()
        TripPlanResult.NoVehicle -> messageTemplate(carContext.getString(R.string.car_route_no_vehicle))
        TripPlanResult.NoRoute -> messageTemplate(carContext.getString(R.string.car_route_no_route))
        is TripPlanResult.NoChargerInReach -> messageTemplate(
            carContext.getString(
                R.string.car_route_no_charger,
                ChargeStopFormatter.distanceLabel(failure.afterKm),
            ),
        )
    }

    private fun loadingTemplate(): Template {
        val waitingText = if (feature.currentFix.value == null) {
            R.string.car_waiting_for_location
        } else {
            R.string.car_route_planning
        }
        return ListTemplate.Builder()
            .setLoading(true)
            .setHeader(header(withRefresh = false, subtitle = carContext.getString(waitingText)))
            .build()
    }

    private fun stopsTemplate(plan: TripPlan): Template {
        // The row count is dictated by the host.
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        itemList.addItem(sendAllRow(plan))
        plan.stops.take(contentLimit - 1).forEachIndexed { index, stop ->
            itemList.addItem(stopRow(index + 1, stop))
        }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header(withRefresh = true))
            .addAction(chargeNowFab())
            .build()
    }

    private fun stopRow(ordinal: Int, stop: PlannedStop): Row = Row.Builder()
        .setTitle(ChargeStopFormatter.plannedStopTitle(ordinal, stop))
        .apply { ChargeStopFormatter.plannedStopAddressLine(stop)?.let(::addText) }
        .addText(ChargeStopFormatter.plannedStopDetailLine(stop))
        .setOnClickListener { navigateTo(carContext, stop.site.name, stop.site.position) }
        .build()

    private fun sendAllRow(plan: TripPlan): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_route_send_all))
        // IMAGE_TYPE_ICON: only tintable icons get recolored by the host.
        .setImage(icon(R.drawable.ic_destination), Row.IMAGE_TYPE_ICON)
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

    /** Floating "charge now" button; hosts render FABs icon-only. */
    private fun chargeNowFab(): Action = Action.Builder()
        .setIcon(icon(R.drawable.ic_bolt))
        .setBackgroundColor(CarColor.PRIMARY)
        .setOnClickListener { screenManager.push(ChargeNowScreen(carContext, feature)) }
        .build()

    /** Destination in reach without charging: no list to show, just the handoff. */
    private fun directTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_route_direct))
            .setHeader(header(withRefresh = true))
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_route_navigate))
                    .setOnClickListener { navigateTo(carContext, destination.name, destination.position) }
                    .build(),
            )
            .addAction(chargeNowTitledAction())
            .build()

    private fun messageTemplate(message: String): Template =
        MessageTemplate.Builder(message)
            .setHeader(header(withRefresh = true))
            .addAction(chargeNowTitledAction())
            .build()

    /** Body actions may carry titles — unlike the icon-only FAB. */
    private fun chargeNowTitledAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.car_home_charge_now))
        .setOnClickListener { screenManager.push(ChargeNowScreen(carContext, feature)) }
        .build()

    private fun header(withRefresh: Boolean, subtitle: String? = null): Header {
        val builder = Header.Builder()
            .setTitle(titleText(subtitle))
            .setStartHeaderAction(Action.BACK)

        if (withRefresh) {
            // Re-plans with the freshest position and charge state.
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_refresh))
                    .setOnClickListener {
                        lifecycleScope.launch { feature.currentFix.value?.let { plan(it) } }
                    }
                    .build(),
            )
        }
        return builder.build()
    }

    private fun titleText(subtitle: String?): String = subtitle?.let { "$title · $it" } ?: title
}

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

private const val MAPS_TAKEOVER_TIMEOUT_MS = 3_000L
