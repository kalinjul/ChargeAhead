package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.components.LazyFlowRow
import org.julakali.chargeahead.android.phone.components.LazyFlowRowState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LazyFlowRowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `every item stays visible after the list reorders without changing size`() {
        // Alternating narrow and wide items, so reordering actually moves where lines
        // break — mirrors NetworkSettingsScreen's pills, whose widths vary by name.
        val widths = mapOf("a" to 40.dp, "b" to 40.dp, "c" to 40.dp, "d" to 40.dp, "e" to 40.dp, "f" to 200.dp)
        val order = mutableStateOf(listOf("a", "b", "c", "d", "e", "f"))

        compose.setThemedContent {
            LazyFlowRow(
                modifier = Modifier.width(130.dp).height(300.dp),
                horizontalSpacing = 4.dp,
                verticalSpacing = 4.dp,
            ) {
                items(order.value, key = { it }) { label ->
                    Text(label, modifier = Modifier.width(widths.getValue(label)).height(30.dp))
                }
            }
        }
        order.value.forEach { compose.onNodeWithText(it).assertIsDisplayed() }

        // Mirrors NetworksViewModel re-freezing display order after the search field
        // clears: same width, same item count, different order — the scenario that left
        // LazyFlowRow's line-break cache pointing at the wrong item indices.
        compose.runOnIdle { order.value = order.value.reversed() }
        compose.waitForIdle()

        order.value.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun `a width change keeps the same item in view instead of resetting to the top`() {
        val width = mutableStateOf(160.dp)
        val state = LazyFlowRowState()

        compose.setThemedContent {
            LazyFlowRow(
                modifier = Modifier.width(width.value).height(70.dp),
                state = state,
                horizontalSpacing = 4.dp,
                verticalSpacing = 4.dp,
            ) {
                items((1..8).map { it.toString() }, key = { it }) { label ->
                    Text(label, modifier = Modifier.width(60.dp).height(30.dp))
                }
            }
        }

        // Scroll to the end so the anchor is well past the first line.
        compose.runOnIdle { state.dispatchRawDelta(-100_000f) }
        compose.waitForIdle()
        assertTrue("expected the list to have scrolled past the first line", state.firstVisibleLine > 0)

        // A width-only change (rotation, split-screen resize) invalidates the cached line
        // breaks, but should keep roughly the same item in view rather than silently
        // jumping back to the very first line.
        compose.runOnIdle { width.value = 100.dp }
        compose.waitForIdle()

        assertTrue(
            "a width-only change should keep the scroll position, not reset it to the top",
            state.firstVisibleLine > 0,
        )
    }

    @Test
    fun `an item redraws when its content changes but its key does not`() {
        // The network picker's case: tapping a pill changes only how that pill draws,
        // while the list keeps the same items in the same order, so no key changes.
        // The labels arrive as a parameter, as NetworkSettingsScreen takes its ui state,
        // so only the caller recomposes — the item itself reads no state of its own.
        val selected = mutableStateOf(setOf<String>())

        compose.setThemedContent {
            Labels(labels = listOf("a", "b").map { it to (it in selected.value) })
        }
        compose.onNodeWithText("a off").assertIsDisplayed()

        compose.runOnIdle { selected.value = setOf("a") }
        compose.waitForIdle()

        compose.onNodeWithText("a on").assertIsDisplayed()
        compose.onNodeWithText("b off").assertIsDisplayed()
    }

    @Composable
    private fun Labels(labels: List<Pair<String, Boolean>>) {
        LazyFlowRow(
            modifier = Modifier.width(300.dp).height(300.dp),
            horizontalSpacing = 4.dp,
            verticalSpacing = 4.dp,
        ) {
            items(labels, key = { it.first }) { (label, on) ->
                Text(
                    text = if (on) "$label on" else "$label off",
                    modifier = Modifier.width(100.dp).height(30.dp),
                )
            }
        }
    }
}
