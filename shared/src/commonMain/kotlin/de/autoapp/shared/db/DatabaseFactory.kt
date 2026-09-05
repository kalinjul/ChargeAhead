package de.autoapp.shared.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Like Ktor, SQLDelight has no cross-platform driver — each target brings
 * its own. Android needs a `Context` for that, so the factory takes a
 * platform-specific dependency.
 */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

fun createChargeSiteDatabase(factory: DatabaseDriverFactory): ChargeSiteDatabase =
    ChargeSiteDatabase(factory.create())

/** Database file name. The same on every platform. */
const val CHARGE_SITE_DATABASE_NAME = "charge_sites.db"
