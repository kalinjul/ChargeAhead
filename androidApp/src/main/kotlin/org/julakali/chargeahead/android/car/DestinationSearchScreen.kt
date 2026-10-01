package org.julakali.chargeahead.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.toDestination
import org.julakali.chargeahead.shared.ui.car.CarDestinationSearchViewModel
import org.koin.core.scope.Scope
import kotlinx.coroutines.launch

/**
 * Type a destination in the car. While driving, the host disables the
 * keyboard and the recent destinations remain.
 */
class DestinationSearchScreen(
    carContext: CarContext,
    private val session: Scope,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarDestinationSearchViewModel>() }

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch { viewModel.uiState.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val state = viewModel.uiState.value
        val template = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_home_enter_destination))
            .setShowKeyboardByDefault(true)

        // SearchTemplate rejects an item list while loading (issue #81).
        if (state.searching) return template.setLoading(true).build()

        val emptyMessage = if (state.awaitingSubmit) R.string.car_search_submit_hint else R.string.car_search_empty
        val itemList = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(emptyMessage))
        state.recents.forEach { destination -> itemList.addItem(recentRow(destination)) }
        state.places.forEach { place -> itemList.addItem(placeRow(place)) }

        return template.setItemList(itemList.build()).build()
    }

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            viewModel.onSearchTextChanged(searchText)
        }

        override fun onSearchSubmitted(searchText: String) {
            viewModel.onSearchSubmitted(searchText)
        }
    }

    private fun recentRow(destination: Destination): Row = Row.Builder()
        .setTitle(destination.name)
        .apply { destination.address?.let(::addText) }
        .setOnClickListener { choose(destination) }
        .build()

    private fun placeRow(place: Place): Row = Row.Builder()
        .setTitle(place.name)
        .addText(place.description)
        .setOnClickListener { choose(place.toDestination()) }
        .build()

    private fun choose(destination: Destination) {
        // The search screen replaces itself with the route.
        screenManager.pop()
        screenManager.push(RouteScreen(carContext, session, destination))
    }
}
