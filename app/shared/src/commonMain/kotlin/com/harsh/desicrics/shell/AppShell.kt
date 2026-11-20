package com.harsh.desicrics.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.data.ModePreference
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.navigation.AppNavigation
import com.harsh.desicrics.simple.SimpleApp
import org.koin.compose.koinInject

/**
 * The signed-in app, in the mode picked on this phone. This is the only place that knows both modes exist:
 * each has its own screens and navigation, and switching swaps one whole shell for the other.
 */
@Composable
fun AppShell(
    profile: UserProfile,
    onSignOut: () -> Unit,
    link: String?,
    onLinkHandled: () -> Unit,
    askForNotifications: Boolean,
) {
    val mode by koinInject<ModePreference>().mode.collectAsStateWithLifecycle()
    when (mode) {
        AppMode.FULL -> AppNavigation(profile, onSignOut, link, onLinkHandled, askForNotifications)
        AppMode.SIMPLE -> SimpleApp(profile, onSignOut, link, onLinkHandled, askForNotifications)
    }
}
