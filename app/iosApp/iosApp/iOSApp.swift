import FirebaseCore
import GoogleSignIn
import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        FirebaseApp.configure()
        MainViewControllerKt.startKoinIos(nativeSignIn: SocialSignInBridge())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Google sign-in returns to the app through its reversed-client-ID URL scheme.
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
        }
    }
}
