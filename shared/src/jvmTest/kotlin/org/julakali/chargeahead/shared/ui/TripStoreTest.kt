package org.julakali.chargeahead.shared.ui

import org.julakali.chargeahead.shared.domain.TripStore
import kotlin.test.Test
import kotlin.test.assertNull

class TripStoreTest {
    @Test
    fun `clearing drops the stored plan`() {
        val store = TripStore()
        store.clear()
        assertNull(store.plan.value)
    }
}
