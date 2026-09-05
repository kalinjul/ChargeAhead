package de.autoapp.shared.settings

import android.content.Context

/**
 * Storage backed by SharedPreferences.
 *
 * Deliberately not DataStore: that would add another dependency for a
 * handful of strings that are written rarely and read once at startup.
 * Uses `commit()` instead of `apply()` so a value the driver just set is
 * guaranteed to be on disk even if the process is killed right after —
 * which happens in Android Auto when the connection is disconnected.
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
