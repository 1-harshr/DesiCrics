package com.harsh.desicrics.ui.match
import com.harsh.desicrics.ui.text.ballFor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.designsystem.component.ErrorState
import com.harsh.desicrics.designsystem.component.ErrorBanner

import com.harsh.desicrics.ui.text.formatDayAndTime
import com.harsh.desicrics.ui.text.commentaryText
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.WagonWheel
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.component.themeSurface
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.designsystem.theme.SurfaceArea
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.RuleMode
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.DeliveryError
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.bowlerRuleBreak
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import com.harsh.desicrics.ui.share.ResultShare
import com.harsh.desicrics.ui.share.ScorecardShare
import com.harsh.desicrics.ui.share.ShareStudio
import com.harsh.desicrics.ui.share.matchShareContents
import com.harsh.desicrics.ui.system.rememberShareText
import com.harsh.desicrics.ui.system.rememberSpeechInput
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
    val shareText = rememberShareText()
    val shareMessage = ui.match?.let { stringResource(Res.string.match_share_link_text, stringResource(Res.string.match_title, it.teamA.name, it.teamB.name), viewModel.shareLink) }
    LightStatusBarIcons()
    MatchContent(
        ui = ui,
        actions = MatchActions(
            onShareLink = { shareMessage?.let(shareText) },
            onShareOverlay = { shareText(viewModel.overlayLink) },
            onStartFixture = viewModel::startFixture,
            onBack = onBack,
            onSelectStriker = viewModel::selectStriker,
            onSelectNonStriker = viewModel::selectNonStriker,
            onSelectBowler = viewModel::selectBowler,
            onRuns = viewModel::recordRuns,
            onHandover = viewModel::startHandover,
            onDismissHandover = viewModel::dismissHandover,
            onExtra = viewModel::recordExtra,
            onWicket = viewModel::recordWicket,
            onUndo = viewModel::undo,
            onDismissError = viewModel::dismissError,
            onRetry = viewModel::retry,
            onEndMatch = { ending, winner, reason, beforeStart ->
                if (beforeStart) viewModel.endBeforeStart(ending, winner, reason) else viewModel.endMatch(ending, winner, reason)
            },
            onRetire = viewModel::retire,
            onSwapEnds = viewModel::swapEnds,
            onReplaceBowler = viewModel::replaceBowler,
            onAddPlayer = viewModel::addPlayer,
            onPenalty = viewModel::awardPenalty,
            onDeclare = viewModel::declare,
            onReviseTarget = viewModel::reviseTarget,
            onRecordPendingBall = viewModel::recordPendingBall,
            onDropPendingBall = viewModel::dropPendingBall,
        ),
    )
}

/** Everything the scoring screen can ask for, kept together so the content signature stays short. */
class MatchActions(
    val onBack: () -> Unit,
    val onSelectStriker: (playerId: String) -> Unit,
    val onSelectNonStriker: (playerId: String) -> Unit,
    val onSelectBowler: (playerId: String) -> Unit,
    val onRuns: (Int, FieldZone?) -> Unit,
    val onHandover: () -> Unit,
    val onDismissHandover: () -> Unit,
    val onExtra: (ExtraType, runs: Int) -> Unit,
    val onWicket: (DismissalKind, playerOutId: String, fielderId: String?, runsCompleted: Int) -> Unit,
    val onUndo: () -> Unit,
    val onDismissError: () -> Unit,
    val onRetire: (playerId: String, out: Boolean) -> Unit,
    val onSwapEnds: () -> Unit,
    /** Someone else finishes the over. */
    val onReplaceBowler: (playerId: String) -> Unit,
    val onAddPlayer: (TeamSide, name: String) -> Unit,
    val onPenalty: (toBattingSide: Boolean, runs: Int) -> Unit,
    val onDeclare: () -> Unit,
    val onReviseTarget: (overs: Int, target: Int?) -> Unit,
    val onRecordPendingBall: () -> Unit,
    val onDropPendingBall: () -> Unit,
    val onShareLink: () -> Unit = {},
    val onShareOverlay: () -> Unit = {},
    val onStartFixture: (SquadRequest, SquadRequest, TeamSide, TossDecision) -> Unit = { _, _, _, _ -> },
    /** Stops the match early: during play, or ([MatchEvent.EndMatch.beforeStart]) a fixture that never starts. */
    val onEndMatch: (ending: MatchEnding, winner: TeamSide?, reason: AbandonReason?, beforeStart: Boolean) -> Unit = { _, _, _, _ -> },
    val onRetry: () -> Unit = {},
)

private enum class MatchTab { LIVE, SCORECARD, OVERS }

private val MatchTab.label
    get() = when (this) {
        MatchTab.LIVE -> Res.string.match_tab_live
        MatchTab.SCORECARD -> Res.string.match_tab_scorecard
        MatchTab.OVERS -> Res.string.match_tab_overs
    }

