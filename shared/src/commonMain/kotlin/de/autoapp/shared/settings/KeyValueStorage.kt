package de.autoapp.shared.settings

/**
 * Lowest common denominator of the platform stores: SharedPreferences on
 * Android, NSUserDefaults on iOS.
 *
 * Deliberately strings only. This project's settings are a handful of
 * numbers and a connector list — pulling in a database or a serialization
 * library for that would be effort without payoff. What goes in here is
 * formatted by [PersistentSettingsStore].
 */
interface KeyValueStorage {
    fun getStringOrNull(key: String): String?

    /** `null` deletes the entry. */
    fun putString(key: String, value: String?)
}

/** Storage without storage — for tests and for the JVM target, which is never shipped. */
class InMemoryKeyValueStorage(
    initial: Map<String, String> = emptyMap(),
) : KeyValueStorage {

    private val values: MutableMap<String, String> = initial.toMutableMap()

    override fun getStringOrNull(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
