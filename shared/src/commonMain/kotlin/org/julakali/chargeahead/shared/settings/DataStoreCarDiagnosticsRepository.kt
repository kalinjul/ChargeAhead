package org.julakali.chargeahead.shared.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.settings.SettingsKeys.CAR_DEBUG
import org.julakali.chargeahead.shared.settings.SettingsKeys.SOC_DIAGNOSTICS

class DataStoreCarDiagnosticsRepository(
    private val dataStore: DataStore<Preferences>,
) : CarDiagnosticsRepository {

    override val socDiagnostics: Flow<SoCDiagnostics?> = dataStore.read { preferences ->
        preferences.getJson<StoredDiagnostics>(SOC_DIAGNOSTICS)?.let { stored ->
            // An unrecognized outcome was written by a newer version.
            SoCDiagnostics.Outcome.entries.firstOrNull { it.name == stored.outcome }
                ?.let { SoCDiagnostics(stored.checkedAtMillis, it, stored.detail) }
        }
    }

    override val carDebugData: Flow<List<CarDataPoint>> = dataStore.read { it.carData() }

    override suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics) {
        dataStore.edit {
            it.putJson(
                SOC_DIAGNOSTICS,
                StoredDiagnostics(
                    checkedAtMillis = diagnostics.checkedAtMillis,
                    outcome = diagnostics.outcome.name,
                    detail = diagnostics.detail,
                ),
            )
        }
    }

    override suspend fun recordCarDataPoint(point: CarDataPoint) {
        dataStore.edit { preferences ->
            val updated = preferences.carData().filterNot { it.kind == point.kind } + point
            preferences.putJson(
                CAR_DEBUG,
                updated.map { StoredCarData(it.kind.name, it.status.name, it.value, it.observedAtMillis) },
            )
        }
    }

    private fun Preferences.carData(): List<CarDataPoint> =
        getJson<List<StoredCarData>>(CAR_DEBUG).orEmpty().mapNotNull {
            // Entries from a newer app version are skipped, not guessed at.
            val kind = CarDataKind.entries.firstOrNull { k -> k.name == it.kind } ?: return@mapNotNull null
            val status = CarDataStatus.entries.firstOrNull { s -> s.name == it.status } ?: return@mapNotNull null
            CarDataPoint(kind, status, it.value, it.observedAtMillis)
        }

    @Serializable
    private data class StoredCarData(
        val kind: String,
        val status: String,
        val value: String? = null,
        val observedAtMillis: Long,
    )

    @Serializable
    private data class StoredDiagnostics(
        val checkedAtMillis: Long,
        val outcome: String,
        val detail: String? = null,
    )
}
