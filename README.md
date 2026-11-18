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

- **Auth:** Firebase Authentication (phone OTP and Google).
- **Backend:** the Ktor server in [/server](./server). The app calls its REST API with the user's Firebase ID
  token, which the server verifies against Google's public keys. The routes are listed in
  [core/.../api/Api.kt](./core/src/commonMain/kotlin/com/harsh/desicrics/api/Api.kt).
- **Database:** Postgres, accessed through Exposed with a HikariCP connection pool. Routes and services only
  use the `UserStore` / `MatchStore` interfaces. Tests run on in-memory H2 in PostgreSQL mode.
- **Scoring:** `ScoringEngine` and the ball rules in `:core`. The server uses them to validate every ball and
  compute the score; the app uses them to draw the scorecard.

#### Database setup

Start the local Postgres (needs Docker Desktop or another Docker runtime running):

```
docker compose up -d
```

The server's defaults point at it, so `./gradlew :server:run` needs no extra settings. Anywhere else, set
`DATABASE_URL` (e.g. `jdbc:postgresql://<host>:5432/desicrics`), `DATABASE_USER`, `DATABASE_PASSWORD` and
optionally `DATABASE_POOL_SIZE` (default 10).

#### Schema changes

Flyway owns the schema. On startup the server applies any pending migrations from
[server/src/main/resources/db/migration](./server/src/main/resources/db/migration). To change the schema:

1. Add a new file `V<next number>__<description>.sql`. Never edit a migration that has already run.
2. Update the matching Exposed table in [db/Database.kt](./server/src/main/kotlin/com/harsh/desicrics/db/Database.kt).
3. Keep the SQL valid on both Postgres and H2, since the tests use H2.

To wipe local data: `docker compose down -v`.

### Firebase setup

Firebase config files are gitignored (this is a public repo), so each developer adds their own.

1. In the [Firebase console](https://console.firebase.google.com), enable the Phone and Google
   sign-in providers. For phone sign-in, also allow your countries under Authentication → Settings →
   SMS region policy, and add test numbers. Real SMS needs the Blaze plan.
2. **Android:** register package `com.harsh.desicrics`, add your debug SHA-1 and SHA-256
   (`./gradlew :app:androidApp:signingReport`), and put `google-services.json` in `app/androidApp/`.
   Debug builds are signed with a project-only key, so their fingerprints don't clash with Google
   sign-in set up elsewhere for the shared Android debug key. Create it once (it stays out of git):
   ```bash
   keytool -genkeypair -keystore app/androidApp/debug.keystore -storepass android -keypass android \
     -alias desicrics-debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=DesiCrics Debug"
   ```
3. If your Firebase project isn't `desicrics-45117`, run the server with `FIREBASE_PROJECT_ID=<your-project>`.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Server: `docker compose up -d`, then `./gradlew :server:run` (start this first; the app expects it on port 8080). Android emulators reach
  it at `http://10.0.2.2:8080`. For a physical device, build with `-Pdesicrics.apiBaseUrl=http://<your-LAN-IP>:8080`.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:shared:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- Core tests: `./gradlew :core:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…