package org.julakali.chargeahead.shared.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences

/**
 * The settings in the app's files directory. Takes over the old
 * SharedPreferences on first start and deletes them.
 */
fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    val app = context.applicationContext
    return createSettingsDataStore(
        path = app.filesDir.resolve("datastore/$SETTINGS_DATASTORE_FILE").absolutePath,
        migrations = listOf(SharedPreferencesMigration(app, LEGACY_SHARED_PREFERENCES)),
    )
}

private const val LEGACY_SHARED_PREFERENCES = "de.autoapp.settings"
