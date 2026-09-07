package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.autoapp.android.R
import de.autoapp.android.phone.components.AppCard
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.android.phone.components.KeyValueGrid
import de.autoapp.android.phone.components.NetworkDot
import de.autoapp.android.phone.components.PriceText
import de.autoapp.android.phone.theme.tabular
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.PriceKind
import kotlin.math.roundToInt

/**
 * One planned stop in detail: what the site offers, what the plan expects
 * here, and what it costs by tariff. Prices are the demo table until a real
 * price source exists; the estimate note stays until then.
 */
@Composable
fun StopDetailScreen(
    stop: PlannedStop,
    onSendToMaps: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The mockup's det-head.
        Column {
            stop.site.operator?.let { operator ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    NetworkDot(operatorColor(operator))
                    Text(
                        operator.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stop.site.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 5.dp))
            stop.site.address?.let { address ->
                val place = listOfNotNull(address.postalCode, address.town).joinToString(" ")
                val line = listOfNotNull(address.street, place.takeIf { it.isNotBlank() }).joinToString(", ")
                if (line.isNotBlank()) {
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        val dcCount = stop.site.connectors
            .filter { it.type == ConnectorType.CCS2 || it.type == ConnectorType.TESLA_NACS }
            .sumOf { it.count ?: 1 }
        KeyValueGrid(
            listOf(
                stringResource(R.string.detail_kv_connectors) to
                    stringResource(R.string.detail_connector_value, dcCount, stop.maxPowerKw.roundToInt()),
                stringResource(R.string.detail_kv_charge) to stringResource(
                    R.string.detail_kv_charge_value,
                    stop.chargeMinutes.roundToInt(),
                    stop.arrivalSocPercent.roundToInt(),
                    stop.departureSocPercent.roundToInt(),
                ),
                stringResource(R.string.detail_kv_arrival) to stringResource(
                    R.string.detail_kv_arrival_value,
                    etaText(stop.etaMinutesFromStart - stop.chargeMinutes),
                    stop.arrivalSocPercent.roundToInt(),
                ),
                stringResource(R.string.detail_kv_energy) to
                    stringResource(R.string.detail_kv_energy_value, stop.chargeKwh.roundToInt()),
            ),
        )

        // The mockup's pricelist: hairline rows, cheapest tagged.
        AppCard {
            stop.quote.prices.forEachIndexed { index, price ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(price.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                when (price.kind) {
                                    PriceKind.AD_HOC -> R.string.detail_price_adhoc
                                    PriceKind.SUBSCRIPTION -> R.string.detail_price_subscription
                                    PriceKind.ROAMING -> R.string.detail_price_roaming
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (price == stop.quote.best) {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                stringResource(R.string.detail_cheapest).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                        PriceText(price.euroPerKwh)
                    } else {
                        Text(
                            "${price.euroPerKwh.twoDecimals()} €",
                            style = MaterialTheme.typography.titleSmall.tabular,
                        )
                    }
                }
            }
        }

        if (stop.quote.isEstimate) Fineprint(stringResource(R.string.trip_estimate_note))

        Button(
            onClick = { onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) },
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.ic_destination), contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.detail_navigate_maps))
        }
    }
}
