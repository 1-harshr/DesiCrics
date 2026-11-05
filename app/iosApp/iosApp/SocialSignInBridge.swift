import AuthenticationServices
import CryptoKit
import FirebaseCore
import GoogleSignIn
import Shared
import UIKit

/// Native half of the iOS sign-in flow. Kotlin calls in through `NativeSignInBridge`, we show the
/// Google / Apple UI and hand back raw tokens; Kotlin turns them into Firebase credentials.
final class SocialSignInBridge: NSObject, NativeSignInBridge {

    private var appleCompletion: ((AppleTokens?, String?) -> Void)?
    private var appleNonce: String?

    // MARK: Google

    func signInWithGoogle(completion: @escaping (GoogleTokens?, String?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            completion(nil, "Missing CLIENT_ID in GoogleService-Info.plist")
            return
        }
        guard let presenter = Self.topViewController() else {
            completion(nil, "No screen to present Google sign-in from")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            if let error {
                let cancelled = (error as? GIDSignInError)?.code == .canceled
                completion(nil, cancelled ? nil : error.localizedDescription)
                return
            }
            guard let user = result?.user, let idToken = user.idToken?.tokenString else {
                completion(nil, "Google didn't return an ID token")
                return
            }
            completion(
                GoogleTokens(idToken: idToken, accessToken: user.accessToken.tokenString, displayName: user.profile?.name),
                nil
            )
        }
    }

    // MARK: Apple

    func signInWithApple(completion: @escaping (AppleTokens?, String?) -> Void) {
        let nonce = Self.randomNonce()
        appleNonce = nonce
        appleCompletion = completion

        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        // Apple signs the hashed nonce into the ID token; Firebase checks it against the raw one.
        request.nonce = Self.sha256(nonce)

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = self
        controller.presentationContextProvider = self
        controller.performRequests()
    }

    private func finishApple(_ tokens: AppleTokens?, _ error: String?) {
        appleCompletion?(tokens, error)
        appleCompletion = nil
        appleNonce = nil
    }

    // MARK: Helpers

    private static func topViewController() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        precondition(status == errSecSuccess, "Unable to generate nonce")
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

extension SocialSignInBridge: ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        Self.topViewController()?.view.window ?? ASPresentationAnchor()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let tokenData = credential.identityToken,
            let idToken = String(data: tokenData, encoding: .utf8),
            let nonce = appleNonce
        else {
            finishApple(nil, "Apple didn't return an identity token")
            return
        }
        // Apple only shares the name the first time someone signs in.
        let name = credential.fullName.map { PersonNameComponentsFormatter().string(from: $0) }
        finishApple(AppleTokens(idToken: idToken, rawNonce: nonce, displayName: name?.isEmpty == false ? name : nil), nil)
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        let cancelled = (error as? ASAuthorizationError)?.code == .canceled
        finishApple(nil, cancelled ? nil : error.localizedDescription)
    }
}
