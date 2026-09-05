package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.domain.NetworkPreferences
import de.autoapp.shared.domain.OperatorKey
import de.autoapp.shared.domain.OperatorOption

/**
 * Selecting charging networks.
 *
 * The list comes from the charging sites in the current surroundings, not
 * from a maintained enumeration. That has two advantages: it's never
 * incomplete, and it only shows what actually occurs here — a selection of
 * a hundred providers, two of which are nearby, would help no one.
 *
 * Still, it quickly grows to dozens of entries, hence the search. "All" and
 * "None" act on the **displayed** list: with "ionity" in the search field,
 * one tap selects every spelling variant of that network without touching
 * the rest of the selection. That's the reason the search is more than just
 * a reading aid.
 */
@Composable
fun NetworkSettingsScreen(
    preferences: NetworkPreferences,
    available: List<OperatorOption>,
    onChange: (NetworkPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }

    val shown = remember(available, search) {
        val needle = OperatorKey.folded(search.trim())
        if (needle.isEmpty()) {
            available
        } else {
            available.filter { OperatorKey.folded(it.displayName).contains(needle) }
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = stringResource(R.string.phone_networks_intro),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Switch(
                checked = preferences.onlyPreferred,
                onCheckedChange = { onChange(preferences.copy(onlyPreferred = it)) },
            )
            Text(
                text = stringResource(R.string.phone_networks_only),
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.phone_networks_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.phone_networks_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            OutlinedButton(
                enabled = shown.isNotEmpty(),
                onClick = {
                    val updated = preferences.preferredOperators + shown.map { it.key }
                    onChange(preferences.copy(preferredOperators = updated))
                },
            ) {
                Text(stringResource(R.string.phone_networks_all))
            }
            OutlinedButton(
                enabled = shown.isNotEmpty(),
                onClick = {
                    val updated = preferences.preferredOperators - shown.map { it.key }.toSet()
                    onChange(preferences.copy(preferredOperators = updated))
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.phone_networks_none))
            }
        }

        // The hint only appears while searching: without search text,
        // "displayed" equals "all", where it would just be noise.
        if (search.isNotBlank()) {
            Text(
                text = stringResource(R.string.phone_networks_bulk_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (shown.isEmpty()) {
            Text(
                text = stringResource(R.string.phone_networks_no_match, search.trim()),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            // Key from position: operator names come from the data sources,
            // and two identical keys crash the app mid-composition. This
            // must not depend on external data.
            itemsIndexed(shown, key = { index, _ -> "netz-$index" }) { _, option ->
                val checked = option.key in preferences.preferredOperators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { nowChecked ->
                            val updated = if (nowChecked) {
                                preferences.preferredOperators + option.key
                            } else {
                                preferences.preferredOperators - option.key
                            }
                            onChange(preferences.copy(preferredOperators = updated))
                        },
                    )
                    Column {
                        Text(option.displayName)
                        Text(
                            text = pluralStringResource(
                                R.plurals.phone_networks_count,
                                option.siteCount,
                                option.siteCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
