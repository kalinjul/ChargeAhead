package org.julakali.chargeahead.uitests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.HomeSearchBar
import org.julakali.chargeahead.android.phone.SearchResultsPanel
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState

@Composable
private fun Framed(content: @Composable () -> Unit) {
    PreviewScaffold {
        Box(Modifier.width(400.dp).padding(8.dp)) { content() }
    }
}

@Composable
private fun Bar(query: String, searching: Boolean = false, clearable: Boolean = false) {
    Framed {
        HomeSearchBar(
            query = query,
            searching = searching,
            onFocused = {},
            onQueryChange = {},
            onClear = {},
            focusRequester = FocusRequester(),
            clearable = clearable,
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeSearchBarEmpty() = Bar(query = "")

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeSearchBarWithQuery() = Bar(query = "München Marienplatz")

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeSearchBarSearching() = Bar(query = "München", searching = true)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun HomeSearchBarClearable() = Bar(query = "", clearable = true)

private fun row(title: String, detail: String?, distanceKm: Double?, recent: Boolean) = SearchRow(
    destination = Destination(name = title, position = LatLon(48.1, 11.5), address = detail),
    title = title,
    detail = detail,
    distanceKm = distanceKm,
    recent = recent,
)

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SearchResultsRecents() {
    Framed {
        SearchResultsPanel(
            uiState = SearchUiState(
                rows = listOf(
                    row("München", "Marienplatz 1, 80331 München", null, recent = true),
                    row("Berlin Hauptbahnhof", "Europaplatz 1, 10557 Berlin", null, recent = true),
                ),
            ),
            onPick = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SearchResultsHits() {
    Framed {
        SearchResultsPanel(
            uiState = SearchUiState(
                query = "Münch",
                rows = listOf(
                    row("München", "Bayern, Deutschland", 612.3, recent = false),
                    row("Münchberg", "Bayern, Deutschland", 487.0, recent = false),
                    row("Münchhausen", "Hessen, Deutschland", 0.4, recent = false),
                ),
            ),
            onPick = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SearchResultsFailed() {
    Framed {
        SearchResultsPanel(uiState = SearchUiState(query = "München", failed = true), onPick = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SearchResultsEmpty() {
    Framed {
        SearchResultsPanel(uiState = SearchUiState(query = "Xyzzyplonk"), onPick = {})
    }
}
