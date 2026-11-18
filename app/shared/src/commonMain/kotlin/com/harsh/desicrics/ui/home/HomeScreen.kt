package com.harsh.desicrics.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.LiveBadge
import com.harsh.desicrics.designsystem.component.MatchCard
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.TeamLine
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.icon.DesiIllustrations
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.formatOvers
import com.harsh.desicrics.ui.text.matchStatusText
import com.harsh.desicrics.ui.text.oversText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    playerName: String,
    onNewMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onProfile: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val takeOver by viewModel.takeOver.collectAsStateWithLifecycle()
    var takingOver by rememberSaveable { mutableStateOf(false) }
    HomeContent(state, playerName, viewModel.currentUid, onNewMatch, onOpenMatch, onProfile, onTakeOver = { takingOver = true })
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
        PrimaryButton(
            text = stringResource(Res.string.home_take_over_submit),
            onClick = { onSubmit(code) },
            enabled = code.length == 6,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Home without its ViewModel, so previews and tests can render any state: a greeting, the "start a
 * match" card, then live matches and recent results.
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    playerName: String,
    currentUid: String?,
    onNewMatch: () -> Unit,
    onOpenMatch: (matchId: String) -> Unit,
    onProfile: () -> Unit,
    onTakeOver: () -> Unit = {},
) {
    val space = DesiTheme.space
    val live = state.matches.filter { it.status != MatchStatus.COMPLETED }
    val recent = state.matches.filter { it.status == MatchStatus.COMPLETED }

    LazyColumn(
        Modifier.fillMaxSize().background(DesiTheme.colors.bg),
        contentPadding = PaddingValues(start = space.screen, end = space.screen, bottom = space.s8),
        verticalArrangement = Arrangement.spacedBy(space.s3),
    ) {
        item { Greeting(playerName, onProfile) }
        item { StartMatchCard(onNewMatch) }
        item {
            GhostButton(stringResource(Res.string.home_take_over), onClick = onTakeOver, modifier = Modifier.fillMaxWidth().padding(bottom = space.s4))
        }
        if (state.loadFailed) {
            item { Text(stringResource(Res.string.home_error), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
        }
        when {
            state.loading -> item {
                Box(Modifier.fillMaxWidth().padding(space.s8), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DesiTheme.colors.brand)
                }
            }
            state.matches.isEmpty() -> item { EmptyMatches() }
        }
        if (live.isNotEmpty()) {
            item { SectionLabel(stringResource(Res.string.home_section_live)) }
            items(live, key = { it.id }) { HomeMatchCard(it, isMine = it.scorerUid == currentUid, onClick = { onOpenMatch(it.id) }) }
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel(stringResource(Res.string.home_section_recent), Modifier.padding(top = space.s4)) }
            items(recent, key = { it.id }) { HomeMatchCard(it, isMine = it.scorerUid == currentUid, onClick = { onOpenMatch(it.id) }) }
        }
        item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun Greeting(playerName: String, onProfile: () -> Unit) {
    val colors = DesiTheme.colors
    val profileLabel = stringResource(Res.string.home_profile)
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = DesiTheme.space.s4, bottom = DesiTheme.space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.app_name).uppercase(), style = DesiTheme.type.label, color = colors.brand)
            Text(
                stringResource(Res.string.home_greeting, playerName.trim().substringBefore(' ')),
                style = DesiTheme.type.scoreLg,
                color = colors.ink,
                maxLines = 1,
            )
        }
        Avatar(
            playerName,
            size = 44,
            modifier = Modifier
                .clip(DesiTheme.radius.full)
                .clickable(role = Role.Button, onClick = onProfile)
                .semantics { contentDescription = profileLabel },
        )
    }
}

/** The main action: a green card with the ball, like the sign-in header. */
@Composable
private fun StartMatchCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DesiTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(DesiTheme.radius.lg)
            .background(colors.brandStrong)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Image(
            painterResource(DesiIllustrations.BallHero),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .requiredWidth(150.dp)
                .aspectRatio(54f / 34f)
                // Let the ball run off the card's edge.
                .offset(x = 36.dp, y = DesiTheme.space.s4),
        )
        Column(
            Modifier.padding(DesiTheme.space.s5).padding(end = 96.dp),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        ) {
            Text(stringResource(Res.string.home_start_title), style = DesiTheme.type.titleLg, color = colors.onBrandStrong)
            Text(stringResource(Res.string.home_start_body), style = DesiTheme.type.body, color = colors.onBrandStrong.copy(alpha = 0.8f))
            Row(
                Modifier
                    .padding(top = DesiTheme.space.s2)
                    .clip(DesiTheme.radius.full)
                    .background(colors.onBrandStrong)
                    .padding(start = DesiTheme.space.s4, end = DesiTheme.space.s3, top = DesiTheme.space.s2, bottom = DesiTheme.space.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
            ) {
                Text(stringResource(Res.string.home_start_action), style = DesiTheme.type.button, color = colors.brandStrong)
                Icon(painterResource(DesiIcons.Forward), contentDescription = null, tint = colors.brandStrong, modifier = Modifier.size(DesiTheme.size.iconSmall))
            }
        }
    }
}

