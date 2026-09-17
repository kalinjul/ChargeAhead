package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Layers the charge-level sources: if the vehicle supplies a value, it wins
 * regardless of age; otherwise the typed-in one applies.
 */
class CombinedSoCSource(
    private val manual: SoCSource,
    private val hardware: SoCSource?,
) : SoCSource {

    override val kind: SoCSourceKind = hardware?.kind ?: manual.kind

    override val energy: Flow<EnergyState?> =
        if (hardware == null) {
            manual.energy
        } else {
            combine(hardware.energy, manual.energy) { fromCar, fromDriver -> fromCar ?: fromDriver }
        }
}
