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
import org.julakali.chargeahead.shared.ui.car.CarViewModels
import kotlinx.coroutines.launch

/** Enter the state of charge manually while driving, in steps from high to low. */
class SoCScreen(
    carContext: CarContext,
    viewModels: CarViewModels,
    private val permissions: CarPermissions,
) : Screen(carContext) {

    private val viewModel = screenViewModel { viewModels.soc() }

    // The permission is the platform's; only it stays here.
    private var hasCarFuelPermission =
        permissions.granted(CarEnergyLevels.CAR_FUEL_PERMISSION).value
    private var permissionRequestPending = false

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (state.saved) screenManager.pop() else invalidate()
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
        val state = viewModel.uiState.value
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // The first row only displays the current value.
        itemList.addItem(
            Row.Builder()
                .setTitle(currentText(state.currentPercent))
                .addText(carHardwareText(state.carReading))
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

        state.steps.take(contentLimit - reserved).forEach { percent -> itemList.addItem(buildRow(percent)) }

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
    private fun currentText(currentPercent: Double?): String {
        val percent = currentPercent
            ?: return carContext.getString(R.string.car_soc_unset)
        return carContext.getString(R.string.car_soc_current, percent.toInt().toString())
    }

    private fun buildRow(percent: Int): Row =
        Row.Builder()
            .setTitle(carContext.getString(R.string.car_soc_percent, percent))
            .setOnClickListener { viewModel.onStepPicked(percent) }
            .build()

    /** What the vehicle delivered on the last attempt. */
    private fun carHardwareText(carReading: String?): String = when {
        permissionRequestPending -> carContext.getString(R.string.car_soc_carhardware_asking)
        carReading != null -> carContext.getString(R.string.car_soc_carhardware_available, carReading)
        else -> carContext.getString(R.string.car_soc_carhardware_none)
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
}
