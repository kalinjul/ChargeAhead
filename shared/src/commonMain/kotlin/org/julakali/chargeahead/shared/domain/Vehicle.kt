package org.julakali.chargeahead.shared.domain

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The vehicle calculations are done for. */
data class VehicleProfile(
    val displayName: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val acceptedConnectors: Set<ConnectorType>,
    /** DC charging peak; `null` means unknown and the site's connector power is used alone. */
    val dcPeakPowerKw: Double? = null,
    /** The catalog model the car is linked to; `null` for one typed in by hand. */
    val modelId: String? = null,
    /** The catalog's curve for [modelId], attached for planning; not stored with the garage. */
    val roadLoad: RoadLoad? = null,
    /** The driver changed the catalog values; the car no longer follows the catalog. */
    val customized: Boolean = false,
    /** The driver set the consumption to their own driving; the catalog keeps it. */
    val ownConsumption: Boolean = false,
    /** The car's place in the garage; stays the same through every edit. */
    val id: String = newVehicleId(),
) {
    init {
        require(usableBatteryKwh > 0.0) { "usableBatteryKwh must be positive" }
        require(consumptionKwhPer100Km > 0.0) { "consumptionKwhPer100Km must be positive" }
    }
}

/**
 * A model from the backend's vehicle catalog. Only a starting point: the
 * values land in an editable [VehicleProfile].
 */
data class VehiclePreset(
    val id: String,
    val name: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    /** 0 for a vehicle without a DC inlet. */
    val dcPeakPowerKw: Double,
    val connectors: Set<ConnectorType>,
    val roadLoad: RoadLoad? = null,
) {
    fun toProfile(): VehicleProfile = VehicleProfile(
        displayName = name,
        usableBatteryKwh = usableBatteryKwh,
        consumptionKwhPer100Km = consumptionKwhPer100Km,
        acceptedConnectors = connectors,
        dcPeakPowerKw = dcPeakPowerKw.takeIf { it > 0.0 },
        modelId = id,
    )
}

/** Where the charge level comes from. Determines how much to trust it. */
enum class SoCSourceKind {
    /** Typed in by the driver. */
    MANUAL,

    /** From the head unit via the Car App Library. Rarely available, but accurate when it is. */
    CAR_HARDWARE,

    /** Via an OEM cloud service. Not yet connected. */
    OEM_CLOUD,
}

/** A charge level with its source and timestamp. */
data class EnergyState(
    val socPercent: Double,
    val source: SoCSourceKind,
    val observedAtMillis: Long,
)

/** A level the vehicle itself reported, as opposed to one the driver typed. */
val EnergyState?.reportedByCar: Boolean get() = this != null && source != SoCSourceKind.MANUAL

/** Share of the battery that's never planned into range calculations. */
const val DEFAULT_RESERVE_SOC_PERCENT = 10.0

/** Default charge level to still have when arriving at the destination. */
const val DEFAULT_ARRIVAL_SOC_PERCENT = DEFAULT_RESERVE_SOC_PERCENT

/** The highest arrival level worth offering. */
const val MAX_ARRIVAL_SOC_PERCENT = 80.0

/** The garage as the screen shows it. */
data class Garage(
    val vehicles: List<VehicleProfile> = emptyList(),
    val selected: VehicleProfile? = null,
    /** The selected car's range on a full battery, down to 0 %. */
    val selectedFullRangeKm: Double? = null,
    /** The catalog consumption, when the selected car was added from a preset. */
    val selectedPresetConsumption: Double? = null,
)

/**
 * How the car's consumption depends on speed: `F(v) = f0 + f1·v + f2·v²` in N
 * with v in km/h, plus what it takes to run the car and to speed it up.
 */
data class RoadLoad(
    val f0: Double,
    val f1: Double,
    val f2: Double,
    val massKg: Double,
    /** Battery to wheel, 0..1. */
    val drivetrainEfficiency: Double,
    /** Heating, air conditioning and electronics while driving. */
    val auxiliaryPowerKw: Double,
    /** Share of the braking energy that goes back into the battery, 0..1. */
    val recuperationShare: Double,
    /** What this curve consumes over the WLTC, at the plug. */
    val wltpKwhPer100Km: Double,
) {
    fun forceN(speedKmh: Double): Double = f0 + f1 * speedKmh + f2 * speedKmh * speedKmh
}

@OptIn(ExperimentalUuidApi::class)
fun newVehicleId(): String = Uuid.random().toString()

/** The car as the catalog describes it now; a customized car, or one typed in by hand, stays as it is. */
fun VehicleProfile.followingCatalog(presets: List<VehiclePreset>): VehicleProfile {
    if (customized) return this
    val current = presets.presetOf(this)?.toProfile()?.copy(id = id) ?: return this
    return if (ownConsumption) current.copy(consumptionKwhPer100Km = consumptionKwhPer100Km, ownConsumption = true) else current
}

/** The catalog model [vehicle] is linked to. */
fun List<VehiclePreset>.presetOf(vehicle: VehicleProfile): VehiclePreset? =
    vehicle.modelId?.let { modelId -> firstOrNull { it.id == modelId } }

/** The car with its catalog curve; `null` curve for one typed in by hand. */
fun VehicleProfile.withRoadLoadFrom(presets: List<VehiclePreset>): VehicleProfile =
    copy(roadLoad = presets.presetOf(this)?.roadLoad)
