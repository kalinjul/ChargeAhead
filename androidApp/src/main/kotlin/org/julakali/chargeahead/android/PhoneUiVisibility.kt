package org.julakali.chargeahead.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether one of the app's activities is visible on the phone. Android only
 * lets the app open another app while it is visible there.
 */
object PhoneUiVisibility : Application.ActivityLifecycleCallbacks {

    private var started = 0
    private val mutableVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = mutableVisible.asStateFlow()

    fun register(app: Application) = app.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        started++
        mutableVisible.value = true
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        mutableVisible.value = started > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
