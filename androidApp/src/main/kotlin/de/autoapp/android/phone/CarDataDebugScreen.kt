package de.autoapp.android.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.autoapp.android.R
import de.autoapp.android.phone.components.Fineprint
import de.autoapp.shared.currentTimeMillis
import de.autoapp.shared.domain.CarDataKind
import de.autoapp.shared.domain.CarDataPoint
import de.autoapp.shared.domain.CarDataStatus

/**
 * Everything the car hardware last delivered, one row per data point —
 * recorded by [de.autoapp.android.car.CarHardwareDebugRecorder] during an
 * Android Auto session, readable here at the desk afterwards.
 *
 * Rows without a recording say so instead of hiding: seeing "noch nie
 * empfangen" per data point is the entire purpose of a debug view.
 */
@Composable
fun CarDataDebugScreen(
    points: List<CarDataPoint>,
    modifier: Modifier = Modifier,
) {
    val byKind = points.associateBy { it.kind }

    Column(modifier = modifier.fillMaxSize()) {
        Fineprint(
            text = stringResource(R.string.cardata_intro),
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn {
            items(CarDataKind.entries, key = { it.name }) { kind ->
                val point = byKind[kind]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(kind.label(), style = MaterialTheme.typography.titleSmall)
                        Text(
                            point?.ageText() ?: stringResource(R.string.cardata_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        point?.displayValue() ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (point?.status == CarDataStatus.AVAILABLE) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CarDataKind.label(): String = stringResource(
    when (this) {
        CarDataKind.MODEL -> R.string.cardata_kind_model
        CarDataKind.ENERGY_PROFILE -> R.string.cardata_kind_energy_profile
        CarDataKind.BATTERY_PERCENT -> R.string.cardata_kind_battery
        CarDataKind.RANGE -> R.string.cardata_kind_range
        CarDataKind.ENERGY_IS_LOW -> R.string.cardata_kind_energy_low
        CarDataKind.SPEED -> R.string.cardata_kind_speed
        CarDataKind.ODOMETER -> R.string.cardata_kind_odometer
    },
)

@Composable
private fun CarDataPoint.displayValue(): String = when (status) {
    CarDataStatus.AVAILABLE -> value ?: "—"
    CarDataStatus.NO_PERMISSION -> stringResource(R.string.cardata_no_permission)
    CarDataStatus.NO_DATA -> stringResource(R.string.cardata_no_data)
    CarDataStatus.NO_CAR_HARDWARE -> stringResource(R.string.cardata_no_hardware)
}

@Composable
private fun CarDataPoint.ageText(): String {
    val minutes = ((currentTimeMillis() - observedAtMillis) / 60_000L).coerceAtLeast(0)
    val text = when {
        minutes < 60 -> stringResource(R.string.phone_duration_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.phone_duration_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.phone_duration_days, (minutes / (60 * 24)).toInt())
    }
    return stringResource(R.string.cardata_age, text)
}
