import UIKit
import SwiftUI

/// Scene delegate for the phone UI. Hosts `ContentView` in a
/// `UIHostingController`, since the project doesn't use the SwiftUI app
/// lifecycle (`@main struct ... : App`), but is wired up the classic way via
/// `AppDelegate` + `UISceneDelegate` — this keeps scene selection visible in
/// one place, `AppDelegate.swift`.
class PhoneSceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }

        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = UIHostingController(rootView: HomeMapView())
        self.window = window
        window.makeKeyAndVisible()
    }
}
