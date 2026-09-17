package org.julakali.chargeahead.android

import android.app.Application
import org.julakali.chargeahead.shared.chargeStopsModule
import org.julakali.chargeahead.shared.ui.sharedUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ChargeAheadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneUiVisibility.register(this)
        startKoin {
            androidContext(this@ChargeAheadApp)
            modules(appModule, chargeStopsModule(), sharedUiModule())
        }
    }
}
