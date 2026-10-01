package org.julakali.chargeahead.shared.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CarDiagnosticsRepositoryTest {

    private val storage = InMemoryPreferencesDataStore()
    private val repository = DataStoreCarDiagnosticsRepository(storage)

    @Test
    fun `a new store has no diagnostics and no car data`() = runBlocking {
        assertNull(repository.socDiagnostics.first())
        assertTrue(repository.carDebugData.first().isEmpty())
    }

    @Test
    fun `the recorded diagnostics survive a restart`() = runBlocking {
        val diagnostics = SoCDiagnostics(1_700_000_000_000, SoCDiagnostics.Outcome.NO_PERMISSION, "CAR_FUEL denied")

        repository.recordSoCDiagnostics(diagnostics)

        assertEquals(diagnostics, DataStoreCarDiagnosticsRepository(storage).socDiagnostics.first())
    }

    @Test
    fun `recorded car data survives a restart`() = runBlocking {
        val speed = CarDataPoint(CarDataKind.SPEED, CarDataStatus.AVAILABLE, "97 km/h", 10L)
        val range = CarDataPoint(CarDataKind.RANGE, CarDataStatus.NO_DATA, null, 11L)

        repository.recordCarDataPoint(speed)
        repository.recordCarDataPoint(range)

        assertEquals(listOf(speed, range), DataStoreCarDiagnosticsRepository(storage).carDebugData.first())
    }

    /** One entry per kind: a newer point replaces the older one and moves to the end. */
    @Test
    fun `the list is bounded to one point per kind`() = runBlocking {
        repeat(3) { round ->
            CarDataKind.entries.forEach { kind ->
                repository.recordCarDataPoint(CarDataPoint(kind, CarDataStatus.AVAILABLE, "round $round", round.toLong()))
            }
        }

        val stored = repository.carDebugData.first()
        assertEquals(CarDataKind.entries.size, stored.size)
        assertEquals(CarDataKind.entries.toSet(), stored.map { it.kind }.toSet())
        assertTrue(stored.all { it.value == "round 2" })
    }

    @Test
    fun `the newest point of a kind replaces the old one and goes last`() = runBlocking {
        repository.recordCarDataPoint(CarDataPoint(CarDataKind.SPEED, CarDataStatus.NO_DATA, null, 1L))
        repository.recordCarDataPoint(CarDataPoint(CarDataKind.MODEL, CarDataStatus.AVAILABLE, "Zoe", 2L))
        val newer = CarDataPoint(CarDataKind.SPEED, CarDataStatus.AVAILABLE, "50 km/h", 3L)

        repository.recordCarDataPoint(newer)

        assertEquals(listOf(CarDataKind.MODEL, CarDataKind.SPEED), repository.carDebugData.first().map { it.kind })
        assertEquals(newer, repository.carDebugData.first().last())
    }

    @Test
    fun `a corrupt payload reads as nothing instead of throwing`() = runBlocking {
        val corrupt = DataStoreCarDiagnosticsRepository(
            InMemoryPreferencesDataStore(
                mapOf(
                    SettingsKeys.SOC_DIAGNOSTICS to "{not json",
                    SettingsKeys.CAR_DEBUG to """[{"kind":"SPEED"""",
                ),
            ),
        )

        assertNull(corrupt.socDiagnostics.first())
        assertTrue(corrupt.carDebugData.first().isEmpty())
    }

    /** Names a newer build introduced are skipped, the rest is kept. */
    @Test
    fun `unknown enum names from a newer build are dropped, not fatal`() = runBlocking {
        val fromTheFuture = DataStoreCarDiagnosticsRepository(
            InMemoryPreferencesDataStore(
                mapOf(
                    SettingsKeys.SOC_DIAGNOSTICS to """{"checkedAtMillis":5,"outcome":"TELEPATHY"}""",
                    SettingsKeys.CAR_DEBUG to """[
                        {"kind":"TYRE_PRESSURE","status":"AVAILABLE","value":"2.4","observedAtMillis":1},
                        {"kind":"SPEED","status":"GUESSED","observedAtMillis":2},
                        {"kind":"SPEED","status":"AVAILABLE","value":"80 km/h","observedAtMillis":3}
                    ]""",
                ),
            ),
        )

        assertNull(fromTheFuture.socDiagnostics.first())
        assertEquals(
            listOf(CarDataPoint(CarDataKind.SPEED, CarDataStatus.AVAILABLE, "80 km/h", 3L)),
            fromTheFuture.carDebugData.first(),
        )
    }
}
