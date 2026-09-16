package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

/**
 * Keeps the car's last measured charge level as the stored one, so the phone
 * plans with it after the car disconnects instead of with an old typed-in
 * value. The phone has no car source of its own; the stored level is all it reads.
 */
class RememberingSoCSource(
    private val source: SoCSource,
    private val settingsStore: SettingsStore,
) : SoCSource {

    override val kind: SoCSourceKind = source.kind

    override val energy: Flow<EnergyState?> = source.energy.onEach { state ->
        if (state == null) return@onEach
        // Whole percent is what the driver sees and types; finer changes
        // would only mean a disk write per reading.
        val stored = settingsStore.manualSocPercent.first()
        if (stored?.roundToInt() != state.socPercent.roundToInt()) {
            settingsStore.setManualSocPercent(state.socPercent)
        }
    }
}
