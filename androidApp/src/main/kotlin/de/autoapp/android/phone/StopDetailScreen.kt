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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.KeyValueGrid
import de.autoapp.android.phone.components.NetworkDot
import de.autoapp.shared.core.MapsHandoff
import de.autoapp.shared.core.PlannedStop
import de.autoapp.shared.domain.ConnectorType
import kotlin.math.roundToInt

/** One planned stop in detail: what the site offers and what the plan expects here. */
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
        // The mockup's det-head, with the network as the headline: the site
        // name is either the town again ("Kiel"), which the address line
        // below already carries, or an operator's internal id
        // ("DE*CNT*EP00214*001"), so it isn't shown at all.
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NetworkDot(operatorColor(stop.site.operator))
                Text(
                    stop.site.operator ?: stop.site.name,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
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
