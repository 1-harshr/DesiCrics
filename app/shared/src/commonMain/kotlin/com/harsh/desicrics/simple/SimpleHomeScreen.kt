package com.harsh.desicrics.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.data.AppMode
import com.harsh.desicrics.data.ModePreference
import com.harsh.desicrics.designsystem.component.BallSpinner
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.LocalNavBarClearance
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.app_name
import com.harsh.desicrics.resources.home_empty_body
import com.harsh.desicrics.resources.home_empty_title
import com.harsh.desicrics.resources.home_error_body
import com.harsh.desicrics.resources.home_error_title
import com.harsh.desicrics.resources.home_greeting
import com.harsh.desicrics.resources.home_profile
import com.harsh.desicrics.resources.home_start_action
import com.harsh.desicrics.resources.home_start_body
import com.harsh.desicrics.resources.home_start_title
import com.harsh.desicrics.resources.home_take_over
import com.harsh.desicrics.resources.home_take_over_code
import com.harsh.desicrics.resources.home_take_over_failed
import com.harsh.desicrics.resources.home_take_over_hint
import com.harsh.desicrics.resources.home_take_over_submit
import com.harsh.desicrics.resources.simple_switch_full
import com.harsh.desicrics.resources.simple_switch_full_body
import com.harsh.desicrics.resources.simple_your_matches
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.home.HomeUiState
import com.harsh.desicrics.ui.home.HomeViewModel
import com.harsh.desicrics.ui.home.TakeOverState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SimpleHomeScreen(
    profile: UserProfile,
    onNewMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onProfile: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val takeOver by viewModel.takeOver.collectAsStateWithLifecycle()
    val modes = koinInject<ModePreference>()
    var takingOver by rememberSaveable { mutableStateOf(false) }
    SimpleHomeContent(
        state = state,
        playerName = profile.name,
        playerPhoto = profile.photoUrl,
        onNewMatch = onNewMatch,
        onOpenMatch = onOpenMatch,
        onProfile = onProfile,
        onTakeOver = { takingOver = true },
        onSwitchToFull = { modes.select(AppMode.FULL) },
    )
    if (takingOver) {
        TakeOverSheet(
            state = takeOver,
            onSubmit = { code ->
                viewModel.takeOver(code) { matchId ->
                    takingOver = false
                    onOpenMatch(matchId)
                }
            },
            onDismiss = {
                takingOver = false
                viewModel.resetTakeOver()
            },
        )
    }
}

/**
 * Home in Simple mode: a greeting, one big "start a match" card, the user's matches, taking over scoring
 * from another phone, and at the foot the way to Full mode.
 */
@Composable
internal fun SimpleHomeContent(
    state: HomeUiState,
    playerName: String,
    playerPhoto: String?,
    onNewMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onProfile: () -> Unit,
    onTakeOver: () -> Unit,
    onSwitchToFull: () -> Unit,
) {
    val space = DesiTheme.space
    val matches = state.feed.mine
    LazyColumn(
        Modifier.fillMaxSize().background(DesiTheme.colors.bg),
        contentPadding = PaddingValues(start = space.screen, end = space.screen, bottom = space.s8 + LocalNavBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(space.s4),
    ) {
        item { Greeting(playerName, playerPhoto, onProfile) }
        item { StartCard(onNewMatch) }
        if (state.loadFailed) {
            item { ErrorBanner(stringResource(Res.string.home_error_body), title = stringResource(Res.string.home_error_title), icon = DesiIcons.CloudOff, warning = true) }
        }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(space.s8), contentAlignment = Alignment.Center) { BallSpinner() }
            }
            matches.isEmpty() && !state.loadFailed -> item {
                EmptyState(DesiIcons.Cricket, stringResource(Res.string.home_empty_title), body = stringResource(Res.string.home_empty_body))
            }
            matches.isNotEmpty() -> item { SectionHeader(stringResource(Res.string.simple_your_matches)) }
        }
        items(matches, key = { it.id }) { SimpleMatchCard(it, onClick = { onOpenMatch(it.id) }) }
        item {
            GhostButton(stringResource(Res.string.home_take_over), onClick = onTakeOver, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Swap)
        }
        item { SwitchToFull(onSwitchToFull) }
    }
}

