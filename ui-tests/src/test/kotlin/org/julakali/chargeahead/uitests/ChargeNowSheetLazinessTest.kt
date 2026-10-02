package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.ChargeNowSheetContent
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.components.AppSheet
import org.julakali.chargeahead.shared.domain.ChargeNowCandidate
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.ui.ChargeNowUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class ChargeNowSheetLazinessTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun candidate(i: Int) = ChargeNowCandidate(
        site = Fixtures.site("s$i", "Ionity", LatLon(48.0 + i * 0.001, 11.0), "Ort $i"),
        distanceKm = i * 0.1,
        maxPowerKw = 300.0,
    )

    private fun result() = ChargeNowResult(
        candidates = (1..3).map(::candidate),
        relaxed = emptyList(),
        more = (4..308).map(::candidate),
    )

    private fun composedCards() = compose.onAllNodesWithContentDescription(
        compose.string(R.string.cn_navigate, ""),
        substring = true,
    ).fetchSemanticsNodes().size

    @Test
    fun `inside the modal sheet too`() {
        compose.setThemedContent {
            AppSheet(onDismissRequest = {}) {
                ChargeNowSheetContent(uiState = ChargeNowUiState.Ready(result()), onNavigate = {})
            }
        }
        compose.waitForIdle()
        val composed = composedCards()
        assertTrue("composed $composed cards in the modal sheet", composed < 30)
    }

    /** 305 "more" rows: a sheet half a screen tall must compose a screenful, not all of them. */
    @Test
    fun `a long tail composes only what fits`() {
        val result = ChargeNowResult(
            candidates = (1..3).map(::candidate),
            relaxed = emptyList(),
            more = (4..308).map(::candidate),
        )
        compose.setThemedContent {
            Box(Modifier.height(600.dp)) {
                ChargeNowSheetContent(uiState = ChargeNowUiState.Ready(result), onNavigate = {})
            }
        }
        compose.waitForIdle()

        val composed = compose.onAllNodesWithContentDescription(
            compose.string(R.string.cn_navigate, ""),
            substring = true,
        ).fetchSemanticsNodes().size
        assertTrue("composed $composed cards for a 600dp sheet", composed < 30)
    }
}
