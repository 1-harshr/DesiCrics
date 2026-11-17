This is a Kotlin Multiplatform project targeting Android, iOS, Server.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/shared](./app/shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./app/shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./app/shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./app/shared/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

### Architecture

- **Auth:** Firebase Authentication (Google, Apple on iOS, email/password, phone OTP).
- **Backend:** the Ktor server in [/server](./server). The app calls its REST API with the user's Firebase ID
  token, which the server verifies against Google's public keys. The routes are listed in
  [core/.../api/Api.kt](./core/src/commonMain/kotlin/com/harsh/desicrics/api/Api.kt).
- **Database:** H2 running inside the server process (PostgreSQL mode), accessed through Exposed. Data is
  stored in `server/data/`. Routes and services only use the `UserStore` / `MatchStore` interfaces.
- **Scoring:** `ScoringEngine` and the ball rules in `:core`. The server uses them to validate every ball and
  compute the score; the app uses them to draw the scorecard.

#### Swapping the database

Any SQL database Exposed supports needs only environment variables, no code changes:

```
DATABASE_URL=jdbc:postgresql://<host>:5432/desicrics DATABASE_USER=... DATABASE_PASSWORD=... ./gradlew :server:run
```

Something that isn't SQL needs new implementations of `UserStore` and `MatchStore` in
[server/.../store](./server/src/main/kotlin/com/harsh/desicrics/store). Tables are created on startup; add
versioned migrations (e.g. Flyway) before relying on a production database.

### Firebase setup

Firebase config files are gitignored (this is a public repo), so each developer adds their own.

1. In the [Firebase console](https://console.firebase.google.com), enable the Google, Email/Password and Phone
   sign-in providers. For phone sign-in, also allow your countries under Authentication → Settings →
   SMS region policy, and add test numbers. Real SMS needs the Blaze plan.
2. **Android:** register package `com.harsh.desicrics`, add your debug SHA-1 and SHA-256
   (`./gradlew :app:androidApp:signingReport`), and put `google-services.json` in `app/androidApp/`.
3. **iOS:** register the bundle ID from `app/iosApp/Configuration/Config.xcconfig`, then in Xcode:
   - Put `GoogleService-Info.plist` in `app/iosApp/iosApp/`.
   - Add Swift packages `https://github.com/firebase/firebase-ios-sdk` (FirebaseAuth, FirebaseStorage)
     and `https://github.com/google/GoogleSignIn-iOS` (GoogleSignIn).
   - Add a URL type whose scheme is `REVERSED_CLIENT_ID` from `GoogleService-Info.plist`.
   - Add the **Sign in with Apple** capability.
4. If your Firebase project isn't `desicrics-d5fbe`, run the server with `FIREBASE_PROJECT_ID=<your-project>`.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Server: `./gradlew :server:run` (start this first; the app expects it on port 8080). Android emulators reach
  it at `http://10.0.2.2:8080`. For a physical device, build with `-Pdesicrics.apiBaseUrl=http://<your-LAN-IP>:8080`.
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- iOS tests: `./gradlew :app:shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…