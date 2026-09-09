package de.autoapp.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class DatabaseFactory {
    actual fun builder(): RoomDatabase.Builder<ChargeSiteDatabase> {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as String
        return Room.databaseBuilder<ChargeSiteDatabase>(name = "$documents/$CHARGE_SITE_DATABASE_NAME")
    }
}
