package de.autoapp.android.phone

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The shared ViewModel for this screen, resolved from `sharedUiModule` and
 * scoped to the activity.
 *
 * Activity scope, not per-entry scope, although Navigation3 is in place:
 * the ViewModelStore decorator lives in lifecycle-viewmodel-navigation3,
 * which rides lifecycle 2.11 — and that needs the compileSdk 37 the SDK
 * Manager doesn't offer yet (ARCHITECTURE.md §9). When Platform 37 lands,
 * per-entry scope is one artifact plus one NavDisplay decorator line.
 */
@Composable
internal inline fun <reified VM : ViewModel> phoneViewModel(): VM = koinViewModel()
