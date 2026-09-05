package de.autoapp.android.car

import android.content.pm.PackageManager
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
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.domain.SoCDiagnostics
import kotlinx.coroutines.launch

/**
 * Enter the state of charge manually while driving.
 *
 * In steps rather than as a number input: typing while driving is not an
 * option, and the Car App Library offers no number field for good reason.
 * The precision is sufficient — the range estimate is based on a constant
 * consumption anyway, against which five percentage points of input error don't matter.
 *
 * The steps run from high to low so the most likely tap (a high charge
 * level right after charging) is on top.
 */
class SoCScreen(carContext: CarContext) : Screen(carContext) {

    private val settings = ChargeStopsFeatureProvider.settingsStore(carContext)

    // The current value belongs visibly on screen: without it the driver
    // might re-enter the same value or have to guess what was last set.
    private var currentPercent: Double? = null
    private var diagnostics: SoCDiagnostics? = null
    private var permissionRequestPending = false

    init {
        lifecycleScope.launch {
            settings.manualSocPercent.collect { updated ->
                currentPercent = updated
                invalidate()
            }
        }
        lifecycleScope.launch {
            settings.socDiagnostics.collect { updated ->
                diagnostics = updated
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // First row only displays, without doing anything — hence one fewer
        // step available for selection.
        itemList.addItem(
            Row.Builder()
                .setTitle(currentText())
                .addText(carHardwareText())
                .build(),
        )

        // Only offer what's still missing: if the permission is already
        // granted, the row would be a dead button.
        var reserved = 1
        if (!hasCarFuelPermission()) {
            itemList.addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_soc_carhardware_ask))
                    .setOnClickListener(::requestCarFuelPermission)
                    .build(),
            )
            reserved = 2
        }

        STEP_PERCENTS.take(contentLimit - reserved).forEach { percent -> itemList.addItem(buildRow(percent)) }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.car_soc_title))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    /** "Current: 60%" — or a note that nothing has been entered yet. */
    private fun currentText(): String {
        val percent = currentPercent
            ?: return carContext.getString(R.string.car_soc_unset)
        return carContext.getString(R.string.car_soc_current, percent.toInt().toString())
    }

    private fun buildRow(percent: Int): Row =
        Row.Builder()
            .setTitle(carContext.getString(R.string.car_soc_percent, percent))
            .setOnClickListener {
                lifecycleScope.launch {
                    settings.setManualSocPercent(percent.toDouble())
                    screenManager.pop()
                }
            }
            .build()

    /** What the vehicle delivered on the last attempt. */
    private fun carHardwareText(): String {
        val current = diagnostics
        return when {
            permissionRequestPending -> carContext.getString(R.string.car_soc_carhardware_asking)
            current?.outcome == SoCDiagnostics.Outcome.AVAILABLE ->
                carContext.getString(
                    R.string.car_soc_carhardware_available,
                    current.detail.orEmpty(),
                )

            else -> carContext.getString(R.string.car_soc_carhardware_none)
        }
    }

    private fun hasCarFuelPermission(): Boolean =
        carContext.checkSelfPermission(CarHardwareSoCSource.CAR_FUEL_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Requested separately from location, and only here.
     *
     * Vehicle data is an opportunistic upgrade: the app does everything it
     * needs to without it. Asking for a permission you don't need,
     * unprompted, at startup is the surest way to get it denied.
     */
    private fun requestCarFuelPermission() {
        if (permissionRequestPending) return
        permissionRequestPending = true
        invalidate()

        carContext.requestPermissions(listOf(CarHardwareSoCSource.CAR_FUEL_PERMISSION)) { _, _ ->
            permissionRequestPending = false
            invalidate()
        }
    }

    private companion object {
        /**
         * Steps of ten from the top down. Below 10%, the reserve is reached
         * and range is zero — finer granularity there wouldn't help.
         */
        val STEP_PERCENTS = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10)
    }
}
