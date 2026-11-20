package com.harsh.desicrics.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.OverStrip
import com.harsh.desicrics.designsystem.component.PadKey
import com.harsh.desicrics.designsystem.component.PadKeyStyle
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RunPad
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.common_back
import com.harsh.desicrics.resources.common_cancel
import com.harsh.desicrics.resources.error_connection_body
import com.harsh.desicrics.resources.error_match_missing_title
import com.harsh.desicrics.resources.error_match_title
import com.harsh.desicrics.resources.match_dismiss
import com.harsh.desicrics.resources.match_first_innings_score
import com.harsh.desicrics.resources.match_innings_first
import com.harsh.desicrics.resources.match_innings_label
import com.harsh.desicrics.resources.match_innings_second
import com.harsh.desicrics.resources.match_need
import com.harsh.desicrics.resources.match_not_found
import com.harsh.desicrics.resources.match_not_your_match
import com.harsh.desicrics.resources.match_overs_of
import com.harsh.desicrics.resources.match_pick_players_first
import com.harsh.desicrics.resources.match_title
import com.harsh.desicrics.resources.match_undo_last_ball
import com.harsh.desicrics.resources.score_saving
import com.harsh.desicrics.resources.scoring_error_save_failed
import com.harsh.desicrics.resources.scoring_key_no_ball
import com.harsh.desicrics.resources.scoring_key_out
import com.harsh.desicrics.resources.scoring_key_undo
import com.harsh.desicrics.resources.scoring_key_wide
import com.harsh.desicrics.resources.match_load_failed
import com.harsh.desicrics.resources.scoring_pick_bowler
import com.harsh.desicrics.resources.scoring_pick_non_striker
import com.harsh.desicrics.resources.scoring_pick_striker
import com.harsh.desicrics.resources.scoring_rule_warning_record
import com.harsh.desicrics.resources.scoring_rule_warning_title
import com.harsh.desicrics.resources.scoring_wicket_confirm
import com.harsh.desicrics.resources.scoring_wicket_how
import com.harsh.desicrics.resources.scoring_wicket_title
import com.harsh.desicrics.resources.scoring_wicket_who
import com.harsh.desicrics.resources.simple_batter_figures
import com.harsh.desicrics.resources.simple_batting
import com.harsh.desicrics.resources.simple_bowler_figures
import com.harsh.desicrics.resources.simple_bowling
import com.harsh.desicrics.resources.simple_pick
import com.harsh.desicrics.resources.simple_this_over
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.ui.match.MatchUiState
import com.harsh.desicrics.ui.match.MatchViewModel
import com.harsh.desicrics.ui.match.ScoringError
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.text.ballFor
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.ui.text.resultText
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun SimpleMatchScreen(
    matchId: String,
    onBack: () -> Unit,
    viewModel: MatchViewModel = koinViewModel { parametersOf(matchId) },
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    SimpleMatchContent(
        ui,
        SimpleMatchActions(
            onBack = onBack,
            onStriker = viewModel::selectStriker,
            onNonStriker = viewModel::selectNonStriker,
            onBowler = viewModel::selectBowler,
            onRuns = { viewModel.recordRuns(it) },
            onExtra = { viewModel.recordExtra(it, 0) },
            onWicket = { kind, out -> viewModel.recordWicket(kind, out, fielderId = null) },
            onUndo = viewModel::undo,
            onDismissError = viewModel::dismissError,
            onRetry = viewModel::retry,
            onRecordPending = viewModel::recordPendingBall,
            onDropPending = viewModel::dropPendingBall,
        ),
    )
}

/** What the Simple scoring screen can ask for. */
internal class SimpleMatchActions(
    val onBack: () -> Unit,
    val onStriker: (playerId: String) -> Unit,
    val onNonStriker: (playerId: String) -> Unit,
    val onBowler: (playerId: String) -> Unit,
    val onRuns: (Int) -> Unit,
    /** A wide or no-ball with nothing else run. */
    val onExtra: (ExtraType) -> Unit,
    val onWicket: (DismissalKind, playerOutId: String) -> Unit,
    val onUndo: () -> Unit,
    val onDismissError: () -> Unit,
    val onRetry: () -> Unit,
    val onRecordPending: () -> Unit,
    val onDropPending: () -> Unit,
)

private enum class Pick { STRIKER, NON_STRIKER, BOWLER }

/** The ways out Simple mode offers; the rarer ones stay in Full mode. */
private val SimpleDismissals = listOf(
    DismissalKind.BOWLED, DismissalKind.CAUGHT, DismissalKind.RUN_OUT, DismissalKind.LBW, DismissalKind.STUMPED, DismissalKind.HIT_WICKET,
)