/** A choice the scorer is making in a bottom sheet. */
private sealed interface ScoringSheet {
    data object Striker : ScoringSheet
    data object NonStriker : ScoringSheet
    data object Bowler : ScoringSheet
    data class Extra(val type: ExtraType) : ScoringSheet
    data object Wicket : ScoringSheet
    data class BatterOptions(val player: Player) : ScoringSheet
    data object BowlerOptions : ScoringSheet
    /** Who finishes the over in place of the current bowler. */
    data object ReplaceBowler : ScoringSheet
    data object MatchOptions : ScoringSheet
    data object EndMatch : ScoringSheet
    /** Asks before declaring, which ends the innings (or concedes the chase). */
    data object Declare : ScoringSheet
    data object Penalty : ScoringSheet
    data object ReviseTarget : ScoringSheet
    /** Where a scoring shot went, for the wagon wheel. */
    data class Zone(val runs: Int) : ScoringSheet
    data object Share : ScoringSheet
    /** The share studio, opened on [initial] (a content key) or on the score. */
    data class ShareCard(val initial: String? = null) : ScoringSheet
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
            ui.notFound || state == null -> MatchMissing(ui.notFound, actions)
            state.match.status == MatchStatus.SCHEDULED ->
                FixturePending(state.match, ui.fixture, actions.onStartFixture, actions.onBack, onEnd = { ending, winner, reason -> actions.onEndMatch(ending, winner, reason, true) })
            // A walkover or call-off: no ball was bowled, so there's no scorecard to show.
            ui.events.any { it is MatchEvent.EndMatch && it.beforeStart } && state.result != null ->
                EndedBeforePlay(state.match, state.result!!, ui.canUndo, onUndo = actions.onUndo, onBack = actions.onBack)
            else -> Scoring(ui, state, actions)
        }
    }
}

