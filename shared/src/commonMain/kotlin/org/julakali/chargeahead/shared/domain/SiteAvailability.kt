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
         * point; otherwise only the fast-charging ones the marker's power stands for.
         */
        fun of(points: List<ChargePointStatus>, slowMode: Boolean): SiteAvailability? {
            val known = points
                .filter { slowMode || it.isFastCharging() }
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
    }
}

enum class AvailabilityLevel { GOOD, LOW, NONE }
