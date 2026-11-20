package com.harsh.desicrics.di

import com.harsh.desicrics.data.AuctionRepository
import com.harsh.desicrics.data.CityRepository
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.CommunityRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.data.StatsRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.ThemePreference
import com.harsh.desicrics.data.ModePreference
import com.harsh.desicrics.data.TournamentRepository
import com.harsh.desicrics.data.UserRepository
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.data.api.IdTokenProvider
import com.harsh.desicrics.session.SessionViewModel
import com.harsh.desicrics.ui.auction.AuctionViewModel
import com.harsh.desicrics.ui.community.CommunityViewModel
import com.harsh.desicrics.ui.home.HomeViewModel
import com.harsh.desicrics.ui.match.MatchViewModel
import com.harsh.desicrics.ui.newmatch.NewMatchViewModel
import com.harsh.desicrics.ui.onboarding.OnboardingViewModel
import com.harsh.desicrics.ui.profile.EditProfileViewModel
import com.harsh.desicrics.ui.profile.ProfileViewModel
import com.harsh.desicrics.ui.signin.SignInViewModel
import com.harsh.desicrics.ui.social.NotificationsViewModel
import com.harsh.desicrics.ui.social.PlayerViewModel
import com.harsh.desicrics.ui.social.SearchViewModel
import com.harsh.desicrics.ui.stats.CareerViewModel
import com.harsh.desicrics.ui.stats.CompareViewModel
import com.harsh.desicrics.ui.stats.LeaderboardViewModel
import com.harsh.desicrics.ui.teams.TeamViewModel
import com.harsh.desicrics.ui.tournament.TournamentViewModel
import com.harsh.desicrics.ui.tournament.TournamentsViewModel
import com.harsh.desicrics.ui.teams.TeamsViewModel
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

/** Needs an `ApiConfig` and an `AppInfo` from the platform module (Android: BuildConfig, iOS: Swift). */
val dataModule = module {
    single { Firebase.auth }
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json { ignoreUnknownKeys = true })
                pingIntervalMillis = 20_000
            }
            install(HttpTimeout) {
                // Long enough to outlast a sleeping server waking up (about a minute on Render's free plan).
                requestTimeoutMillis = 75_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    singleOf(::AuthRepository) bind IdTokenProvider::class
    singleOf(::DesiCricsApi)
    singleOf(::UserRepository)
    singleOf(::MatchRepository)
    singleOf(::TeamRepository)
    singleOf(::SocialRepository)
    singleOf(::TournamentRepository)
    singleOf(::StatsRepository)
    singleOf(::CommunityRepository)
    singleOf(::AuctionRepository)
    singleOf(::CityRepository)
    single { ThemePreference(Settings()) }
    single { ModePreference(Settings()) }
}

val viewModelModule = module {
    viewModelOf(::SessionViewModel)
    viewModelOf(::SignInViewModel)
    viewModelOf(::EditProfileViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::NewMatchViewModel)
    viewModel { (matchId: String) -> MatchViewModel(matchId, get(), get(), get(), get(), get()) }
    viewModelOf(::TeamsViewModel)
    viewModelOf(::ProfileViewModel)
    viewModel { (teamId: String) -> TeamViewModel(teamId, get(), get(), get()) }
    viewModelOf(::SearchViewModel)
    viewModel { (city: String) -> TournamentsViewModel(city, get()) }
    viewModel { (profileId: String) -> CareerViewModel(profileId, get()) }
    viewModel { (profileId: String) -> CompareViewModel(profileId, get(), get()) }
    viewModel { (tournamentId: String?) -> LeaderboardViewModel(tournamentId, get()) }
    viewModel { (city: String) -> CommunityViewModel(city, get(), get()) }
    viewModel { (tournamentId: String) -> TournamentViewModel(tournamentId, get(), get(), get(), get()) }
    viewModel { (auctionId: String) -> AuctionViewModel(auctionId, get(), get(), get<AuthRepository>().currentUser?.uid) }
    viewModelOf(::NotificationsViewModel)
    viewModel { (profileId: String, myId: String?) -> PlayerViewModel(profileId, get(), myId) }
}

/** Called once per process: from the Android `Application`, and from the iOS app's init. */
fun initKoin(config: KoinAppDeclaration = {}) = startKoin {
    config()
    modules(dataModule, viewModelModule)
}
