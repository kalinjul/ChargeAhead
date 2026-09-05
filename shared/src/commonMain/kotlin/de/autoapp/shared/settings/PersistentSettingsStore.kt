package de.autoapp.shared.settings

import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.SoCDiagnostics
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Vehicle profile and charge level, stored persistently.
 *
 * Read once on creation, then kept in memory and written through on every
 * change. That's enough, because the values are written rarely but read on
 * every range recalculation.
 *
 * A profile stored incompletely or corrupted is treated as "no profile"
 * rather than partially loaded: a vehicle with a battery but no consumption
 * value would break the range formula.
 */
class PersistentSettingsStore(
    private val storage: KeyValueStorage,
) : SettingsStore {

    private val mutableVehicle = MutableStateFlow(readVehicle())
    override val vehicle: StateFlow<VehicleProfile?> = mutableVehicle.asStateFlow()

    private val mutableManualSoc = MutableStateFlow(readManualSoc())
    override val manualSocPercent: StateFlow<Double?> = mutableManualSoc.asStateFlow()

    override suspend fun setVehicle(profile: VehicleProfile?) {
        storage.putString(KEY_NAME, profile?.displayName)
        storage.putString(KEY_BATTERY_KWH, profile?.usableBatteryKwh?.toString())
        storage.putString(KEY_CONSUMPTION, profile?.consumptionKwhPer100Km?.toString())
        storage.putString(KEY_CONNECTORS, profile?.acceptedConnectors?.joinToString(",") { it.name })
        mutableVehicle.value = profile
    }

    private val mutableDestination = MutableStateFlow(readDestinations().firstOrNull { it.current }?.toDomain())
    override val destination: StateFlow<Destination?> = mutableDestination.asStateFlow()

    private val mutableRecent = MutableStateFlow(readDestinations().map(StoredDestination::toDomain))
    override val recentDestinations: StateFlow<List<Destination>> = mutableRecent.asStateFlow()

    override suspend fun setDestination(destination: Destination?) {
        val previous = mutableRecent.value
        val updated = if (destination == null) {
            previous
        } else {
            // Newest first, duplicates removed, length capped: the history is
            // a usability aid for the car, not an archive.
            (listOf(destination) + previous.filterNot { it.position == destination.position })
                .take(MAX_RECENT_DESTINATIONS)
        }

        storage.putString(
            KEY_DESTINATIONS,
            updated.takeIf { it.isNotEmpty() }?.let { list ->
                json.encodeToString(
                    list.map { StoredDestination(it.name, it.position.lat, it.position.lon, it == destination) },
                )
            },
        )
        mutableRecent.value = updated
        mutableDestination.value = destination
    }

    private val mutableNetworks = MutableStateFlow(readNetworks())
    override val networks: StateFlow<NetworkPreferences> = mutableNetworks.asStateFlow()

    override suspend fun setNetworks(preferences: NetworkPreferences) {
        storage.putString(KEY_ONLY_PREFERRED, preferences.onlyPreferred.toString())
        storage.putString(
            KEY_PREFERRED_NETWORKS,
            preferences.preferredOperators.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it.toList()) },
        )
        mutableNetworks.value = preferences
    }

    private fun readNetworks(): NetworkPreferences {
        val onlyPreferred = storage.getStringOrNull(KEY_ONLY_PREFERRED)?.toBooleanStrictOrNull() ?: false
        val preferred = storage.getStringOrNull(KEY_PREFERRED_NETWORKS)
            ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull() }
            ?.toSet()
            .orEmpty()
        return NetworkPreferences(onlyPreferred, preferred)
    }

    private val mutableDiagnostics = MutableStateFlow(readDiagnostics())
    override val socDiagnostics: StateFlow<SoCDiagnostics?> = mutableDiagnostics.asStateFlow()

    override suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics) {
        storage.putString(
            KEY_SOC_DIAGNOSTICS,
            json.encodeToString(
                StoredDiagnostics(
                    checkedAtMillis = diagnostics.checkedAtMillis,
                    outcome = diagnostics.outcome.name,
                    detail = diagnostics.detail,
                ),
            ),
        )
        mutableDiagnostics.value = diagnostics
    }

    private fun readDiagnostics(): SoCDiagnostics? {
        val raw = storage.getStringOrNull(KEY_SOC_DIAGNOSTICS) ?: return null
        val stored = runCatching { json.decodeFromString<StoredDiagnostics>(raw) }.getOrNull() ?: return null
        // An unrecognized outcome means it was written by a newer version.
        // Better to show nothing than something wrong.
        val outcome = SoCDiagnostics.Outcome.entries.firstOrNull { it.name == stored.outcome } ?: return null
        return SoCDiagnostics(stored.checkedAtMillis, outcome, stored.detail)
    }

    override suspend fun setManualSocPercent(socPercent: Double?) {
        val clamped = socPercent?.coerceIn(0.0, 100.0)
        storage.putString(KEY_MANUAL_SOC, clamped?.toString())
        mutableManualSoc.value = clamped
    }

    private fun readVehicle(): VehicleProfile? {
        val battery = storage.getStringOrNull(KEY_BATTERY_KWH)?.toDoubleOrNull() ?: return null
        val consumption = storage.getStringOrNull(KEY_CONSUMPTION)?.toDoubleOrNull() ?: return null
        if (battery <= 0.0 || consumption <= 0.0) return null

        return VehicleProfile(
            displayName = storage.getStringOrNull(KEY_NAME).orEmpty(),
            usableBatteryKwh = battery,
            consumptionKwhPer100Km = consumption,
            acceptedConnectors = readConnectors(),
        )
    }

    /**
     * Unknown connector names are skipped rather than thrown on — otherwise
     * an older app version would lose the entire profile on rollback, just
     * because a single new enum value is stored in it.
     */
    private fun readConnectors(): Set<ConnectorType> =
        storage.getStringOrNull(KEY_CONNECTORS)
            ?.split(",")
            ?.mapNotNull { name -> ConnectorType.entries.firstOrNull { it.name == name.trim() } }
            ?.toSet()
            .orEmpty()

    /**
     * Destinations as JSON rather than hand-rolled: a place name may contain
     * any character, including whatever would have been chosen as a delimiter.
     */
    private fun readDestinations(): List<StoredDestination> {
        val raw = storage.getStringOrNull(KEY_DESTINATIONS) ?: return emptyList()
        // A corrupted history only costs the history, not the app.
        return runCatching { json.decodeFromString<List<StoredDestination>>(raw) }.getOrElse { emptyList() }
    }

    private fun readManualSoc(): Double? =
        storage.getStringOrNull(KEY_MANUAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)

    @Serializable
    private data class StoredDiagnostics(
        val checkedAtMillis: Long,
        val outcome: String,
        val detail: String? = null,
    )

    @Serializable
    private data class StoredDestination(
        val name: String,
        val lat: Double,
        val lon: Double,
        /** Exactly one is the current destination; the rest are just history. */
        val current: Boolean = false,
    ) {
        fun toDomain() = Destination(name, LatLon(lat, lon))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val MAX_RECENT_DESTINATIONS = 8

        const val KEY_DESTINATIONS = "route.destinations"
        const val KEY_SOC_DIAGNOSTICS = "energy.socDiagnostics"
        const val KEY_ONLY_PREFERRED = "networks.onlyPreferred"
        const val KEY_PREFERRED_NETWORKS = "networks.preferred"
        const val KEY_NAME = "vehicle.displayName"
        const val KEY_BATTERY_KWH = "vehicle.usableBatteryKwh"
        const val KEY_CONSUMPTION = "vehicle.consumptionKwhPer100Km"
        const val KEY_CONNECTORS = "vehicle.acceptedConnectors"
        const val KEY_MANUAL_SOC = "energy.manualSocPercent"
    }
}
