package org.julakali.chargeahead.shared.settings

import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.DEFAULT_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.MAX_ARRIVAL_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.Destination
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.NetworkCatalog
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.domain.SavedRoute
import org.julakali.chargeahead.shared.domain.SoCDiagnostics
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.VehicleProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The driver's settings, stored persistently.
 *
 * Read once on creation, then kept in memory and written through on every
 * change. A corrupt profile is treated as "no profile".
 */
class PersistentSettingsStore(
    private val storage: KeyValueStorage,
    // Where the blocking writes run.
    private val writeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SettingsStore {

    private val mutableVehicle = MutableStateFlow(readVehicle())
    override val vehicle: StateFlow<VehicleProfile?> = mutableVehicle.asStateFlow()

    // Adopts a vehicle that exists only in the legacy keys.
    private val mutableVehicles = MutableStateFlow(
        readGarage().ifEmpty { listOfNotNull(readVehicle()) },
    )
    override val vehicles: StateFlow<List<VehicleProfile>> = mutableVehicles.asStateFlow()

    private val mutableManualSoc = MutableStateFlow(readManualSoc())
    override val manualSocPercent: StateFlow<Double?> = mutableManualSoc.asStateFlow()

    private val mutableArrivalSoc = MutableStateFlow(readArrivalSoc())
    override val arrivalSocPercent: StateFlow<Double> = mutableArrivalSoc.asStateFlow()

    // Every write goes through here: off the main thread and serialized,
    // because several writes do read-modify-write.
    private val writeMutex = Mutex()

    private suspend fun write(block: () -> Unit) = withContext(writeDispatcher) { writeMutex.withLock(action = block) }

    override suspend fun setVehicle(profile: VehicleProfile?) = write { writeVehicle(profile) }

    // The un-locked core, for callers already holding the write lock.
    private fun writeVehicle(profile: VehicleProfile?) {
        // The selected vehicle stays on the legacy keys.
        storage.putString(KEY_NAME, profile?.displayName)
        storage.putString(KEY_BATTERY_KWH, profile?.usableBatteryKwh?.toString())
        storage.putString(KEY_CONSUMPTION, profile?.consumptionKwhPer100Km?.toString())
        storage.putString(KEY_CONNECTORS, profile?.acceptedConnectors?.joinToString(",") { it.name })
        storage.putString(KEY_DC_PEAK, profile?.dcPeakPowerKw?.toString())
        mutableVehicle.value = profile

        if (profile != null) {
            val current = mutableVehicles.value
            // A known car is updated in its slot; only a new one is appended.
            val updated = if (current.any { it.displayName == profile.displayName }) {
                current.map { if (it.displayName == profile.displayName) profile else it }
            } else {
                current + profile
            }
            writeGarage(updated)
        }
    }

    override suspend fun removeVehicle(displayName: String) = write {
        val remaining = mutableVehicles.value.filterNot { it.displayName == displayName }
        writeGarage(remaining)
        if (mutableVehicle.value?.displayName == displayName) {
            writeVehicle(remaining.firstOrNull())
        }
    }

    private fun writeGarage(vehicles: List<VehicleProfile>) {
        storage.putJson(
            KEY_GARAGE,
            vehicles.takeIf { it.isNotEmpty() }?.map {
                StoredVehicle(
                    name = it.displayName,
                    batteryKwh = it.usableBatteryKwh,
                    consumption = it.consumptionKwhPer100Km,
                    connectors = it.acceptedConnectors.map(ConnectorType::name),
                    dcPeakKw = it.dcPeakPowerKw,
                )
            },
        )
        mutableVehicles.value = vehicles
    }

    private fun readGarage(): List<VehicleProfile> =
        storage.getJson<List<StoredVehicle>>(KEY_GARAGE)
            .orEmpty()
            .mapNotNull { it.toProfileOrNull() }

    private val storedDestinations = readDestinations()
    private val mutableDestination = MutableStateFlow(storedDestinations.firstOrNull { it.current }?.toDomain())
    override val destination: StateFlow<Destination?> = mutableDestination.asStateFlow()

    private val mutableRecent = MutableStateFlow(storedDestinations.map(StoredDestination::toDomain))
    override val recentDestinations: StateFlow<List<Destination>> = mutableRecent.asStateFlow()

    override suspend fun setDestination(destination: Destination?) = write {
        val previous = mutableRecent.value
        val updated = if (destination == null) {
            previous
        } else {
            // Newest first, duplicates removed, length capped.
            (listOf(destination) + previous.filterNot { it.position == destination.position })
                .take(MAX_RECENT_DESTINATIONS)
        }

        storage.putJson(
            KEY_DESTINATIONS,
            updated.takeIf { it.isNotEmpty() }
                ?.map { StoredDestination(it.name, it.position.lat, it.position.lon, it == destination, it.address) },
        )
        mutableRecent.value = updated
        mutableDestination.value = destination
    }

    private val mutableNetworks = MutableStateFlow(readNetworks())
    override val networks: StateFlow<NetworkPreferences> = mutableNetworks.asStateFlow()

    override suspend fun setNetworks(preferences: NetworkPreferences) = write {
        storage.putString(KEY_ONLY_PREFERRED, preferences.onlyPreferred.toString())
        storage.putJson(
            KEY_PREFERRED_NETWORKS,
            preferences.preferredOperators.takeIf { it.isNotEmpty() }?.toList(),
        )
        mutableNetworks.value = preferences
    }

    private fun readNetworks(): NetworkPreferences {
        val onlyPreferred = storage.getStringOrNull(KEY_ONLY_PREFERRED)?.toBooleanStrictOrNull()
            ?: NetworkPreferences().onlyPreferred
        val preferred = storage.getJson<List<String>>(KEY_PREFERRED_NETWORKS)
            .orEmpty().map(NetworkCatalog::currentKey).toSet()
        return NetworkPreferences(onlyPreferred, preferred)
    }

    private val mutableFilters = MutableStateFlow(readFilters())
    override val chargeFilters: StateFlow<ChargeFilters> = mutableFilters.asStateFlow()

    override suspend fun setChargeFilters(filters: ChargeFilters) = write {
        storage.putJson(
            KEY_CHARGE_FILTERS,
            StoredFilters(filters.minPowerKw, filters.maxDistanceKm),
        )
        mutableFilters.value = filters
    }

    private fun readFilters(): ChargeFilters {
        val stored = storage.getJson<StoredFilters>(KEY_CHARGE_FILTERS) ?: return ChargeFilters()
        return ChargeFilters(stored.minPowerKw, stored.maxDistanceKm)
    }

    private val mutableSavedRoutes = MutableStateFlow(readSavedRoutes())
    override val savedRoutes: StateFlow<List<SavedRoute>> = mutableSavedRoutes.asStateFlow()

    override suspend fun saveRoute(route: SavedRoute) = write {
        writeSavedRoutes(listOf(route) + mutableSavedRoutes.value.filterNot { it.id == route.id })
    }

    override suspend fun renameSavedRoute(id: String, name: String) = write {
        writeSavedRoutes(mutableSavedRoutes.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    override suspend fun removeSavedRoute(id: String) = write {
        writeSavedRoutes(mutableSavedRoutes.value.filterNot { it.id == id })
    }

    private fun writeSavedRoutes(routes: List<SavedRoute>) {
        storage.putJson(
            KEY_SAVED_ROUTES,
            routes.takeIf { it.isNotEmpty() }?.map {
                StoredSavedRoute(
                    id = it.id,
                    name = it.name,
                    destName = it.destination.name,
                    lat = it.destination.position.lat,
                    lon = it.destination.position.lon,
                    summary = it.summary,
                    destAddress = it.destination.address,
                )
            },
        )
        mutableSavedRoutes.value = routes
    }

    private fun readSavedRoutes(): List<SavedRoute> =
        storage.getJson<List<StoredSavedRoute>>(KEY_SAVED_ROUTES)
            .orEmpty()
            .map { SavedRoute(it.id, it.name, Destination(it.destName, LatLon(it.lat, it.lon), it.destAddress), it.summary) }

    private val mutableCarData = MutableStateFlow(readCarData())
    override val carDebugData: StateFlow<List<CarDataPoint>> = mutableCarData.asStateFlow()

    override suspend fun recordCarDataPoint(point: CarDataPoint) = write {
        val updated = mutableCarData.value.filterNot { it.kind == point.kind } + point
        storage.putJson(
            KEY_CAR_DEBUG,
            updated.map { StoredCarData(it.kind.name, it.status.name, it.value, it.observedAtMillis) },
        )
        mutableCarData.value = updated
    }

    private fun readCarData(): List<CarDataPoint> {
        val stored = storage.getJson<List<StoredCarData>>(KEY_CAR_DEBUG).orEmpty()
        return stored.mapNotNull {
            // Entries from a newer app version are skipped, not guessed at.
            val kind = CarDataKind.entries.firstOrNull { k -> k.name == it.kind } ?: return@mapNotNull null
            val status = CarDataStatus.entries.firstOrNull { s -> s.name == it.status } ?: return@mapNotNull null
            CarDataPoint(kind, status, it.value, it.observedAtMillis)
        }
    }

    private val mutableDiagnostics = MutableStateFlow(readDiagnostics())
    override val socDiagnostics: StateFlow<SoCDiagnostics?> = mutableDiagnostics.asStateFlow()

    override suspend fun recordSoCDiagnostics(diagnostics: SoCDiagnostics) = write {
        storage.putJson(
            KEY_SOC_DIAGNOSTICS,
            StoredDiagnostics(
                checkedAtMillis = diagnostics.checkedAtMillis,
                outcome = diagnostics.outcome.name,
                detail = diagnostics.detail,
            ),
        )
        mutableDiagnostics.value = diagnostics
    }

    private fun readDiagnostics(): SoCDiagnostics? {
        val stored = storage.getJson<StoredDiagnostics>(KEY_SOC_DIAGNOSTICS) ?: return null
        // An unrecognized outcome was written by a newer version.
        val outcome = SoCDiagnostics.Outcome.entries.firstOrNull { it.name == stored.outcome } ?: return null
        return SoCDiagnostics(stored.checkedAtMillis, outcome, stored.detail)
    }

    override suspend fun setManualSocPercent(socPercent: Double?) = write {
        val clamped = socPercent?.coerceIn(0.0, 100.0)
        storage.putString(KEY_MANUAL_SOC, clamped?.toString())
        mutableManualSoc.value = clamped
    }

    override suspend fun setArrivalSocPercent(socPercent: Double) = write {
        val clamped = socPercent.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT)
        storage.putString(KEY_ARRIVAL_SOC, clamped.toString())
        mutableArrivalSoc.value = clamped
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
            dcPeakPowerKw = storage.getStringOrNull(KEY_DC_PEAK)?.toDoubleOrNull(),
        )
    }

    /** Unknown connector names are skipped rather than thrown on. */
    private fun readConnectors(): Set<ConnectorType> =
        storage.getStringOrNull(KEY_CONNECTORS)
            ?.split(",")
            ?.mapNotNull { name -> ConnectorType.entries.firstOrNull { it.name == name.trim() } }
            ?.toSet()
            .orEmpty()

    private fun readDestinations(): List<StoredDestination> =
        storage.getJson<List<StoredDestination>>(KEY_DESTINATIONS).orEmpty()

    private fun readManualSoc(): Double? =
        storage.getStringOrNull(KEY_MANUAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, 100.0)

    private fun readArrivalSoc(): Double =
        storage.getStringOrNull(KEY_ARRIVAL_SOC)?.toDoubleOrNull()?.coerceIn(0.0, MAX_ARRIVAL_SOC_PERCENT)
            ?: DEFAULT_ARRIVAL_SOC_PERCENT

    @Serializable
    private data class StoredVehicle(
        val name: String,
        val batteryKwh: Double,
        val consumption: Double,
        val connectors: List<String> = emptyList(),
        val dcPeakKw: Double? = null,
    ) {
        /** Broken numbers cost the entry, not the garage. */
        fun toProfileOrNull(): VehicleProfile? {
            if (batteryKwh <= 0.0 || consumption <= 0.0) return null
            return VehicleProfile(
                displayName = name,
                usableBatteryKwh = batteryKwh,
                consumptionKwhPer100Km = consumption,
                acceptedConnectors = connectors
                    .mapNotNull { stored -> ConnectorType.entries.firstOrNull { it.name == stored } }
                    .toSet(),
                dcPeakPowerKw = dcPeakKw,
            )
        }
    }

    @Serializable
    private data class StoredFilters(
        val minPowerKw: Double,
        val maxDistanceKm: Double,
    )

    @Serializable
    private data class StoredSavedRoute(
        val id: String,
        val name: String,
        val destName: String,
        val lat: Double,
        val lon: Double,
        val summary: String? = null,
        val destAddress: String? = null,
    )

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

    @Serializable
    private data class StoredDestination(
        val name: String,
        val lat: Double,
        val lon: Double,
        /** Exactly one is the current destination; the rest are just history. */
        val current: Boolean = false,
        val address: String? = null,
    ) {
        fun toDomain() = Destination(name, LatLon(lat, lon), address)
    }

    private companion object {
        const val MAX_RECENT_DESTINATIONS = 8

        const val KEY_DESTINATIONS = "route.destinations"
        const val KEY_SOC_DIAGNOSTICS = "energy.socDiagnostics"
        const val KEY_ONLY_PREFERRED = "networks.onlyPreferred"
        const val KEY_PREFERRED_NETWORKS = "networks.preferred"
        const val KEY_NAME = "vehicle.displayName"
        const val KEY_BATTERY_KWH = "vehicle.usableBatteryKwh"
        const val KEY_CONSUMPTION = "vehicle.consumptionKwhPer100Km"
        const val KEY_CONNECTORS = "vehicle.acceptedConnectors"
        const val KEY_DC_PEAK = "vehicle.dcPeakPowerKw"
        const val KEY_GARAGE = "vehicle.garage"
        const val KEY_MANUAL_SOC = "energy.manualSocPercent"
        const val KEY_ARRIVAL_SOC = "energy.arrivalSocPercent"
        const val KEY_CHARGE_FILTERS = "filters.charge"
        const val KEY_SAVED_ROUTES = "routes.saved"
        const val KEY_CAR_DEBUG = "car.debugData"
    }
}

private val json = Json { ignoreUnknownKeys = true }

private inline fun <reified T> KeyValueStorage.putJson(key: String, value: T?) {
    putString(key, value?.let { json.encodeToString(it) })
}

/** null on a missing key or a corrupt payload — the caller supplies the default. */
private inline fun <reified T> KeyValueStorage.getJson(key: String): T? =
    getStringOrNull(key)?.let { raw -> runCatching { json.decodeFromString<T>(raw) }.getOrNull() }
