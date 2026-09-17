package org.julakali.chargeahead.android.car

import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import androidx.car.app.Session

/**
 * Entry point for Android Auto. The host (Google Maps / Android Auto app)
 * binds to this service via the intent filter in the manifest.
 */
class ChargeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // TODO replace with a real HostValidator before release: this lets any app bind.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ChargeSession()
    }
}
