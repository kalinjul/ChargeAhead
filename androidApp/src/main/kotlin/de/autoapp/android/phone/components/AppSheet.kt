package de.autoapp.android.phone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The mockup's `.sheet`: opens half-height, drags up to fullscreen. The
 * expansion is native ModalBottomSheet behavior — content taller than half
 * the screen starts partially expanded; below-the-fold sections are the
 * mockup's `.fullonly`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        // Top inset only: the default also padded the bottom, doubling our own
        // navigationBarsPadding into a white bar under the list. The status bar
        // must be handled HERE and not via Modifier.statusBarsPadding — a
        // displaced surface breaks the sheet's internal offset math, which
        // shows up as a white band at the bottom while dragging.
        contentWindowInsets = { WindowInsets.statusBars },
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            // No bottom padding here: it would end the scroll viewport 48dp
            // above the sheet's edge — a dead white strip under every list.
            // Lists bring their own inset via [sheetListPadding]; static
            // endings pad themselves.
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .imePadding(),
        ) { content() }
    }
}

/**
 * Bottom inset for the list that ends a sheet: rows scroll under the gesture
 * area to the sheet's edge, the last one rests above it.
 */
@Composable
fun sheetListPadding(): PaddingValues =
    WindowInsets.navigationBars.add(WindowInsets(bottom = 24.dp)).asPaddingValues()
