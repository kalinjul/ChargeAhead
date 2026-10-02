package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChargeModeTest {

    private val browsing = NetworkPreferences(onlyPreferred = false)
    private val filtering = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity"))

    @Test
    fun `nothing special on is the normal mode`() {
        assertEquals(ChargeMode.NORMAL, ChargeMode.of(ChargeFilters(), filtering))
    }

    @Test
    fun `ac mode and stoebermodus each have their own mode`() {
        assertEquals(ChargeMode.AC, ChargeMode.of(ChargeFilters(slowMode = true), filtering))
        assertEquals(ChargeMode.BROWSE, ChargeMode.of(ChargeFilters(), browsing))
    }

    /** Both on should not happen; if an old preference file says so, AC wins, it is the louder one. */
    @Test
    fun `both on reads as ac`() {
        assertEquals(ChargeMode.AC, ChargeMode.of(ChargeFilters(slowMode = true), browsing))
    }
}
