package com.harsh.desicrics
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.navigation.AppNavigation
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.common_try_again
import com.harsh.desicrics.resources.profile_sign_out
import com.harsh.desicrics.resources.session_error_body
import com.harsh.desicrics.resources.session_error_title
import com.harsh.desicrics.session.SessionState
import com.harsh.desicrics.session.SessionViewModel
import com.harsh.desicrics.ui.profile.EditProfileScreen
import com.harsh.desicrics.ui.signin.SignInScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The whole app. [link] is the path of a link it was opened with ("/join/{code}", "/m/{matchId}"); it waits
 * until someone is signed in, then [onLinkHandled] clears it.
 */
@Composable
fun App(link: String? = null, onLinkHandled: () -> Unit = {}) {
    DesiTheme {
        Surface(Modifier.fillMaxSize(), color = DesiTheme.colors.bg) {
            val session = koinViewModel<SessionViewModel>()
            val state by session.state.collectAsStateWithLifecycle()
            // Auth gates the whole app; navigation only exists once there's a profile.
            when (val current = state) {
                SessionState.Loading -> FullScreenLoading()
                SessionState.SignedOut -> SignInScreen()
                is SessionState.NeedsProfile -> EditProfileScreen(isOnboarding = true, onDone = {}, onBack = null)
                is SessionState.SignedIn -> AppNavigation(current.profile, onSignOut = session::signOut, link = link, onLinkHandled = onLinkHandled)
                SessionState.Error -> SessionErrorScreen(onRetry = session::retry, onSignOut = session::signOut)
            }
        }
    }
}

@Composable
private fun SessionErrorScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    ErrorState(
        title = stringResource(Res.string.session_error_title),
        body = stringResource(Res.string.session_error_body),
        modifier = Modifier.background(DesiTheme.colors.bg).safeDrawingPadding(),
        onRetry = onRetry,
        secondary = {
            GhostButton(stringResource(Res.string.profile_sign_out), onClick = onSignOut, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Logout)
        },
    )
}

@Preview
@Composable
private fun SessionErrorPreview() = DesiTheme(darkTheme = false) {
    Box(Modifier.background(DesiTheme.colors.bg)) { SessionErrorScreen(onRetry = {}, onSignOut = {}) }
}

@Preview
@Composable
private fun SessionErrorDarkPreview() = DesiTheme(darkTheme = true) {
    Box(Modifier.background(DesiTheme.colors.bg)) { SessionErrorScreen(onRetry = {}, onSignOut = {}) }
}
