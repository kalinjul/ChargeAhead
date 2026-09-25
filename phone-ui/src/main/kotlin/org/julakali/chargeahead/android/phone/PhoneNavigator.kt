package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * The one owner of the back stack. Pages and sheets are both destinations on
 * it, so "where is the app" has a single answer, and [back] is the only way
 * back — whether that undoes a step on the root screen or pops a destination.
 */
@Stable
class PhoneNavigator(val backStack: NavBackStack<NavKey>) {

    private var screenStep: (() -> Unit)? by mutableStateOf(null)

    /** Nothing but the map: no page, no sheet. */
    val atRoot: Boolean get() = backStack.size == 1

    /**
     * Whether back is the navigator's to take. With a destination on top it is
     * the `NavDisplay`'s, which needs it to animate its own pop.
     */
    val ownsBack: Boolean get() = atRoot && screenStep != null

    fun open(destination: PhoneDestination) {
        // A second tap before the destination is drawn must not stack it twice.
        if (backStack.lastOrNull() != destination) backStack.add(destination)
    }

    /** Drawer targets never stack on each other. */
    fun openFromRoot(destination: PhoneDestination) {
        popToRoot()
        if (destination != Home) backStack.add(destination)
    }

    /** One step back: on the root screen its own, otherwise the destination on top. */
    fun back() {
        if (atRoot) screenStep?.invoke() else backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Registers what back undoes on the root screen before it reaches the back
     * stack, `null` while there is nothing left. The screen names the step,
     * because only it knows its own state; the navigator decides when to run it.
     */
    @Composable
    fun ScreenStep(step: (() -> Unit)?) {
        DisposableEffect(step) {
            screenStep = step
            onDispose { screenStep = null }
        }
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
