package de.autoapp.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(private val context: Context) {
    actual fun builder(): RoomDatabase.Builder<ChargeSiteDatabase> {
        val app = context.applicationContext
        // The SQLDelight-era file — Room can't adopt it, so don't leave a
        // multi-MB corpse in every updated install. deleteDatabase, not
        // delete(): the journal sidecars have to go too.
        SQLiteDatabase.deleteDatabase(app.getDatabasePath("charge_sites.db"))
        return Room.databaseBuilder<ChargeSiteDatabase>(
            context = app,
            name = app.getDatabasePath(CHARGE_SITE_DATABASE_NAME).absolutePath,
        )
    }
}
