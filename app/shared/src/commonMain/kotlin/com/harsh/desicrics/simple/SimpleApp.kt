package com.harsh.desicrics.simple

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.savedstate.serialization.SavedStateConfiguration
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.designsystem.component.DesiNavigationBar
import com.harsh.desicrics.designsystem.component.LocalNavBarClearance
import com.harsh.desicrics.designsystem.component.NavBarClearance
import com.harsh.desicrics.designsystem.component.NavTab
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.tab_home
import com.harsh.desicrics.resources.tab_me
import com.harsh.desicrics.ui.system.RegisterForPush
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// Simple mode's own screens. They share view models and repositories with Full mode, never its screens.

@Serializable internal data object SimpleHomeRoute : NavKey
@Serializable internal data object SimpleMeRoute : NavKey
@Serializable internal data object SimpleNewMatchRoute : NavKey
@Serializable internal data class SimpleMatchRoute(val matchId: String) : NavKey

private val RouteSerialization = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SimpleHomeRoute::class)
            subclass(SimpleMeRoute::class)
            subclass(SimpleNewMatchRoute::class)
            subclass(SimpleMatchRoute::class)
        }
    }
}

/**
 * The minimal app: Home, where matches start and carry on, and Me. Each tab keeps its own back stack, and
 * the tab bar shows only on a tab's root screen.
 */
@Composable
fun SimpleApp(
    profile: UserProfile,
    onSignOut: () -> Unit,
    link: String?,
    onLinkHandled: () -> Unit,
    askForNotifications: Boolean,
) {
    val stacks = listOf(rememberNavBackStack(RouteSerialization, SimpleHomeRoute), rememberNavBackStack(RouteSerialization, SimpleMeRoute))
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val backStack = stacks[tab]
    val decorators = stacks.map {
        listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>(), rememberViewModelStoreNavEntryDecorator<NavKey>())
    }

    val social = koinInject<SocialRepository>()
    RegisterForPush(onToken = social::savePushToken, askPermission = askForNotifications)

    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        simpleRouteFor(link)?.let {
            tab = 0
            stacks[0].add(it)
        }
        onLinkHandled()
    }

    val atRoot = backStack.size == 1
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() else tab = 0 }
    // Back on Me goes Home before it leaves the app.
    NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), isBackEnabled = atRoot && tab != 0, onBackCompleted = { tab = 0 })
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalNavBarClearance provides if (atRoot) NavBarClearance + systemBar else 0.dp) {
            NavDisplay(
                modifier = Modifier.fillMaxSize().then(if (atRoot) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                backStack = backStack,
                onBack = back,
                entryDecorators = decorators[tab],
                entryProvider = entryProvider {
                    entry<SimpleHomeRoute> {
                        SimpleHomeScreen(
                            profile = profile,
                            onNewMatch = { backStack.add(SimpleNewMatchRoute) },
                            onOpenMatch = { backStack.add(SimpleMatchRoute(it)) },
                            onProfile = { tab = 1 },
                        )
                    }
                    entry<SimpleMeRoute> {
                        SimpleMeScreen(profile = profile, onSignOut = onSignOut, onOpenMatch = { backStack.add(SimpleMatchRoute(it)) })
                    }
                    entry<SimpleNewMatchRoute> {
                        // The new match replaces the form, so back from the match goes home.
                        SimpleNewMatchScreen(onCreated = { backStack.replaceLast(SimpleMatchRoute(it)) }, onBack = back)
                    }
                    entry<SimpleMatchRoute> { route ->
                        SimpleMatchScreen(matchId = route.matchId, onBack = back)
                    }
                },
            )
        }
        if (atRoot) {
            DesiNavigationBar(
                tabs = listOf(
                    NavTab(stringResource(Res.string.tab_home), DesiIcons.Home, DesiIcons.HomeFilled),
                    NavTab(stringResource(Res.string.tab_me), DesiIcons.Person, DesiIcons.PersonFilled),
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Simple mode opens match links; anything else it doesn't have is left alone. */
internal fun simpleRouteFor(path: String): NavKey? {
    val parts = path.trim('/').split('/')
    val value = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return if (parts.first() == "m") SimpleMatchRoute(value) else null
}

private fun NavBackStack<NavKey>.replaceLast(route: NavKey) {
    removeLastOrNull()
    add(route)
}
