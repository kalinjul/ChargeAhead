package org.julakali.chargeahead.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.Fix
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import org.julakali.chargeahead.shared.ui.CarDataViewModel
import org.koin.core.scope.Scope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What the car reports, read in the car: the charge state the app runs on,
 * the last fix, then every point the hardware recorder stored, the same
 * ones the phone's debug view lists. The host's row limit cuts the tail,
 * so the rows that matter while driving come first.
 */
class CarDebugScreen(
    carContext: CarContext,
    session: Scope,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarDataViewModel>() }
    private val feature: ChargeStopsFeature = session.get()
    private val time: TimeProvider = session.get()

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch { viewModel.uiState.collect { invalidate() } }
        lifecycleScope.launch { feature.currentEnergy.collect { invalidate() } }
        lifecycleScope.launch { feature.currentFix.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val points = viewModel.uiState.value.points.associateBy { it.kind }

        val rows = buildList {
            add(energyRow(feature.currentEnergy.value))
            add(fixRow(feature.currentFix.value))
            KINDS_IN_ORDER.forEach { kind -> add(pointRow(kind, points[kind])) }
        }
        val itemList = ItemList.Builder()
        rows.take(contentLimit).forEach(itemList::addItem)

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.cardata_title))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    private fun energyRow(energy: EnergyState?): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_debug_energy))
        .addText(
            energy?.let {
                carContext.getString(R.string.car_debug_energy_value, it.socPercent.roundToInt(), sourceLabel(it.source)) +
                    " · " + age(it.observedAtMillis)
            } ?: carContext.getString(R.string.value_unknown),
        )
        .build()

    private fun fixRow(fix: Fix?): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_debug_fix))
        .addText(
            fix?.let {
                carContext.getString(
                    R.string.car_debug_fix_value,
                    "%.4f, %.4f".format(it.position.lat, it.position.lon),
                    it.speedMps?.let { mps -> (mps * 3.6).roundToInt().toString() } ?: carContext.getString(R.string.value_unknown),
                    it.bearingDeg?.roundToInt()?.toString() ?: carContext.getString(R.string.value_unknown),
                ) + " · " + age(it.timestampMillis)
            } ?: carContext.getString(R.string.value_unknown),
        )
        .build()

    private fun pointRow(kind: CarDataKind, point: CarDataPoint?): Row = Row.Builder()
        .setTitle(carContext.getString(kindLabel(kind)))
        .addText(
            point?.let { "${value(it)} · ${age(it.observedAtMillis)}" }
                ?: carContext.getString(R.string.cardata_never),
        )
        .build()

    private fun value(point: CarDataPoint): String = when (point.status) {
        CarDataStatus.AVAILABLE -> point.value ?: carContext.getString(R.string.value_unknown)
        CarDataStatus.NO_PERMISSION -> carContext.getString(R.string.cardata_no_permission)
        CarDataStatus.NO_DATA -> carContext.getString(R.string.cardata_no_data)
        CarDataStatus.NO_CAR_HARDWARE -> carContext.getString(R.string.cardata_no_hardware)
    }

    private fun sourceLabel(source: SoCSourceKind): String = carContext.getString(
        when (source) {
            SoCSourceKind.MANUAL -> R.string.car_debug_source_manual
            SoCSourceKind.CAR_HARDWARE -> R.string.car_debug_source_car
            SoCSourceKind.OEM_CLOUD -> R.string.car_debug_source_cloud
        },
    )

    /** "vor 3 Minuten", or "gerade eben" under a minute. */
    private fun age(atMillis: Long): String {
        val minutes = (time.nowMillis() - atMillis) / 60_000.0
        if (minutes < 1) return carContext.getString(R.string.car_debug_just_now)
        val (plural, count) = when {
            minutes < 60 -> R.plurals.phone_duration_minutes to minutes.toInt()
            minutes < 60 * 24 -> R.plurals.phone_duration_hours to (minutes / 60).toInt()
            else -> R.plurals.phone_duration_days to (minutes / (60 * 24)).toInt()
        }
        val text = carContext.resources.getQuantityString(plural, count, count)
        return carContext.getString(R.string.cardata_age, text)
    }

    private fun kindLabel(kind: CarDataKind): Int = when (kind) {
        CarDataKind.MODEL -> R.string.cardata_kind_model
        CarDataKind.ENERGY_PROFILE -> R.string.cardata_kind_energy_profile
        CarDataKind.BATTERY_PERCENT -> R.string.cardata_kind_battery
        CarDataKind.RANGE -> R.string.cardata_kind_range
        CarDataKind.ENERGY_IS_LOW -> R.string.cardata_kind_energy_low
        CarDataKind.SPEED -> R.string.cardata_kind_speed
        CarDataKind.ODOMETER -> R.string.cardata_kind_odometer
    }
}

/** What matters while driving first; the host's row limit cuts the tail. */
private val KINDS_IN_ORDER = listOf(
    CarDataKind.BATTERY_PERCENT,
    CarDataKind.SPEED,
    CarDataKind.RANGE,
    CarDataKind.ENERGY_IS_LOW,
    CarDataKind.ODOMETER,
    CarDataKind.MODEL,
    CarDataKind.ENERGY_PROFILE,
)
