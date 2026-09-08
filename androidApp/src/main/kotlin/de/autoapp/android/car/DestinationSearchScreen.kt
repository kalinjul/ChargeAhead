package de.autoapp.android.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import de.autoapp.android.ChargeStopsFeatureProvider
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Type a destination in the car. While driving, the host disables the
 * keyboard on its own — then the recent destinations below the search box
 * remain, which is exactly the list a driver can still use safely.
 */
class DestinationSearchScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
) : Screen(carContext) {

    private val settings = ChargeStopsFeatureProvider.settingsStore(carContext)

    // onGetTemplate() is synchronous and therefore only reads the last
    // remembered state; changes are picked up via invalidate().
    private var recents: List<Destination> = emptyList()
    private var results: List<Place> = emptyList()
    private var query = ""
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
        val itemList = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(R.string.car_search_empty))

        if (query.isBlank()) {
            recents.forEach { destination -> itemList.addItem(recentRow(destination)) }
        } else {
            results.forEach { place -> itemList.addItem(placeRow(place)) }
        }

        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_home_enter_destination))
            .setShowKeyboardByDefault(true)
            .setLoading(searching)
            .setItemList(itemList.build())
            .build()
    }

    // Geocoding fires on submit only, not per keystroke: Nominatim's usage
    // policy caps at one request per second, and a car keyboard produces
    // characters slower than that limit forgives mistakes.
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
        .setOnClickListener { choose(destination) }
        .build()

    private fun placeRow(place: Place): Row = Row.Builder()
        .setTitle(place.name)
        .addText(place.description)
        .setOnClickListener { choose(Destination(place.name, place.position)) }
        .build()

    private fun choose(destination: Destination) {
        // The search screen replaces itself with the route: back from the
        // planned stops should land on the start screen, not in the keyboard.
        screenManager.pop()
        screenManager.push(RouteScreen(carContext, feature, destination))
    }
}
