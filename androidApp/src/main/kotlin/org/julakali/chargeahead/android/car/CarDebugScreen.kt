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
import org.julakali.chargeahead.android.phone.coarseDuration
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
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.car_debug_energy
import org.julakali.chargeahead.shared.resources.car_debug_energy_value
import org.julakali.chargeahead.shared.resources.car_debug_fix
import org.julakali.chargeahead.shared.resources.car_debug_fix_value
import org.julakali.chargeahead.shared.resources.car_debug_just_now
import org.julakali.chargeahead.shared.resources.car_debug_source_car
import org.julakali.chargeahead.shared.resources.car_debug_source_cloud
import org.julakali.chargeahead.shared.resources.car_debug_source_manual
import org.julakali.chargeahead.shared.resources.cardata_age
import org.julakali.chargeahead.shared.resources.cardata_kind_battery
import org.julakali.chargeahead.shared.resources.cardata_kind_charge_port_connected
import org.julakali.chargeahead.shared.resources.cardata_kind_charge_port_open
import org.julakali.chargeahead.shared.resources.cardata_kind_energy_low
import org.julakali.chargeahead.shared.resources.cardata_kind_energy_profile
import org.julakali.chargeahead.shared.resources.cardata_kind_model
import org.julakali.chargeahead.shared.resources.cardata_kind_odometer
import org.julakali.chargeahead.shared.resources.cardata_kind_range
import org.julakali.chargeahead.shared.resources.cardata_kind_speed
import org.julakali.chargeahead.shared.resources.cardata_never
import org.julakali.chargeahead.shared.resources.cardata_no_data
import org.julakali.chargeahead.shared.resources.cardata_no_hardware
import org.julakali.chargeahead.shared.resources.cardata_no_permission
import org.julakali.chargeahead.shared.resources.cardata_title
import org.julakali.chargeahead.shared.resources.value_unknown
import org.julakali.chargeahead.shared.Texts
import org.jetbrains.compose.resources.StringResource

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
                    .setTitle(Texts.string(Res.string.cardata_title))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    private fun energyRow(energy: EnergyState?): Row = Row.Builder()
        .setTitle(Texts.string(Res.string.car_debug_energy))
        .addText(
            energy?.let {
                Texts.string(Res.string.car_debug_energy_value, it.socPercent.roundToInt(), sourceLabel(it.source)) +
                    " · " + age(it.observedAtMillis)
            } ?: Texts.string(Res.string.value_unknown),
        )
        .build()

    private fun fixRow(fix: Fix?): Row = Row.Builder()
        .setTitle(Texts.string(Res.string.car_debug_fix))
        .addText(
            fix?.let {
                Texts.string(Res.string.car_debug_fix_value,
                    "%.4f, %.4f".format(it.position.lat, it.position.lon),
                    it.speedMps?.let { mps -> (mps * 3.6).roundToInt().toString() } ?: Texts.string(Res.string.value_unknown),
                    it.bearingDeg?.roundToInt()?.toString() ?: Texts.string(Res.string.value_unknown),
                ) + " · " + age(it.timestampMillis)
            } ?: Texts.string(Res.string.value_unknown),
        )
        .build()

    private fun pointRow(kind: CarDataKind, point: CarDataPoint?): Row = Row.Builder()
        .setTitle(Texts.string(kindLabel(kind)))
        .addText(
            point?.let { "${value(it)} · ${age(it.observedAtMillis)}" }
                ?: Texts.string(Res.string.cardata_never),
        )
        .build()

    private fun value(point: CarDataPoint): String = when (point.status) {
        CarDataStatus.AVAILABLE -> point.value ?: Texts.string(Res.string.value_unknown)
        CarDataStatus.NO_PERMISSION -> Texts.string(Res.string.cardata_no_permission)
        CarDataStatus.NO_DATA -> Texts.string(Res.string.cardata_no_data)
        CarDataStatus.NO_CAR_HARDWARE -> Texts.string(Res.string.cardata_no_hardware)
    }

    private fun sourceLabel(source: SoCSourceKind): String = Texts.string(
        when (source) {
            SoCSourceKind.MANUAL -> Res.string.car_debug_source_manual
            SoCSourceKind.CAR_HARDWARE -> Res.string.car_debug_source_car
            SoCSourceKind.OEM_CLOUD -> Res.string.car_debug_source_cloud
        },
    )

    /** "vor 3 Minuten", or "gerade eben" under a minute. */
    private fun age(atMillis: Long): String {
        val minutes = (time.nowMillis() - atMillis) / 60_000.0
        if (minutes < 1) return Texts.string(Res.string.car_debug_just_now)
        val (plural, count) = coarseDuration(minutes.toLong())
        val text = Texts.plural(plural, count, count)
        return Texts.string(Res.string.cardata_age, text)
    }

    private fun kindLabel(kind: CarDataKind): StringResource = when (kind) {
        CarDataKind.MODEL -> Res.string.cardata_kind_model
        CarDataKind.ENERGY_PROFILE -> Res.string.cardata_kind_energy_profile
        CarDataKind.BATTERY_PERCENT -> Res.string.cardata_kind_battery
        CarDataKind.RANGE -> Res.string.cardata_kind_range
        CarDataKind.ENERGY_IS_LOW -> Res.string.cardata_kind_energy_low
        CarDataKind.CHARGE_PORT_OPEN -> Res.string.cardata_kind_charge_port_open
        CarDataKind.CHARGE_PORT_CONNECTED -> Res.string.cardata_kind_charge_port_connected
        CarDataKind.SPEED -> Res.string.cardata_kind_speed
        CarDataKind.ODOMETER -> Res.string.cardata_kind_odometer
    }
}

/** What matters while driving first; the host's row limit cuts the tail. */
private val KINDS_IN_ORDER = listOf(
    CarDataKind.BATTERY_PERCENT,
    CarDataKind.SPEED,
    CarDataKind.RANGE,
    CarDataKind.ENERGY_IS_LOW,
    CarDataKind.CHARGE_PORT_CONNECTED,
    CarDataKind.CHARGE_PORT_OPEN,
    CarDataKind.ODOMETER,
    CarDataKind.MODEL,
    CarDataKind.ENERGY_PROFILE,
)
