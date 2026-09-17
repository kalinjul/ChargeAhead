package org.julakali.chargeahead.shared.settings

import platform.Foundation.NSUserDefaults

/** Storage backed by NSUserDefaults. */
class UserDefaultsStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStorage {

    override fun getStringOrNull(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}