/**
 * The match in Simple mode: the score, who's in, this over and a run pad with only the keys most games
 * need. The scorer is asked for the openers, each new batter and each new bowler as they're needed.
 */
@Composable
internal fun SimpleMatchContent(ui: MatchUiState, actions: SimpleMatchActions) {
    val state = ui.state
    Column(Modifier.fillMaxSize().background(DesiTheme.colors.bg)) {
        BrandStatusBar()
        when {
            ui.loading -> FullScreenLoading()
            ui.notFound || state == null -> ErrorState(
                title = stringResource(if (ui.notFound) Res.string.error_match_missing_title else Res.string.error_match_title),
                body = stringResource(if (ui.notFound) Res.string.match_not_found else Res.string.error_connection_body),
                onRetry = actions.onRetry.takeIf { !ui.notFound },
                secondary = { GhostButton(stringResource(Res.string.common_back), onClick = actions.onBack, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Back) },
            )
            else -> Live(ui, state, actions)
        }
    }
}

@Composable
private fun ColumnScope.Live(ui: MatchUiState, state: MatchState, actions: SimpleMatchActions) {
    val innings = state.current
    var picking by remember { mutableStateOf<Pick?>(null) }
    var wicket by remember { mutableStateOf(false) }
    val scoring = ui.isScorer && !state.isComplete
    // The pick the scorer has to make before the next ball. It opens by itself once per gap.
    val needed = when {
        !ui.isScorer || !ui.canEdit || ui.pendingBall != null -> null
        ui.strikerId == null -> Pick.STRIKER
        ui.nonStrikerId == null && !ui.battingAlone -> Pick.NON_STRIKER
        ui.bowlerId == null -> Pick.BOWLER
        else -> null
    }
    var autoOpened by remember { mutableStateOf<Pair<Pick, Int>?>(null) }
    LaunchedEffect(needed, ui.nextSeq, picking == null) {
        val gap = needed?.let { it to ui.nextSeq } ?: return@LaunchedEffect
        if (picking == null && autoOpened != gap) {
            autoOpened = gap
            picking = needed
        }
    }

    Header(state, actions.onBack)
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(DesiTheme.space.screen),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        if (!state.isComplete) Crease(ui, innings, onPick = { pick -> picking = pick.takeIf { ui.canEdit } })
        if (ui.saving) Text(stringResource(Res.string.score_saving), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        if (!ui.isScorer && !state.isComplete) Text(stringResource(Res.string.match_not_your_match), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        if (state.isComplete && ui.canUndo) {
            GhostButton(stringResource(Res.string.match_undo_last_ball), onClick = actions.onUndo, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.History)
        }
    }
    if (scoring) Pad(ui, actions, onOut = { wicket = true })

    picking?.let { pick ->
        val players = when (pick) {
            Pick.STRIKER -> innings.canComeIn.filter { it.id != ui.nonStrikerId }
            Pick.NON_STRIKER -> innings.canComeIn.filter { it.id != ui.strikerId }
            Pick.BOWLER -> innings.bowlingTeam.players
        }
        PlayerSheet(pick, players, onPick = {
            when (pick) {
                Pick.STRIKER -> actions.onStriker(it.id)
                Pick.NON_STRIKER -> actions.onNonStriker(it.id)
                Pick.BOWLER -> actions.onBowler(it.id)
            }
            picking = null
        }, onDismiss = { picking = null })
    }
    if (wicket) {
        val batting = innings.battingTeam.players
        val striker = batting.firstOrNull { it.id == ui.strikerId }
        if (striker == null) {
            wicket = false
        } else {
            WicketSheet(striker, batting.firstOrNull { it.id == ui.nonStrikerId }, onOut = { kind, out ->
                actions.onWicket(kind, out.id)
                wicket = false
            }, onDismiss = { wicket = false })
        }
    }
    ui.pendingBall?.let { pending ->
        AlertDialog(
            onDismissRequest = actions.onDropPending,
            title = { Text(stringResource(Res.string.scoring_rule_warning_title)) },
            text = { Text(pending.warnings.map { stringResource(it.label) }.joinToString("\n")) },
            confirmButton = { TextButton(onClick = actions.onRecordPending) { Text(stringResource(Res.string.scoring_rule_warning_record)) } },
            dismissButton = { TextButton(onClick = actions.onDropPending) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
}

/** The green header: the teams, then the batting side's score and how the innings stands, or the result. */
@Composable
private fun Header(state: MatchState, onBack: () -> Unit) {
    val colors = DesiTheme.colors
    val onGreen = colors.onBrandStrong
    val innings = state.current
    val match = state.match
    val result = state.result
    Column(
        Modifier.fillMaxWidth().background(colors.brandStrong).padding(start = DesiTheme.space.screen, end = DesiTheme.space.screen, bottom = DesiTheme.space.s5),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = -DesiTheme.space.s3)) {
                Icon(painterResource(DesiIcons.Back), stringResource(Res.string.common_back), tint = onGreen)
            }
            Text(
                stringResource(Res.string.match_title, match.teamA.name, match.teamB.name),
                style = DesiTheme.type.titleMd,
                color = onGreen,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        val inningsName = stringResource(if (innings.number == 1) Res.string.match_innings_first else Res.string.match_innings_second)
        Text(
            stringResource(Res.string.match_innings_label, innings.battingTeam.name, inningsName).uppercase(),
            style = DesiTheme.type.label,
            color = onGreen.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(innings.scoreText, style = DesiTheme.type.scoreXl, color = onGreen)
            Text(
                stringResource(Res.string.match_overs_of, innings.oversText, innings.maxBalls / 6),
                style = DesiTheme.type.data,
                color = onGreen.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = DesiTheme.space.s2),
            )
        }
        val line = when {
            result != null -> resultText(match, result)
            else -> innings.runsNeeded?.let { pluralStringResource(Res.plurals.match_need, innings.ballsRemaining, it, innings.ballsRemaining) }
        }
        if (line != null) Text(line, style = DesiTheme.type.bodyStrong, color = onGreen)
        if (state.innings.size > 1) {
            val first = state.innings.first()
            Text(
                stringResource(Res.string.match_first_innings_score, first.battingTeam.name, first.scoreText, first.oversText),
                style = DesiTheme.type.caption,
                color = onGreen.copy(alpha = 0.7f),
            )
        }
    }
}

/** Both batters and the bowler, with a Pick button where someone's needed, then this over. */
@Composable
private fun Crease(ui: MatchUiState, innings: InningsState, onPick: (Pick) -> Unit) {
    val batting = stringResource(Res.string.simple_batting)
    fun batter(id: String?) = innings.batters.firstOrNull { it.player.id == id }
    ListCard {
        listOf(Pick.STRIKER to ui.strikerId, Pick.NON_STRIKER to ui.nonStrikerId).forEach { (pick, id) ->
            // A last batter on their own leaves the other end empty, and that's not a gap to fill.
            if (pick == Pick.NON_STRIKER && id == null && ui.battingAlone) return@forEach
            val card = batter(id)
            Slot(
                name = card?.player?.name,
                role = batting,
                figures = card?.let { stringResource(Res.string.simple_batter_figures, it.runs, it.balls) },
                onStrike = pick == Pick.STRIKER,
                divider = pick != Pick.STRIKER,
                onPick = { onPick(pick) }.takeIf { ui.isScorer },
            )
        }
        val bowler = innings.bowlers.firstOrNull { it.player.id == ui.bowlerId }
        Slot(
            name = bowler?.player?.name ?: innings.bowlingTeam.players.firstOrNull { it.id == ui.bowlerId }?.name,
            role = stringResource(Res.string.simple_bowling),
            figures = bowler?.let { stringResource(Res.string.simple_bowler_figures, it.wickets, it.runs, it.oversText) },
            divider = true,
            onPick = { onPick(Pick.BOWLER) }.takeIf { ui.isScorer },
        )
    }
    if (innings.currentOver.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(stringResource(Res.string.simple_this_over).uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
            OverStrip(innings.currentOver.map { ballFor(it) })
        }
    }
}

@Composable
private fun Slot(name: String?, role: String, figures: String?, divider: Boolean, onPick: (() -> Unit)?, onStrike: Boolean = false) {
    val colors = DesiTheme.colors
    if (name == null) {
        ListRow(stringResource(Res.string.simple_pick), subtitle = role, divider = divider, titleColor = colors.brand, onClick = onPick)
        return
    }
    ListRow(
        if (onStrike) "$name *" else name,
        subtitle = role,
        divider = divider,
        trailing = { if (figures != null) Text(figures, style = DesiTheme.type.data, color = colors.ink) },
    )
}

/** The keys most games need: runs, wide, no-ball, out and undo. */
@Composable
private fun Pad(ui: MatchUiState, actions: SimpleMatchActions, onOut: () -> Unit) {
    val colors = DesiTheme.colors
    val canScore = ui.canScore
    val keys = listOf(
        PadKey("0", "0", enabled = canScore), PadKey("1", "1", enabled = canScore),
        PadKey("2", "2", enabled = canScore), PadKey("3", "3", enabled = canScore),
        PadKey("4", "4", PadKeyStyle.Four, enabled = canScore), PadKey("6", "6", PadKeyStyle.Six, enabled = canScore),
        PadKey("wd", stringResource(Res.string.scoring_key_wide), PadKeyStyle.Extra, enabled = canScore),
        PadKey("nb", stringResource(Res.string.scoring_key_no_ball), PadKeyStyle.Extra, enabled = canScore),
        PadKey("out", stringResource(Res.string.scoring_key_out), PadKeyStyle.Wicket, enabled = canScore),
        PadKey("undo", stringResource(Res.string.scoring_key_undo), PadKeyStyle.Utility, enabled = ui.canUndo),
    )
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Column(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(DesiTheme.space.screen),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
        ) {
            ui.error?.let { error ->
                val text = when (error) {
                    is ScoringError.Rejected -> stringResource(error.error.label)
                    ScoringError.SaveFailed -> stringResource(Res.string.scoring_error_save_failed)
                    ScoringError.LoadFailed -> stringResource(Res.string.match_load_failed)
                }
                ErrorBanner(text, action = stringResource(Res.string.match_dismiss), onAction = actions.onDismissError)
            }
            if (!canScore && !ui.saving && ui.error == null) {
                Text(stringResource(Res.string.match_pick_players_first), style = DesiTheme.type.body, color = colors.muted)
            }
            RunPad(keys, enabled = !ui.saving, onKey = { key ->
                when (key.id) {
                    "wd" -> actions.onExtra(ExtraType.WIDE)
                    "nb" -> actions.onExtra(ExtraType.NO_BALL)
                    "out" -> onOut()
                    "undo" -> actions.onUndo()
                    else -> actions.onRuns(key.id.toInt())
                }
            })
        }
    }
}

@Composable
private fun PlayerSheet(pick: Pick, players: List<Player>, onPick: (Player) -> Unit, onDismiss: () -> Unit) {
    val title = when (pick) {
        Pick.STRIKER -> Res.string.scoring_pick_striker
        Pick.NON_STRIKER -> Res.string.scoring_pick_non_striker
        Pick.BOWLER -> Res.string.scoring_pick_bowler
    }
    DesiSheet(stringResource(title), onDismiss) {
        ListCard {
            players.forEachIndexed { index, player ->
                ListRow(player.name, divider = index > 0, onClick = { onPick(player) })
            }
        }
    }
}

/** How they were out, and for a run out, which batter. Runs and fielders are left to Full mode. */
@Composable
private fun WicketSheet(striker: Player, nonStriker: Player?, onOut: (DismissalKind, Player) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf<DismissalKind?>(null) }
    var out by remember { mutableStateOf(striker) }
    DesiSheet(stringResource(Res.string.scoring_wicket_title), onDismiss) {
        Text(stringResource(Res.string.scoring_wicket_how), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
        ChoiceChips(SimpleDismissals, kind, label = { stringResource(it.label) }, onSelect = { picked ->
            kind = picked
            if (picked.strikerOnly) out = striker
        })
        val chosen = kind
        if (chosen != null && !chosen.strikerOnly && nonStriker != null) {
            Text(stringResource(Res.string.scoring_wicket_who), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
            ChoiceChips(listOf(striker, nonStriker), out, label = { it.name }, onSelect = { out = it })
        }
        PrimaryButton(
            stringResource(Res.string.scoring_wicket_confirm),
            onClick = { chosen?.let { onOut(it, out) } },
            enabled = chosen != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Previews: sample teams and balls only; all labels come from string resources.

private val PreviewMatch = Match(
    id = "preview", createdBy = "me", createdAt = 0,
    teamA = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan").mapIndexed { i, n -> Player("a$i", n) }),
    teamB = Team("Powai XI", listOf("Dev", "Esha", "Farhan").mapIndexed { i, n -> Player("b$i", n) }),
    oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT, scorerUid = "me",
)

internal fun simplePreviewUi(runs: List<Int> = listOf(1, 4, 0, 6, 1)): MatchUiState {
    val deliveries = runs.mapIndexed { i, r -> Delivery(i, 1, if (i % 2 == 0) "a0" else "a1", if (i % 2 == 0) "a1" else "a0", "b0", runsOffBat = r, timestamp = 0) }
    return MatchUiState(
        loading = false, match = PreviewMatch, deliveries = deliveries, isScorer = true,
        state = ScoringEngine.compute(PreviewMatch, deliveries, emptyList()),
    )
}

internal val SimplePreviewActions = SimpleMatchActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})

@Preview
@Composable
private fun SimpleMatchPreview() = DesiTheme(darkTheme = false) { SimpleMatchContent(simplePreviewUi(), SimplePreviewActions) }
