package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SettingsStore
import org.julakali.chargeahead.shared.domain.SoCSource
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The typed-in charge level.
 *
 * The timestamp is the one at read time, not at entry time.
 */
class ManualSoCSource(
    settingsStore: SettingsStore,
    private val time: TimeProvider,
) : SoCSource {

    override val kind: SoCSourceKind = SoCSourceKind.MANUAL

    override val energy: Flow<EnergyState?> =
        settingsStore.manualSocPercent.map { socPercent ->
            socPercent?.let { EnergyState(it, SoCSourceKind.MANUAL, time.nowMillis()) }
        }
}
