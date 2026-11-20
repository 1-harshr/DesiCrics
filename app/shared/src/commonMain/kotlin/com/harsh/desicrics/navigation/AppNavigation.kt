package com.harsh.desicrics.navigation
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.harsh.desicrics.designsystem.component.DesiNavigationBar
import com.harsh.desicrics.designsystem.component.LocalNavBarClearance
import com.harsh.desicrics.designsystem.component.NavBarClearance
import com.harsh.desicrics.designsystem.component.NavTab
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.IntOffset
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.harsh.desicrics.designsystem.theme.DesiMotion
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
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
import com.harsh.desicrics.ui.auction.AuctionPoster
import com.harsh.desicrics.ui.auction.AuctionScreen
import com.harsh.desicrics.ui.share.ShareStudio
import com.harsh.desicrics.ui.share.auctionShareContents
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
/** A tournament's player auction: its setup, the live room (hall or online) and the results. */
@Serializable data class AuctionRoute(val auctionId: String) : NavKey

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
            subclass(AuctionRoute::class)
        }
    }
}

/**
 * The signed-in app. The back stack is a plain list of routes: opening a screen adds one, back removes
 * the last. Each entry keeps its own saved state and ViewModels, cleared when it leaves the stack.
 */
@Composable
fun AppNavigation(
    profile: UserProfile,
    onSignOut: () -> Unit,
    link: String? = null,
    onLinkHandled: () -> Unit = {},
    askForNotifications: Boolean = true,
) {
    val roots = listOf(HomeRoute, FeedRoute, TournamentsRoute, ProfileRoute)
    val stacks = listOf(rememberNavBackStack(RouteSerialization, HomeRoute),
        rememberNavBackStack(RouteSerialization, FeedRoute), rememberNavBackStack(RouteSerialization, TournamentsRoute),
        rememberNavBackStack(RouteSerialization, ProfileRoute))
    // The tabs sit side by side on a pager: picking one slides to it, and from a tab's root a swipe does too.
    val pager = rememberPagerState { stacks.size }
    val scope = rememberCoroutineScope()
    val select: (Int) -> Unit = { tab -> scope.launch { pager.animateScrollToPage(tab, animationSpec = tween(SLIDE_MS, easing = DesiMotion.standard)) } }
    val currentStack = stacks[pager.currentPage]
    // Each tab's saved state and ViewModels live up here, so they outlast the pager dropping the tab's
    // page while another tab is showing.
    val decorators = stacks.map {
        listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())
    }

    val social = koinInject<SocialRepository>()
    RegisterForPush(onToken = social::savePushToken, askPermission = askForNotifications)

    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        routeFor(link)?.let { currentStack.add(it) }
        onLinkHandled()
    }

    // The tab bar floats over a tab's root screen, above the system navigation bar. The screen pads for
    // neither on its own: it leaves room for both through LocalNavBarClearance.
    val isRoot = currentStack.size == 1
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Back on another tab's root goes to Home before it leaves the app.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = isRoot && pager.currentPage != 0,
        onBackCompleted = { select(0) })
    Box(Modifier.fillMaxSize()) {
    HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        // Inside a tab, a sideways swipe would throw away the screen the user opened.
        userScrollEnabled = isRoot,
        key = { it },
    ) { page ->
    val backStack = stacks[page]
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() else select(0) }
    val atRoot = backStack.size == 1
    CompositionLocalProvider(LocalNavBarClearance provides if (atRoot) NavBarClearance + systemBar else 0.dp) {
    NavDisplay(
        modifier = Modifier.fillMaxSize().then(if (atRoot) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
        backStack = backStack,
        onBack = back,
        entryDecorators = decorators[page],
        transitionSpec = { slideFrom(right = true) },
        popTransitionSpec = { slideFrom(right = false) },
        predictivePopTransitionSpec = { slideFrom(right = false) },
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
                    onProfile = { select(3) },
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
                // The Tournaments tab's root, like Feed and Me, has no back arrow.
                TournamentsScreen(onOpen = { backStack.add(TournamentRoute(it)) }, onBack = if (backStack.size > 1) back else null)
            }
            entry<TournamentRoute> { route ->
                TournamentScreen(
                    tournamentId = route.tournamentId,
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onOpenPlayer = { backStack.add(PlayerRoute(it)) },
                    onLeaderboard = { backStack.add(LeaderboardRoute(route.tournamentId)) },
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onOpenAuction = { backStack.add(AuctionRoute(it)) },
                    onBack = back,
                )
            }
            entry<AuctionRoute> { route ->
                // Share on the sold moment or a team's results opens the studio with that poster.
                var poster by remember { mutableStateOf<AuctionPoster?>(null) }
                AuctionScreen(route.auctionId, onBack = back, onOpenPlayer = { backStack.add(PlayerRoute(it)) }, onSharePoster = { poster = it })
                poster?.let { current ->
                    ShareStudio(remember(current) { auctionShareContents(current) }, onDismiss = { poster = null }, title = stringResource(Res.string.poster_studio_title))
                }
            }
            entry<NotificationsRoute> {
                NotificationsScreen(onOpenMatch = { backStack.add(MatchRoute(it)) }, onBack = back, onOpenTeam = { backStack.add(TeamRoute(it)) })
            }
            entry<PlayerRoute> { route ->
                PlayerScreen(
                    profileId = route.profileId,
                    myId = profile.uid,
                    onOpenTeam = { backStack.add(TeamRoute(it)) },
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onBack = back,
                    extra = { page ->
                        CareerSection(
                            page.player.profileId,
                            onCompare = { backStack.add(CompareRoute(page.player.profileId, page.player.name)) },
                            name = page.player.name,
                            onOpenMatch = { backStack.add(MatchRoute(it)) },
                            isMe = page.player.profileId == profile.uid,
                        )
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
    }
    }
    if (isRoot) {
        DesiNavigationBar(
            tabs = listOf(
                NavTab(stringResource(Res.string.tab_home), DesiIcons.Home, DesiIcons.HomeFilled),
                NavTab(stringResource(Res.string.tab_feed), DesiIcons.Feed, DesiIcons.FeedFilled),
                NavTab(stringResource(Res.string.home_tournaments), DesiIcons.Trophy, DesiIcons.TrophyFilled),
                NavTab(stringResource(Res.string.tab_me), DesiIcons.Person, DesiIcons.PersonFilled),
            ),
            selected = pager.targetPage,
            onSelect = select,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }

}

private const val SLIDE_MS = 350

/**
 * Screens slide like the tabs do, never crossfade: an opened screen pushes in from the right, and back
 * slides it out to the right again.
 */
private fun slideFrom(right: Boolean): ContentTransform {
    val spec = tween<IntOffset>(SLIDE_MS, easing = DesiMotion.standard)
    val sign = if (right) 1 else -1
    return slideInHorizontally(spec) { sign * it } togetherWith slideOutHorizontally(spec) { -sign * it }
}

/** The screen a link path opens, or null for a link the app doesn't know. */
internal fun routeFor(path: String): NavKey? {
    val parts = path.trim('/').split('/')
    val value = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return when (parts.first()) {
        "join" -> JoinTeamRoute(value)
        "m" -> MatchRoute(value)
        "t" -> TournamentRoute(value)
        // Only from the app's own notifications: the server has no public team page.
        "team" -> TeamRoute(value)
        else -> null
    }
}

private fun NavBackStack<NavKey>.replaceLast(route: NavKey) {
    removeLastOrNull()
    add(route)
}
