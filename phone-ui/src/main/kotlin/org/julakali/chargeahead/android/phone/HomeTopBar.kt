package org.julakali.chargeahead.android.phone

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.TripPlan
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState

/** Material's docked search bar with the hits inside, or the destination header while a trip is planned. */
@Composable
fun HomeTopBar(
    search: SearchUiState,
    trip: TripPlan?,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onPick: (SearchRow) -> Unit,
    onClearTrip: () -> Unit,
    onFlyTo: (LatLon) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The search closes from outside too (a pick, back, a tap on the map); the keyboard follows the state.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(search.expanded) {
        if (!search.expanded) focusManager.clearFocus()
    }

    // Header and bar swap with Material's fade-through instead of a hard cut.
    AnimatedContent(
        targetState = trip != null && !search.expanded,
        transitionSpec = { fadeIn(ChargeAheadMotion.fadeThroughIn()) togetherWith fadeOut(ChargeAheadMotion.fadeThroughOut()) },
        label = "top bar",
        modifier = modifier,
    ) { showHeader ->
        if (showHeader && trip != null) {
            DestinationHeader(
                // The short name (town, or street and number), not the full label.
                title = trip.destination.name,
                subtitle = trip.headerLine(),
                onClear = onClearTrip,
                onTitleClick = { onFlyTo(trip.destination.position) },
                // Material's bar brings its own surface, the header has to bring one too.
                standalone = true,
            )
        } else {
            HomeDockedSearchBar(
                query = search.query,
                searching = search.searching,
                expanded = search.expanded,
                onExpandedChange = onExpandedChange,
                onQueryChange = onQueryChange,
                onClear = { onQueryChange("") },
            ) {
                SearchResultsPanel(uiState = search, onPick = onPick, standalone = false)
            }
        }
    }
}
