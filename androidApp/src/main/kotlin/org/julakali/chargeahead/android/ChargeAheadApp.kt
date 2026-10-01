package org.julakali.chargeahead.android

import android.app.Application
import org.julakali.chargeahead.shared.chargeStopsModule
import org.julakali.chargeahead.shared.ui.sharedUiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.julakali.chargeahead.shared.ui.car.carUiModule
import org.julakali.chargeahead.shared.domain.usecases.StartAppInteractor
import org.julakali.chargeahead.shared.domain.invoke
import org.julakali.chargeahead.shared.AppScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class ChargeAheadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PhoneUiVisibility.register(this)
        val koin = startKoin {
            androidContext(this@ChargeAheadApp)
            modules(appModule, chargeStopsModule(), sharedUiModule(), carUiModule())
        }.koin
        koin.get<CoroutineScope>(AppScope).launch { koin.get<StartAppInteractor>()() }
    }
}
