package com.harsh.desicrics.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Modifier
import com.harsh.desicrics.ui.home.FeedScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.ui.system.RegisterForPush
import org.koin.compose.koinInject
import com.harsh.desicrics.ui.community.CommunityScreen
import com.harsh.desicrics.ui.home.HomeScreen
import com.harsh.desicrics.ui.match.MatchScreen
import com.harsh.desicrics.ui.newmatch.NewMatchScreen
import com.harsh.desicrics.ui.profile.EditProfileScreen
import com.harsh.desicrics.ui.profile.ProfileScreen
import com.harsh.desicrics.ui.social.NotificationsScreen
import com.harsh.desicrics.ui.social.PlayerScreen
import com.harsh.desicrics.ui.social.SearchScreen
import com.harsh.desicrics.ui.stats.CareerSection
import com.harsh.desicrics.ui.stats.CompareScreen
import com.harsh.desicrics.ui.stats.LeaderboardScreen
import com.harsh.desicrics.ui.teams.TeamScreen
import com.harsh.desicrics.ui.tournament.TournamentScreen
import com.harsh.desicrics.ui.tournament.TournamentsScreen
import com.harsh.desicrics.ui.teams.TeamsScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Screens a signed-in user moves between. Sign-in, onboarding and the session error screen sit outside
// this: App switches to them from the session state.

@Serializable data object FeedRoute : NavKey
@Serializable data object HomeRoute : NavKey
@Serializable data object ProfileRoute : NavKey
@Serializable data object EditProfileRoute : NavKey
@Serializable data object NewMatchRoute : NavKey
@Serializable data class MatchRoute(val matchId: String) : NavKey
@Serializable data object TeamsRoute : NavKey
@Serializable data class TeamRoute(val teamId: String) : NavKey
@Serializable data object SearchRoute : NavKey
@Serializable data object NotificationsRoute : NavKey
@Serializable data class PlayerRoute(val profileId: String) : NavKey
@Serializable data object TournamentsRoute : NavKey
@Serializable data class TournamentRoute(val tournamentId: String) : NavKey
@Serializable data class CompareRoute(val profileId: String, val name: String) : NavKey
/** All leaderboards, or one tournament's when [tournamentId] is set. */
@Serializable data class LeaderboardRoute(val tournamentId: String? = null) : NavKey
/** Looking-for posts, rankings and the directory, for the user's city. */
@Serializable data object CommunityRoute : NavKey
/** Opened from a join link or QR code: the teams list with the join sheet filled in. */
@Serializable data class JoinTeamRoute(val code: String) : NavKey

/** Lets the back stack be saved and restored (process death, rotation) without reflection. */
private val RouteSerialization = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(FeedRoute::class)
            subclass(HomeRoute::class)
            subclass(ProfileRoute::class)
            subclass(EditProfileRoute::class)
            subclass(NewMatchRoute::class)
            subclass(MatchRoute::class)
            subclass(TeamsRoute::class)
            subclass(TeamRoute::class)
            subclass(JoinTeamRoute::class)
            subclass(SearchRoute::class)
            subclass(NotificationsRoute::class)
            subclass(PlayerRoute::class)
            subclass(TournamentsRoute::class)
            subclass(TournamentRoute::class)
            subclass(CompareRoute::class)
            subclass(LeaderboardRoute::class)
            subclass(CommunityRoute::class)
        }
    }
}

/**
 * The signed-in app. The back stack is a plain list of routes: opening a screen adds one, back removes
 * the last. Each entry keeps its own saved state and ViewModels, cleared when it leaves the stack.
 */
