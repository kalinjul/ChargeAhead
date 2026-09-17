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
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import kotlinx.coroutines.launch

/** Enter the state of charge manually while driving, in steps from high to low. */
class SoCScreen(
    carContext: CarContext,
    private val settings: SettingsStore,
    private val permissions: CarPermissions,
) : Screen(carContext) {

    private var currentPercent: Double? = null
    private var diagnostics: SoCDiagnostics? = null
    private var hasCarFuelPermission =
        permissions.granted(CarEnergyLevels.CAR_FUEL_PERMISSION).value
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
        lifecycleScope.launch {
            permissions.granted(CarEnergyLevels.CAR_FUEL_PERMISSION).collect { granted ->
                hasCarFuelPermission = granted
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // The first row only displays the current value.
        itemList.addItem(
            Row.Builder()
                .setTitle(currentText())
                .addText(carHardwareText())
                .build(),
        )

        // Only offer the permission row if it isn't granted yet.
        var reserved = 1
        if (!hasCarFuelPermission) {
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

    /** Requested separately from location, and only here. */
    private fun requestCarFuelPermission() {
        if (permissionRequestPending) return
        permissionRequestPending = true
        invalidate()

        // A grant reaches the SoC source through CarPermissions.
        permissions.request(listOf(CarEnergyLevels.CAR_FUEL_PERMISSION)) {
            permissionRequestPending = false
            invalidate()
        }
    }

    private companion object {
        /** Steps of ten from the top down, down to the reserve. */
        val STEP_PERCENTS = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10)
    }
}
