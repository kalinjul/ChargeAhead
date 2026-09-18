package org.julakali.chargeahead.shared.domain

sealed interface SiteAvailability {

    data class Live(val free: Int, val total: Int) : SiteAvailability {
        val level: AvailabilityLevel
            get() = when {
                free == 0 -> AvailabilityLevel.NONE
                free * 2 < total -> AvailabilityLevel.LOW
                else -> AvailabilityLevel.GOOD
            }
    }

    data object OutOfOrder : SiteAvailability

    companion object {

        /**
         * `null` when no point reports a known state. [slowMode] counts every
         * point; otherwise only the fast-charging ones from [minPowerKw] up.
         * Only the live points' own power decides, not the site's.
         */
        fun of(points: List<ChargePointStatus>, slowMode: Boolean, minPowerKw: Double = 0.0): SiteAvailability? {
            val known = points
                .filter { slowMode || (it.isFastCharging() && it.reaches(minPowerKw)) }
                .filter { it.state != ChargePointState.UNKNOWN }
            if (known.isEmpty()) return null
            if (known.all { it.state == ChargePointState.OUT_OF_ORDER || it.state == ChargePointState.BLOCKED }) {
                return OutOfOrder
            }
            return Live(free = known.count { it.state == ChargePointState.AVAILABLE }, total = known.size)
        }

        // A point without connector types can't be ruled out.
        private fun ChargePointStatus.isFastCharging(): Boolean =
            connectors.isEmpty() || connectors.any { it == ConnectorType.CCS2 || it == ConnectorType.TESLA_NACS }

        // Neither can a point without a known power.
        private fun ChargePointStatus.reaches(minPowerKw: Double): Boolean =
            maxPowerKw == null || maxPowerKw >= minPowerKw
    }
}

enum class AvailabilityLevel { GOOD, LOW, NONE }
