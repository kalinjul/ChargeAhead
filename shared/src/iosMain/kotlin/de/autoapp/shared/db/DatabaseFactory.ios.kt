package de.autoapp.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(ChargeSiteDatabase.Schema, CHARGE_SITE_DATABASE_NAME)
}
