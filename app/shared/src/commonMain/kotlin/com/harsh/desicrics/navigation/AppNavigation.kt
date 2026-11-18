package com.harsh.desicrics.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.ui.home.HomeScreen
import com.harsh.desicrics.ui.match.MatchScreen
import com.harsh.desicrics.ui.newmatch.NewMatchScreen
import com.harsh.desicrics.ui.profile.EditProfileScreen
import com.harsh.desicrics.ui.profile.ProfileScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Screens a signed-in user moves between. Sign-in, onboarding and the session error screen sit outside
// this: App switches to them from the session state.

@Serializable data object HomeRoute : NavKey
@Serializable data object ProfileRoute : NavKey
@Serializable data object EditProfileRoute : NavKey
@Serializable data object NewMatchRoute : NavKey
@Serializable data class MatchRoute(val matchId: String) : NavKey

/** Lets the back stack be saved and restored (process death, rotation) without reflection. */
private val RouteSerialization = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(HomeRoute::class)
            subclass(ProfileRoute::class)
            subclass(EditProfileRoute::class)
            subclass(NewMatchRoute::class)
            subclass(MatchRoute::class)
        }
    }
}

/**
 * The signed-in app. The back stack is a plain list of routes: opening a screen adds one, back removes
 * the last. Each entry keeps its own saved state and ViewModels, cleared when it leaves the stack.
 */
@Composable
fun AppNavigation(profile: UserProfile, onSignOut: () -> Unit) {
    val backStack = rememberNavBackStack(RouteSerialization, HomeRoute)
    val back: () -> Unit = { backStack.removeLastOrNull() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    playerName = profile.name,
                    onNewMatch = { backStack.add(NewMatchRoute) },
                    onOpenMatch = { backStack.add(MatchRoute(it)) },
                    onProfile = { backStack.add(ProfileRoute) },
                )
            }
            entry<ProfileRoute> {
                ProfileScreen(profile = profile, onEdit = { backStack.add(EditProfileRoute) }, onSignOut = onSignOut, onBack = back)
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
        },
    )
}

private fun NavBackStack<NavKey>.replaceLast(route: NavKey) {
    removeLastOrNull()
    add(route)
}
