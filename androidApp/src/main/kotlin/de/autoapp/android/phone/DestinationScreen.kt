package de.autoapp.android.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.domain.Destination
import de.autoapp.shared.domain.Place
import kotlinx.coroutines.launch

/**
 * Destination entry.
 *
 * The note about the duplicate entry is deliberately placed up top rather
 * than in the fine print: the driver also has to set their destination in
 * the navigation app, because its route isn't readable by third-party apps
 * (ARCHITECTURE.md 1.1). Without knowing that, it looks like a bug.
 */
@Composable
fun DestinationScreen(
    current: Destination?,
    recent: List<Destination>,
    onSearch: suspend (String) -> List<Place>?,
    onSelect: (Destination?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        failed = false
        scope.launch {
            val found = onSearch(query)
            searching = false
            failed = found == null
            results = found
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.phone_destination_intro),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.phone_destination_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (current != null) {
            Text(
                text = stringResource(R.string.phone_destination_current),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(current.name)
            OutlinedButton(
                onClick = { onSelect(null) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.phone_destination_clear))
            }
        } else {
            Text(
                text = stringResource(R.string.phone_destination_none),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.phone_destination_field)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Button(
            onClick = ::runSearch,
            enabled = query.isNotBlank() && !searching,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.phone_destination_search))
        }

        val status = when {
            searching -> stringResource(R.string.phone_destination_searching)
            failed -> stringResource(R.string.phone_destination_failed)
            results?.isEmpty() == true -> stringResource(R.string.phone_destination_no_hits)
            else -> null
        }
        status?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            // Key from position, not from content: duplicate keys crash the
            // app mid-composition, and whether two results share a name is
            // decided by the remote API, not by us. This is exactly what
            // broke the search for "Münster".
            itemsIndexed(results.orEmpty(), key = { index, _ -> "result-$index" }) { _, place ->
                PlaceRow(
                    title = place.name,
                    subtitle = place.description,
                    onClick = { onSelect(Destination(place.name, place.position)) },
                )
            }

            if (recent.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.phone_destination_recent),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                itemsIndexed(recent, key = { index, _ -> "history-$index" }) { _, destination ->
                    PlaceRow(
                        title = destination.name,
                        subtitle = null,
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        subtitle?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}
