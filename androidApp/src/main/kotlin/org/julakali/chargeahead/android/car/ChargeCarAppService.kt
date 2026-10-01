package org.julakali.chargeahead.android.car

import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import androidx.car.app.Session

/**
 * Entry point for Android Auto. The host (Google Maps / Android Auto app)
 * binds to this service via the intent filter in the manifest.
 */
class ChargeCarAppService : CarAppService() {

    // Google's own hosts: Android Auto, Android Automotive and the desktop head unit. Anyone else stays out.
    override fun createHostValidator(): HostValidator =
        HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session {
        return ChargeSession()
    }
}
