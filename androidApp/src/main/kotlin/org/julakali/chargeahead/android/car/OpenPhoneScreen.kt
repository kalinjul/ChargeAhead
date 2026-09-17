package org.julakali.chargeahead.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.PhoneUiVisibility
import org.julakali.chargeahead.android.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Asks the driver to open the app on the phone, which the whole-route
 * hand-off needs. It goes ahead by itself the moment the app is visible, so
 * the driver doesn't have to tap again. The next stop alone works without the
 * phone and is offered as the alternative.
 */
class OpenPhoneScreen(
    carContext: CarContext,
    private val onPhoneOpened: () -> Unit,
    private val onNextStopOnly: () -> Unit,
) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            PhoneUiVisibility.isVisible.first { it }
            screenManager.pop()
            onPhoneOpened()
        }
    }

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_route_open_phone))
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.car_route_send_all))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .setIcon(icon(R.drawable.ic_destination))
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_route_next_stop_only))
                    .setOnClickListener {
                        screenManager.pop()
                        onNextStopOnly()
                    }
                    .build(),
            )
            .build()
}
