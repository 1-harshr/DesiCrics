package com.harsh.desicrics.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.Ball
import com.harsh.desicrics.designsystem.component.BallKind
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.LiveBadge
import com.harsh.desicrics.designsystem.component.OverStrip
import com.harsh.desicrics.designsystem.component.PadKey
import com.harsh.desicrics.designsystem.component.PadKeyStyle
import com.harsh.desicrics.designsystem.component.RunPad
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
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
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.ui.text.resultText
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MatchScreen(
    matchId: String,
    onBack: () -> Unit,
    viewModel: MatchViewModel = koinViewModel { parametersOf(matchId) },
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LightStatusBarIcons()
    MatchContent(
        ui = ui,
        actions = MatchActions(
            onBack = onBack,
            onSelectStriker = viewModel::selectStriker,
            onSelectNonStriker = viewModel::selectNonStriker,
            onSelectBowler = viewModel::selectBowler,
            onRuns = viewModel::recordRuns,
            onExtra = viewModel::recordExtra,
            onWicket = viewModel::recordWicket,
            onUndo = viewModel::undo,
            onDismissError = viewModel::dismissError,
        ),
    )
}

/** Everything the scoring screen can ask for, kept together so the content signature stays short. */
class MatchActions(
    val onBack: () -> Unit,
    val onSelectStriker: (playerId: String) -> Unit,
    val onSelectNonStriker: (playerId: String) -> Unit,
    val onSelectBowler: (playerId: String) -> Unit,
    val onRuns: (Int) -> Unit,
    val onExtra: (ExtraType, runs: Int) -> Unit,
    val onWicket: (DismissalKind, playerOutId: String, fielderId: String?, runsCompleted: Int) -> Unit,
    val onUndo: () -> Unit,
    val onDismissError: () -> Unit,
)

private enum class MatchTab { LIVE, SCORECARD }

/** A choice the scorer is making in a bottom sheet. */
private sealed interface ScoringSheet {
    data object Striker : ScoringSheet
    data object NonStriker : ScoringSheet
    data object Bowler : ScoringSheet
    data class Extra(val type: ExtraType) : ScoringSheet
    data object Wicket : ScoringSheet
}

/**
 * The scoring screen without its ViewModel, so previews and tests can render any state: a green
 * scoreboard, Live and Scorecard tabs, and for the scorer a run pad pinned at the bottom.
 */
@Composable
fun MatchContent(ui: MatchUiState, actions: MatchActions) {
    val colors = DesiTheme.colors
    val state = ui.state
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        BrandStatusBar()
        when {
            ui.loading -> FullScreenLoading()
            ui.notFound || state == null -> MessageScreen(
                if (ui.notFound) Res.string.match_not_found else Res.string.match_load_failed,
                actions.onBack,
            )
            else -> Scoring(ui, state, actions)
        }
    }
}

