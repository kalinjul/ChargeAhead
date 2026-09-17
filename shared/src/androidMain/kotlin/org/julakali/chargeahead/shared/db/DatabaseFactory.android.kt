package org.julakali.chargeahead.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase

// TODO no factory needed, use koin DBModule
actual class DatabaseFactory(private val context: Context) {
    actual fun builder(): RoomDatabase.Builder<ChargeSiteDatabase> {
        val app = context.applicationContext
        // Remove the old SQLDelight file, including journal sidecars.
        SQLiteDatabase.deleteDatabase(app.getDatabasePath("charge_sites.db"))
        return Room.databaseBuilder<ChargeSiteDatabase>(
            context = app,
            name = app.getDatabasePath(CHARGE_SITE_DATABASE_NAME).absolutePath,
        )
    }
}
