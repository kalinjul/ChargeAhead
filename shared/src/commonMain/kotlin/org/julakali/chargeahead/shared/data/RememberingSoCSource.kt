package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.VehicleRepository
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

/**
 * Keeps the car's last measured charge level as the stored one, so the phone
 * plans with it after the car disconnects.
 */
class RememberingSoCSource(
    private val source: SoCSource,
    private val vehicles: VehicleRepository,
) : SoCSource {

    override val kind: SoCSourceKind = source.kind

    override val energy: Flow<EnergyState?> = source.energy.onEach { state ->
        if (state == null) return@onEach
        // Only write on whole-percent changes.
        val stored = vehicles.manualSocPercent.first()
        if (stored?.roundToInt() != state.socPercent.roundToInt()) {
            vehicles.setManualSocPercent(state.socPercent)
        }
    }
}
