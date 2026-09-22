package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.julakali.chargeahead.android.phone.HomeSearchBar
import org.julakali.chargeahead.android.phone.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSearchBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bar(
        query: String = "",
        searching: Boolean = false,
        clearable: Boolean = false,
        takeFocus: Boolean = false,
        onQueryChange: (String) -> Unit = {},
        onClear: () -> Unit = {},
    ) = compose.setThemedContent {
        HomeSearchBar(
            query = query,
            searching = searching,
            onFocused = {},
            onQueryChange = onQueryChange,
            onClear = onClear,
            focusRequester = remember { FocusRequester() },
            clearable = clearable,
            takeFocus = takeFocus,
        )
    }

    private val clear get() = compose.onNodeWithContentDescription(compose.string(R.string.home_search_clear))
    private val spinner get() = compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))

    @Test
    fun `placeholder is shown while nothing is typed`() {
        bar()
        compose.onNodeWithText(compose.string(R.string.home_search_hint)).assertIsDisplayed()
    }

    @Test
    fun `typing reports the new query`() {
        val typed = mutableListOf<String>()
        bar(onQueryChange = { typed += it })
        compose.onNode(hasSetTextAction()).performTextInput("Ham")
        assertEquals(listOf("Ham"), typed)
    }

    @Test
    fun `the x shows with a query and clears`() {
        var cleared = false
        bar(query = "Hamburg", onClear = { cleared = true })
        clear.assertIsDisplayed().performClick()
        assertTrue(cleared)
    }

    @Test
    fun `no x without a query`() {
        bar()
        clear.assertDoesNotExist()
    }

    @Test
    fun `clearable keeps the x even without a query`() {
        bar(clearable = true)
        clear.assertIsDisplayed()
    }

    @Test
    fun `a spinner replaces the x while searching`() {
        bar(query = "Hamburg", searching = true)
        spinner.assertIsDisplayed()
        clear.assertDoesNotExist()
    }

    @Test
    fun `takeFocus puts the cursor into the field`() {
        bar(takeFocus = true)
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }
}
