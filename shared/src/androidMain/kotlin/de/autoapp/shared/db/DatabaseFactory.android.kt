package de.autoapp.shared.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(
        schema = ChargeSiteDatabase.Schema,
        context = context.applicationContext,
        name = CHARGE_SITE_DATABASE_NAME,
    )
}
