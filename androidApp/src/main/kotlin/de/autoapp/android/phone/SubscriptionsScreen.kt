package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.SearchField
import de.autoapp.android.phone.components.TickRow
import de.autoapp.shared.domain.TariffCatalog

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

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchField(
            value = search,
            onValueChange = { search = it },
            placeholder = stringResource(R.string.subs_search),
            modifier = Modifier.padding(top = 14.dp),
        )
        Fineprint(stringResource(R.string.subs_note))
        if (hits.isEmpty()) {
            Text(
                stringResource(R.string.subs_none_found),
            )
        }
        LazyColumn {
            item {
                AppCard {
                    hits.forEachIndexed { index, tariff ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        val checked = tariff.id in activeIds
                        TickRow(
                            label = tariff.displayName,
                            sublabel = tariff.monthlyFeeEuro
                                ?.let { stringResource(R.string.subs_fee, it.twoDecimals()) }
                                ?: stringResource(R.string.subs_no_fee),
                            checked = checked,
                            onClick = {
                                onChange(if (checked) activeIds - tariff.id else activeIds + tariff.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** "0.49" → "0,49": prices are user-visible text and therefore German. */
internal fun Double.twoDecimals(): String {
    val cents = (this * 100).toInt()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
