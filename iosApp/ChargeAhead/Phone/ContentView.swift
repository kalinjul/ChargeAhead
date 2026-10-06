import SwiftUI
import Shared

/// Phone UI: a read-only overview. Actual interaction happens in CarPlay.
struct ContentView: View {

    var body: some View {
        List {
            Section {
                LabeledContent(
                    localized("phone_platform_label"),
                    value: Platform_iosKt.platformName()
                )
                Text(localized("ios_hint_car_ui"))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(localized("app_name"))
    }
}
