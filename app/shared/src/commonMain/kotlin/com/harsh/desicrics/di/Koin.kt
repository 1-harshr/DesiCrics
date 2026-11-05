package com.harsh.desicrics.di

import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.UserRepository
import com.harsh.desicrics.data.sqlconnect.IdTokenProvider
import com.harsh.desicrics.data.sqlconnect.SqlConnectClient
import com.harsh.desicrics.data.sqlconnect.SqlConnectConfig
import com.harsh.desicrics.session.SessionViewModel
import com.harsh.desicrics.ui.home.HomeViewModel
import com.harsh.desicrics.ui.match.MatchViewModel
import com.harsh.desicrics.ui.newmatch.NewMatchViewModel
import com.harsh.desicrics.ui.profile.EditProfileViewModel
import com.harsh.desicrics.ui.signin.SignInViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.app
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    single { Firebase.auth }
    single {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    single {
        // Read from google-services.json / GoogleService-Info.plist, so there's nothing extra to configure.
        val options = Firebase.app.options
        SqlConnectConfig(
            projectId = checkNotNull(options.projectId) { "Firebase project ID missing from config" },
            apiKey = options.apiKey,
        )
    }
    singleOf(::AuthRepository) bind IdTokenProvider::class
    singleOf(::SqlConnectClient)
    singleOf(::UserRepository)
    singleOf(::MatchRepository)
}

val viewModelModule = module {
    viewModelOf(::SessionViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::NewMatchViewModel)
    viewModel { (matchId: String) -> MatchViewModel(matchId, get(), get()) }
}

/** Called once per process: from the Android `Application`, and from the iOS app's init. */
fun initKoin(config: KoinAppDeclaration = {}) = startKoin {
    config()
    modules(dataModule, viewModelModule)
}
