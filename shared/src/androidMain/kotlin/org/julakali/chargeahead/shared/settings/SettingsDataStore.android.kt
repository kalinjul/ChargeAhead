package org.julakali.chargeahead.shared.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

/**
 * The settings in the app's files directory. Takes over the old
 * SharedPreferences on first start and deletes them.
 */
fun createSettingsDataStore(context: Context, scope: CoroutineScope): DataStore<Preferences> {
    val app = context.applicationContext
    return createSettingsDataStore(
        path = app.filesDir.resolve("datastore/$SETTINGS_DATASTORE_FILE").absolutePath,
        scope = scope,
        migrations = listOf(SharedPreferencesMigration(app, LEGACY_SHARED_PREFERENCES)),
    )
}

/** The trip state in the app's files directory, apart from the settings. */
fun createTripDataStore(context: Context, scope: CoroutineScope): DataStore<Preferences> =
    createSettingsDataStore(
        path = context.applicationContext.filesDir.resolve("datastore/$TRIP_DATASTORE_FILE").absolutePath,
        scope = scope,
    )

private const val LEGACY_SHARED_PREFERENCES = "de.autoapp.settings"
