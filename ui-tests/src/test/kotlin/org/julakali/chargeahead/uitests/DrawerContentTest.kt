package org.julakali.chargeahead.uitests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.julakali.chargeahead.android.phone.DrawerContent
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.shared.ui.DrawerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrawerContentTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var selected: ChargeMode? = null

    private fun drawer(mode: ChargeMode = ChargeMode.NORMAL) = compose.setThemedContent {
        DrawerContent(
            uiState = DrawerUiState(mode = mode),
            onOpen = {},
            onFilters = {},
            onModeSelected = { selected = it },
        )
    }

    private fun ac() = compose.onNodeWithText(compose.string(R.string.drawer_mode_ac))
    private fun browse() = compose.onNodeWithText(compose.string(R.string.mode_browse))

    @Test
    fun `both modes are on offer and neither is on`() {
        drawer()
        ac().assertIsEnabled().assertIsNotSelected()
        browse().assertIsEnabled().assertIsNotSelected()
    }

    @Test
    fun `the mode that is on is marked, the other stays available`() {
        drawer(ChargeMode.AC)
        ac().assertIsSelected()
        browse().assertIsEnabled().assertIsNotSelected()
    }

    /** Nothing is ever dead: tapping the other mode hands over to it. */
    @Test
    fun `tapping the other mode hands over to it`() {
        drawer(ChargeMode.AC)
        browse().performClick()
        assertEquals(ChargeMode.BROWSE, selected)
    }

    @Test
    fun `tapping a mode turns it on`() {
        drawer()
        ac().performClick()
        assertEquals(ChargeMode.AC, selected)
    }

    /** The same button switches it off again; there is no other way back. */
    @Test
    fun `tapping the mode that is on turns it off`() {
        drawer(ChargeMode.BROWSE)
        browse().performClick()
        assertEquals(ChargeMode.NORMAL, selected)
    }

    @Test
    fun `the info icon explains both modes`() {
        drawer()
        compose.onNodeWithContentDescription(compose.string(R.string.drawer_mode_info)).performClick()
        compose.onNodeWithText(compose.string(R.string.drawer_mode_tooltip), substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping the info icon again closes the explanation`() {
        drawer()
        val info = compose.onNodeWithContentDescription(compose.string(R.string.drawer_mode_info))
        info.performClick()
        info.performClick()
        compose.onNodeWithText(compose.string(R.string.drawer_mode_tooltip), substring = true).assertDoesNotExist()
    }

    @Test
    fun `the explanation opens again as often as asked`() {
        drawer()
        val info = compose.onNodeWithContentDescription(compose.string(R.string.drawer_mode_info))
        repeat(3) { info.performClick() }
        compose.onNodeWithText(compose.string(R.string.drawer_mode_tooltip), substring = true).assertIsDisplayed()
    }
}
