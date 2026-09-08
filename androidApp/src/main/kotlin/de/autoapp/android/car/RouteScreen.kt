package de.autoapp.android.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.core.TripPlan
import de.autoapp.shared.core.TripPlanResult
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Fix
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The committed route: its planned charging stops, numbered like an
 * itinerary. Tapping a stop hands it straight to the navigation app — while
 * driving there is no time for a detour through a detail view.
 *
 * Deliberately no map template: the map is Google Maps' territory — this app
 * is the text panel beside it and hands every drive off.
 *
 * Plans with the live charge state: when the car reports its battery, that
 * value wins and the driver never has to type a percentage.
 */
class RouteScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val destination: Destination,
    private val title: String = destination.name,
) : Screen(carContext) {

    // onGetTemplate() is synchronous and therefore only reads the last
    // remembered state; changes are picked up via invalidate().
    private var result: TripPlanResult? = null

    init {
        lifecycleScope.launch {
            // Remembered as the app-wide destination: the phone follows along,
            // and the recents list learns what the driver actually goes to.
            feature.setDestination(destination)
            plan(feature.currentFix.filterNotNull().first())
        }
    }

    private suspend fun plan(fix: Fix) {
        result = null
        invalidate()
        val planning = feature.planning ?: return
        result = planning.planTrip(
            from = fix.position,
            destination = destination,
            socOverridePercent = feature.currentEnergy.value?.socPercent,
        )
        invalidate()
    }

    override fun onGetTemplate(): Template = when (val current = result) {
        null -> loadingTemplate()
        is TripPlanResult.Planned ->
            if (current.plan.stops.isEmpty()) {
                directTemplate()
            } else {
                stopsTemplate(current.plan)
            }

        TripPlanResult.NoVehicle -> messageTemplate(carContext.getString(R.string.car_route_no_vehicle))
        TripPlanResult.NoRoute -> messageTemplate(carContext.getString(R.string.car_route_no_route))
        is TripPlanResult.NoChargerInReach -> messageTemplate(
            carContext.getString(
                R.string.car_route_no_charger,
                ChargeStopFormatter.distanceLabel(current.afterKm),
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
        // The row count is dictated by the host, not the app (AGENTS.md).
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
        .addText(ChargeStopFormatter.plannedStopPrimaryLine(stop))
        .addText(ChargeStopFormatter.plannedStopSecondaryLine(stop))
        .setOnClickListener { navigateTo(carContext, stop.site.name, stop.site.position) }
        .build()

    private fun sendAllRow(plan: TripPlan): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_route_send_all))
        // IMAGE_TYPE_ICON: only icons declared tintable get recolored by the
        // host — untinted ones stay black on a dark theme.
        .setImage(icon(R.drawable.ic_send), Row.IMAGE_TYPE_ICON)
        .setOnClickListener { sendRouteToMaps(plan) }
        .build()

    /**
     * The whole route with every stop as a waypoint. `geo:`/ACTION_NAVIGATE
     * can't carry waypoints, so this opens the Maps directions URL on the
     * phone — once the driver starts it there, Maps takes the car screen.
     *
     * Via the application context, not the [CarContext]: the latter is bound
     * to the car's virtual display, and launching a phone activity there is a
     * SecurityException ("launchDisplayId=…") — observed on the DHU.
     */
    private fun sendRouteToMaps(plan: TripPlan) {
        val url = MapsHandoff.directionsUrl(
            origin = null,
            destination = plan.destination.position,
            waypoints = plan.stops.map { it.site.position },
        )
        try {
            carContext.applicationContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_route_sent_to_phone),
                CarToast.LENGTH_LONG,
            ).show()
        } catch (notFound: ActivityNotFoundException) {
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_no_navigation_app),
                CarToast.LENGTH_LONG,
            ).show()
        } catch (denied: SecurityException) {
            // Never let a blocked launch crash the car UI again.
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_no_navigation_app),
                CarToast.LENGTH_LONG,
            ).show()
        }
    }

    /** Floating "charge now" button — hosts render FABs icon-only, so the bolt has to say it. */
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
            // Re-plans with the freshest position and charge state — the plan
            // ages while the car drives and charges.
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

    private fun titleText(subtitle: String?): String {
        val base = subtitle?.let { "$title · $it" } ?: title
        return if (feature.currentState.isDemo) {
            carContext.getString(R.string.car_title_suffix_demo, base)
        } else {
            base
        }
    }

    private fun icon(resId: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
}
