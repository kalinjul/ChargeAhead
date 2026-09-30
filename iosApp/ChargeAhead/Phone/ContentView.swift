import SwiftUI
import Shared

/// Phone UI: a read-only overview. Actual interaction happens in CarPlay.
struct ContentView: View {

    var body: some View {
        List {
            Section {
                LabeledContent(
                    NSLocalizedString("phone_platform_label", comment: ""),
                    value: Platform_iosKt.platformName()
                )
                Text(NSLocalizedString("phone_hint_car_ui", comment: ""))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(NSLocalizedString("app_name", comment: ""))
    }
}
