package de.autoapp.shared.domain

/**
 * Common EV models as presets for the garage.
 *
 * ROADMAP open item 4 names the trade-off: every facelift makes an entry
 * stale, and a wrong preset is worse than none because nobody double-checks
 * it. The presets are therefore only a starting point — battery and
 * consumption land in an editable [VehicleProfile], and consumption in
 * particular is meant to be adjusted per driver (nobody drives the WLTP value).
 */
data class VehiclePreset(
    val name: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val dcPeakPowerKw: Double,
) {
    fun toProfile(): VehicleProfile = VehicleProfile(
        displayName = name,
        usableBatteryKwh = usableBatteryKwh,
        consumptionKwhPer100Km = consumptionKwhPer100Km,
        // CCS is the fast-charging standard in Europe; presets that need
        // something else (older Leaf: CHAdeMO) don't belong in this list.
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = dcPeakPowerKw,
    )
}

object VehicleCatalog {

    val all: List<VehiclePreset> = listOf(
        VehiclePreset("VW ID.4 Pro", 77.0, 19.5, 135.0),
        VehiclePreset("Tesla Model 3 LR", 75.0, 16.8, 250.0),
        VehiclePreset("Tesla Model Y LR", 75.0, 16.9, 250.0),
        VehiclePreset("Renault Megane E-Tech", 60.0, 17.2, 130.0),
        VehiclePreset("Hyundai Ioniq 5 77", 74.0, 18.0, 233.0),
        VehiclePreset("Kia EV6 GT-Line", 74.0, 17.8, 236.0),
        VehiclePreset("Skoda Enyaq 85", 77.0, 16.8, 175.0),
        VehiclePreset("BMW i4 eDrive40", 81.0, 18.1, 205.0),
        VehiclePreset("Audi Q4 e-tron 45", 77.0, 19.2, 175.0),
        VehiclePreset("Mercedes EQA 250+", 70.0, 17.7, 100.0),
        VehiclePreset("Polestar 2 LR", 79.0, 17.6, 205.0),
        VehiclePreset("Cupra Born 77", 77.0, 17.0, 170.0),
        VehiclePreset("Volvo EX30 Extended", 64.0, 17.5, 153.0),
        VehiclePreset("MG4 Urban 54", 52.8, 16.0, 87.0),
        VehiclePreset("Porsche Taycan", 89.0, 20.0, 270.0),
        VehiclePreset("Fiat 500e 42", 37.0, 14.9, 85.0),
        VehiclePreset("Dacia Spring 65", 25.0, 13.9, 30.0),
    )
}
