package org.julakali.chargeahead.android.phone

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The shared ViewModel for this screen, resolved from `sharedUiModule` and
 * scoped to the activity.
 * TODO scope per Navigation3 entry once lifecycle-viewmodel-navigation3 (compileSdk 37) is available
 */
@Composable
internal inline fun <reified VM : ViewModel> phoneViewModel(): VM = koinViewModel()
