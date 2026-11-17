import FirebaseCore
import GoogleSignIn
import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        // The Ktor server; the simulator shares the Mac's localhost. Use the Mac's LAN address on a device.
        MainViewControllerKt.startKoinIos(nativeSignIn: SocialSignInBridge(), apiBaseUrl: "http://localhost:8080")
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Google sign-in returns to the app through its reversed-client-ID URL scheme.
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}
