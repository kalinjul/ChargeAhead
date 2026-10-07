import SwiftUI
import Shared

/// The garage: the car planning uses, adding one from the catalog, and the two battery levels.
struct GarageView: View {
    @StateObject private var owner: ViewModelOwner

    init(feature: ChargeStopsFeature) {
        _owner = StateObject(wrappedValue: ViewModelOwner(feature: feature))
    }

    var body: some View {
        let garage = owner.viewModels.garage()
        NavigationStack {
            Observing(garage.uiState) { state in
                List {
                    if state.loading {
                        ProgressView()
                    } else {
                        Section {
                            if state.vehicles.isEmpty {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(localized("garage_empty_title")).font(.headline)
                                    Text(localized("garage_empty_text"))
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            ForEach(state.vehicles, id: \.id) { vehicle in
                                Button {
                                    garage.onVehicleSelected(profile: vehicle)
                                } label: {
                                    VehicleRow(
                                        vehicle: vehicle,
                                        rangeKm: state.fullRangeKm[vehicle.id]?.doubleValue,
                                        selected: vehicle.id == state.selected?.id
                                    )
                                }
                                .foregroundStyle(.primary)
                                .accessibilityIdentifier("vehicle")
                                .accessibilityValue(vehicle.id == state.selected?.id ? "selected" : "")
                            }
                            NavigationLink {
                                AddCarView(addCar: owner.viewModels.addCar())
                            } label: {
                                Label(localized("garage_add_title"), systemImage: "plus")
                            }
                            .accessibilityIdentifier("garage-add")
                        }
                        Section {
                            LevelRow(
                                icon: "battery.25",
                                title: localized("garage_soc_title"),
                                hint: localized("garage_soc_row_hint"),
                                percent: state.socPercent
                            ) { garage.onSocSheetOpened() }
                            .accessibilityIdentifier("level-soc")
                            LevelRow(
                                icon: "flag",
                                title: localized("garage_arrival_title"),
                                hint: localized("garage_arrival_row_hint"),
                                percent: state.arrivalSocPercent
                            ) { garage.onArrivalSheetOpened() }
                            .accessibilityIdentifier("level-arrival")
                        }
                    }
                }
                .sheet(isPresented: Binding(
                    get: { state.socSheet != nil },
                    set: { shown in if !shown { garage.onSocSheetDismissed() } }
                )) {
                    LevelSheet(
                        title: localized("garage_soc_title"),
                        hint: localized("garage_soc_sheet_hint"),
                        percent: state.socSheet?.intValue ?? 0,
                        range: TripPlanKt.SOC_RANGE,
                        onChange: { garage.onSocSheetChanged(percent: Int32($0)) },
                        onApply: { garage.onSocSheetConfirmed() }
                    )
                }
                .sheet(isPresented: Binding(
                    get: { state.arrivalSheet != nil },
                    set: { shown in if !shown { garage.onArrivalSheetDismissed() } }
                )) {
                    LevelSheet(
                        title: localized("garage_arrival_title"),
                        hint: localized("garage_arrival_sheet_hint"),
                        percent: state.arrivalSheet?.intValue ?? 0,
                        range: GarageViewModelKt.ARRIVAL_SOC_RANGE,
                        onChange: { garage.onArrivalSheetChanged(percent: Int32($0)) },
                        onApply: { garage.onArrivalSheetConfirmed() }
                    )
                }
            }
            .navigationTitle(localized("garage_title"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct VehicleRow: View {
    let vehicle: VehicleProfile
    let rangeKm: Double?
    let selected: Bool

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(vehicle.displayName).font(.headline)
                Text(specs).font(.footnote).foregroundStyle(.secondary)
            }
            Spacer()
            if selected {
                Image(systemName: "checkmark").foregroundStyle(.tint)
            }
        }
    }

    private var specs: String {
        var parts = [
            localized("garage_kwh", oneDecimal(vehicle.usableBatteryKwh)),
            localized("garage_kwh_per_100", oneDecimal(vehicle.consumptionKwhPer100Km)),
        ]
        if let range = rangeKm {
            parts.insert("\(localized("garage_range_number", Int32(range.rounded()))) \(localized("garage_range_unit"))", at: 0)
        }
        return parts.joined(separator: " · ")
    }
}

private struct LevelRow: View {
    let icon: String
    let title: String
    let hint: String
    let percent: Double
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Label {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                        Text(hint).font(.footnote).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: icon)
                }
                Spacer()
                Text(localized("garage_percent", Int32(percent.rounded())))
                    .foregroundStyle(.tint)
            }
        }
        .foregroundStyle(.primary)
    }
}

/// A level picked with a slider, applied only on confirm, like the Android sheet.
private struct LevelSheet: View {
    let title: String
    let hint: String
    let percent: Int
    let range: KotlinIntRange
    let onChange: (Int) -> Void
    let onApply: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.title2.bold())
            Text(hint).foregroundStyle(.secondary)
            Text(localized("garage_percent", Int32(percent)))
                .font(.system(size: 56, weight: .semibold, design: .rounded))
            Slider(
                value: Binding(get: { Double(percent) }, set: { onChange(Int($0.rounded())) }),
                in: Double(range.first)...Double(range.last),
                step: 1
            )
            Button(action: onApply) {
                Text(localized("soc_dialog_apply")).frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
        }
        .padding(24)
        .presentationDetents([.medium])
    }
}

/// The catalog minus what is already in the garage, searchable; a tap adds the car and selects it.
struct AddCarView: View {
    let addCar: AddCarViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    var body: some View {
        Observing(addCar.uiState) { state in
            List(state.matches, id: \.id) { preset in
                Button {
                    addCar.onPresetAdded(preset: preset)
                    dismiss()
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(preset.name).font(.headline)
                        Text(localized(
                            "garage_preset_line",
                            oneDecimal(preset.usableBatteryKwh),
                            oneDecimal(preset.consumptionKwhPer100Km),
                            Int32(preset.dcPeakPowerKw.rounded())
                        ))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    }
                }
                .foregroundStyle(.primary)
                .accessibilityIdentifier("preset")
            }
        }
        .searchable(text: $query, prompt: localized("garage_search"))
        .onChange(of: query) { _, text in addCar.onQueryChanged(query: text) }
        .navigationTitle(localized("garage_add_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// "52.8" or "52,8", following the device's locale like the Android garage.
private func oneDecimal(_ value: Double) -> String {
    value.formatted(.number.precision(.fractionLength(0...1)))
}
