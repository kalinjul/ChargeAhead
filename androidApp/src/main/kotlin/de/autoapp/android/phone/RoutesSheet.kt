package de.autoapp.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.GoButton
import de.autoapp.android.phone.components.SectionLabel
import de.autoapp.android.phone.components.sheetListPadding
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.SavedRoute
import de.autoapp.shared.ui.RoutesUiState
import de.autoapp.shared.ui.RoutesViewModel

@Composable
fun RoutesRoute(
    onOpen: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = phoneViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoutesSheetContent(
        uiState = uiState,
        onOpen = onOpen,
        onRename = viewModel::onRenamed,
        onDelete = viewModel::onDeleted,
        onFavorite = viewModel::onFavourited,
        modifier = modifier,
    )
}

/** Saved routes on top, recent destinations below with a quick-favorite heart. */
@Composable
fun RoutesSheetContent(
    uiState: RoutesUiState,
    onOpen: (Destination) -> Unit,
    onRename: (SavedRoute, String) -> Unit,
    onDelete: (SavedRoute) -> Unit,
    onFavorite: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = uiState.saved
    var renaming by remember { mutableStateOf<SavedRoute?>(null) }

    renaming?.let { route ->
        RenameDialog(
            route = route,
            onConfirm = { name -> onRename(route, name); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    Column(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = sheetListPadding(),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            item { Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.titleMedium) }
            item { SectionLabel(stringResource(R.string.routes_saved)) }
            if (saved.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            stringResource(R.string.routes_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                        )
                    }
                }
            }
            items(saved, key = { it.id }) { route ->
                AppCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Column(Modifier.weight(1f).clickable { onOpen(route.destination) }) {
                            Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            route.summary?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall.tabular, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        GoButton(painterResource(R.drawable.ic_pen), stringResource(R.string.routes_rename), onClick = { renaming = route })
                        GoButton(
                            painterResource(R.drawable.ic_remove),
                            stringResource(R.string.routes_delete),
                            onClick = { onDelete(route) },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item { SectionLabel(stringResource(R.string.routes_recent), modifier = Modifier.padding(top = 8.dp)) }
            items(uiState.recent, key = { "recent-${it.name}-${it.position.lat}" }) { destination ->
                val alreadySaved = saved.any { it.destination.position == destination.position }
                AppCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Text(
                            destination.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { onOpen(destination) },
                        )
                        GoButton(
                            icon = painterResource(if (alreadySaved) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                            contentDescription = stringResource(R.string.routes_save_recent),
                            onClick = { if (!alreadySaved) onFavorite(destination) },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(route: SavedRoute, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(route.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routes_rename_title)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifEmpty { route.name }) }) {
                Text(stringResource(R.string.routes_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.routes_cancel)) }
        },
    )
}