@Composable
private fun ColumnScope.Scoring(ui: MatchUiState, state: MatchState, actions: MatchActions) {
    var tab by rememberSaveable { mutableStateOf(MatchTab.LIVE) }
    var sheet by remember { mutableStateOf<ScoringSheet?>(null) }
    val innings = state.current

    Scoreboard(state, actions.onBack)
    SegmentedControl(
        options = MatchTab.entries,
        selected = tab,
        label = { stringResource(if (it == MatchTab.LIVE) Res.string.match_tab_live else Res.string.match_tab_scorecard) },
        onSelect = { tab = it },
        modifier = Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s3),
    )
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal = DesiTheme.space.screen).padding(bottom = DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        if (ui.offline) Text(stringResource(Res.string.match_offline), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        when (tab) {
            MatchTab.LIVE -> {
                if (!state.isComplete) {
                    CreaseCard(ui, innings, onChoose = { sheet = it })
                    ThisOver(innings)
                }
                if (!ui.isScorer) {
                    Text(stringResource(Res.string.match_not_your_match), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                }
                if (state.isComplete && ui.canUndo) {
                    GhostButton(stringResource(Res.string.match_undo_last_ball), onClick = actions.onUndo, modifier = Modifier.fillMaxWidth())
                }
            }
            MatchTab.SCORECARD -> state.innings.forEach { InningsScorecard(it) }
        }
    }
    // The pad only shows with the live view, so the scorecard gets the whole screen.
    if (ui.isScorer && !state.isComplete && tab == MatchTab.LIVE) {
        ScoringPad(ui, actions, onSheet = { sheet = it })
    }

    ScoringSheets(sheet, ui, innings, actions, onClose = { sheet = null })
}

/** The green header: teams, then the batting side's score, overs and where the chase stands. */
@Composable
private fun Scoreboard(state: MatchState, onBack: () -> Unit) {
    val colors = DesiTheme.colors
    val onGreen = colors.onBrandStrong
    val innings = state.current
    val match = state.match
    Column(
        Modifier.fillMaxWidth().background(colors.brandStrong)
            .padding(start = DesiTheme.space.screen, end = DesiTheme.space.screen, bottom = DesiTheme.space.s5),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!state.isComplete) {
                Box(Modifier.clip(DesiTheme.radius.full).background(colors.surface).padding(horizontal = DesiTheme.space.s3, vertical = 6.dp)) {
                    LiveBadge()
                }
            }
        }
        val inningsName = stringResource(if (innings.number == 1) Res.string.match_innings_first else Res.string.match_innings_second)
        Text(
            stringResource(Res.string.match_innings_label, innings.battingTeam.name, inningsName).uppercase(),
            style = DesiTheme.type.label,
            color = onGreen.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = DesiTheme.space.s2),
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(innings.scoreText, style = DesiTheme.type.scoreXl, color = onGreen)
            Text(
                stringResource(Res.string.match_overs_of, innings.oversText, match.oversPerInnings),
                style = DesiTheme.type.data,
                color = onGreen.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = DesiTheme.space.s2),
            )
        }
        val result = state.result
        if (result != null) {
            Text(resultText(match, result), style = DesiTheme.type.bodyStrong, color = onGreen)
        } else {
            val details = buildList {
                add(stringResource(Res.string.match_run_rate, innings.runRate.format2()))
                innings.target?.let { add(stringResource(Res.string.match_target, it)) }
                innings.runsNeeded?.let { add(pluralStringResource(Res.plurals.match_need, innings.ballsRemaining, it, innings.ballsRemaining)) }
            }
            Text(details.joinToString(" · "), style = DesiTheme.type.data, color = onGreen)
        }
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

/** Both batters and the bowler. Empty slots are buttons for the scorer to fill. */
@Composable
private fun CreaseCard(ui: MatchUiState, innings: InningsState, onChoose: (ScoringSheet) -> Unit) {
    fun batter(id: String?) = innings.battingTeam.players.firstOrNull { it.id == id }
    val bowler = innings.bowlingTeam.players.firstOrNull { it.id == ui.bowlerId }
    val chooseBowler = if (innings.legalBalls == 0) Res.string.match_choose_opening_bowler else Res.string.match_choose_next_bowler

    DesiCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(Res.string.match_section_crease))
        BatterSlot(batter(ui.strikerId), innings, onStrike = true, empty = stringResource(Res.string.match_choose_striker), enabled = ui.isScorer) {
            onChoose(ScoringSheet.Striker)
        }
        BatterSlot(batter(ui.nonStrikerId), innings, onStrike = false, empty = stringResource(Res.string.match_choose_non_striker), enabled = ui.isScorer) {
            onChoose(ScoringSheet.NonStriker)
        }
        HorizontalDivider(color = DesiTheme.colors.line)
        if (bowler == null) {
            EmptySlot(stringResource(chooseBowler), enabled = ui.isScorer) { onChoose(ScoringSheet.Bowler) }
        } else {
            val card = innings.bowlers.firstOrNull { it.player.id == bowler.id }
            SlotRow(
                icon = { SlotIcon(DesiIcons.Ball, highlighted = true) },
                name = bowler.name,
                caption = null,
                figures = card?.let { "${it.oversText}-${it.maidens}-${it.runs}-${it.wickets}" } ?: "0.0-0-0-0",
            )
        }
    }
}

