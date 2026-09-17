package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.flow.SharingStarted

/** How every `uiState` in this package is shared; the timeout survives configuration changes. */
internal val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(UI_STOP_TIMEOUT_MILLIS)

private const val UI_STOP_TIMEOUT_MILLIS = 5_000L
