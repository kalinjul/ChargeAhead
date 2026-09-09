package de.autoapp.shared.data

import de.autoapp.shared.db.ChargeSiteDatabase
import de.autoapp.shared.db.ChargeSiteEntity
import de.autoapp.shared.db.DatabaseFactory
import de.autoapp.shared.db.createChargeSiteDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorCatalogTest {

    private fun database(): ChargeSiteDatabase = createChargeSiteDatabase(DatabaseFactory())

    private suspend fun ChargeSiteDatabase.insert(id: String, operator: String?) {
        chargeSites().upsertSites(
            listOf(ChargeSiteEntity(id, "test", "Ladepark $id", operator, null, 48.9, 11.4, "CCS2:150.0:4", null, null, null, 0L)),
        )
    }

    @Test
    fun `merges spelling variants and sums their sites`() = runBlocking {
        val db = database()
        db.insert("a", "IONITY GmbH")
        db.insert("b", "Ionity")
        db.insert("c", "Fastned")
        db.insert("d", null)

        val options = OperatorCatalog(db).options()

        assertEquals(listOf("Fastned", "Ionity"), options.map { it.displayName })
        assertEquals(2, options.single { it.displayName == "Ionity" }.siteCount)
    }

    @Test
    fun `an empty store seeds nothing`() = runBlocking {
        assertTrue(OperatorCatalog(database()).options().isEmpty())
    }
}
