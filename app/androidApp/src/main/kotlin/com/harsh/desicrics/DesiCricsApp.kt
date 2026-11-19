package com.harsh.desicrics

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import com.harsh.desicrics.auth.GoogleSignInConfig
import com.harsh.desicrics.data.Analytics
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.di.initKoin
import com.harsh.desicrics.util.AppInfo
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class DesiCricsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PushService.createChannels(this)
        initKoin {
            androidContext(this@DesiCricsApp)
            modules(
                module {
                    single { GoogleSignInConfig(webClientId = googleWebClientId()) }
                    single { ApiConfig(baseUrl = BuildConfig.API_BASE_URL, publicUrl = BuildConfig.PUBLIC_URL) }
                    single { AppInfo("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) Android ${Build.VERSION.RELEASE}") }
                    single<Analytics> { FirebaseAnalyticsLogger(get()) }
                },
            )
        }
    }
}

/**
 * The google-services plugin generates `default_web_client_id` only when google-services.json has a web
 * OAuth client, which appears once Google sign-in is enabled in Firebase. Looked up by name so the app
 * still builds and runs before that; the Google button then explains it isn't set up yet.
 */
@SuppressLint("DiscouragedApi")
private fun Context.googleWebClientId(): String? =
    resources.getIdentifier("default_web_client_id", "string", packageName)
        .takeIf { it != 0 }
        ?.let(::getString)
