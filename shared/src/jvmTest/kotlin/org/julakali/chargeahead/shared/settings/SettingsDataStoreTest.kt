package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
        block: suspend (PersistentSettingsStore) -> T,
    ): T = runBlocking {
        val job = SupervisorJob()
        try {
            val dataStore = createSettingsDataStore(file.absolutePath, migrations, CoroutineScope(Dispatchers.IO + job))
            block(PersistentSettingsStore(dataStore))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun aSavedProfile_survivesARestart_onDisk() {
        withStore { it.setVehicle(vehicle) }

        assertEquals(vehicle, withStore { it.vehicle.value })
    }

    @Test
    fun aCorruptFile_isTreatedAsNoSettings() {
        file.writeText("kein Protobuf")

        assertNull(withStore { it.vehicle.value })
    }

    @Test
    fun theOldKeyValueEntries_areTakenOverOnce_andRemovedThere() {
        val old = mutableMapOf(
            "vehicle.displayName" to "Testwagen",
            "vehicle.usableBatteryKwh" to "77.0",
            "vehicle.consumptionKwhPer100Km" to "18.0",
            "vehicle.acceptedConnectors" to "CCS2",
            "energy.manualSocPercent" to "64.0",
            "unrelated" to "stays",
        )
        val migration = KeyValueMigration(PersistentSettingsStore.ALL_KEYS, old::get, { old.remove(it) })

        val (migratedVehicle, migratedSoc) = withStore(listOf(migration)) {
            it.vehicle.value to it.manualSocPercent.value
        }

        assertEquals(vehicle, migratedVehicle)
        assertEquals(64.0, migratedSoc)
        assertEquals(mapOf("unrelated" to "stays"), old)
        // Without the old entries, the next start reads DataStore alone.
        assertEquals(vehicle, withStore(listOf(migration)) { it.vehicle.value })
    }

    @Test
    fun aValueAlreadyInDataStore_winsOverTheOldStore() {
        withStore { it.setManualSocPercent(30.0) }
        val old = mutableMapOf("energy.manualSocPercent" to "64.0")
        val migration = KeyValueMigration(PersistentSettingsStore.ALL_KEYS, old::get, { old.remove(it) })

        assertEquals(30.0, withStore(listOf(migration)) { it.manualSocPercent.value })
        assertTrue(old.isEmpty())
    }
}
