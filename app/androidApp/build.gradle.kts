import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    // Requires app/androidApp/google-services.json (download from the Firebase console)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(project.dependencies.platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExt.junit)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.harsh.desicrics"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.harsh.desicrics"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI numbers each release build (see .github/workflows/android-release.yml) so it installs over the last.
        versionCode = providers.gradleProperty("desicrics.versionCode").map { it.toInt() }.getOrElse(1)
        versionName = providers.gradleProperty("desicrics.versionName").getOrElse("1.0")
    }
    // Which Ktor server the app calls, independent of debug or release. `local` calls the server on this
    // computer (Docker), which `adb reverse tcp:8080 tcp:8080` forwards; `remote` calls the proxy Worker
    // (proxy/), whose address stays put when the server moves hosts. -Pdesicrics.apiBaseUrl overrides either.
    flavorDimensions += "server"
    fun com.android.build.api.dsl.ApplicationProductFlavor.server(defaultUrl: String) {
        dimension = "server"
        val apiBaseUrl = providers.gradleProperty("desicrics.apiBaseUrl").getOrElse(defaultUrl)
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // Where join links and public scorecards point: the same server, reachable from outside.
        val publicUrl = providers.gradleProperty("desicrics.publicUrl").getOrElse(apiBaseUrl)
        buildConfigField("String", "PUBLIC_URL", "\"$publicUrl\"")
        manifestPlaceholders["publicHost"] = publicUrl.substringAfter("://").substringBefore("/").substringBefore(":")
    }
    productFlavors {
        create("local") {
            server("http://localhost:8080")
        }
        create("remote") {
            server("https://desicrics-api.desicrics-proxy.workers.dev")
        }
    }
    // Robolectric tests run the real services against Android's own notification manager.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        // A debug key for this project only, so its SHA fingerprints don't clash with OAuth clients
        // registered elsewhere for the shared ~/.android/debug.keystore. Kept out of git (see README.md);
        // without it, as on CI, debug builds fall back to the default debug key.
        val projectDebugKey = file("debug.keystore")
        if (projectDebugKey.exists()) getByName("debug") {
            storeFile = projectDebugKey
            storePassword = "android"
            keyAlias = "desicrics-debug"
            keyPassword = "android"
        }
        // The release key lives outside git. CI passes it in from repository secrets; locally, pass the same
        // -Pdesicrics.release* properties. Without them release builds come out unsigned.
        val releaseKey = providers.gradleProperty("desicrics.releaseKeystore").orNull
        if (releaseKey != null) create("release") {
            storeFile = file(releaseKey)
            storePassword = providers.gradleProperty("desicrics.releaseKeystorePassword").get()
            keyAlias = providers.gradleProperty("desicrics.releaseKeyAlias").get()
            keyPassword = providers.gradleProperty("desicrics.releaseKeyPassword").get()
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 shrinks and obfuscates; Crashlytics uploads the mapping file so crash reports deobfuscate.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}