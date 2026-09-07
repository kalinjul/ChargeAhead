package de.autoapp.shared.settings

import de.autoapp.shared.domain.CarDataKind
import de.autoapp.shared.domain.CarDataPoint
import de.autoapp.shared.domain.CarDataStatus
import de.autoapp.shared.domain.ChargeFilters
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.MapLabelStyle
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.SavedRoute
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

    // A pre-garage install has its one vehicle only in the legacy keys;
    // adopting it here keeps that vehicle visible in the new garage list.
    private val mutableVehicles = MutableStateFlow(
        readGarage().ifEmpty { listOfNotNull(readVehicle()) },
    )
    override val vehicles: StateFlow<List<VehicleProfile>> = mutableVehicles.asStateFlow()

    private val mutableManualSoc = MutableStateFlow(readManualSoc())
    override val manualSocPercent: StateFlow<Double?> = mutableManualSoc.asStateFlow()

    override suspend fun setVehicle(profile: VehicleProfile?) {
        // The selected vehicle stays on the legacy keys so the car UIs and
        // older installs read it unchanged; the garage is bookkeeping on top.
        storage.putString(KEY_NAME, profile?.displayName)
        storage.putString(KEY_BATTERY_KWH, profile?.usableBatteryKwh?.toString())
        storage.putString(KEY_CONSUMPTION, profile?.consumptionKwhPer100Km?.toString())
        storage.putString(KEY_CONNECTORS, profile?.acceptedConnectors?.joinToString(",") { it.name })
        storage.putString(KEY_DC_PEAK, profile?.dcPeakPowerKw?.toString())
        mutableVehicle.value = profile

        if (profile != null) {
            val updated = mutableVehicles.value
                .filterNot { it.displayName == profile.displayName } + profile
            writeGarage(updated)
        }
    }

    override suspend fun removeVehicle(displayName: String) {
        val remaining = mutableVehicles.value.filterNot { it.displayName == displayName }
        writeGarage(remaining)
        if (mutableVehicle.value?.displayName == displayName) {
            setVehicle(remaining.firstOrNull())
        }
    }

    private fun writeGarage(vehicles: List<VehicleProfile>) {
        storage.putString(
            KEY_GARAGE,
            vehicles.takeIf { it.isNotEmpty() }?.let { list ->
                json.encodeToString(
                    list.map {
                        StoredVehicle(
                            name = it.displayName,
                            batteryKwh = it.usableBatteryKwh,
                            consumption = it.consumptionKwhPer100Km,
                            connectors = it.acceptedConnectors.map(ConnectorType::name),
                            dcPeakKw = it.dcPeakPowerKw,
                        )
                    },
                )
            },
        )
        mutableVehicles.value = vehicles
    }

    private fun readGarage(): List<VehicleProfile> {
        val raw = storage.getStringOrNull(KEY_GARAGE) ?: return emptyList()
        val stored = runCatching { json.decodeFromString<List<StoredVehicle>>(raw) }.getOrElse { return emptyList() }
        return stored.mapNotNull { it.toProfileOrNull() }
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

    private val mutableFilters = MutableStateFlow(readFilters())
    override val chargeFilters: StateFlow<ChargeFilters> = mutableFilters.asStateFlow()

    override suspend fun setChargeFilters(filters: ChargeFilters) {
        storage.putString(
            KEY_CHARGE_FILTERS,
            json.encodeToString(StoredFilters(filters.minPowerKw, filters.maxPriceEuroPerKwh, filters.maxDistanceKm)),
        )
        mutableFilters.value = filters
    }

    private fun readFilters(): ChargeFilters {
        val raw = storage.getStringOrNull(KEY_CHARGE_FILTERS) ?: return ChargeFilters()
        val stored = runCatching { json.decodeFromString<StoredFilters>(raw) }.getOrNull() ?: return ChargeFilters()
        return ChargeFilters(stored.minPowerKw, stored.maxPrice, stored.maxDistanceKm)
    }

    private val mutableTariffs = MutableStateFlow(readTariffIds())
    override val activeTariffIds: StateFlow<Set<String>> = mutableTariffs.asStateFlow()

    override suspend fun setActiveTariffIds(ids: Set<String>) {
        storage.putString(
            KEY_ACTIVE_TARIFFS,
            ids.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it.toList()) },
        )
        mutableTariffs.value = ids
    }

    private fun readTariffIds(): Set<String> =
        storage.getStringOrNull(KEY_ACTIVE_TARIFFS)
            ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull() }
            ?.toSet()
            .orEmpty()

    private val mutableSavedRoutes = MutableStateFlow(readSavedRoutes())
    override val savedRoutes: StateFlow<List<SavedRoute>> = mutableSavedRoutes.asStateFlow()

    override suspend fun saveRoute(route: SavedRoute) {
        writeSavedRoutes(listOf(route) + mutableSavedRoutes.value.filterNot { it.id == route.id })
    }

    override suspend fun renameSavedRoute(id: String, name: String) {
        writeSavedRoutes(mutableSavedRoutes.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    override suspend fun removeSavedRoute(id: String) {
        writeSavedRoutes(mutableSavedRoutes.value.filterNot { it.id == id })
    }

    private fun writeSavedRoutes(routes: List<SavedRoute>) {
        storage.putString(
            KEY_SAVED_ROUTES,
            routes.takeIf { it.isNotEmpty() }?.let { list ->
                json.encodeToString(
                    list.map {
                        StoredSavedRoute(
                            id = it.id,
                            name = it.name,
                            destName = it.destination.name,
                            lat = it.destination.position.lat,
                            lon = it.destination.position.lon,
                            summary = it.summary,
                        )
                    },
                )
            },
        )
        mutableSavedRoutes.value = routes
    }

    private fun readSavedRoutes(): List<SavedRoute> {
        val raw = storage.getStringOrNull(KEY_SAVED_ROUTES) ?: return emptyList()
        val stored = runCatching { json.decodeFromString<List<StoredSavedRoute>>(raw) }.getOrElse { return emptyList() }
        return stored.map {
            SavedRoute(it.id, it.name, Destination(it.destName, LatLon(it.lat, it.lon)), it.summary)
        }
    }

    private val mutableCarData = MutableStateFlow(readCarData())
    override val carDebugData: StateFlow<List<CarDataPoint>> = mutableCarData.asStateFlow()

    override suspend fun recordCarDataPoint(point: CarDataPoint) {
        val updated = mutableCarData.value.filterNot { it.kind == point.kind } + point
        storage.putString(
            KEY_CAR_DEBUG,
            json.encodeToString(
                updated.map { StoredCarData(it.kind.name, it.status.name, it.value, it.observedAtMillis) },
            ),
        )
        mutableCarData.value = updated
    }

    private fun readCarData(): List<CarDataPoint> {
        val raw = storage.getStringOrNull(KEY_CAR_DEBUG) ?: return emptyList()
        val stored = runCatching { json.decodeFromString<List<StoredCarData>>(raw) }.getOrElse { return emptyList() }
        return stored.mapNotNull {
            // Entries from a newer app version are skipped, not guessed at.
            val kind = CarDataKind.entries.firstOrNull { k -> k.name == it.kind } ?: return@mapNotNull null
            val status = CarDataStatus.entries.firstOrNull { s -> s.name == it.status } ?: return@mapNotNull null
            CarDataPoint(kind, status, it.value, it.observedAtMillis)
        }
    }

    private val mutableMapLabel = MutableStateFlow(readMapLabelStyle())
    override val mapLabelStyle: StateFlow<MapLabelStyle> = mutableMapLabel.asStateFlow()

    override suspend fun setMapLabelStyle(style: MapLabelStyle) {
        storage.putString(KEY_MAP_LABEL, style.name)
        mutableMapLabel.value = style
    }

    private fun readMapLabelStyle(): MapLabelStyle =
        storage.getStringOrNull(KEY_MAP_LABEL)
            ?.let { stored -> MapLabelStyle.entries.firstOrNull { it.name == stored } }
            ?: MapLabelStyle.PRICE

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
            dcPeakPowerKw = storage.getStringOrNull(KEY_DC_PEAK)?.toDoubleOrNull(),
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
    private data class StoredVehicle(
        val name: String,
        val batteryKwh: Double,
        val consumption: Double,
        val connectors: List<String> = emptyList(),
        val dcPeakKw: Double? = null,
    ) {
        /** Same forgiveness as the legacy keys: broken numbers cost the entry, not the garage. */
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
        val maxPrice: Double,
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
        const val KEY_DC_PEAK = "vehicle.dcPeakPowerKw"
        const val KEY_GARAGE = "vehicle.garage"
        const val KEY_MANUAL_SOC = "energy.manualSocPercent"
        const val KEY_CHARGE_FILTERS = "filters.charge"
        const val KEY_ACTIVE_TARIFFS = "tariffs.active"
        const val KEY_SAVED_ROUTES = "routes.saved"
        const val KEY_MAP_LABEL = "map.labelStyle"
        const val KEY_CAR_DEBUG = "car.debugData"
    }
}