@Composable
private fun Greeting(playerName: String, playerPhoto: String?, onProfile: () -> Unit) {
    val colors = DesiTheme.colors
    val profileLabel = stringResource(Res.string.home_profile)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(vertical = DesiTheme.space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.app_name).uppercase(), style = DesiTheme.type.label, color = colors.brand)
            Text(stringResource(Res.string.home_greeting, playerName.trim().substringBefore(' ')), style = DesiTheme.type.scoreLg, color = colors.ink, maxLines = 1)
        }
        PhotoAvatar(
            playerName,
            playerPhoto,
            size = 44,
            modifier = Modifier.clip(DesiTheme.radius.full).clickable(role = Role.Button, onClick = onProfile).semantics { contentDescription = profileLabel },
        )
    }
}

/** The one thing this mode is for, as the biggest thing on the screen. */
@Composable
private fun StartCard(onNewMatch: () -> Unit) {
    val colors = DesiTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).background(colors.brandStrong).padding(DesiTheme.space.s5),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        Text(stringResource(Res.string.home_start_title), style = DesiTheme.type.titleLg, color = colors.onBrandStrong)
        Text(stringResource(Res.string.home_start_body), style = DesiTheme.type.body, color = colors.onBrandStrong.copy(alpha = 0.8f))
        // Light on the green card, so the one button on Home stands out.
        Row(
            Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2).clip(DesiTheme.radius.full).background(colors.onBrandStrong)
                .clickable(role = Role.Button, onClick = onNewMatch).padding(DesiTheme.space.s4),
            horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(DesiIcons.Add), contentDescription = null, tint = colors.brandStrong)
            Text(stringResource(Res.string.home_start_action), style = DesiTheme.type.button, color = colors.brandStrong)
        }
    }
}

@Composable
private fun SwitchToFull(onSwitch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = DesiTheme.space.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Text(stringResource(Res.string.simple_switch_full_body), style = DesiTheme.type.caption, color = DesiTheme.colors.muted, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.simple_switch_full),
            style = DesiTheme.type.bodyStrong,
            color = DesiTheme.colors.brand,
            modifier = Modifier.clip(DesiTheme.radius.full).clickable(role = Role.Button, onClick = onSwitch).padding(DesiTheme.space.s2),
        )
    }
}

/** Enter the code from the scorer's phone to carry on scoring here. */
@Composable
private fun TakeOverSheet(state: TakeOverState, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.home_take_over), onDismiss, stringResource(Res.string.home_take_over_hint)) {
        DesiTextField(
            value = code,
            onValueChange = { new -> code = new.filter { it.isDigit() }.take(6) },
            label = stringResource(Res.string.home_take_over_code),
            error = stringResource(Res.string.home_take_over_failed).takeIf { state.failed },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onSubmit(code) }),
        )
        PrimaryButton(stringResource(Res.string.home_take_over_submit), onClick = { onSubmit(code) }, enabled = code.length == 6, loading = state.saving, modifier = Modifier.fillMaxWidth())
    }
}

// Previews: sample teams and scores only; all labels come from string resources.

private fun previewTeam(name: String) = Team(name, listOf(Player("$name-1", "Player")))

internal val SimplePreviewMatches = listOf(
    Match(
        id = "live", createdBy = "me", createdAt = 0,
        teamA = previewTeam("Andheri Strikers"), teamB = previewTeam("Powai XI"),
        oversPerInnings = 10, tossWinner = TeamSide.B, tossDecision = TossDecision.BAT, status = MatchStatus.IN_PROGRESS,
        innings = listOf(InningsScore(TeamSide.B, 84, 3, 60), InningsScore(TeamSide.A, 41, 2, 31)),
    ),
)

@Preview
@Composable
private fun SimpleHomePreview() = DesiTheme(darkTheme = false) {
    SimpleHomeContent(HomeUiState(loading = false, feed = TodayFeed(mine = SimplePreviewMatches)), "Rohan Mehta", null, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun SimpleHomeEmptyDarkPreview() = DesiTheme(darkTheme = true) {
    SimpleHomeContent(HomeUiState(loading = false), "Rohan Mehta", null, {}, {}, {}, {}, {})
}
