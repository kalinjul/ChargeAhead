package org.julakali.chargeahead.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.width
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState
import org.julakali.chargeahead.android.phone.theme.ChargeAheadMotion
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.android.phone.theme.ChargeAheadColors
import org.julakali.chargeahead.android.phone.theme.tabular
import org.julakali.chargeahead.shared.ui.SearchRow
import org.julakali.chargeahead.shared.ui.SearchUiState
import org.julakali.chargeahead.shared.ui.asKmLabel
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.home_search_back
import org.julakali.chargeahead.shared.resources.home_search_clear
import org.julakali.chargeahead.shared.resources.home_search_hint
import org.julakali.chargeahead.shared.resources.home_trip_clear
import org.julakali.chargeahead.shared.resources.plan_no_results
import org.julakali.chargeahead.shared.resources.plan_result_distance
import org.julakali.chargeahead.shared.resources.plan_search_failed

/** The always-present search pill. Focus switches the app into searching. */
@Composable
fun HomeSearchBar(
    query: String,
    searching: Boolean,
    onFocused: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    /** Show the x even with nothing typed, e.g. while a trip sits underneath. */
    clearable: Boolean = false,
    /** Grab focus once composed; the bar may appear only when search mode starts. */
    takeFocus: Boolean = false,
    /** Full width with a back arrow and start-aligned text; collapsed it is a centred pill. */
    expanded: Boolean = false,
    onBack: () -> Unit = {},
    /** Own pill surface, or bare rows inside the home screen's search panel. */
    standalone: Boolean = true,
) {
    LaunchedEffect(takeFocus) {
        if (takeFocus) focusRequester.requestFocus()
    }
    // Read only while placing, so the animation doesn't recompose the text field every frame.
    val startPadding = animateDpAsState(if (expanded) 4.dp else 16.dp, ChargeAheadMotion.spatial(), label = "search bar start")
    // Centred pill ↔ start-aligned field: the text is laid out at the start and slid by an
    // offset that shrinks to zero, so nothing gets re-measured or clipped on the way.
    val centring by animateFloatAsState(if (expanded) 0f else 1f, ChargeAheadMotion.spatial(), label = "search text centring")
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    var contentWidthPx by remember { mutableIntStateOf(0) }

    PillContainer(standalone) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // As tall as Material's field, so the pill matches the buttons beside it.
            modifier = Modifier.defaultMinSize(minHeight = SEARCH_BAR_HEIGHT).startPadding { startPadding.value }.padding(end = 6.dp).fillMaxWidth(),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(ChargeAheadMotion.effects()) + expandHorizontally(ChargeAheadMotion.spatial()),
                exit = fadeOut(ChargeAheadMotion.effects()) + shrinkHorizontally(ChargeAheadMotion.spatial()),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.home_search_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                // BasicTextField's cursor defaults to black, invisible on the dark bar.
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                onTextLayout = { if (query.isNotEmpty()) contentWidthPx = it.size.width },
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { fieldWidthPx = it.width }
                            .offset { IntOffset((((fieldWidthPx - contentWidthPx) / 2f) * centring).roundToInt().coerceAtLeast(0), 0) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(Res.string.home_search_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = ChargeAheadColors.faint,
                                maxLines = 1,
                                onTextLayout = { contentWidthPx = it.size.width },
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 14.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFocused() },
            )
            when {
                searching -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(end = 10.dp).size(18.dp),
                )
                query.isNotEmpty() || (clearable && !expanded) -> IconButton(onClick = onClear) {
                    // Next to Material's arrow the thin drawable looks off; match the glyph.
                    if (expanded) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(Res.string.home_search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_remove),
                            contentDescription = stringResource(Res.string.home_search_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Replaces the bar while a trip is shown. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DestinationHeader(title: String, subtitle: String, onClear: () -> Unit, onTitleClick: () -> Unit, standalone: Boolean = true) {
    PillContainer(standalone) {
        // Material's subtitle app bar is still expressive-only in 1.4.0, so the title slot stacks both lines.
        TopAppBar(
            title = {
                // A clickable surface, so the tap ripples like any other Material button.
                Surface(onClick = onTitleClick, shape = CircleShape, color = Color.Transparent) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // A long trip line wants the whole width on a narrow phone; a step down beats a cut.
                            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 14.sp, stepSize = 0.5.sp),
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onClear) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(Res.string.home_trip_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            // Same height as the search bar it fades into, so the slot doesn't grow mid-fade.
            expandedHeight = SEARCH_BAR_HEIGHT,
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
    }
}

/** Recents or hits under the bar. */
@Composable
fun SearchResultsPanel(
    uiState: SearchUiState,
    onPick: (SearchRow) -> Unit,
    modifier: Modifier = Modifier,
    /** Own card surface, or the list filling the home screen's search panel. */
    standalone: Boolean = true,
) {
    val container: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (standalone) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = modifier.fillMaxWidth(),
            ) { content() }
        } else {
            Box(modifier.fillMaxSize()) { content() }
        }
    }
    container {
        when {
            uiState.failed -> Message(stringResource(Res.string.plan_search_failed), error = true)
            uiState.rows.isEmpty() && !uiState.isQueryTooShort && !uiState.searching ->
                Message(stringResource(Res.string.plan_no_results))
            uiState.rows.isEmpty() -> Unit
            else -> LazyColumn(modifier = if (standalone) Modifier.heightIn(max = 360.dp) else Modifier.fillMaxSize()) {
                items(uiState.rows, key = { "${it.destination.position}${it.title}" }) { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(row) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            painterResource(if (row.recent) R.drawable.ic_refresh else R.drawable.ic_destination),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            row.detail?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        row.distanceKm?.let {
                            Text(
                                stringResource(Res.string.plan_result_distance, it.asKmLabel()),
                                style = MaterialTheme.typography.bodySmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(14.dp),
    )
}

/** The pill's own surface when it stands alone on the map; nothing when it sits inside the search panel. */
@Composable
private fun PillContainer(standalone: Boolean, content: @Composable () -> Unit) {
    if (standalone) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) { content() }
    } else {
        content()
    }
}

/**
 * Material's own pill-to-panel search: the field expands into a docked panel with
 * [results] inside, transition, elevation and back handling included.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDockedSearchBar(
    query: String,
    searching: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    results: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    DockedSearchBar(
        // Our field inside Material's panel: centred placeholder that glides to the start.
        inputField = {
            HomeSearchBar(
                query = query,
                searching = searching,
                onFocused = { onExpandedChange(true) },
                onQueryChange = onQueryChange,
                onClear = onClear,
                focusRequester = focusRequester,
                takeFocus = expanded,
                expanded = expanded,
                onBack = { onExpandedChange(false) },
                standalone = false,
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        // The docked bar defaults to Material's 360dp minimum and sits at the start; fill the slot instead.
        modifier = modifier.fillMaxWidth(),
        // Same lift as the custom pill, so the two compare on equal footing.
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.fillMaxWidth().height(DOCKED_RESULTS_HEIGHT)) { results() }
    }
}

/** Six result rows; the list scrolls for the rest. */
private val DOCKED_RESULTS_HEIGHT = 64.dp * 6

private fun Modifier.startPadding(start: () -> Dp): Modifier = layout { measurable, constraints ->
    val startPx = start().roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = -startPx))
    layout(placeable.width + startPx, placeable.height) { placeable.place(startPx, 0) }
}
