package org.julakali.chargeahead.uitests

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.julakali.chargeahead.android.phone.AddCarScreen
import org.julakali.chargeahead.android.phone.components.ChargeLevelKind
import org.julakali.chargeahead.android.phone.components.ChargeLevelSheetContent
import org.jetbrains.compose.resources.stringResource
import org.julakali.chargeahead.android.phone.CarDataScreen
import org.julakali.chargeahead.android.phone.GarageScreen
import org.julakali.chargeahead.android.phone.VehicleEditActions
import org.julakali.chargeahead.android.phone.VehicleEditScreen
import org.julakali.chargeahead.android.phone.FieldEditor
import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.VehiclePreset
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.shared.ui.AddCarUiState
import org.julakali.chargeahead.shared.ui.CarDataUiState
import org.julakali.chargeahead.shared.ui.GarageUiState
import org.julakali.chargeahead.shared.ui.VehicleEditUiState
import org.julakali.chargeahead.shared.ui.VehicleEditor
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.garage_arrival_sheet_hint
import org.julakali.chargeahead.shared.resources.garage_arrival_title
import org.julakali.chargeahead.shared.resources.garage_soc_sheet_hint
import org.julakali.chargeahead.shared.resources.garage_soc_title
import org.julakali.chargeahead.shared.resources.soc_dialog_car_silent
import org.julakali.chargeahead.shared.resources.soc_dialog_title
import org.julakali.chargeahead.shared.resources.trip_soc_confirm

private val Id3 = VehicleProfile("VW ID.3 Pro S", 77.0, 16.5, setOf(ConnectorType.CCS2, ConnectorType.TYPE2), dcPeakPowerKw = 175.0, modelId = "id3", id = "a")
private val Model3 = VehicleProfile("Tesla Model 3 LR", 75.0, 15.5, setOf(ConnectorType.CCS2, ConnectorType.TYPE2), dcPeakPowerKw = 250.0, modelId = "m3", id = "b")

/** A page's ground, as the app's pages draw it. */
@Composable
private fun PageFrame(content: @Composable () -> Unit) {
    PreviewScaffold {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 8.dp)) { content() }
    }
}

/** A sheet's content on the sheet's own surface; the sheet window itself does not render in previews. */
@Composable
private fun SheetFrame(content: @Composable () -> Unit) {
    PreviewScaffold {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(top = 24.dp)) { content() }
    }
}

@Composable
private fun Garage(state: GarageUiState) = PageFrame {
    GarageScreen(
        uiState = state,
        onSelect = {}, onOpenVehicle = {}, onOpenAdd = {},
        onArrivalSheetOpen = {}, onArrivalChange = {}, onArrivalConfirm = {}, onArrivalDismiss = {},
        onSocSheetOpen = {}, onSocChange = {}, onSocConfirm = {}, onSocDismiss = {},
    )
}

private val oneCar = GarageUiState(vehicles = listOf(Id3), selected = Id3, arrivalSocPercent = 10.0, fullRangeKm = mapOf(Id3.id to 467.0, Model3.id to 484.0))
private val twoCars = oneCar.copy(vehicles = listOf(Id3, Model3))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun GarageOneCar() = Garage(oneCar)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun GarageTwoCars() = Garage(twoCars)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GarageTwoCarsDark() = Garage(twoCars)

private val Ioniq5 = VehicleProfile("Hyundai Ioniq 5", 74.0, 17.9, setOf(ConnectorType.CCS2, ConnectorType.TYPE2), dcPeakPowerKw = 230.0, id = "c")

/** In the middle of three: the one swiped past is gone to the left, the one to come lies in the stack. */
@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun GarageMiddleOfThree() = Garage(
    twoCars.copy(vehicles = listOf(Id3, Model3, Ioniq5), selected = Model3, fullRangeKm = mapOf(Id3.id to 467.0, Model3.id to 484.0, Ioniq5.id to 413.0)),
)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun GarageLoading() = Garage(GarageUiState(loading = true))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun GarageEmpty() = Garage(GarageUiState(arrivalSocPercent = 10.0))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GarageEmptyDark() = Garage(GarageUiState(arrivalSocPercent = 10.0))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411)
@Composable
fun GarageArrivalSheet() = SheetFrame { ArrivalSheet() }

