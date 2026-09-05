package de.autoapp.android.car

import androidx.car.app.CarAppService
import androidx.car.app.validation.HostValidator
import androidx.car.app.Session

/**
 * Entry point for Android Auto. The host (Google Maps / Android Auto app)
 * binds to this service via the intent filter in the manifest.
 */
class ChargeCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        // WARNING: ALLOW_ALL_HOSTS_VALIDATOR lets any app talk to this service —
        // deliberately so for the debug walking skeleton (M0), since signed host
        // certificates don't need to be checked yet. Before a release build, this
        // MUST be replaced with a real HostValidator (e.g. via allowed_hosts.xml or
        // a signature check against the known fingerprint of Google Maps / Android
        // Auto), otherwise any app can talk to this service and read its own
        // data/templates.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ChargeSession()
    }
}
