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
        versionCode = 1
        versionName = "1.0"

        // The Ktor server. Locally the app calls localhost and `adb reverse tcp:8080 tcp:8080` forwards it
        val apiBaseUrl = providers.gradleProperty("desicrics.apiBaseUrl").getOrElse("http://localhost:8080")
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // Where join links and public scorecards point. The same server, reachable from outside: set it
        // to the deployed address (and add its Digital Asset Links file) once there is one.
        val publicUrl = providers.gradleProperty("desicrics.publicUrl").getOrElse(apiBaseUrl)
        buildConfigField("String", "PUBLIC_URL", "\"$publicUrl\"")
        manifestPlaceholders["publicHost"] = publicUrl.substringAfter("://").substringBefore("/").substringBefore(":")
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
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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