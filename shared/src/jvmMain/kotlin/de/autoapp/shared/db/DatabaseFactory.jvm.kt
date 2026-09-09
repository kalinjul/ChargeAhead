package de.autoapp.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * For tests and local desktop development only. In-memory — the JVM target
 * is never shipped and shouldn't leave files behind.
 */
actual class DatabaseFactory {
    actual fun builder(): RoomDatabase.Builder<ChargeSiteDatabase> =
        Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
}
