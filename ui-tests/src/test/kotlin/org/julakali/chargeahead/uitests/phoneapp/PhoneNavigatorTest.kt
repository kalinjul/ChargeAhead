package org.julakali.chargeahead.uitests.phoneapp

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.julakali.chargeahead.android.phone.CarData
import org.julakali.chargeahead.android.phone.ChargeNow
import org.julakali.chargeahead.android.phone.Garage
import org.julakali.chargeahead.android.phone.Home
import org.julakali.chargeahead.android.phone.PhoneNavigator
import org.julakali.chargeahead.android.phone.VehicleEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNavigatorTest {

    private val backStack = NavBackStack<NavKey>(Home)
    private val navigator = PhoneNavigator(backStack)

    @Test
    fun `pages stack and back peels them one at a time`() {
        navigator.open(Garage)
        navigator.open(VehicleEdit)
        assertFalse(navigator.atRoot)

        navigator.back()
        assertEquals(listOf(Home, Garage), backStack.toList())

        navigator.back()
        assertTrue(navigator.atRoot)
    }

    @Test
    fun `back on the map does nothing`() {
        navigator.back()

        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `a drawer target replaces whatever was open instead of stacking on it`() {
        navigator.open(Garage)
        navigator.open(VehicleEdit)

        navigator.openFromRoot(CarData)

        assertEquals(listOf(Home, CarData), backStack.toList())
    }

    @Test
    fun `opening Home from the drawer just goes back to the map`() {
        navigator.open(Garage)

        navigator.openFromRoot(Home)

        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `a second tap on the same target does not stack it twice`() {
        navigator.open(ChargeNow)
        navigator.open(ChargeNow)

        assertEquals(listOf(Home, ChargeNow), backStack.toList())
    }
}
