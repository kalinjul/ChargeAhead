package org.julakali.chargeahead.shared.domain

/** The corridor list's geometry and ranking. */
interface CorridorPlanning {

    /** The corridor along [route], or along the direction of travel when there is none. */
    fun corridor(route: Route?): Corridor

    interface Corridor {
        /** Where to search from [fix], as far as vehicle and charge reach. */
        fun searchArea(fix: Fix, vehicle: VehicleProfile?, energy: EnergyState?): SearchArea

        /** The charge stops among [sites], nearest reachable first. */
        fun stops(
            fix: Fix,
            area: SearchArea,
            sites: List<ChargeSite>,
            vehicle: VehicleProfile?,
            energy: EnergyState?,
            networks: NetworkPreferences,
        ): List<ChargeStop>
    }
}
