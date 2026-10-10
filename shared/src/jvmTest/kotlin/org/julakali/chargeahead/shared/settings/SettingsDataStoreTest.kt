package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.testDatabase
import org.julakali.chargeahead.shared.testVehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsDataStoreTest {

    private val directory = createTempDirectory("settings").toFile()
    private val file = File(directory, SETTINGS_DATASTORE_FILE)
    private val database = testDatabase()

    private val vehicle = VehicleProfile(
        displayName = "Testwagen",
        usableBatteryKwh = 77.0,
        consumptionKwhPer100Km = 18.0,
        acceptedConnectors = setOf(ConnectorType.CCS2),
    )

    @AfterTest
    fun deleteFiles() {
        directory.deleteRecursively()
    }

    // DataStore allows one active instance per file, so a restart closes
    // the first one before opening the next.
    private fun <T> withStore(
        migrations: List<KeyValueMigration> = emptyList(),
        block: suspend (VehicleRepository) -> T,
    ): T = runBlocking {
        val job = SupervisorJob()
        try {
            val dataStore = createSettingsDataStore(file.absolutePath, CoroutineScope(Dispatchers.IO + job), migrations)
            block(testVehicleRepository(dataStore, database))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun aSavedProfile_survivesARestart_onDisk() {
        withStore { it.setVehicle(vehicle) }

        assertEquals(vehicle, withStore { it.vehicle.first() })
    }

    @Test
    fun aCorruptFile_isTreatedAsNoSettings() {
        file.writeText("kein Protobuf")

        assertNull(withStore { it.vehicle.first() })
    }

    @Test
    fun theOldKeyValueEntries_areTakenOverOnce_andRemovedThere() {
        val old = mutableMapOf(
            "energy.arrivalSocPercent" to "25.0",
            "energy.manualSocPercent" to "64.0",
            "unrelated" to "stays",
        )
        val migration = KeyValueMigration(SettingsKeys.ALL, old::get, { old.remove(it) })

        val (migratedArrival, migratedSoc) = withStore(listOf(migration)) {
            it.arrivalSocPercent.first() to it.manualSocPercent.first()
        }

        assertEquals(25.0, migratedArrival)
        assertEquals(64.0, migratedSoc)
        assertEquals(mapOf("unrelated" to "stays"), old)
        // Without the old entries, the next start reads DataStore alone.
        assertEquals(25.0, withStore(listOf(migration)) { it.arrivalSocPercent.first() })
    }

    @Test
    fun aValueAlreadyInDataStore_winsOverTheOldStore() {
        withStore { it.setManualSocPercent(30.0) }
        val old = mutableMapOf("energy.manualSocPercent" to "64.0")
        val migration = KeyValueMigration(SettingsKeys.ALL, old::get, { old.remove(it) })

        assertEquals(30.0, withStore(listOf(migration)) { it.manualSocPercent.first() })
        assertTrue(old.isEmpty())
    }
}
