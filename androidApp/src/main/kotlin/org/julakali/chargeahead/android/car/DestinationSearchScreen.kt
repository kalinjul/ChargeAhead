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
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.DestinationSearch
import org.julakali.chargeahead.shared.domain.usecases.DestinationSearchObserver
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.toDestination
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Type a destination in the car. While driving, the host disables the
 * keyboard and the recent destinations remain.
 */
class DestinationSearchScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val settings: SettingsStore,
) : Screen(carContext), KoinComponent {

    private val observeDestinationSearch: DestinationSearchObserver = get()

    // onGetTemplate() is synchronous; changes are picked up via invalidate().
    private var recents: List<Destination> = emptyList()
    private var search = DestinationSearch(query = "", results = emptyList(), searching = false)
    private var query = ""
    private var submittedQuery = ""

    init {
        lifecycleScope.launch {
            settings.recentDestinations.collect { updated ->
                recents = updated
                invalidate()
            }
        }
        lifecycleScope.launch {
            observeDestinationSearch.flow.collect { updated ->
                search = updated
                invalidate()
            }
        }
        observeDestinationSearch(DestinationSearchObserver.Params(query = ""))
    }

    override fun onGetTemplate(): Template {
        val template = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_home_enter_destination))
            .setShowKeyboardByDefault(true)

        // SearchTemplate rejects an item list while loading (issue #81).
        if (search.searching) return template.setLoading(true).build()

        // Until the search is submitted, an empty list means "not searched yet".
        val emptyMessage = if (query.isNotBlank() && query != submittedQuery) {
            R.string.car_search_submit_hint
        } else {
            R.string.car_search_empty
        }
        val itemList = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(emptyMessage))

        if (query.isBlank()) {
            recents.forEach { destination -> itemList.addItem(recentRow(destination)) }
        } else {
            search.results.orEmpty().forEach { place -> itemList.addItem(placeRow(place)) }
        }

        return template.setItemList(itemList.build()).build()
    }

    // Geocoding fires on submit only, not per keystroke.
    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            query = searchText
            if (searchText.isBlank()) observeDestinationSearch(DestinationSearchObserver.Params(query = ""))
            invalidate()
        }

        override fun onSearchSubmitted(searchText: String) {
            query = searchText
            if (searchText.isBlank()) return

            submittedQuery = searchText
            observeDestinationSearch(DestinationSearchObserver.Params(searchText, debounce = false))
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
        screenManager.push(RouteScreen(carContext, feature, destination))
    }
}
