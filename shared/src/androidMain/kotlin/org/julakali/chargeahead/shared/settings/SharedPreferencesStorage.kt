package org.julakali.chargeahead.shared.settings

import android.content.Context

/**
 * Storage backed by SharedPreferences.
 *
 * `commit()` instead of `apply()`, since the process may be killed right after.
 */
class SharedPreferencesStorage(context: Context) : KeyValueStorage {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun getStringOrNull(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.commit()
    }

    private companion object {
        const val NAME = "de.autoapp.settings"
    }
}