@Composable
private fun BatterSlot(player: Player?, innings: InningsState, onStrike: Boolean, empty: String, enabled: Boolean, onChoose: () -> Unit) {
    if (player == null) {
        EmptySlot(empty, enabled, onChoose)
        return
    }
    val card = innings.batters.firstOrNull { it.player.id == player.id }
    SlotRow(
        icon = { SlotIcon(DesiIcons.Bat, highlighted = onStrike) },
        name = player.name,
        caption = if (onStrike) stringResource(Res.string.match_on_strike) else null,
        figures = stringResource(Res.string.match_batter_score, card?.runs ?: 0, card?.balls ?: 0),
    )
}

@Composable
private fun SlotRow(icon: @Composable () -> Unit, name: String, caption: String?, figures: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(name, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = DesiTheme.type.caption, color = DesiTheme.colors.brand)
        }
        Text(figures, style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
    }
}

@Composable
private fun SlotIcon(icon: DrawableResource, highlighted: Boolean) {
    val colors = DesiTheme.colors
    Box(
        Modifier.size(36.dp).clip(DesiTheme.radius.md).background(if (highlighted) colors.brandSoft else colors.sunk),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (highlighted) colors.brand else colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
    }
}

/** A slot nobody fills yet: a soft brand button with a plus. */
@Composable
private fun EmptySlot(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DesiTheme.radius.md)
            .background(colors.brandSoft)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DesiTheme.space.s3, vertical = DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        Icon(painterResource(DesiIcons.Add), contentDescription = null, tint = colors.brand, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(text, style = DesiTheme.type.bodyStrong, color = colors.brand)
    }
}

@Composable
private fun ThisOver(innings: InningsState) {
    Column(Modifier.padding(top = DesiTheme.space.s2), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionLabel(stringResource(Res.string.match_section_this_over))
        if (innings.currentOver.isEmpty()) {
            Text(stringResource(Res.string.match_over_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        } else {
            OverStrip(innings.currentOver.map { ballFor(it) })
        }
    }
}

/** Pinned run pad. Undo stays usable while the run keys wait for a batter or bowler to be picked. */
@Composable
private fun ScoringPad(ui: MatchUiState, actions: MatchActions, onSheet: (ScoringSheet) -> Unit) {
    val colors = DesiTheme.colors
    val canScore = ui.canScore
    val keys = listOf(
        PadKey("0", "0", enabled = canScore), PadKey("1", "1", enabled = canScore),
        PadKey("2", "2", enabled = canScore), PadKey("3", "3", enabled = canScore),
        PadKey("4", "4", PadKeyStyle.Four, enabled = canScore), PadKey("6", "6", PadKeyStyle.Six, enabled = canScore),
        PadKey("wd", stringResource(Res.string.scoring_key_wide), PadKeyStyle.Extra, enabled = canScore),
        PadKey("nb", stringResource(Res.string.scoring_key_no_ball), PadKeyStyle.Extra, enabled = canScore),
        PadKey("b", stringResource(Res.string.scoring_key_bye), PadKeyStyle.Extra, enabled = canScore),
        PadKey("lb", stringResource(Res.string.scoring_key_leg_bye), PadKeyStyle.Extra, enabled = canScore),
        PadKey("out", stringResource(Res.string.scoring_key_out), PadKeyStyle.Wicket, enabled = canScore),
        PadKey("undo", stringResource(Res.string.scoring_key_undo), PadKeyStyle.Utility, enabled = ui.canUndo),
    )
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Column(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(DesiTheme.space.screen),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
        ) {
            ui.error?.let { ErrorBanner(it, actions.onDismissError) }
            if (!canScore && !ui.saving && ui.error == null) {
                Text(stringResource(Res.string.match_pick_players_first), style = DesiTheme.type.body, color = colors.muted)
            }
            RunPad(keys, enabled = !ui.saving, onKey = { key ->
                when (key.id) {
                    "wd" -> onSheet(ScoringSheet.Extra(ExtraType.WIDE))
                    "nb" -> onSheet(ScoringSheet.Extra(ExtraType.NO_BALL))
                    "b" -> onSheet(ScoringSheet.Extra(ExtraType.BYE))
                    "lb" -> onSheet(ScoringSheet.Extra(ExtraType.LEG_BYE))
                    "out" -> onSheet(ScoringSheet.Wicket)
                    "undo" -> actions.onUndo()
                    else -> actions.onRuns(key.id.toInt())
                }
            })
        }
    }
}

@Composable
private fun ErrorBanner(error: ScoringError, onDismiss: () -> Unit) {
    val colors = DesiTheme.colors
    val text = when (error) {
        is ScoringError.Rejected -> stringResource(error.error.label)
        ScoringError.SaveFailed -> stringResource(Res.string.scoring_error_save_failed)
        ScoringError.LoadFailed -> stringResource(Res.string.match_load_failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = DesiTheme.type.body, color = colors.event.wicket, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.match_dismiss), style = DesiTheme.type.button, color = colors.brand) }
    }
}

