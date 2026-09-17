import UIKit
import CarPlay

/// Selects the scene configuration for the connecting session's role: phone
/// UI (SwiftUI) or CarPlay template UI. Both are declared in Info.plist.
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
