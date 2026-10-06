package org.julakali.chargeahead.android.phone

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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.julakali.chargeahead.android.phone.components.Fineprint
import org.julakali.chargeahead.shared.currentTimeMillis
import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.ui.CarDataUiState
import org.julakali.chargeahead.shared.ui.CarDataViewModel
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.cardata_age
import org.julakali.chargeahead.shared.resources.cardata_intro
import org.julakali.chargeahead.shared.resources.cardata_kind_battery
import org.julakali.chargeahead.shared.resources.cardata_kind_energy_low
import org.julakali.chargeahead.shared.resources.cardata_kind_energy_profile
import org.julakali.chargeahead.shared.resources.cardata_kind_model
import org.julakali.chargeahead.shared.resources.cardata_kind_odometer
import org.julakali.chargeahead.shared.resources.cardata_kind_range
import org.julakali.chargeahead.shared.resources.cardata_kind_speed
import org.julakali.chargeahead.shared.resources.cardata_never
import org.julakali.chargeahead.shared.resources.cardata_no_data
import org.julakali.chargeahead.shared.resources.cardata_no_hardware
import org.julakali.chargeahead.shared.resources.cardata_no_permission
import org.julakali.chargeahead.shared.resources.garage_percent
import org.julakali.chargeahead.shared.resources.phone_field_soc
import org.julakali.chargeahead.shared.resources.phone_soc_source_car
import org.julakali.chargeahead.shared.resources.phone_soc_source_stored
import org.julakali.chargeahead.shared.resources.value_unknown

/**
 * Everything the car hardware last delivered, one row per data point, as
 * recorded by [org.julakali.chargeahead.android.car.CarHardwareDebugRecorder].
 */
@Composable
fun CarDataDebugRoute(
    modifier: Modifier = Modifier,
    viewModel: CarDataViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CarDataDebugScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun CarDataDebugScreen(
    uiState: CarDataUiState,
    modifier: Modifier = Modifier,
) {
    val byKind = uiState.points.associateBy { it.kind }

    Column(modifier = modifier.fillMaxSize()) {
        Fineprint(
            text = stringResource(Res.string.cardata_intro),
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn {
            item(key = "soc") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.phone_field_soc), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            uiState.socPercent?.let { stringResource(Res.string.garage_percent, it.roundToInt()) } ?: stringResource(Res.string.value_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        stringResource(if (uiState.socFromCar) Res.string.phone_soc_source_car else Res.string.phone_soc_source_stored),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CarHardwareStatus(diagnostics = uiState.socDiagnostics, modifier = Modifier.padding(top = 16.dp))
                }
                HorizontalDivider()
            }
            items(CarDataKind.entries, key = { it.name }) { kind ->
                val point = byKind[kind]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(kind.label(), style = MaterialTheme.typography.titleSmall)
                        Text(
                            point?.ageText() ?: stringResource(Res.string.cardata_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        point?.displayValue() ?: stringResource(Res.string.value_unknown),
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
        CarDataKind.MODEL -> Res.string.cardata_kind_model
        CarDataKind.ENERGY_PROFILE -> Res.string.cardata_kind_energy_profile
        CarDataKind.BATTERY_PERCENT -> Res.string.cardata_kind_battery
        CarDataKind.RANGE -> Res.string.cardata_kind_range
        CarDataKind.ENERGY_IS_LOW -> Res.string.cardata_kind_energy_low
        CarDataKind.SPEED -> Res.string.cardata_kind_speed
        CarDataKind.ODOMETER -> Res.string.cardata_kind_odometer
    },
)

@Composable
private fun CarDataPoint.displayValue(): String = when (status) {
    CarDataStatus.AVAILABLE -> value ?: stringResource(Res.string.value_unknown)
    CarDataStatus.NO_PERMISSION -> stringResource(Res.string.cardata_no_permission)
    CarDataStatus.NO_DATA -> stringResource(Res.string.cardata_no_data)
    CarDataStatus.NO_CAR_HARDWARE -> stringResource(Res.string.cardata_no_hardware)
}

@Composable
private fun CarDataPoint.ageText(): String {
    val minutes = ((currentTimeMillis() - observedAtMillis) / 60_000L).coerceAtLeast(0)
    val (plural, count) = coarseDuration(minutes)
    val text = pluralStringResource(plural, count, count)
    return stringResource(Res.string.cardata_age, text)
}
