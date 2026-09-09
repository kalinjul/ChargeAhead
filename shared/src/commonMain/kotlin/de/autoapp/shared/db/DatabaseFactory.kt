package de.autoapp.shared.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Room builds per platform: Android needs a `Context` for the database
 * path, iOS and JVM don't. The bundled driver keeps the SQLite version
 * identical on every platform.
 */
expect class DatabaseFactory {
    fun builder(): RoomDatabase.Builder<ChargeSiteDatabase>
}

fun createChargeSiteDatabase(factory: DatabaseFactory): ChargeSiteDatabase =
    factory.builder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

/**
 * Database file name. The same on every platform; `.room.` in it because
 * Room cannot adopt the SQLDelight file this store replaces.
 */
const val CHARGE_SITE_DATABASE_NAME = "charge_sites.room.db"
