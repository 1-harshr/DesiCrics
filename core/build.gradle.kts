import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    // For the web app: an ES module with TypeScript definitions, called through WebCore (see docs/web.md).
    js {
        outputModuleName = "desicrics-core"
        nodejs {
            // Mocha stops a test after 2 s by default; replaying whole 20-over matches takes longer on CI runners.
            testTask { useMocha { timeout = "30s" } }
        }
        binaries.library()
        useEsModules()
        generateTypeScriptDefinitions()
    }
    android {
       namespace = "com.harsh.desicrics.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_17
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}