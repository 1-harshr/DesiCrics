# Deploying the server

The server is one container (`server/Dockerfile`) talking to one Postgres. Everything environment-specific comes from env vars. The image has been checked to build, start, apply every migration to an empty Postgres 17, and serve pages.

Hosting is still undecided (see [ADR 0001](decisions/0001-backend-stack.md)). Both options are written out below, so staging is a few commands once it's decided.

## Build the image

`core` has an Android target, so the image doesn't compile the source. Build the jar first:

```sh
./gradlew :server:buildFatJar
docker build -t desicrics-server server
```

## Settings

| Variable | Needed | What it is |
|---|---|---|
| `DATABASE_URL` | Yes | JDBC URL, e.g. `jdbc:postgresql://host:5432/desicrics`. Flyway migrates it on start. |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Yes | Postgres credentials. |
| `DATABASE_POOL_SIZE` | No | Default 10. Keep it under the database's connection limit. |
| `PORT` | Set by the host | Default 8080. |
| `FIREBASE_PROJECT_ID` | No | Default `desicrics-45117`. Used to verify sign-in tokens. |
| `FIREBASE_SERVICE_ACCOUNT` | For push | Path to a Firebase service account JSON key (mount it as a secret file). Without it, notifications only go to the in-app inbox. |
| `ANDROID_SHA256_FINGERPRINTS` | For verified links | Comma-separated SHA-256 fingerprints of the app's signing certificates (`./gradlew :app:androidApp:signingReport`, or Play Console › App integrity). Served at `/.well-known/assetlinks.json`, so `/join`, `/m` and `/t` links open the app without a chooser. |
| `WEB_ORIGINS` | For the web app's dev server | Comma-separated origins allowed to read the public API from a browser, e.g. `http://localhost:5173`. Not needed for the [web app](web.md) served by the proxy, which is on the API's own address. |

## The permanent address

The app never calls the host directly. The `remote` builds (`assembleRemoteDebug`, `assembleRemoteRelease`) call a Cloudflare Worker, `https://desicrics-api.desicrics-proxy.workers.dev` (`proxy/`), which passes every request, WebSockets included, through to wherever the server runs now. Join, match and tournament links use the same address, so moving hosts needs no new app build and old links keep working.

The Worker also serves the [web app](web.md). A browser loading a page (a navigation, or a request that accepts HTML) gets the web app's files from `web/dist`; everything else, including all of the app's API calls and WebSockets, goes to the server as before. A few server pages stay with the server even in a browser: the stream overlay, `/m/{id}/overlay.json`, `/.well-known/`, `/images/` and `/health`. The web app calls the API on the same address, so it needs no `WEB_ORIGINS` there.

To point it at a new host, change `BACKEND_URL` in `proxy/wrangler.jsonc`, then:

```sh
cd proxy && npm install && npm run deploy   # first time: npx wrangler login. Builds web/ first, so it needs Java too
```

To build the app against some other address instead, pass `-Pdesicrics.apiBaseUrl=https://<host> -Pdesicrics.publicUrl=https://<host>`.

## Option A: Cloud Run + Cloud SQL

```sh
gcloud artifacts repositories create desicrics --repository-format=docker --location=asia-south1
docker tag desicrics-server asia-south1-docker.pkg.dev/<project>/desicrics/server
docker push asia-south1-docker.pkg.dev/<project>/desicrics/server

gcloud sql instances create desicrics --database-version=POSTGRES_17 --tier=db-f1-micro --region=asia-south1
gcloud sql databases create desicrics --instance=desicrics
gcloud sql users create desicrics --instance=desicrics --password=<password>

gcloud run deploy desicrics-server \
  --image=asia-south1-docker.pkg.dev/<project>/desicrics/server \
  --region=asia-south1 --allow-unauthenticated \
  --add-cloudsql-instances=<project>:asia-south1:desicrics \
  --set-env-vars=DATABASE_URL='jdbc:postgresql:///desicrics?cloudSqlInstance=<project>:asia-south1:desicrics&socketFactory=com.google.cloud.sql.postgres.SocketFactory',DATABASE_USER=desicrics \
  --set-secrets=DATABASE_PASSWORD=db-password:latest,/secrets/firebase.json=firebase-admin:latest \
  --set-env-vars=FIREBASE_SERVICE_ACCOUNT=/secrets/firebase.json \
  --min-instances=1 --max-instances=1 --timeout=3600
```

- `--max-instances=1` keeps live scores on one instance. Watchers are kept in memory, and a second instance needs Postgres LISTEN/NOTIFY between them (see architecture.md).
- `--timeout=3600` lets WebSockets stay open for an hour; the app reconnects after that.
- The Cloud SQL socket factory is a dependency the server doesn't include yet (`com.google.cloud.sql:postgres-socket-factory`). The alternative is Cloud SQL's private IP with a plain `jdbc:postgresql://<ip>/desicrics` URL.

