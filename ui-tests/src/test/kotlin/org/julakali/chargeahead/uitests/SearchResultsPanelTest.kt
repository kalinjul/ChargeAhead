package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.SearchResultsPanel
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchResultsPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun panel(state: SearchUiState, onPick: (SearchRow) -> Unit = {}) =
        compose.setThemedContent { SearchResultsPanel(state, onPick) }

    @Test
    fun `recents show title, detail and distance`() {
        panel(SearchUiState(rows = listOf(Fixtures.searchRow("München", "Marienplatz 1", distanceKm = 612.0))))
        compose.onNodeWithText("München").assertIsDisplayed()
        compose.onNodeWithText("Marienplatz 1").assertIsDisplayed()
        compose.onNodeWithText(compose.string(R.string.plan_result_distance, "612")).assertIsDisplayed()
    }

    @Test
    fun `tapping a row picks it`() {
        val rows = listOf(Fixtures.searchRow("München"), Fixtures.searchRow("Berlin"))
        var picked: SearchRow? = null
        panel(SearchUiState(rows = rows), onPick = { picked = it })
        compose.onNodeWithText("Berlin").performClick()
        assertEquals(rows[1], picked)
    }

    @Test
    fun `a failed search says so`() {
        panel(SearchUiState(query = "München", failed = true))
        compose.onNodeWithText(compose.string(R.string.plan_search_failed)).assertIsDisplayed()
    }

    @Test
    fun `no hits for a long query reads as no results`() {
        panel(SearchUiState(query = "Nirgendwo", rows = emptyList(), searching = false))
        compose.onNodeWithText(compose.string(R.string.plan_no_results)).assertIsDisplayed()
    }

    @Test
    fun `a short query with no rows shows nothing`() {
        panel(SearchUiState(query = "M", rows = emptyList()))
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
    }
}
