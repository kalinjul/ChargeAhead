package de.autoapp.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.domain.Destination
import kotlinx.coroutines.launch

/**
 * Choose a destination while driving — from history, not by typing.
 *
 * The Car App Library offers no text field, and that's correct: typing an
 * address while driving is off the table. The first destination of a trip
 * is therefore set on the phone; in the car it can only be changed or cleared.
 */
class DestinationScreen(carContext: CarContext) : Screen(carContext) {

    private val settings = ChargeStopsFeatureProvider.settingsStore(carContext)

    // onGetTemplate() is synchronous and therefore only reads the last
    // remembered state; changes are picked up via invalidate().
    private var recent: List<Destination> = emptyList()

    init {
        lifecycleScope.launch {
            settings.recentDestinations.collect { updated ->
                recent = updated
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {

        if (recent.isEmpty()) {
            return MessageTemplate.Builder(carContext.getString(R.string.car_destination_empty))
                .setHeader(header())
                .build()
        }

        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // The entry to clear the destination is on top: it's the one you
        // want to find quickly when in doubt.
        itemList.addItem(clearRow())
        recent.take(contentLimit - 1).forEach { itemList.addItem(destinationRow(it)) }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header())
            .build()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(carContext.getString(R.string.car_destination_title))
        .setStartHeaderAction(Action.BACK)
        .build()

    private fun clearRow(): Row = Row.Builder()
        .setTitle(carContext.getString(R.string.car_destination_clear))
        .addText(carContext.getString(R.string.car_destination_none))
        .setOnClickListener { choose(null) }
        .build()

    private fun destinationRow(destination: Destination): Row = Row.Builder()
        .setTitle(destination.name)
        .setOnClickListener { choose(destination) }
        .build()

    private fun choose(destination: Destination?) {
        lifecycleScope.launch {
            settings.setDestination(destination)
            screenManager.pop()
        }
    }
}
