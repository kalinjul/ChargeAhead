package org.julakali.chargeahead.shared.settings

/**
 * Lowest common denominator of the platform stores: SharedPreferences on
 * Android, NSUserDefaults on iOS. Strings only.
 */
// TODO use androidx datastore instead (#82)
interface KeyValueStorage {
    fun getStringOrNull(key: String): String?

    /** `null` deletes the entry. */
    fun putString(key: String, value: String?)
}

/** For tests and the JVM target. */
class InMemoryKeyValueStorage(
    initial: Map<String, String> = emptyMap(),
) : KeyValueStorage {

    private val values: MutableMap<String, String> = initial.toMutableMap()

    override fun getStringOrNull(key: String): String? = values[key]

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
