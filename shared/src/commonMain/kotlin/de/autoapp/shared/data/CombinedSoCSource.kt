package de.autoapp.shared.data

import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.SoCSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Layers the charge-level sources on top of each other: if the vehicle
 * supplies a value, it wins; otherwise the typed-in one applies
 * (ARCHITECTURE.md 1.2).
 *
 * The precedence is non-negotiable and doesn't depend on how old the values
 * are: a measurement from the car is fundamentally superior to a manual
 * entry, no matter how fresh that entry is. Conversely, `null` from the
 * vehicle source is the normal case — in projection mode, hardly any head
 * unit provides this data.
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
