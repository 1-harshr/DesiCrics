package com.harsh.desicrics.di

import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.UserRepository
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.data.api.IdTokenProvider
import com.harsh.desicrics.session.SessionViewModel
import com.harsh.desicrics.ui.home.HomeViewModel
import com.harsh.desicrics.ui.match.MatchViewModel
import com.harsh.desicrics.ui.newmatch.NewMatchViewModel
import com.harsh.desicrics.ui.profile.EditProfileViewModel
import com.harsh.desicrics.ui.signin.SignInViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

/** Needs an `ApiConfig` from the platform module (Android: BuildConfig, iOS: Swift). */
val dataModule = module {
    single { Firebase.auth }
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    singleOf(::AuthRepository) bind IdTokenProvider::class
    singleOf(::DesiCricsApi)
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