@Composable
private fun ColumnScope.Scoring(ui: MatchUiState, state: MatchState, actions: MatchActions) {
    // A finished match has nothing live to follow, so it opens on its scorecard.
    var tab by rememberSaveable { mutableStateOf(if (state.isComplete) MatchTab.SCORECARD else MatchTab.LIVE) }
    var sheet by remember { mutableStateOf<ScoringSheet?>(null) }
    val innings = state.current

    var undoSeq by remember { mutableStateOf<Int?>(null) }
    val askUndo = { undoSeq = ui.nextSeq }
    if (undoSeq == ui.nextSeq && ui.canUndo) {
        val ball = state.innings.flatMap { it.overs }.flatMap { it.balls }.lastOrNull { it.delivery.seq == ui.nextSeq - 1 }
        val description = ball?.let { "${it.ballNumber} · ${commentaryText(it)}" } ?: stringResource(Res.string.undo_event)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { undoSeq = null },
            title = { Text(stringResource(Res.string.scoring_key_undo)) },
            text = { Text(description) },
            confirmButton = { TextButton(onClick = { undoSeq = null; actions.onUndo() }) { Text(stringResource(Res.string.scoring_key_undo)) } },
            dismissButton = { TextButton(onClick = { undoSeq = null }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    val crease = tab == MatchTab.LIVE && !state.isComplete
    // With the wagon wheel on, the scorer taps where the ball went and then the runs. A zone stays armed
    // until a ball is recorded or undone (either moves nextSeq on), or the same zone is tapped again.
    val wagonWheel = ui.match?.rules?.wagonWheel == true
    var armed by remember(ui.nextSeq) { mutableStateOf<FieldZone?>(null) }
    // Dots, extras and wickets take no zone. Runs with none armed ask for one, with a Skip.
    val scoreRuns: (Int) -> Unit = { runs ->
        when {
            !wagonWheel || runs == 0 -> actions.onRuns(runs, null)
            armed != null -> actions.onRuns(runs, armed)
            else -> sheet = ScoringSheet.Zone(runs)
        }
    }
    // Voice scoring. Anything unclear opens a sheet or asks again; nothing ambiguous is recorded straight from speech.
    var misheard by remember { mutableStateOf(false) }
    val listen = rememberSpeechInput { heard ->
        val command = heard?.let { VoiceCommands.parse(it) } ?: VoiceCommand.Unknown
        misheard = command == VoiceCommand.Unknown || (!ui.canScore && command != VoiceCommand.Undo)
        if (misheard) return@rememberSpeechInput
        when (command) {
            is VoiceCommand.Runs -> scoreRuns(command.runs)
            is VoiceCommand.Extra -> actions.onExtra(command.type, command.runs)
            VoiceCommand.Wicket -> sheet = ScoringSheet.Wicket
            VoiceCommand.Undo -> if (ui.canUndo) askUndo()
            VoiceCommand.Unknown -> Unit
        }
    }?.takeIf { ui.isScorer && crease }
    // While the pad is up the screen is tight, so the header shrinks and the crease card tightens; the keys
    // keep their full size. Followers and the other tabs get the roomy layout.
    val compact = ui.isScorer && crease
    Scoreboard(
        state,
        actions.onBack,
        onShare = { sheet = ScoringSheet.Share },
        onOptions = if (ui.isScorer && !state.isComplete) ({ sheet = ScoringSheet.MatchOptions }) else null,
        optionsEnabled = ui.canEdit,
        compact = compact,
    )
    Row(
        Modifier.padding(horizontal = DesiTheme.space.screen, vertical = if (compact) DesiTheme.space.s2 else DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedControl(
            options = MatchTab.entries,
            selected = tab,
            label = { stringResource(it.label) },
            onSelect = { tab = it },
            modifier = Modifier.weight(1f),
        )
        if (listen != null) {
            IconButton(onClick = { misheard = false; listen() }, enabled = !ui.saving, modifier = Modifier.offset(x = DesiTheme.space.s2)) {
                Icon(painterResource(DesiIcons.Mic), stringResource(Res.string.scoring_voice), tint = DesiTheme.colors.brand)
            }
        }
    }
    // The batters, bowler and this over come first, right above the pad, so the scorer always sees who's in;
    // the compact header leaves room for them on a budget phone. Weighted and scrolling, so on a smaller
    // screen this gives way rather than the pad, and what doesn't fit scrolls instead of being cut off.
    val scroll = rememberScrollState()
    // A connection warning appears at the top of the scrolling part; bring it into view.
    LaunchedEffect(ui.offline) { if (ui.offline) scroll.animateScrollTo(0) }
    // The pick the scorer has to make before the next ball: openers at the start, a new batter after a
    // wicket, the next bowler after an over. It opens by itself once per gap; dismissing it leaves the
    // slot's button to tap, rather than reopening on every recomposition.
    val needed = when {
        !ui.isScorer || !ui.canEdit || ui.pendingBall != null -> null
        ui.strikerId == null -> ScoringSheet.Striker
        ui.nonStrikerId == null && !ui.battingAlone -> ScoringSheet.NonStriker
        ui.bowlerId == null -> ScoringSheet.Bowler
        else -> null
    }
    // Taps on run keys that wait for that pick: the slot turns red and shakes, and the phone buzzes.
    var nudges by remember(needed) { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val onBlockedKey: () -> Unit = {
        if (needed != null) {
            nudges++
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)
            .padding(horizontal = DesiTheme.space.screen).padding(bottom = DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(if (compact) DesiTheme.space.s2 else DesiTheme.space.s3),
    ) {
        if (crease) {
            CreaseCard(
                ui,
                innings,
                blocking = needed?.takeIf { nudges > 0 },
                nudges = nudges,
                compact = compact,
                onChoose = { sheet = it },
            )
        }
        if (ui.saving || ui.reconciling) Text(stringResource(if (ui.saving) Res.string.score_saving else Res.string.score_checking), style = DesiTheme.type.caption)
        if (ui.offline) {
            ErrorBanner(
                listOfNotNull(stringResource(Res.string.match_offline), ui.confirmedAt?.let { stringResource(Res.string.score_confirmed, formatDayAndTime(it)) }).joinToString(" "),
                title = stringResource(Res.string.match_offline_title),
                icon = DesiIcons.CloudOff,
                warning = true,
            )
        }
        when (tab) {
            MatchTab.LIVE -> {
                // Once it's over, how it ended and who starred, rather than a note about who can score.
                val result = state.result
                if (state.isComplete && result != null) {
                    ResultBanner(state.match, result)
                    MatchMvp(state)
                    TonalButton(
                        stringResource(Res.string.share_result_action),
                        onClick = { sheet = ScoringSheet.ShareCard(ResultShare.KEY) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = DesiIcons.Share,
                    )
                } else if (!ui.isScorer) {
                    Text(stringResource(Res.string.match_not_your_match), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                }
                if (state.isComplete && ui.canUndo) {
                    // Undo after an early ending reopens the match rather than taking back a ball.
                    val endedEarly = state.result?.ending != null
                    GhostButton(
                        stringResource(if (endedEarly) Res.string.end_reopen else Res.string.match_undo_last_ball),
                        onClick = if (endedEarly) actions.onUndo else askUndo,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = DesiIcons.History,
                    )
                }
            }
            MatchTab.SCORECARD -> {
                state.innings.forEach {
                    InningsScorecard(it, onShare = { key -> sheet = ScoringSheet.ShareCard(key) })
                    InningsWagonWheel(it)
                }
                MatchMvp(state)
                if (state.isComplete) {
                    TonalButton(
                        stringResource(Res.string.share_scorecard_action),
                        onClick = { sheet = ScoringSheet.ShareCard(ScorecardShare.KEY) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = DesiIcons.Share,
                    )
                }
            }
            // Latest innings first, so the over in progress is at the top.
            MatchTab.OVERS -> state.innings.asReversed().forEach { InningsOvers(it, showTitle = state.innings.size > 1) }
        }
    }
    // The pad only shows with the live view, so the scorecard gets the whole screen.
    if (ui.isScorer && !state.isComplete && tab == MatchTab.LIVE) {
        ScoringPad(
            ui, actions, askUndo, misheard, scoreRuns, nudged = nudges > 0, onBlockedKey = onBlockedKey, onSheet = { sheet = it },
            armed = armed,
            onZone = if (wagonWheel) ({ zone -> armed = zone.takeIf { it != armed } }) else null,
        )
    }

    var autoOpened by remember { mutableStateOf<Pair<ScoringSheet, Int>?>(null) }
    LaunchedEffect(needed, ui.nextSeq, sheet == null) {
        val gap = needed?.let { it to ui.nextSeq } ?: return@LaunchedEffect
        if (sheet == null && autoOpened != gap) {
            autoOpened = gap
            tab = MatchTab.LIVE
            sheet = needed
        }
    }

    ScoringSheets(sheet, ui, state, actions, onSheet = { sheet = it }, onClose = { sheet = null })
    ui.pendingBall?.let { RuleWarningSheet(it, actions.onRecordPendingBall, actions.onDropPendingBall) }
    if (ui.handoverCode != null || ui.handoverFailed) HandoverSheet(ui.handoverCode?.code, actions.onDismissHandover)
    ScoringHaptics(ui)
}

/**
 * A buzz the scorer can feel for each new ball, so they can keep their eyes on the field: a strong one for
 * a wicket, a confirming one for a boundary, a light tick for the rest. Only for balls added while the
 * screen is open, not the ones loaded with it.
 */
@Composable
private fun ScoringHaptics(ui: MatchUiState) {
    if (!ui.isScorer) return
    val haptics = LocalHapticFeedback.current
    var seen by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(ui.deliveries.size) {
        val previous = seen
        seen = ui.deliveries.size
        if (previous == null || ui.deliveries.size <= previous) return@LaunchedEffect
        val last = ui.deliveries.last()
        haptics.performHapticFeedback(
            when {
                last.wicket != null -> HapticFeedbackType.Reject
                last.runsOffBat == 4 || last.runsOffBat == 6 -> HapticFeedbackType.Confirm
                else -> HapticFeedbackType.VirtualKey
            },
        )
    }
}

/**
 * The green header: teams, then the batting side's score, overs and where the chase stands. [compact] fits
 * it in two rows for while the scorer's pad is up, dropping the live badge and first-innings line.
 */
@Composable
private fun Scoreboard(
    state: MatchState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOptions: (() -> Unit)?,
    optionsEnabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = DesiTheme.colors
    val onGreen = colors.onBrandStrong
    val innings = state.current
    val match = state.match
    val inningsName = stringResource(if (innings.number == 1) Res.string.match_innings_first else Res.string.match_innings_second)
    val details = buildList {
        add(stringResource(Res.string.match_run_rate, innings.runRate.format2()))
        if (innings.isPowerplay) add(stringResource(Res.string.match_powerplay))
        if (innings.isRevised) add(stringResource(Res.string.match_revised))
        innings.target?.let { add(stringResource(Res.string.match_target, it)) }
        innings.runsNeeded?.let { add(pluralStringResource(Res.plurals.match_need, innings.ballsRemaining, it, innings.ballsRemaining)) }
    }.joinToString(" · ")
    if (compact) {
        Column(
            Modifier.fillMaxWidth().background(colors.brandStrong).themeSurface(SurfaceArea.Scoreboard)
                .padding(start = DesiTheme.space.screen, end = DesiTheme.space.screen, bottom = DesiTheme.space.s3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.offset(x = -DesiTheme.space.s3)) {
                    Icon(painterResource(DesiIcons.Back), stringResource(Res.string.common_back), tint = onGreen)
                }
                Text(
                    stringResource(Res.string.match_title, match.teamA.name, match.teamB.name),
                    style = DesiTheme.type.bodyStrong,
                    color = onGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onShare, modifier = Modifier.offset(x = DesiTheme.space.s2)) {
                    Icon(painterResource(DesiIcons.Share), stringResource(Res.string.match_share), tint = onGreen)
                }
                if (onOptions != null) {
                    IconButton(onClick = onOptions, enabled = optionsEnabled, modifier = Modifier.offset(x = DesiTheme.space.s3)) {
                        Icon(painterResource(DesiIcons.More), stringResource(Res.string.match_options), tint = onGreen)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                Text(innings.scoreText, style = DesiTheme.type.scoreLg, color = onGreen)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.match_innings_label, innings.battingTeam.name, inningsName).uppercase(),
                        style = DesiTheme.type.label,
                        color = onGreen.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(stringResource(Res.string.match_overs_of, innings.oversText, innings.maxBalls / 6), details).joinToString(" · "),
                        style = DesiTheme.type.data,
                        color = onGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().background(colors.brandStrong).themeSurface(SurfaceArea.Scoreboard)
            .padding(start = DesiTheme.space.screen, end = DesiTheme.space.screen, bottom = DesiTheme.space.s5),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = -DesiTheme.space.s3)) {
                Icon(painterResource(DesiIcons.Back), stringResource(Res.string.common_back), tint = onGreen)
            }
            // The two names are what this screen is about: up to two lines, and nothing else competing in the row.
            Text(
                stringResource(Res.string.match_title, match.teamA.name, match.teamB.name),
                style = DesiTheme.type.titleMd,
                color = onGreen,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShare, modifier = Modifier.offset(x = DesiTheme.space.s2)) {
                Icon(painterResource(DesiIcons.Share), stringResource(Res.string.match_share), tint = onGreen)
            }
            if (onOptions != null) {
                IconButton(onClick = onOptions, enabled = optionsEnabled, modifier = Modifier.offset(x = DesiTheme.space.s3)) {
                    Icon(painterResource(DesiIcons.More), stringResource(Res.string.match_options), tint = onGreen)
                }
            }
        }
        Row(Modifier.padding(top = DesiTheme.space.s2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            if (!state.isComplete) {
                Box(Modifier.clip(DesiTheme.radius.full).background(colors.surface).padding(start = 2.dp, end = DesiTheme.space.s3, top = 2.dp, bottom = 2.dp)) {
                    LiveBadge()
                }
            }
            Text(
                stringResource(Res.string.match_innings_label, innings.battingTeam.name, inningsName).uppercase(),
                style = DesiTheme.type.label,
                color = onGreen.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(innings.scoreText, style = DesiTheme.type.scoreXl, color = onGreen)
            Text(
                stringResource(Res.string.match_overs_of, innings.oversText, innings.maxBalls / 6),
                style = DesiTheme.type.data,
                color = onGreen.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = DesiTheme.space.s2),
            )
        }
        val result = state.result
        if (result != null) {
            Text(resultText(match, result), style = DesiTheme.type.bodyStrong, color = onGreen)
        } else {
            Text(details, style = DesiTheme.type.data, color = onGreen)
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

/**
 * Both batters, the bowler and the over so far, pinned above the pad. Empty slots are buttons for the
 * scorer to fill. [compact] drops the title and tightens the spacing, for while the pad is up.
 */
@Composable
private fun CreaseCard(
    ui: MatchUiState,
    innings: InningsState,
    blocking: ScoringSheet?,
    nudges: Int,
    compact: Boolean,
    onChoose: (ScoringSheet) -> Unit,
) {
    fun batter(id: String?) = innings.battingTeam.players.firstOrNull { it.id == id }
    val bowler = innings.bowlingTeam.players.firstOrNull { it.id == ui.bowlerId }
    val chooseBowler = if (innings.legalBalls == 0) Res.string.match_choose_opening_bowler else Res.string.match_choose_next_bowler

    val rules = ui.match?.rules
    // Batters on the run limit who haven't retired yet; once they come back, the limit doesn't apply again.
    val mustRetire = rules?.takeIf { it.retireAtMode != RuleMode.OFF }?.let { r ->
        innings.batters.filter { it.runs >= r.retireAtRuns && it.retiredAtWickets == null }.map { it.player.id }.toSet()
    }.orEmpty()

    val space = DesiTheme.space
    DesiCard(Modifier.fillMaxWidth(), padding = if (compact) space.s4 else space.s5, spacing = if (compact) space.s2 else space.s3) {
        if (!compact) SectionHeader(stringResource(Res.string.match_section_crease), icon = DesiIcons.Bat)
        BatterSlot(batter(ui.strikerId), innings, onStrike = true, empty = stringResource(Res.string.match_choose_striker), ui.canEdit, mustRetire, rules, onChoose, nudges.takeIf { blocking == ScoringSheet.Striker }) {
            onChoose(ScoringSheet.Striker)
        }
        if (ui.battingAlone) {
            Text(stringResource(Res.string.match_batting_alone), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        } else {
            BatterSlot(batter(ui.nonStrikerId), innings, onStrike = false, empty = stringResource(Res.string.match_choose_non_striker), ui.canEdit, mustRetire, rules, onChoose, nudges.takeIf { blocking == ScoringSheet.NonStriker }) {
                onChoose(ScoringSheet.NonStriker)
            }
        }
        HorizontalDivider(color = DesiTheme.colors.line)
        if (bowler == null) {
            EmptySlot(stringResource(chooseBowler), enabled = ui.canEdit, nudges = nudges.takeIf { blocking == ScoringSheet.Bowler }) { onChoose(ScoringSheet.Bowler) }
        } else {
            val card = innings.bowlers.firstOrNull { it.player.id == bowler.id }
            SlotRow(
                icon = { SlotIcon(DesiIcons.Ball, highlighted = true) },
                name = bowler.name,
                caption = null,
                figures = card?.let { "${it.oversText}-${it.maidens}-${it.runs}-${it.wickets}" } ?: "0.0-0-0-0",
                onOptions = if (ui.canEdit) ({ onChoose(ScoringSheet.BowlerOptions) }) else null,
            )
        }
        HorizontalDivider(color = DesiTheme.colors.line)
        ThisOver(innings, compact)
    }
}

@Composable
private fun FreeHitTag() {
    Text(
        stringResource(Res.string.match_free_hit).uppercase(),
        style = DesiTheme.type.label,
        color = DesiTheme.colors.onBrand,
        modifier = Modifier.clip(DesiTheme.radius.full).background(DesiTheme.colors.brand)
            .padding(horizontal = DesiTheme.space.s2, vertical = 2.dp),
    )
}

@Composable
private fun BatterSlot(
    player: Player?,
    innings: InningsState,
    onStrike: Boolean,
    empty: String,
    canEdit: Boolean,
    mustRetire: Set<String>,
    rules: MatchRules?,
    onSheet: (ScoringSheet) -> Unit,
    nudges: Int?,
    onChoose: () -> Unit,
) {
    if (player == null) {
        EmptySlot(empty, canEdit, nudges, onChoose)
        return
    }
    val card = innings.batters.firstOrNull { it.player.id == player.id }
    SlotRow(
        icon = { SlotIcon(DesiIcons.Bat, highlighted = onStrike) },
        name = player.name,
        caption = when {
            player.id in mustRetire && rules != null -> stringResource(Res.string.match_must_retire, rules.retireAtRuns)
            onStrike -> stringResource(Res.string.match_on_strike)
            else -> null
        },
        figures = stringResource(Res.string.match_batter_score, card?.runs ?: 0, card?.balls ?: 0),
        onOptions = if (canEdit) ({ onSheet(ScoringSheet.BatterOptions(player)) }) else null,
    )
}

/** A player at the crease or bowling. With [onOptions], tapping the row opens what the scorer can change. */
@Composable
private fun SlotRow(icon: @Composable () -> Unit, name: String, caption: String?, figures: String, onOptions: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(DesiTheme.radius.md)
            .then(
                if (onOptions == null) Modifier
                else Modifier.clickable(role = Role.Button, onClickLabel = stringResource(Res.string.scoring_player_options, name), onClick = onOptions),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(name, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = DesiTheme.type.caption, color = DesiTheme.colors.brand)
        }
        Text(figures, style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
        if (onOptions != null) {
            Icon(painterResource(DesiIcons.More), contentDescription = null, tint = DesiTheme.colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
        }
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

/**
 * A slot nobody fills yet: a soft brand button with a plus. With [nudges], it's the slot holding up the
 * next ball and the scorer has tried to score anyway: it turns red and shakes once per try.
 */
@Composable
private fun EmptySlot(text: String, enabled: Boolean, nudges: Int? = null, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    val alert = nudges != null
    val tone = if (alert) colors.event.wicket else colors.brand
    val shake = remember { Animatable(0f) }
    val reduceMotion = DesiTheme.reduceMotion
    LaunchedEffect(nudges) {
        if (nudges == null || nudges == 0 || reduceMotion) return@LaunchedEffect
        for (x in listOf(-10f, 10f, -8f, 8f, -4f, 4f, 0f)) shake.animateTo(x, tween(40))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .clip(DesiTheme.radius.md)
            .background(if (alert) tone.copy(alpha = 0.08f) else colors.brandSoft)
            .then(if (alert) Modifier.border(1.dp, tone, DesiTheme.radius.md) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = DesiTheme.space.s3, vertical = DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        Icon(painterResource(DesiIcons.Add), contentDescription = null, tint = tone, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(text, style = DesiTheme.type.bodyStrong, color = tone)
    }
}

@Composable
private fun ThisOver(innings: InningsState, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        // The next ball's free hit shows by the over it belongs to. Compact, the strip under the bowler speaks for itself.
        if (!compact || innings.isFreeHit) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                if (!compact) Text(stringResource(Res.string.match_section_this_over), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                if (innings.isFreeHit) FreeHitTag()
            }
        }
        if (innings.currentOver.isEmpty()) {
            // Six empty slots, so the strip has its shape before the first ball.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                repeat(6) {
                    Box(Modifier.size(DesiTheme.size.ballChip).clip(DesiTheme.radius.full).border(1.5.dp, DesiTheme.colors.line, DesiTheme.radius.full))
                }
            }
        } else {
            OverStrip(innings.currentOver.map { ballFor(it) })
        }
    }
}

/** Pinned run pad. Undo stays usable while the run keys wait for a batter or bowler to be picked. */
@Composable
private fun ScoringPad(
    ui: MatchUiState,
    actions: MatchActions,
    askUndo: () -> Unit,
    misheard: Boolean,
    runs: (Int) -> Unit,
    nudged: Boolean,
    onBlockedKey: () -> Unit,
    onSheet: (ScoringSheet) -> Unit,
    armed: FieldZone? = null,
    onZone: ((FieldZone) -> Unit)? = null,
) {
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
            // The field for the wagon wheel: a fixed strip, so on a small phone the crease above gives way
            // and the keys stay put.
            if (onZone != null) {
                WagonWheel(
                    labels = FieldZone.Clockwise.map { stringResource(it.label) },
                    modifier = Modifier.height(DesiTheme.size.scoringField),
                    selected = armed?.let { FieldZone.Clockwise.indexOf(it) },
                    onSector = { onZone(FieldZone.Clockwise[it]) },
                    description = stringResource(Res.string.scoring_field_description),
                    aspectRatio = null,
                )
            }
            ui.error?.let { ScoringErrorBanner(it, actions.onDismissError) }
            val hint = when {
                misheard -> stringResource(Res.string.scoring_voice_unknown)
                !canScore && !ui.saving && ui.error == null -> stringResource(Res.string.match_pick_players_first)
                else -> null
            }
            // Only when there's something to say, so the crease above keeps the room.
            if (hint != null) Text(hint, style = DesiTheme.type.body, color = if (nudged) colors.event.wicket else colors.muted)
            RunPad(keys, enabled = !ui.saving, onBlockedKey = { onBlockedKey() }, onKey = { key ->
                when (key.id) {
                    "wd" -> onSheet(ScoringSheet.Extra(ExtraType.WIDE))
                    "nb" -> onSheet(ScoringSheet.Extra(ExtraType.NO_BALL))
                    "b" -> onSheet(ScoringSheet.Extra(ExtraType.BYE))
                    "lb" -> onSheet(ScoringSheet.Extra(ExtraType.LEG_BYE))
                    "out" -> onSheet(ScoringSheet.Wicket)
                    "undo" -> askUndo()
                    else -> runs(key.id.toInt())
                }
            })
        }
    }
}

@Composable
private fun ScoringErrorBanner(error: ScoringError, onDismiss: () -> Unit) {
    val text = when (error) {
        is ScoringError.Rejected -> stringResource(error.error.label)
        ScoringError.SaveFailed -> stringResource(Res.string.scoring_error_save_failed)
        ScoringError.LoadFailed -> stringResource(Res.string.match_load_failed)
    }
    // Above the keys space is tight, so a lost ball is one line; a refused one says which rule.
    ErrorBanner(
        if (error == ScoringError.SaveFailed) stringResource(Res.string.scoring_error_save_title) else text,
        icon = if (error is ScoringError.Rejected) DesiIcons.Info else DesiIcons.CloudOff,
        action = stringResource(Res.string.match_dismiss),
        onAction = onDismiss,
    )
}

@Composable
private fun ScoringSheets(
    sheet: ScoringSheet?,
    ui: MatchUiState,
    state: MatchState,
    actions: MatchActions,
    onSheet: (ScoringSheet) -> Unit,
    onClose: () -> Unit,
) {
    val innings = state.current
    val rules = state.match.rules
    fun batter(id: String?) = innings.battingTeam.players.firstOrNull { it.id == id }
    when (sheet) {
        null -> Unit
        ScoringSheet.Striker, ScoringSheet.NonStriker -> {
            val other = if (sheet == ScoringSheet.Striker) ui.nonStrikerId else ui.strikerId
            val canReturn = stringResource(Res.string.scoring_can_return)
            val notYet = stringResource(Res.string.scoring_return_not_yet)
            PlayerSheet(
                title = stringResource(if (sheet == ScoringSheet.Striker) Res.string.scoring_pick_striker else Res.string.scoring_pick_non_striker),
                players = innings.canComeIn.filter { it.id != other },
                // Players the rules keep out only show up here when the rule warns rather than enforces.
                notes = innings.batters.filter { it.retired?.canReturn == true }
                    .associate { it.player.id to if (it.player.id in innings.returnBlocked) notYet else canReturn },
                onPick = {
                    if (sheet == ScoringSheet.Striker) actions.onSelectStriker(it.id) else actions.onSelectNonStriker(it.id)
                    onClose()
                },
                onDismiss = onClose,
                onAddPlayer = { actions.onAddPlayer(innings.battingSide, it) },
            )
        }
        ScoringSheet.Bowler, ScoringSheet.ReplaceBowler -> {
            // Bowlers an enforced rule rules out are hidden; a warned rule just shows why next to their name.
            val breaks = innings.bowlingTeam.players.associate { it.id to state.bowlerRuleBreak(it.id) }
            val bowledLast = stringResource(Res.string.scoring_bowled_last_over)
            val allBowled = stringResource(Res.string.scoring_over_limit_reached)
            PlayerSheet(
                title = stringResource(Res.string.scoring_pick_bowler),
                // Someone else finishes an over the bowler can't.
                players = innings.bowlingTeam.players.filter {
                    breaks[it.id]?.mode != RuleMode.ENFORCE && (sheet != ScoringSheet.ReplaceBowler || it.id != ui.bowlerId)
                },
                notes = buildMap {
                    innings.previousOverBowlerId?.let { put(it, bowledLast) }
                    breaks.forEach { (id, rule) -> if (rule?.error == DeliveryError.BOWLER_OVER_LIMIT) put(id, allBowled) }
                },
                onPick = {
                    // A replacement is recorded with the pick, so the change and the new bowler undo together.
                    if (sheet == ScoringSheet.ReplaceBowler) actions.onReplaceBowler(it.id) else actions.onSelectBowler(it.id)
                    onClose()
                },
                onDismiss = onClose,
                onAddPlayer = { actions.onAddPlayer(innings.battingSide.other, it) },
            )
        }
        is ScoringSheet.BatterOptions -> {
            val card = innings.batters.firstOrNull { it.player.id == sheet.player.id }
            // Not out either way; the engine tells a run-limit retirement from an injury by the score.
            val atRunLimit = rules.retireAtMode != RuleMode.OFF && card != null && card.runs >= rules.retireAtRuns
            val (retire, retireHint) = if (atRunLimit) Res.string.scoring_retire_not_out to Res.string.scoring_retire_not_out_hint
            else Res.string.scoring_retire_hurt to Res.string.scoring_retire_hurt_hint
            OptionsSheet(
                title = sheet.player.name,
                options = listOf(
                    SheetOption(stringResource(retire), stringResource(retireHint)) {
                        actions.onRetire(sheet.player.id, false)
                        onClose()
                    },
                    SheetOption(stringResource(Res.string.scoring_retire_out), stringResource(Res.string.scoring_retire_out_hint)) {
                        actions.onRetire(sheet.player.id, true)
                        onClose()
                    },
                    SheetOption(stringResource(Res.string.scoring_swap_ends), stringResource(Res.string.scoring_swap_ends_hint)) {
                        actions.onSwapEnds()
                        onClose()
                    },
                ),
                onDismiss = onClose,
            )
        }
        ScoringSheet.MatchOptions -> OptionsSheet(
            title = stringResource(Res.string.match_options),
            options = listOf(
                SheetOption(stringResource(Res.string.match_penalty), stringResource(Res.string.match_penalty_hint)) {
                    onSheet(ScoringSheet.Penalty)
                },
                SheetOption(stringResource(Res.string.match_handover), stringResource(Res.string.match_handover_hint)) {
                    actions.onHandover()
                    onClose()
                },
                SheetOption(stringResource(Res.string.match_revise), stringResource(Res.string.match_revise_hint)) {
                    onSheet(ScoringSheet.ReviseTarget)
                },
                SheetOption(stringResource(Res.string.end_title), stringResource(Res.string.end_option_hint)) {
                    onSheet(ScoringSheet.EndMatch)
                },
                SheetOption(
                    stringResource(Res.string.match_declare),
                    stringResource(if (innings.number == 1) Res.string.match_declare_hint else Res.string.match_declare_chase_hint),
                ) {
                    onSheet(ScoringSheet.Declare)
                },
            ),
            onDismiss = onClose,
        )
        ScoringSheet.Declare -> androidx.compose.material3.AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(if (innings.number == 1) Res.string.match_declare_confirm else Res.string.match_declare_chase_confirm)) },
            text = { Text(stringResource(if (innings.number == 1) Res.string.match_declare_hint else Res.string.match_declare_chase_hint)) },
            confirmButton = { TextButton(onClick = { onClose(); actions.onDeclare() }) { Text(stringResource(Res.string.match_declare)) } },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.common_cancel)) } },
        )
        ScoringSheet.EndMatch -> EndMatchSheet(
            match = state.match,
            beforeStart = false,
            onEnd = { ending, winner, reason ->
                actions.onEndMatch(ending, winner, reason, false)
                onClose()
            },
            onDismiss = onClose,
        )
        ScoringSheet.Penalty -> PenaltySheet(
            battingTeam = innings.battingTeam.name,
            fieldingTeam = innings.bowlingTeam.name,
            onAward = { toBatting, runs ->
                actions.onPenalty(toBatting, runs)
                onClose()
            },
            onDismiss = onClose,
        )
        ScoringSheet.Share -> OptionsSheet(
            title = stringResource(Res.string.match_share),
            options = listOf(
                SheetOption(stringResource(Res.string.match_share_link), stringResource(Res.string.match_share_link_hint)) {
                    actions.onShareLink()
                    onClose()
                },
                SheetOption(stringResource(Res.string.match_share_card), stringResource(Res.string.match_share_card_hint)) {
                    onSheet(ScoringSheet.ShareCard())
                },
                SheetOption(stringResource(Res.string.match_share_overlay), stringResource(Res.string.match_share_overlay_hint)) {
                    actions.onShareOverlay()
                    onClose()
                },
            ),
            onDismiss = onClose,
        )
        is ScoringSheet.ShareCard -> ShareStudio(remember(state) { matchShareContents(state) }, onDismiss = onClose, initial = sheet.initial)
        is ScoringSheet.Zone -> ZoneSheet(
            runs = sheet.runs,
            onPick = { zone ->
                actions.onRuns(sheet.runs, zone)
                onClose()
            },
            onDismiss = onClose,
        )
        ScoringSheet.ReviseTarget -> ReviseTargetSheet(
            innings = innings,
            matchOvers = state.match.oversPerInnings,
            onRevise = { overs, target ->
                actions.onReviseTarget(overs, target)
                onClose()
            },
            onDismiss = onClose,
        )
        ScoringSheet.BowlerOptions -> OptionsSheet(
            title = innings.bowlingTeam.players.firstOrNull { it.id == ui.bowlerId }?.name.orEmpty(),
            options = listOf(
                SheetOption(stringResource(Res.string.scoring_change_bowler), stringResource(Res.string.scoring_change_bowler_hint)) {
                    onSheet(ScoringSheet.ReplaceBowler)
                },
            ),
            onDismiss = onClose,
        )
        is ScoringSheet.Extra -> ExtraRunsSheet(sheet.type, rules.penalty(sheet.type), onPick = { runs ->
            actions.onExtra(sheet.type, runs)
            onClose()
        }, onDismiss = onClose)
        ScoringSheet.Wicket -> {
            val striker = batter(ui.strikerId)
            val nonStriker = batter(ui.nonStrikerId)
            if (striker == null || (nonStriker == null && !ui.battingAlone)) return onClose()
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
private fun MatchMissing(notFound: Boolean, actions: MatchActions) {
    ErrorState(
        title = stringResource(if (notFound) Res.string.error_match_missing_title else Res.string.error_match_title),
        body = stringResource(if (notFound) Res.string.match_not_found else Res.string.error_connection_body),
        icon = if (notFound) DesiIcons.SearchOff else DesiIcons.CloudOff,
        onRetry = actions.onRetry.takeIf { !notFound },
        secondary = { GhostButton(stringResource(Res.string.common_back), onClick = actions.onBack, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Back) },
    )
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

private val PreviewActions = MatchActions({}, {}, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _, _, _ -> }, {}, {}, { _, _ -> }, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {})

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
