package de.autoapp.shared.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the migration from schema version 1 to 2 (postal address per site).
 *
 * Replaces SQLDelight's `verifyMigrations`: that would need a schema dump of
 * every prior version as a `.db` file in the source set. Here the old table
 * is created, filled, and migrated by hand instead — which additionally
 * checks what the verification does *not* check, namely that the data
 * survives.
 *
 * Why migrate at all when the cache is just a cache: throwing it away on
 * update would mean the first drive after every app update has no list
 * without network. That's exactly what it exists to prevent.
 */
class ChargeSiteMigrationTest {

    /** The schema as it looked before the postal address was added. */
    private val schemaVersion1 = listOf(
        """
        CREATE TABLE chargeSite (
            id          TEXT    NOT NULL PRIMARY KEY,
            sourceId    TEXT    NOT NULL,
            name        TEXT    NOT NULL,
            operator    TEXT,
            lat         REAL    NOT NULL,
            lon         REAL    NOT NULL,
            connectors  TEXT    NOT NULL
        )
        """.trimIndent(),
        "CREATE INDEX chargeSite_position ON chargeSite(lat, lon)",
        """
        CREATE TABLE tileCoverage (
            sourceId        TEXT    NOT NULL,
            tileLat         INTEGER NOT NULL,
            tileLon         INTEGER NOT NULL,
            fetchedAtMillis INTEGER NOT NULL,
            PRIMARY KEY (sourceId, tileLat, tileLon)
        )
        """.trimIndent(),
    )

    @Test
    fun theMigrationRunsAndPreservesTheStore() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        schemaVersion1.forEach { driver.execute(null, it, 0).value }
        driver.execute(
            null,
            "INSERT INTO chargeSite(id, sourceId, name, operator, lat, lon, connectors) " +
                "VALUES ('ocm:1', 'ocm', 'Alter Ladepark', 'AltNetz', 48.9, 11.4, 'CCS2:150.0:4')",
            0,
        ).value
        driver.execute(null, "INSERT INTO tileCoverage VALUES ('ocm', 489, 114, 12345)", 0).value

        ChargeSiteDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2).value

        val database = ChargeSiteDatabase(driver)
        val loaded = database.chargeSitesQueries
            .sitesInBox(south = 48.0, north = 49.0, west = 11.0, east = 12.0)
            .executeAsList()

        assertEquals(1, loaded.size, "The cache did not survive the migration")
        assertEquals("Alter Ladepark", loaded.single().name)
        // The new columns are there and empty — not populated, but readable.
        assertEquals(null, loaded.single().street)
        assertEquals(null, loaded.single().town)

        // Coverage is preserved too: otherwise everything would be re-fetched
        // after every update, and the preserved cache would be useless.
        val tiles = database.chargeSitesQueries.freshTileCount(
            sourceId = "ocm",
            minTileLat = 480, maxTileLat = 490,
            minTileLon = 110, maxTileLon = 120,
            notOlderThanMillis = 0,
        ).executeAsOne()
        assertEquals(1L, tiles)
    }

    @Test
    fun newColumnsAreWritable() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ChargeSiteDatabase.Schema.create(driver).value
        val database = ChargeSiteDatabase(driver)

        database.chargeSitesQueries.upsertSite(
            id = "bnetza:1",
            sourceId = "bnetza",
            name = "Neuer Ladepark",
            operator_ = "Amtlich GmbH",
            lat = 48.9,
            lon = 11.4,
            connectors = "CCS2:300.0:6",
            street = "Hauptstr. 5",
            postalCode = "85095",
            town = "Denkendorf",
        )

        val loaded = database.chargeSitesQueries
            .sitesInBox(south = 48.0, north = 49.0, west = 11.0, east = 12.0)
            .executeAsOne()

        assertEquals("Hauptstr. 5", loaded.street)
        assertEquals("85095", loaded.postalCode)
        assertEquals("Denkendorf", loaded.town)
    }

    @Test
    fun theSchemaKnowsTheSecondVersion() {
        assertTrue(ChargeSiteDatabase.Schema.version >= 2, "Schema version was not bumped")
    }
}
