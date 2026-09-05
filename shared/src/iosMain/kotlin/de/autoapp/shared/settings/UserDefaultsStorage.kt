package de.autoapp.shared.settings

import platform.Foundation.NSUserDefaults

/**
 * Storage backed by NSUserDefaults. Counterpart to SharedPreferencesStorage
 * on Android; the keys are the same so both platforms describe the same profile.
 */
class UserDefaultsStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : KeyValueStorage {

    override fun getStringOrNull(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }
}
