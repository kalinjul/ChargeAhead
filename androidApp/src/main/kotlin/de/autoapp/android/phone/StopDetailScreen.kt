package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
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
        Text(stop.site.name, style = MaterialTheme.typography.headlineSmall)
        stop.site.operator?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.detail_plan), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.detail_plan_value,
                        stop.chargeMinutes.roundToInt(),
                        stop.arrivalSocPercent.roundToInt(),
                        stop.departureSocPercent.roundToInt(),
                    ),
                )
                stop.quote.best?.let { best ->
                    Text(
                        stringResource(
                            R.string.detail_energy,
                            stop.chargeKwh.roundToInt(),
                            (stop.chargeKwh * best.euroPerKwh).twoDecimals(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text(stringResource(R.string.phone_detail_connectors), style = MaterialTheme.typography.titleSmall)
        stop.site.connectors.forEach { connector ->
            Text(
                "${connector.type.name} · ${connector.maxPowerKw.roundToInt()} kW" +
                    (connector.count?.let { " · ${it}×" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(stringResource(R.string.detail_prices), style = MaterialTheme.typography.titleSmall)
        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stop.quote.prices.forEach { price ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(price.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(
                                    when (price.kind) {
                                        PriceKind.AD_HOC -> R.string.detail_price_adhoc
                                        PriceKind.SUBSCRIPTION -> R.string.detail_price_subscription
                                        PriceKind.ROAMING -> R.string.detail_price_roaming
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            stringResource(R.string.cn_price, price.euroPerKwh.twoDecimals()),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (price == stop.quote.best) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        if (stop.quote.isEstimate) {
            Text(stringResource(R.string.trip_estimate_note), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = { onSendToMaps(MapsHandoff.navigateUrl(stop.site.position)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.phone_detail_navigate))
        }
    }
}
