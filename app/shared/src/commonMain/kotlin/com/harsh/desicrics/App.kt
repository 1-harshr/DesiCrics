package com.harsh.desicrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.session.SessionState
import com.harsh.desicrics.session.SessionViewModel
import com.harsh.desicrics.ui.components.FullScreenLoading
import com.harsh.desicrics.ui.home.HomeScreen
import com.harsh.desicrics.ui.match.MatchScreen
import com.harsh.desicrics.ui.newmatch.NewMatchScreen
import com.harsh.desicrics.ui.profile.EditProfileScreen
import com.harsh.desicrics.ui.profile.ProfileScreen
import com.harsh.desicrics.ui.signin.SignInScreen
import com.harsh.desicrics.ui.theme.DesiCricsTheme
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable object HomeRoute
@Serializable object ProfileRoute
@Serializable object EditProfileRoute
@Serializable object NewMatchRoute
@Serializable data class MatchRoute(val matchId: String)

@Composable
fun App() {
    DesiCricsTheme {
        Surface(Modifier.fillMaxSize()) {
            val session = koinViewModel<SessionViewModel>()
            val state by session.state.collectAsStateWithLifecycle()
            // Auth gates the whole app; the nav graph only exists once there's a profile.
            when (val current = state) {
                SessionState.Loading -> FullScreenLoading()
                SessionState.SignedOut -> SignInScreen()
                is SessionState.NeedsProfile -> EditProfileScreen(isOnboarding = true, onDone = {}, onBack = null)
                is SessionState.SignedIn -> MainNavHost(current.profile, onSignOut = session::signOut)
                is SessionState.Error -> SessionErrorScreen(current.message, onRetry = session::retry, onSignOut = session::signOut)
            }
        }
    }
}

@Composable
private fun SessionErrorScreen(message: String, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Couldn't load your profile", style = MaterialTheme.typography.titleMedium)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }
}

@Composable
private fun MainNavHost(profile: UserProfile, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onNewMatch = { navController.navigate(NewMatchRoute) },
                onOpenMatch = { navController.navigate(MatchRoute(it)) },
                onProfile = { navController.navigate(ProfileRoute) },
            )
        }
        composable<ProfileRoute> {
            ProfileScreen(
                profile = profile,
                onEdit = { navController.navigate(EditProfileRoute) },
                onSignOut = onSignOut,
                onBack = navController::popBackStack,
            )
        }
        composable<EditProfileRoute> {
            EditProfileScreen(
                isOnboarding = false,
                onDone = navController::popBackStack,
                onBack = navController::popBackStack,
            )
        }
        composable<NewMatchRoute> {
            NewMatchScreen(
                onCreated = { matchId ->
                    navController.navigate(MatchRoute(matchId)) {
                        popUpTo<NewMatchRoute> { inclusive = true }
                    }
                },
                onBack = navController::popBackStack,
            )
        }
        composable<MatchRoute> { entry ->
            MatchScreen(matchId = entry.toRoute<MatchRoute>().matchId, onBack = navController::popBackStack)
        }
    }
}