## Option B: Fly.io + Neon

```sh
fly launch --no-deploy --copy-config --name desicrics-staging   # uses fly.toml
fly secrets set DATABASE_URL='jdbc:postgresql://<neon-host>/desicrics?sslmode=require' \
  DATABASE_USER=<user> DATABASE_PASSWORD=<password>
fly secrets set FIREBASE_SERVICE_ACCOUNT=/secrets/firebase.json   # and add the key as a Fly file secret
./gradlew :server:buildFatJar
fly deploy --config fly.toml --dockerfile server/Dockerfile server
```

`fly.toml` keeps one machine always running (no scale to zero), for the same WebSocket reason.

## Option C: Render (free, for staging)

Free, but the web service sleeps after about 15 minutes without traffic (the next request waits about a minute) and the free Postgres expires after 30 days. Fine for trying the app with a few people; not for a live Sunday.

Render can't build the image itself (no Android SDK), so `.github/workflows/server-image.yml` builds it on every push to `main` that touches the server and publishes it as `ghcr.io/<owner>/desicrics-server`.

1. **Postgres:** New › Postgres, Free, region Singapore (the closest to India). From its Connections section, note the *internal* hostname, database, user and password.
2. **Image:** run the Server image workflow once (Actions › Server image › Run workflow), then make the package public (GitHub › Packages › desicrics-server › Package settings), or give Render a GitHub token as registry credentials.
3. **Web service:** New › Web Service › Existing image, `ghcr.io/<owner>/desicrics-server:latest`, Free, region Singapore. Environment:
   - `DATABASE_URL` = `jdbc:postgresql://<internal hostname>:5432/<database>`
   - `DATABASE_USER`, `DATABASE_PASSWORD` from step 1
   - `DATABASE_POOL_SIZE` = `5` (the free database allows few connections)
   - `FIREBASE_SERVICE_ACCOUNT` as a secret file, for push (optional)
   
   Render sets `PORT`, and the health check path is `/`.
4. **Redeploys:** copy the service's Deploy Hook URL into the `RENDER_DEPLOY_HOOK` repository secret, so each new image deploys itself.
5. Set `BACKEND_URL` in `proxy/wrangler.jsonc` to `https://<service>.onrender.com` and deploy the proxy (see [The permanent address](#the-permanent-address)).
6. **Keep it awake:** in [UptimeRobot](https://uptimerobot.com) (free), add an HTTP(s) monitor for `https://desicrics-api.desicrics-proxy.workers.dev/health` every 5 minutes, which checks the proxy and the server together. The server then never sleeps, and Render's free 750 hours a month cover one service running all month. Without it, the app still copes: it wakes the server as it opens and waits up to 75 s for the first answer.

## After the first deploy

1. Open `https://<host>/` and check it answers "DesiCrics API".
2. Check `https://<host>/.well-known/assetlinks.json` lists the app's fingerprint.
3. Sign in on a phone with the app built for that host, score a ball, and open the match link in a browser.

## Android releases

Once CI passes on `main`, **Android release** builds the signed APK for the deployed server (`remoteRelease`).
The version is `1.0.<run>` so each new build installs over the last. You can also build it from
GitHub › Actions › Android release › Run workflow. Download the APK from the run's **Artifacts** section.

To send a build to testers, open a successful **Android release** run on `main` that has an APK artifact.
Copy the number after `/actions/runs/` in its URL. Then go to GitHub › Actions › Firebase App Distribution › Run workflow
and enter that number in `release_run_id`. Firebase notifies the `internal` tester group when the build is available.

Repository secrets for the Android release build and Firebase distribution:

| Secret | What |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | `app/androidApp/google-services.json` (CI uses it too) |
| `RELEASE_KEYSTORE_BASE64` | the release key: `base64 -i app/androidApp/release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | from `app/androidApp/release.keystore.properties` |
| `FIREBASE_SERVICE_ACCOUNT` | JSON key of a service account with the Firebase App Distribution Admin role. Required for the Firebase workflow. |

The release key and its passwords stay out of git. **Back them up**: an APK signed with any other key won't
install over the existing ones, so testers would have to uninstall first. Its SHA-1 and SHA-256 must be on the
Android app in Firebase (Project settings › Your apps) for Google and phone sign-in, and its SHA-256 in the
server's `ANDROID_SHA256_FINGERPRINTS` for app links. To build the same APK locally:

```bash
set -a; . app/androidApp/release.keystore.properties; set +a
./gradlew :app:androidApp:assembleRemoteRelease -Pdesicrics.releaseKeystore=$PWD/app/androidApp/release.keystore \
  -Pdesicrics.releaseKeystorePassword=$RELEASE_KEYSTORE_PASSWORD -Pdesicrics.releaseKeyAlias=$RELEASE_KEY_ALIAS \
  -Pdesicrics.releaseKeyPassword=$RELEASE_KEY_PASSWORD
```
