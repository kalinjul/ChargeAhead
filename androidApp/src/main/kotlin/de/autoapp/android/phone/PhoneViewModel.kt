package de.autoapp.android.phone

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The shared ViewModel for this screen, resolved from `sharedUiModule` and
 * scoped to the activity.
 *
 * Activity scope, not screen scope, because navigation is still the enum
 * page switch in [PhoneApp]: there is no per-destination store to scope to
 * yet. Navigation3 (plans/technical-debts.md) brings one, and only this
 * function changes.
 */
@Composable
internal inline fun <reified VM : ViewModel> phoneViewModel(): VM = koinViewModel()
