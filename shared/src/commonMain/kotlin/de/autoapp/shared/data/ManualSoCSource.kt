package de.autoapp.shared.data

import de.autoapp.shared.domain.EnergyState
import de.autoapp.shared.domain.SettingsStore
import de.autoapp.shared.domain.SoCSource
import de.autoapp.shared.domain.SoCSourceKind
import de.autoapp.shared.domain.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The typed-in charge level — the only source that works on both platforms,
 * and thus the baseline (ARCHITECTURE.md 1.2).
 *
 * The timestamp is the one at read time, not at entry time. This is a
 * deliberate simplification for M2: the value ages while driving, and
 * projecting it forward based on distance driven is its own item on the
 * list (ARCHITECTURE.md, open item 3). Until then, the UI needs to make it
 * easy for the driver to update it.
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
