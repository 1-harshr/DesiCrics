import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Crease: the app's design tokens, fonts, icons and shared components. See docs/design-system.md.
// Depends only on Compose; it must not depend on :app:shared or :core.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.harsh.desicrics.designsystem"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
        androidResources {
            enable = true
        }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            api(libs.compose.uiToolingPreview)
        }
    }
}

compose.resources {
    // Features use the fonts, icons and licence files through this class.
    publicResClass = true
    packageOfResClass = "com.harsh.desicrics.designsystem.resources"
}
