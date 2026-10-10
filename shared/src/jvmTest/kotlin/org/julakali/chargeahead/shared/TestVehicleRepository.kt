package org.julakali.chargeahead.shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import org.julakali.chargeahead.shared.data.RoomVehicleRepository
import org.julakali.chargeahead.shared.db.ChargeSiteDatabase
import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore

fun testDatabase(): ChargeSiteDatabase = createChargeSiteDatabase(DatabaseFactory(), Dispatchers.IO)

/** The real repository on in-memory storage. A second one over the same [settings] and [database] is an app restart. */
fun testVehicleRepository(
    settings: DataStore<Preferences> = InMemoryPreferencesDataStore(),
    database: ChargeSiteDatabase = testDatabase(),
): RoomVehicleRepository = RoomVehicleRepository(settings, database)
