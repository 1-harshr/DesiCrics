package com.harsh.desicrics.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.data.ModePreference
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.LocalNavBarClearance
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.mode_title
import com.harsh.desicrics.resources.profile_no_matches
import com.harsh.desicrics.resources.profile_settings
import com.harsh.desicrics.resources.profile_sign_out
import com.harsh.desicrics.resources.simple_your_matches
import com.harsh.desicrics.shell.AppModeSheet
import com.harsh.desicrics.shell.label
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.profile.ProfileViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SimpleMeScreen(
    profile: UserProfile,
    onSignOut: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modes = koinInject<ModePreference>()
    val mode by modes.mode.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    // Coming back from a match the user just scored.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    SimpleMeContent(profile, state.matches, mode, onOpenMatch, onMode = { picking = true }, onSignOut = onSignOut)
    if (picking) {
        AppModeSheet(mode, onSelect = { picking = false; modes.select(it) }, onDismiss = { picking = false })
    }
}

/** Me in Simple mode: who you are, your matches, and the few settings this mode has. */
@Composable
internal fun SimpleMeContent(
    profile: UserProfile,
    matches: List<Match>?,
    mode: AppMode,
    onOpenMatch: (matchId: String) -> Unit,
    onMode: () -> Unit,
    onSignOut: () -> Unit,
) {
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    LazyColumn(
        Modifier.fillMaxSize().background(colors.bg),
        contentPadding = PaddingValues(start = space.screen, end = space.screen, bottom = space.s8 + LocalNavBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(space.s4),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(vertical = space.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.s4),
            ) {
                PhotoAvatar(profile.name, profile.photoUrl, size = 64)
                Column {
                    Text(profile.name, style = DesiTheme.type.titleLg, color = colors.ink, maxLines = 2)
                    if (profile.city.isNotBlank()) Text(profile.city, style = DesiTheme.type.body, color = colors.muted)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.profile_settings), icon = DesiIcons.Settings)
                ListCard {
                    ListRow(stringResource(Res.string.mode_title), subtitle = stringResource(mode.label), leading = { IconTile(DesiIcons.Swap) }, onClick = onMode)
                    ListRow(stringResource(Res.string.profile_sign_out), leading = { IconTile(DesiIcons.Logout) }, divider = true, onClick = onSignOut)
                }
            }
        }
        item { SectionHeader(stringResource(Res.string.simple_your_matches), Modifier.padding(top = space.s2), icon = DesiIcons.History) }
        if (matches?.isEmpty() == true) {
            item { EmptyState(DesiIcons.Cricket, stringResource(Res.string.profile_no_matches)) }
        }
        items(matches.orEmpty(), key = { it.id }) { SimpleMatchCard(it, onClick = { onOpenMatch(it.id) }) }
    }
}

// Previews: a sample profile only; all labels come from string resources.

private val PreviewProfile = UserProfile(uid = "me", name = "Rohan Mehta", phone = "", city = "Mumbai")

@Preview
@Composable
private fun SimpleMePreview() = DesiTheme(darkTheme = false) {
    SimpleMeContent(PreviewProfile, SimplePreviewMatches, AppMode.SIMPLE, {}, {}, {})
}
