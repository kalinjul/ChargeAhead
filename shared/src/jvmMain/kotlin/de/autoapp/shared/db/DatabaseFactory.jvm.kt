package de.autoapp.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * For tests and local desktop development only. In-memory by default — the
 * JVM target is never shipped and shouldn't leave files behind.
 */
actual class DatabaseDriverFactory(private val url: String = JdbcSqliteDriver.IN_MEMORY) {
    actual fun create(): SqlDriver =
        JdbcSqliteDriver(url).also { ChargeSiteDatabase.Schema.create(it) }
}
