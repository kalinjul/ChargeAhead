package de.autoapp.shared.core

import de.autoapp.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import de.autoapp.shared.domain.VehicleProfile

/**
 * Remaining range and arrival charge level (ARCHITECTURE.md section 5.1).
 *
 * ```
 * available_kWh = usableBatteryKwh × (soc − reserveSoc) / 100
 * range_km = available_kWh / consumption_kWh_per_100km × 100
 * ```
 */
object RangeCalculator {

    /**
     * Drivable distance down to the reserve, in kilometers.
     *
     * Never negative: below the reserve, range is zero, not "minus 40 km". A
     * negative value would propagate through reachability classification and
     * into the list.
     */
    fun rangeKm(
        vehicle: VehicleProfile,
        socPercent: Double,
        reserveSocPercent: Double = DEFAULT_RESERVE_SOC_PERCENT,
    ): Double {
        val usableSoc = (socPercent - reserveSocPercent).coerceAtLeast(0.0)
        val availableKwh = vehicle.usableBatteryKwh * usableSoc / 100.0
        return availableKwh / vehicle.consumptionKwhPer100Km * 100.0
    }

    /**
     * Charge level on arrival, in percent.
     *
     * Relative to the full battery, not to the portion above the reserve —
     * the driver reads off the same number their car's own display shows. It
     * may therefore drop below the reserve; it may not drop below zero.
     */
    fun socOnArrivalPercent(
        vehicle: VehicleProfile,
        socPercent: Double,
        distanceKm: Double,
    ): Double {
        val neededKwh = distanceKm / 100.0 * vehicle.consumptionKwhPer100Km
        val neededSoc = neededKwh / vehicle.usableBatteryKwh * 100.0
        return (socPercent - neededSoc).coerceAtLeast(0.0)
    }
}
