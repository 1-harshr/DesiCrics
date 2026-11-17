DesiCrics is an Android app and Ktor server for scoring and following amateur cricket. Product, design and
roadmap docs are in [/docs](./docs).

* [/app/androidApp](./app/androidApp) is the Android entry point.
* [/app/shared](./app/shared/src) holds the app's screens, ViewModels and data layer (Compose Multiplatform,
  Android target only).
* [/core](./core/src) is shared between the app and the server: API types and the scoring rules.
* [/server](./server/src/main/kotlin) is the Ktor server.

iOS is not built for now. [/app/iosApp](./app/iosApp) is kept in the repo but no Gradle module targets iOS;
the iOS sources removed from `app/shared` can be restored from git history.

### Architecture

- **Auth:** Firebase Authentication (Google, email/password, phone OTP).
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
3. If your Firebase project isn't `desicrics-d5fbe`, run the server with `FIREBASE_PROJECT_ID=<your-project>`.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Server: `./gradlew :server:run` (start this first; the app expects it on port 8080). Android emulators reach
  it at `http://10.0.2.2:8080`. For a physical device, build with `-Pdesicrics.apiBaseUrl=http://<your-LAN-IP>:8080`.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- Core tests: `./gradlew :core:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…