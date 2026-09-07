package de.autoapp.android.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import de.autoapp.android.R
import de.autoapp.shared.domain.TariffCatalog
import androidx.compose.ui.unit.dp

/**
 * Which tariffs the driver holds. The active set feeds every price
 * comparison — planning, "charge now", and the stop detail all quote against
 * exactly these plus ad-hoc.
 */
@Composable
fun SubscriptionsScreen(
    activeIds: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val hits = TariffCatalog.all.filter { it.displayName.contains(search.trim(), ignoreCase = true) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.subs_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        Text(
            stringResource(R.string.subs_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (hits.isEmpty()) {
            Text(
                stringResource(R.string.subs_none_found),
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(hits, key = { it.id }) { tariff ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tariff.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            tariff.monthlyFeeEuro
                                ?.let { stringResource(R.string.subs_fee, it.twoDecimals()) }
                                ?: stringResource(R.string.subs_no_fee),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = tariff.id in activeIds,
                        onCheckedChange = { active ->
                            onChange(if (active) activeIds + tariff.id else activeIds - tariff.id)
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

/** "0.49" → "0,49": prices are user-visible text and therefore German. */
internal fun Double.twoDecimals(): String {
    val cents = (this * 100).toInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
