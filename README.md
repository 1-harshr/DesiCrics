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

### Firebase setup

The backend is Firebase Auth (Google / Apple sign-in) plus **Firebase SQL Connect**, a managed PostgreSQL
database. The schema and every query and mutation live in [dataconnect/](./dataconnect). The app calls them
over SQL Connect's REST API from shared Kotlin (`app/shared/.../data/sqlconnect`), since there's no KMP SDK.

Firebase config files are gitignored (this is a public repo), so each developer adds their own.

1. In the [Firebase console](https://console.firebase.google.com), create a project and enable
   **Authentication → Google** and **Authentication → Apple**.
2. Set up SQL Connect and deploy the schema and connector. Keep the service ID, location and connector ID
   in `dataconnect/` in sync with `SqlConnectConfig`:
   ```
   firebase use <your-project-id>
   firebase deploy --only dataconnect
   ```
3. **Android:** add an Android app with package `com.harsh.desicrics`, register your debug SHA-1
   (`./gradlew :app:androidApp:signingReport`), and put `google-services.json` in `app/androidApp/`.
4. **iOS:** add an iOS app with the bundle ID from `app/iosApp/Configuration/Config.xcconfig`, then in Xcode:
   - Put `GoogleService-Info.plist` in `app/iosApp/iosApp/`.
   - Add Swift packages `https://github.com/firebase/firebase-ios-sdk` (FirebaseAuth, FirebaseStorage)
     and `https://github.com/google/GoogleSignIn-iOS` (GoogleSignIn).
   - Add a URL type whose scheme is `REVERSED_CLIENT_ID` from `GoogleService-Info.plist`.
   - Add the **Sign in with Apple** capability.

#### Testing against the local emulator

`SqlConnectEmulatorTest` runs the real repositories against the SQL Connect emulator (no Firebase project needed):

```
firebase emulators:exec --only dataconnect --project demo-desicrics \
  'FDC_EMULATOR_HOST=http://127.0.0.1:9399 ./gradlew :app:shared:testAndroidHostTest'
```

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Server: `./gradlew :server:run`
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- iOS tests: `./gradlew :app:shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…