@Composable
fun AppNavigation(profile: UserProfile, onSignOut: () -> Unit, link: String? = null, onLinkHandled: () -> Unit = {}) {
    val roots = listOf(HomeRoute, FeedRoute, TournamentsRoute, ProfileRoute)
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val stacks = listOf(rememberNavBackStack(RouteSerialization, HomeRoute),
        rememberNavBackStack(RouteSerialization, FeedRoute), rememberNavBackStack(RouteSerialization, TournamentsRoute),
        rememberNavBackStack(RouteSerialization, ProfileRoute))
    val backStack = stacks[selected]
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() else selected = 0 }

    val social = koinInject<SocialRepository>()
    RegisterForPush(onToken = social::savePushToken)

    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        routeFor(link)?.let { backStack.add(it) }
        onLinkHandled()
    }

    Column(Modifier.fillMaxSize()) {
    NavDisplay(
        modifier = Modifier.weight(1f),
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<FeedRoute> {
                FeedScreen(onOpen = { backStack.add(MatchRoute(it)) }, community = { backStack.add(CommunityRoute) },
                    rankings = { backStack.add(LeaderboardRoute()) })
            }
            entry<HomeRoute> {
                HomeScreen(
                    playerName = profile.name,
                    playerPhoto = profile.photoUrl,
                    onNewMatch = { backStack.add(NewMatchRoute) },
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onProfile = { selected = 3 },
                    onTeams = { backStack.add(TeamsRoute) },
                    onSearch = { backStack.add(SearchRoute) },
                    onNotifications = { backStack.add(NotificationsRoute) },
                    onTournaments = { backStack.add(TournamentsRoute) },
                    onLeaderboards = { backStack.add(LeaderboardRoute()) },
                    onCommunity = { backStack.add(CommunityRoute) },
                )
            }
            entry<ProfileRoute> {
                ProfileScreen(
                    profile = profile,
                    onEdit = { backStack.add(EditProfileRoute) },
                    onSignOut = onSignOut,
                    onBack = back,
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onCompare = { backStack.add(CompareRoute(profile.uid, profile.name)) },
                )
            }
            entry<EditProfileRoute> {
                EditProfileScreen(isOnboarding = false, onDone = back, onBack = back)
            }
            entry<NewMatchRoute> {
                // The new match replaces the form, so back from the match goes home, not to the form.
                NewMatchScreen(onCreated = { backStack.replaceLast(MatchRoute(it)) }, onBack = back)
            }
            entry<MatchRoute> { route ->
                MatchScreen(matchId = route.matchId, onBack = back)
            }
            entry<TeamsRoute> {
                TeamsScreen(onOpenTeam = { backStack.add(TeamRoute(it)) }, onBack = back)
            }
            entry<TeamRoute> { route ->
                TeamScreen(teamId = route.teamId, onBack = back, onOpenPlayer = { backStack.add(PlayerRoute(it)) })
            }
            entry<SearchRoute> {
                SearchScreen(
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onOpenPlayer = { backStack.add(PlayerRoute(it)) },
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onBack = back,
                )
            }
            entry<TournamentsRoute> {
                TournamentsScreen(onOpen = { backStack.add(TournamentRoute(it)) }, onBack = back)
            }
            entry<TournamentRoute> { route ->
                TournamentScreen(
                    tournamentId = route.tournamentId,
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onOpenPlayer = { backStack.add(PlayerRoute(it)) },
                    onLeaderboard = { backStack.add(LeaderboardRoute(route.tournamentId)) },
                    onBack = back,
                )
            }
            entry<NotificationsRoute> {
                NotificationsScreen(onOpenMatch = { backStack.add(MatchRoute(it)) }, onBack = back)
            }
            entry<PlayerRoute> { route ->
                PlayerScreen(
                    profileId = route.profileId,
                    myId = profile.uid,
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onBack = back,
                    extra = { page ->
                        CareerSection(page.player.profileId, onCompare = { backStack.add(CompareRoute(page.player.profileId, page.player.name)) }, name = page.player.name, onOpenMatch = { backStack.add(MatchRoute(it)) })
                    },
                )
            }
            entry<CompareRoute> { route ->
                CompareScreen(route.profileId, route.name, onBack = back)
            }
            entry<LeaderboardRoute> { route ->
                LeaderboardScreen(route.tournamentId, onOpenPlayer = { backStack.add(PlayerRoute(it)) }, onBack = back)
            }
            entry<CommunityRoute> {
                CommunityScreen(
                    city = profile.city,
                    myId = profile.uid,
                    onOpenPlayer = { backStack.add(PlayerRoute(it)) },
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onBack = back,
                )
            }
            entry<JoinTeamRoute> { route ->
                TeamsScreen(onOpenTeam = { backStack.replaceLast(TeamRoute(it)) }, onBack = back, joinCode = route.code)
            }
        },
    )
    if (backStack.size == 1) {
        val labels = listOf(Res.string.tab_home, Res.string.tab_feed, Res.string.home_tournaments, Res.string.tab_me)
        val icons = listOf(DesiIcons.Bat, DesiIcons.Ball, DesiIcons.Cricket, DesiIcons.Person)
        NavigationBar {
            roots.indices.forEach { i ->
                NavigationBarItem(selected == i, onClick = { selected = i },
                    icon = { Icon(painterResource(icons[i]), null) }, label = { Text(stringResource(labels[i])) })
            }
        }
    }
    }

}

/** The screen a link path opens, or null for a link the app doesn't know. */
internal fun routeFor(path: String): NavKey? {
    val parts = path.trim('/').split('/')
    val value = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return when (parts.first()) {
        "join" -> JoinTeamRoute(value)
        "m" -> MatchRoute(value)
        "t" -> TournamentRoute(value)
        else -> null
    }
}

private fun NavBackStack<NavKey>.replaceLast(route: NavKey) {
    removeLastOrNull()
    add(route)
}
