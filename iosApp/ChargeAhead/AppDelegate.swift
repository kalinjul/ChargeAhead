import UIKit
import CarPlay
import Shared

/// Selects the scene configuration for the connecting session's role: phone
/// UI (SwiftUI) or CarPlay template UI. Both are declared in Info.plist.
@main
class AppDelegate: UIResponder, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        SharedEntry.start()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        switch connectingSceneSession.role {
        case .carTemplateApplication:
            let configuration = UISceneConfiguration(
                name: "CarPlay Configuration",
                sessionRole: connectingSceneSession.role
            )
            configuration.delegateClass = CarPlaySceneDelegate.self
            return configuration

        default:
            let configuration = UISceneConfiguration(
                name: "Phone Configuration",
                sessionRole: connectingSceneSession.role
            )
            configuration.delegateClass = PhoneSceneDelegate.self
            return configuration
        }
    }
}

/// The shared entry points both scenes build on.
enum SharedEntry {

    /// Builds the shared graph once, before any scene connects.
    static func start() {
        IosEntryPointsKt.startChargeAhead(
            backendBaseUrl: bundleValue("ChargeAheadBaseUrl"),
            backendToken: bundleValue("ChargeAheadToken")
        )
    }

    /// A feature the caller owns and closes.
    static func newFeature() -> ChargeStopsFeature {
        IosEntryPointsKt.createChargeStopsFeature()
    }

    /// Injected into Info.plist via a build setting; the shared module stops
    /// the app when the backend is not configured.
    private static func bundleValue(_ key: String) -> String? {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return nil
        }
        // A trailing space in the .xcconfig would end up in the request.
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
