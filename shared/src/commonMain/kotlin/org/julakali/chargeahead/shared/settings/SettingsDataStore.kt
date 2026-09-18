package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

/** Settings file name, the same on every platform. */
const val SETTINGS_DATASTORE_FILE = "settings.preferences_pb"

/**
 * The settings file at [path]. At most one per file and process: DataStore
 * refuses a second active instance on the same file.
 */
fun createSettingsDataStore(
    path: String,
    migrations: List<DataMigration<Preferences>> = emptyList(),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    // An unreadable file means lost settings, like a corrupt profile does.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    migrations = migrations,
    scope = scope,
    produceFile = { path.toPath() },
)

/**
 * Moves the string [keys] from a platform key-value store (NSUserDefaults)
 * into DataStore, once: afterwards they are gone from the old store, so
 * [shouldMigrate] is false from then on.
 */
class KeyValueMigration(
    private val keys: Set<String>,
    private val read: (key: String) -> String?,
    private val remove: (key: String) -> Unit,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = keys.any { read(it) != null }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migrated = currentData.toMutablePreferences()
        for (key in keys) {
            val preferencesKey = stringPreferencesKey(key)
            // A value already in DataStore is newer than the old store's.
            if (preferencesKey in migrated) continue
            read(key)?.let { migrated[preferencesKey] = it }
        }
        return migrated.toPreferences()
    }

    override suspend fun cleanUp() {
        keys.forEach(remove)
    }
}
