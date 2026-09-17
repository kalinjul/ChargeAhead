package org.julakali.chargeahead.android.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The car session's single place for runtime permissions: asks for them and
 * publishes whether each one is granted, since Android has no grant callback.
 *
 * One instance per [ChargeSession].
 */
class CarPermissions(private val carContext: CarContext) {

    private val states = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /** Whether [permission] is granted — updated after every request and on [refresh]. */
    fun granted(permission: String): StateFlow<Boolean> = synchronized(states) {
        states.getOrPut(permission) { MutableStateFlow(isGranted(permission)) }
    }.asStateFlow()

    /** [onFinished] runs once the driver has answered, after [granted] reflects the answer. */
    fun request(permissions: List<String>, onFinished: () -> Unit = {}) {
        carContext.requestPermissions(permissions) { _, _ ->
            refresh()
            onFinished()
        }
    }

    /** Re-reads every observed permission, for changes made outside a request. */
    fun refresh() {
        synchronized(states) {
            states.forEach { (permission, state) -> state.value = isGranted(permission) }
        }
    }

    private fun isGranted(permission: String): Boolean =
        carContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
