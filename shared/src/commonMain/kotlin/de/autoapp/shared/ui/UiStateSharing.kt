package de.autoapp.shared.ui

import kotlinx.coroutines.flow.SharingStarted

/**
 * How every `uiState` in this package is shared.
 *
 * The five seconds are the "Now in Android" value and they exist for
 * configuration changes: a rotation or a theme switch tears the collector
 * down and puts it back up again a moment later. Stopping immediately would
 * re-run the whole upstream — location, database, price quotes — for a
 * screen the driver never left. Stopping never would keep every screen ever
 * opened subscribed for the rest of the process.
 */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(UI_STOP_TIMEOUT_MILLIS)

private const val UI_STOP_TIMEOUT_MILLIS = 5_000L