@Composable
private fun ScoringSheets(sheet: ScoringSheet?, ui: MatchUiState, innings: InningsState, actions: MatchActions, onClose: () -> Unit) {
    fun batter(id: String?) = innings.battingTeam.players.firstOrNull { it.id == id }
    when (sheet) {
        null -> Unit
        ScoringSheet.Striker, ScoringSheet.NonStriker -> {
            val other = if (sheet == ScoringSheet.Striker) ui.nonStrikerId else ui.strikerId
            PlayerSheet(
                title = stringResource(if (sheet == ScoringSheet.Striker) Res.string.scoring_pick_striker else Res.string.scoring_pick_non_striker),
                players = innings.yetToBat.filter { it.id != other },
                onPick = {
                    if (sheet == ScoringSheet.Striker) actions.onSelectStriker(it.id) else actions.onSelectNonStriker(it.id)
                    onClose()
                },
                onDismiss = onClose,
            )
        }
        ScoringSheet.Bowler -> {
            // The bowler of the previous over can't bowl two in a row.
            val previous = innings.bowlingTeam.players.firstOrNull { it.id == innings.previousOverBowlerId }
            PlayerSheet(
                title = stringResource(Res.string.scoring_pick_bowler),
                hint = previous?.let { stringResource(Res.string.scoring_pick_bowler_hint, it.name) },
                players = innings.bowlingTeam.players.filter { it.id != previous?.id },
                onPick = {
                    actions.onSelectBowler(it.id)
                    onClose()
                },
                onDismiss = onClose,
            )
        }
        is ScoringSheet.Extra -> ExtraRunsSheet(sheet.type, onPick = { runs ->
            actions.onExtra(sheet.type, runs)
            onClose()
        }, onDismiss = onClose)
        ScoringSheet.Wicket -> {
            val striker = batter(ui.strikerId)
            val nonStriker = batter(ui.nonStrikerId)
            if (striker == null || nonStriker == null) return onClose()
            WicketSheet(
                striker = striker,
                nonStriker = nonStriker,
                fielders = innings.bowlingTeam.players,
                onConfirm = { kind, out, fielder, runs ->
                    actions.onWicket(kind, out.id, fielder?.id, runs)
                    onClose()
                },
                onDismiss = onClose,
            )
        }
    }
}

@Composable
private fun MessageScreen(message: StringResource, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(DesiTheme.space.s8),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), style = DesiTheme.type.bodyLg, color = DesiTheme.colors.ink, textAlign = TextAlign.Center)
        GhostButton(stringResource(Res.string.common_back), onClick = onBack)
    }
}

