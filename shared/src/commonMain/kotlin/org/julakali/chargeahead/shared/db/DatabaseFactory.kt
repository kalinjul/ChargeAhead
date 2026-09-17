package org.julakali.chargeahead.shared.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Room builds per platform: Android needs a `Context` for the database path. */
expect class DatabaseFactory {
    fun builder(): RoomDatabase.Builder<ChargeSiteDatabase>
}

fun createChargeSiteDatabase(factory: DatabaseFactory): ChargeSiteDatabase =
    factory.builder()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

/** Database file name, the same on every platform. */
const val CHARGE_SITE_DATABASE_NAME = "charge_sites.room.db"
