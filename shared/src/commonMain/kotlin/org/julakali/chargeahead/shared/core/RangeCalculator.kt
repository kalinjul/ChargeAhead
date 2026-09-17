package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.DEFAULT_RESERVE_SOC_PERCENT
import org.julakali.chargeahead.shared.domain.VehicleProfile

/**
 * Remaining range and arrival charge level.
 *
 * ```
 * available_kWh = usableBatteryKwh × (soc − reserveSoc) / 100
 * range_km = available_kWh / consumption_kWh_per_100km × 100
 * ```
 */
object RangeCalculator {

    /**
     * Drivable distance down to the reserve, in kilometers. Never negative.
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
     * Relative to the full battery, not to the portion above the reserve, so
     * it may drop below the reserve, but not below zero.
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
