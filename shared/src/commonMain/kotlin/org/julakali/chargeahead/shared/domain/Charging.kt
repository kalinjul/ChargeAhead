package org.julakali.chargeahead.shared.domain

internal const val MIN_DC_POWER_KW = 50.0

/** Strongest connector of any type; `null` without connectors. */
val ChargeSite.maxPowerKw: Double?
    get() = connectors.maxOfOrNull { it.maxPowerKw }

/** Strongest CCS2 or NACS connector; `null` when the site has neither. */
val ChargeSite.maxDcPowerKw: Double?
    get() = connectors
        .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
        .maxOfOrNull { it.maxPowerKw }