@Composable
private fun EmptyMatches() {
    val colors = DesiTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(vertical = DesiTheme.space.s8, horizontal = DesiTheme.space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        Box(Modifier.size(56.dp).clip(DesiTheme.radius.full).background(colors.brandSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(DesiIcons.Cricket), contentDescription = null, tint = colors.brand)
        }
        Text(stringResource(Res.string.home_empty_title), style = DesiTheme.type.titleMd, color = colors.ink, modifier = Modifier.padding(top = DesiTheme.space.s2))
        Text(stringResource(Res.string.home_empty_body), style = DesiTheme.type.body, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/** A match in the feed: each team's score in batting order, with the chase, toss or result underneath. */
@Composable
private fun HomeMatchCard(match: Match, isMine: Boolean, onClick: () -> Unit) {
    val battingOrder = listOf(match.battingFirst, match.battingFirst.other)
    val current = match.innings.lastOrNull()?.battingSide
    val teams = battingOrder.map { side ->
        val innings = match.innings.firstOrNull { it.battingSide == side }
        TeamLine(
            name = match.team(side).name,
            score = innings?.let { "${it.runs}/${it.wickets}" },
            overs = innings?.let { formatOvers(it.legalBalls) },
            // Highlight the side batting now, or the winner once it's over.
            batting = when (val result = match.result) {
                null -> side == current
                else -> result.winner == null || result.winner == side
            },
        )
    }
    val overs = oversText(match.oversPerInnings)
    MatchCard(
        teams = teams,
        status = {
            when {
                match.status == MatchStatus.COMPLETED -> StatusLabel(stringResource(Res.string.home_status_result))
                match.innings.isEmpty() -> StatusLabel(stringResource(Res.string.home_status_not_started))
                else -> LiveBadge()
            }
        },
        meta = if (isMine) stringResource(Res.string.home_meta_scoring, overs) else overs,
        footer = matchStatusText(match),
        onClick = onClick,
    )
}

@Composable
private fun StatusLabel(text: String) {
    Text(text.uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
}

// Previews: sample teams and scores only; all labels come from string resources.

private fun previewTeam(name: String) = Team(name, listOf(Player("$name-1", "Player")))

private val PreviewLive = Match(
    id = "live", createdBy = "me", createdAt = 0,
    teamA = previewTeam("Andheri Strikers"), teamB = previewTeam("Powai XI"),
    oversPerInnings = 20, tossWinner = TeamSide.B, tossDecision = TossDecision.BAT, status = MatchStatus.IN_PROGRESS,
    innings = listOf(InningsScore(TeamSide.B, 168, 7, 120), InningsScore(TeamSide.A, 142, 6, 100)),
)

private val PreviewNotStarted = PreviewLive.copy(id = "new", createdBy = "someone", innings = emptyList(), oversPerInnings = 10)

private val PreviewDone = PreviewLive.copy(
    id = "done", status = MatchStatus.COMPLETED,
    innings = listOf(InningsScore(TeamSide.B, 168, 7, 120), InningsScore(TeamSide.A, 151, 9, 120)),
    result = MatchResult(TeamSide.B, 17, MarginUnit.RUNS),
)

@Preview
@Composable
private fun HomePreview() = DesiTheme(darkTheme = false) {
    HomeContent(HomeUiState(loading = false, matches = listOf(PreviewLive, PreviewNotStarted, PreviewDone)), "Rohan Iyer", "me", {}, {}, {})
}

@Preview
@Composable
private fun HomeDarkPreview() = DesiTheme(darkTheme = true) {
    HomeContent(HomeUiState(loading = false, matches = listOf(PreviewLive, PreviewDone)), "Rohan Iyer", "me", {}, {}, {})
}

@Preview
@Composable
private fun HomeEmptyPreview() = DesiTheme(darkTheme = false) {
    HomeContent(HomeUiState(loading = false), "Zoya", null, {}, {}, {})
}

@Preview
@Composable
private fun HomeOfflinePreview() = DesiTheme(darkTheme = false) {
    HomeContent(HomeUiState(loading = false, matches = listOf(PreviewDone), loadFailed = true), "Rohan Iyer", "me", {}, {}, {})
}