@PreviewTest
@Preview(locale = "en", showBackground = true, widthDp = 411)
@Composable
fun GarageArrivalSheetEnglish() = SheetFrame { ArrivalSheet() }

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun GarageArrivalSheetDark() = SheetFrame { ArrivalSheet() }

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411)
@Composable
fun GarageBatteryLevelSheet() = SheetFrame {
    ChargeLevelSheetContent(
        title = stringResource(Res.string.garage_soc_title),
        subtitle = stringResource(Res.string.garage_soc_sheet_hint),
        kind = ChargeLevelKind.NOW,
        percent = 6,
        onChange = {},
        onConfirm = {},
    )
}

@Composable
private fun ArrivalSheet() = ChargeLevelSheetContent(
    title = stringResource(Res.string.garage_arrival_title),
    subtitle = stringResource(Res.string.garage_arrival_sheet_hint),
    kind = ChargeLevelKind.ARRIVAL,
    percent = 20,
    onChange = {},
    onConfirm = {},
)

/** The trip's start level when the car reports none and nothing is stored: a dash, nothing to apply yet. */
@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411)
@Composable
fun TripStartLevelSheetEmpty() = SheetFrame {
    ChargeLevelSheetContent(
        title = stringResource(Res.string.soc_dialog_title),
        subtitle = stringResource(Res.string.soc_dialog_car_silent),
        kind = ChargeLevelKind.NOW,
        percent = null,
        onChange = {},
        onConfirm = {},
        confirmLabel = stringResource(Res.string.trip_soc_confirm),
    )
}

@Composable
private fun Vehicle(state: VehicleEditUiState) = PageFrame { VehicleEditScreen(uiState = state, actions = VehicleEditActions()) }

private val CatalogId3 = VehiclePreset("id3", "VW ID.3 Pro S (2023)", 77.0, 15.9, 175.0, setOf(ConnectorType.CCS2, ConnectorType.TYPE2))
private val vehiclePage = VehicleEditUiState(vehicle = Id3, catalog = CatalogId3)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun VehiclePage() = Vehicle(vehiclePage)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun VehiclePageDark() = Vehicle(vehiclePage)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun VehiclePageOwnCar() = Vehicle(VehicleEditUiState(vehicle = Id3.copy(modelId = null, displayName = "Fiat 500e")))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun VehiclePageCustomized() = Vehicle(vehiclePage.copy(vehicle = Id3.copy(usableBatteryKwh = 72.0, customized = true), canRestoreCatalogValues = true))

/** The dialog's field as it sits in the dialog; the dialog window itself does not render in previews. */
@Composable
private fun Field(label: String, input: String, unit: String?, applicable: Boolean, catalog: String? = null) = PreviewScaffold {
    Box(Modifier.padding(24.dp)) { FieldEditor(label = label, initial = input, unit = unit, applicable = applicable, onValueChange = {}, catalogValue = catalog) }
}

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 320)
@Composable
fun VehicleBatteryFieldInvalid() = Field("Akku nutzbar", "77,,", "kWh", applicable = false)

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 320)
@Composable
fun VehicleBatteryFieldCatalog() = Field("Akku nutzbar", "70", "kWh", applicable = true, catalog = "77 kWh")

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun VehicleConsumptionFieldDark() = Field("Verbrauch", "16,5", "kWh/100 km", applicable = true, catalog = "15,9 kWh/100 km")

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 320, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun VehicleNameFieldDark() = Field("Name", "VW ID.3 Pro S", null, applicable = true)

private val Ioniq = VehiclePreset("ioniq5", "Hyundai Ioniq 5 77 kWh", 74.0, 17.9, 230.0, setOf(ConnectorType.CCS2, ConnectorType.TYPE2))

@Composable
private fun AddCar(state: AddCarUiState) = PageFrame { AddCarScreen(uiState = state, onSearchChange = {}, onAdd = {}, onCreateCustom = {}) }

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 480)
@Composable
fun AddCarWithHits() = AddCar(AddCarUiState(query = "Hyun", matches = listOf(Ioniq)))

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun AddCarNoHitsDark() = AddCar(AddCarUiState(query = "Fiat 500e"))

@Composable
private fun CarData() = PageFrame {
    // No recorded points: their age is measured from now, which no golden can hold still.
    CarDataScreen(CarDataUiState())
}

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640)
@Composable
fun CarDataNeverReceived() = CarData()

@PreviewTest
@Preview(locale = "de", showBackground = true, widthDp = 411, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CarDataNeverReceivedDark() = CarData()
