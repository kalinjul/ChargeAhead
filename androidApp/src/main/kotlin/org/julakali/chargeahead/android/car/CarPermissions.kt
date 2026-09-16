package org.julakali.chargeahead.android.car

import android.content.pm.PackageManager
import androidx.car.app.CarContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The car session's single place for runtime permissions: asks for them and
 * publishes whether each one is granted.
 *
 * Android has no callback for "a permission was just granted" — only the
 * `requestPermissions` caller learns about it. A one-off `checkSelfPermission`
 * therefore goes stale the moment the driver confirms the dialog on the phone:
 * the screen redraws, but a hardware listener that was skipped at session
 * start stays unregistered until the next connection. Observing [granted]
 * instead keeps screens and data sources in step with every request.
 *
 * One instance per [ChargeSession].
 */
class CarPermissions(private val carContext: CarContext) {

    private val states = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /** Whether [permission] is granted — updated after every request and on [refresh]. */
    fun granted(permission: String): StateFlow<Boolean> = synchronized(states) {
        states.getOrPut(permission) { MutableStateFlow(isGranted(permission)) }
    }.asStateFlow()

    /**
     * In projection the head unit can't show the dialog itself; the host
     * tells the driver to confirm it on the phone. [onFinished] runs once the
     * driver has answered, after [granted] reflects the answer.
     */
    fun request(permissions: List<String>, onFinished: () -> Unit = {}) {
        carContext.requestPermissions(permissions) { _, _ ->
            refresh()
            onFinished()
        }
    }

    /**
     * Re-reads every observed permission. Covers changes made outside a
     * request, e.g. in the phone's app settings while the session was in
     * the background.
     */
    fun refresh() {
        synchronized(states) {
            states.forEach { (permission, state) -> state.value = isGranted(permission) }
        }
    }

    private fun isGranted(permission: String): Boolean =
        carContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
