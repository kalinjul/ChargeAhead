package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
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
    loading: Boolean,
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

    Column(modifier = modifier.padding(horizontal = 18.dp)) {
        Fineprint(
            text = stringResource(R.string.phone_networks_intro),
            modifier = Modifier.padding(top = 16.dp),
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
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        if (available.isEmpty()) {
            if (loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Fineprint(
                        text = stringResource(R.string.phone_networks_loading),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            } else {
                Fineprint(
                    text = stringResource(R.string.phone_networks_empty),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            return@Column
        }

        SearchField(
            value = search,
            onValueChange = { search = it },
            placeholder = stringResource(R.string.phone_networks_search),
            modifier = Modifier.padding(top = 16.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            OutlinedButton(
                enabled = shown.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val updated = preferences.preferredOperators + shown.map { it.key }
                    onChange(preferences.copy(preferredOperators = updated))
                },
            ) {
                Text(stringResource(R.string.phone_networks_all))
            }
            OutlinedButton(
                enabled = shown.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
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
            Fineprint(
                text = stringResource(R.string.phone_networks_bulk_hint),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (shown.isEmpty()) {
            Fineprint(
                text = stringResource(R.string.phone_networks_no_match, search.trim()),
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            item {
                AppCard {
                    // Key from position: operator names come from the data sources,
                    // and two identical keys crash the app mid-composition. This
                    // must not depend on external data.
                    shown.forEachIndexed { index, option ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        val checked = option.key in preferences.preferredOperators
                        TickRow(
                            label = option.displayName,
                            sublabel = pluralStringResource(R.plurals.phone_networks_count, option.siteCount, option.siteCount),
                            checked = checked,
                            dotColor = operatorColor(option.displayName),
                            onClick = {
                                val updated = if (checked) {
                                    preferences.preferredOperators - option.key
                                } else {
                                    preferences.preferredOperators + option.key
                                }
                                onChange(preferences.copy(preferredOperators = updated))
                            },
                        )
                    }
                }
            }
        }
    }
}
