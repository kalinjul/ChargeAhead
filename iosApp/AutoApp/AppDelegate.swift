import UIKit
import CarPlay

/// Selects the appropriate scene configuration based on the connecting scene
/// session's role: phone UI (SwiftUI) or CarPlay template UI. Both roles are
/// declared in the `UIApplicationSceneManifest` in Info.plist; this only
/// branches between the two delegates.
class AppDelegate: NSObject, UIApplicationDelegate {

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
