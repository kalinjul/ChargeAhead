import UIKit
import SwiftUI

/// Scene delegate for the phone UI, hosting `ContentView` in a
/// `UIHostingController`. Scene selection lives in `AppDelegate.swift`.
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