/** How a ball looks in the over strip: "4", "W", "2wd", "nb", "1lb", "•". */
@Composable
private fun ballFor(delivery: Delivery): Ball {
    if (delivery.wicket != null) return Ball(stringResource(Res.string.scoring_ball_wicket), BallKind.Wicket)
    val extra = delivery.extra ?: return when (delivery.runsOffBat) {
        0 -> Ball("•", BallKind.Dot)
        4 -> Ball("4", BallKind.Four)
        6 -> Ball("6", BallKind.Six)
        else -> Ball("${delivery.runsOffBat}", BallKind.Runs)
    }
    val (short, runs) = when (extra) {
        ExtraType.WIDE -> Res.string.scoring_ball_wide to delivery.extraRuns
        ExtraType.NO_BALL -> Res.string.scoring_ball_no_ball to delivery.runsOffBat + delivery.extraRuns
        ExtraType.BYE -> Res.string.scoring_ball_bye to delivery.extraRuns
        ExtraType.LEG_BYE -> Res.string.scoring_ball_leg_bye to delivery.extraRuns
    }
    val shortText = stringResource(short)
    // A plain wide or no-ball is just "wd" / "nb"; anything more shows the total, e.g. "3wd".
    val label = if (runs <= 1 && (extra == ExtraType.WIDE || extra == ExtraType.NO_BALL)) shortText
    else stringResource(Res.string.scoring_ball_extra_with_runs, runs, shortText)
    return Ball(label, BallKind.Extra)
}

// Previews: sample teams and balls only; all labels come from string resources.

private val PreviewMatch = Match(
    id = "preview", createdBy = "me", createdAt = 0,
    teamA = Team("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan").mapIndexed { i, n -> Player("a$i", n) }),
    teamB = Team("Powai XI", listOf("Dev", "Esha", "Farhan").mapIndexed { i, n -> Player("b$i", n) }),
    oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
)

private fun previewBalls(): List<Delivery> {
    var seq = 0
    fun ball(striker: String, non: String, bowler: String, runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0, wicket: Wicket? = null) =
        Delivery(seq++, 1, striker, non, bowler, runs, extra, extraRuns, wicket)
    return listOf(
        ball("a0", "a1", "b0", runs = 4), ball("a0", "a1", "b0", runs = 1), ball("a1", "a0", "b0"),
        ball("a1", "a0", "b0", extra = ExtraType.WIDE, extraRuns = 1), ball("a1", "a0", "b0", runs = 6),
        ball("a1", "a0", "b0", wicket = Wicket(DismissalKind.CAUGHT, "a1", "b2")),
    )
}

private fun previewUi(deliveries: List<Delivery>, isScorer: Boolean = true) =
    MatchUiState(loading = false, match = PreviewMatch, deliveries = deliveries, state = ScoringEngine.compute(PreviewMatch, deliveries), isScorer = isScorer)

private val PreviewActions = MatchActions({}, {}, {}, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {})

@Preview
@Composable
private fun MatchStartPreview() = DesiTheme(darkTheme = false) { MatchContent(previewUi(emptyList()), PreviewActions) }

@Preview
@Composable
private fun MatchInPlayPreview() = DesiTheme(darkTheme = false) {
    MatchContent(previewUi(previewBalls().dropLast(1)), PreviewActions)
}

@Preview
@Composable
private fun MatchAfterWicketDarkPreview() = DesiTheme(darkTheme = true) { MatchContent(previewUi(previewBalls()), PreviewActions) }

@Preview
@Composable
private fun MatchFollowerPreview() = DesiTheme(darkTheme = false) {
    MatchContent(previewUi(previewBalls().dropLast(1), isScorer = false), PreviewActions)
}

@Preview
@Composable
private fun MatchErrorPreview() = DesiTheme(darkTheme = false) {
    MatchContent(previewUi(previewBalls().dropLast(1)).copy(error = ScoringError.SaveFailed), PreviewActions)
}
