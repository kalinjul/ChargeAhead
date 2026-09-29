package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

/**
 * The settings in the app's documents directory. Takes over the old
 * NSUserDefaults entries on first start and removes them there.
 */
fun createSettingsDataStore(
    defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
): DataStore<Preferences> =
    createSettingsDataStore(
        path = "${documentsDirectory()}/$SETTINGS_DATASTORE_FILE",
        migrations = listOf(
            KeyValueMigration(
                keys = SettingsKeys.ALL,
                read = defaults::stringForKey,
                remove = defaults::removeObjectForKey,
            ),
        ),
    )

/** The trip state in the app's documents directory, apart from the settings. */
fun createTripDataStore(): DataStore<Preferences> =
    createSettingsDataStore(path = "${documentsDirectory()}/$TRIP_DATASTORE_FILE")

private fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
