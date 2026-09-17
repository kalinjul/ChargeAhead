package org.julakali.chargeahead.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.R
import org.julakali.chargeahead.shared.ChargeStopsFeature
import org.julakali.chargeahead.shared.PlanningFeature
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.Place
import org.julakali.chargeahead.shared.toDestination
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Type a destination in the car. While driving, the host disables the
 * keyboard and the recent destinations remain.
 */
class DestinationSearchScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
    private val planning: PlanningFeature,
    private val settings: SettingsStore,
) : Screen(carContext) {

    // onGetTemplate() is synchronous; changes are picked up via invalidate().
    private var recents: List<Destination> = emptyList()
    private var results: List<Place> = emptyList()
    private var query = ""
    private var submittedQuery = ""
    private var searching = false
    private var searchJob: Job? = null

    init {
        lifecycleScope.launch {
            settings.recentDestinations.collect { updated ->
                recents = updated
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val template = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_home_enter_destination))
            .setShowKeyboardByDefault(true)

        // SearchTemplate rejects an item list while loading (issue #81).
        if (searching) return template.setLoading(true).build()

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
            results.forEach { place -> itemList.addItem(placeRow(place)) }
        }

        return template.setItemList(itemList.build()).build()
    }

    // Geocoding fires on submit only, not per keystroke.
    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            query = searchText
            if (searchText.isBlank()) {
                results = emptyList()
                searchJob?.cancel()
                searching = false
            }
            invalidate()
        }

        override fun onSearchSubmitted(searchText: String) {
            query = searchText
            if (searchText.isBlank()) return

            submittedQuery = searchText
            searchJob?.cancel()
            searching = true
            invalidate()
            searchJob = lifecycleScope.launch {
                results = feature.searchDestinations(searchText)
                searching = false
                invalidate()
            }
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
        screenManager.push(RouteScreen(carContext, feature, planning, destination))
    }
}
