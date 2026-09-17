package org.julakali.chargeahead.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase

/** In-memory, for tests. */
actual class DatabaseFactory {
    actual fun builder(): RoomDatabase.Builder<ChargeSiteDatabase> =
        Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
}
