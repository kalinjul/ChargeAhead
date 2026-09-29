package org.julakali.chargeahead.android.car

import androidx.car.app.Screen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import org.julakali.chargeahead.shared.ui.ViewModelHost

/** A ViewModel that lives as long as this screen; call it once, from the constructor. */
internal inline fun <reified VM : ViewModel> Screen.screenViewModel(noinline create: () -> VM): VM {
    val host = ViewModelHost()
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            host.clear()
        }
    })
    return host.get(create)
}
