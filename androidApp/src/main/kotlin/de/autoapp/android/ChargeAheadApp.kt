package de.autoapp.android

import android.app.Application
import de.autoapp.shared.ui.sharedUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ChargeAheadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ChargeAheadApp)
            modules(appModule, sharedUiModule())
        }
    }
}
