package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * The one owner of the back stack. Pages and sheets are both destinations on
 * it, so "where is the app" has a single answer.
 */
@Stable
class PhoneNavigator(val backStack: NavBackStack<NavKey>) {

    /** Nothing but the map: no page, no sheet. */
    val atRoot: Boolean get() = backStack.size == 1

    fun open(destination: PhoneDestination) {
        // A second tap before the destination is drawn must not stack it twice.
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }

    /** Drawer targets never stack on each other. */
    fun openFromRoot(destination: PhoneDestination) {
        popToRoot()
        if (destination != Home) backStack.add(destination)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    private fun popToRoot() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
}

@Composable
fun rememberPhoneNavigator(): PhoneNavigator {
    val backStack = rememberNavBackStack(Home)
    return remember(backStack) { PhoneNavigator(backStack) }
}
