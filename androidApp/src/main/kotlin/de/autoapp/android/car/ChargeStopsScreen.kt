package de.autoapp.android.car

import android.Manifest
import android.content.pm.PackageManager
import androidx.car.app.CarContext
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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.ChargeStopsState
import de.autoapp.shared.domain.ChargeStop
import de.autoapp.shared.domain.Reachability
import kotlinx.coroutines.launch

/**
 * The list of charging stops in Android Auto.
 *
 * Translation only: every number comes from [ChargeStopFormatter], every
 * state from [ChargeStopsState]. No computation happens here (AGENTS.md, car UI rules).
 */
class ChargeStopsScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val feature = ChargeStopsFeatureProvider.createForCar(carContext)

    private var state = ChargeStopsState()
    private var permissionRequestPending = false

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            // onGetTemplate() is synchronous and therefore only reads the
            // last remembered state; changes are picked up via invalidate().
            feature.state.collect { updated ->
                state = updated
                invalidate()
            }
        }
        startIfPermitted()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        feature.close()
    }

    override fun onGetTemplate(): Template = when {
        !hasLocationPermission() -> permissionTemplate()
        state.stops.isNotEmpty() -> stopsTemplate()
        else -> emptyTemplate()
    }

    private fun startIfPermitted() {
        if (hasLocationPermission()) feature.start()
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
            if (granted.isNotEmpty()) feature.start()
            invalidate()
        }
    }

    private fun permissionTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_permission_message))
            .setHeader(header(withRefresh = false))
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_permission_action))
                    .setOnClickListener(::requestLocationPermission)
                    .build(),
            )
            .build()

    private fun stopsTemplate(): Template {
        // The row count is dictated by the host, not the app (AGENTS.md).
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        state.stops.take(contentLimit).forEach { stop -> itemList.addItem(buildRow(stop)) }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header(withRefresh = true))
            .build()
    }

    private fun emptyTemplate(): Template =
        MessageTemplate.Builder(emptyMessage())
            .setHeader(header(withRefresh = true))
            .build()

    /**
     * Header built from title, app icon and — when there's something to
     * refresh — the refresh action.
     *
     * The action deliberately carries only an icon: actions on the right
     * edge of the header must have one and are not allowed a title of their
     * own (`ActionsConstraints.ACTIONS_CONSTRAINTS_MULTI_HEADER`).
     */
    private fun header(withRefresh: Boolean): Header {
        val builder = Header.Builder()
            .setTitle(title())
            .setStartHeaderAction(Action.APP_ICON)

        if (withRefresh) {
            // The state of charge is the value that changes while driving —
            // it belongs within reach, not in the phone settings.
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_destination),
                        ).build(),
                    )
                    .setOnClickListener { screenManager.push(DestinationScreen(carContext)) }
                    .build(),
            )
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_battery),
                        ).build(),
                    )
                    .setOnClickListener { screenManager.push(SoCScreen(carContext)) }
                    .build(),
            )
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_refresh),
                        ).build(),
                    )
                    .setOnClickListener { feature.refresh() }
                    .build(),
            )
        }
        return builder.build()
    }

    /**
     * The title carries the state along. In a list it's the only area
     * that's always visible — the driver must not have to scroll to find
     * out they're looking at demo data or a stale list.
     */
    private fun title(): String {
        val base = carContext.getString(R.string.car_list_title)
        return when {
            state.isDemo -> carContext.getString(R.string.car_title_suffix_demo, base)
            state.phase == ChargeStopsState.Phase.FAILED && state.stops.isNotEmpty() ->
                carContext.getString(R.string.car_title_suffix_stale, base)
            // Which route is being searched along belongs in the header:
            // it's the only area that's always visible.
            state.routeStatus == ChargeStopsState.RouteStatus.ACTIVE && state.destination != null ->
                carContext.getString(R.string.car_title_suffix_route, base, state.destination!!.name)

            state.routeStatus == ChargeStopsState.RouteStatus.UNAVAILABLE ->
                carContext.getString(
                    R.string.car_title_suffix_route,
                    base,
                    carContext.getString(R.string.car_route_unavailable),
                )

            else -> base
        }
    }

    private fun emptyMessage(): String = when (state.phase) {
        ChargeStopsState.Phase.WAITING_FOR_LOCATION ->
            carContext.getString(R.string.car_waiting_for_location)

        ChargeStopsState.Phase.LOADING -> carContext.getString(R.string.car_loading)

        ChargeStopsState.Phase.READY -> carContext.getString(R.string.car_no_stops)

        ChargeStopsState.Phase.FAILED -> when (state.failure) {
            ChargeStopsState.FailureReason.LOCATION_UNAVAILABLE ->
                carContext.getString(R.string.car_location_unavailable)

            ChargeStopsState.FailureReason.SITES_UNAVAILABLE, null ->
                carContext.getString(R.string.car_sites_unavailable)
        }
    }

    private fun buildRow(stop: ChargeStop): Row {
        // No operator in the title — unlike on the phone. Observed in the
        // car: "Heikendorf · Energieeinkaufs- und Dienstleistungs-
        // gesellschaft mbH" wrapped across two lines, halving the number of
        // visible charging stops. The operator is in the detail view, and
        // whoever wants to filter by network has the filter for that.
        // Reachability is spelled out as a word in the title, not just as a
        // color (AGENTS.md). While it's unknown, there's nothing to name.
        val reachabilityLabel = reachabilityText(stop.reachability)
        val title = if (reachabilityLabel == null) {
            stop.site.name
        } else {
            "$reachabilityLabel · ${stop.site.name}"
        }

        return Row.Builder()
            .setTitle(title)
            .addText(ChargeStopFormatter.primaryLine(stop))
            .addText(ChargeStopFormatter.secondaryLine(stop))
            .setImage(reachabilityIcon(stop.reachability))
            .setOnClickListener { screenManager.push(ChargeStopDetailScreen(carContext, stop)) }
            .build()
    }

    private fun reachabilityText(reachability: Reachability): String? = when (reachability) {
        Reachability.REACHABLE -> carContext.getString(R.string.car_row_reachable)
        Reachability.MARGINAL -> carContext.getString(R.string.car_row_marginal)
        Reachability.UNREACHABLE -> carContext.getString(R.string.car_row_unreachable)
        Reachability.UNKNOWN -> null
    }

    private fun reachabilityIcon(reachability: Reachability): CarIcon {
        val tint = when (reachability) {
            Reachability.REACHABLE -> CarColor.GREEN
            Reachability.MARGINAL -> CarColor.YELLOW
            Reachability.UNREACHABLE -> CarColor.RED
            // Don't invent a gray: the host picks the color that fits its color scheme.
            Reachability.UNKNOWN -> CarColor.DEFAULT
        }
        return CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_charge_pin))
            .setTint(tint)
            .build()
    }
}
