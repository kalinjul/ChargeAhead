import SwiftUI
import Shared

/// Phone UI, analogous to the Compose view in androidApp: a read-only
/// overview. Actual interaction happens in CarPlay, see `phone_hint_car_ui`
/// in Localizable.strings.
struct ContentView: View {

    @StateObject private var viewModel = ChargeStopsViewModel()

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent(
                        NSLocalizedString("phone_platform_label", comment: ""),
                        value: Platform_iosKt.platformName()
                    )
                    Text(NSLocalizedString("phone_hint_car_ui", comment: ""))
                        .font(.footnote)
                        .foregroundStyle(.secondary)

                    if viewModel.state.isDemo {
                        // Presenting made-up charging stations as real ones
                        // would be not just sloppy but dangerous in an app
                        // meant for the car.
                        Text(NSLocalizedString("phone_demo_notice", comment: ""))
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }

                Section(NSLocalizedString("phone_stops_heading", comment: "")) {
                    if viewModel.stops.isEmpty {
                        Text(viewModel.statusText)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                    ForEach(viewModel.stops, id: \.site.id) { stop in
                        VStack(alignment: .leading, spacing: 4) {
                            Text([stop.site.name, stop.site.operator_]
                                .compactMap { $0 }
                                .joined(separator: " · "))
                                .font(.headline)
                            Text(ChargeStopFormatter.shared.primaryLine(stop: stop))
                                .font(.subheadline)
                            Text(ChargeStopFormatter.shared.secondaryLine(stop: stop))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle(NSLocalizedString("app_name", comment: ""))
            .toolbar {
                Button(NSLocalizedString("phone_action_refresh", comment: "")) {
                    viewModel.refresh()
                }
            }
        }
    }
}
