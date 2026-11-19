package com.harsh.desicrics.ui.text
import com.harsh.desicrics.ui.match.label
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.MatchEnding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.BallEvent
import com.harsh.desicrics.scoring.DeliveryError
import com.harsh.desicrics.scoring.Dismissal
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Strikers won by 24 runs", "Match tied", "No result · Rain", "Royals won by walkover". */
@Composable
fun resultText(match: Match, result: MatchResult): String {
    when (result.ending) {
        MatchEnding.ABANDONED -> return result.reason?.takeIf { it != AbandonReason.OTHER }
            ?.let { stringResource(Res.string.match_result_no_result_reason, stringResource(it.label)) }
            ?: stringResource(Res.string.match_result_no_result)
        MatchEnding.CONCEDED -> {
            val winner = result.winner!!
            return stringResource(Res.string.match_result_conceded, match.team(winner).name, match.team(winner.other).name)
        }
        MatchEnding.WALKOVER -> return stringResource(Res.string.match_result_walkover, match.team(result.winner!!).name)
        null -> Unit
    }
    val winner = result.winner ?: return stringResource(Res.string.match_result_tie)
    val plural = when (result.marginUnit) {
        MarginUnit.WICKETS -> Res.plurals.match_result_won_by_wickets
        MarginUnit.RUNS, null -> Res.plurals.match_result_won_by_runs
    }
    return pluralStringResource(plural, result.margin, match.team(winner).name, result.margin)
}

/**
 * One line on where a match stands: the result once it's over, the chase in the second innings
 * ("Royals need 27 off 20 balls"), the toss before that, or for a fixture not yet started, when it's due.
 */
@Composable
fun matchStatusText(match: Match): String {
    match.result?.let { return resultText(match, it) }
    // A fixture's toss hasn't happened; its stored toss is only a placeholder until it starts.
    if (match.status == MatchStatus.SCHEDULED) {
        return match.scheduledAt?.let { formatDayAndTime(it) } ?: stringResource(Res.string.fixture_to_play)
    }
    val chase = match.innings.getOrNull(1)
    val first = match.innings.firstOrNull()
    if (chase != null && first != null) {
        // Older rows have no target or ball limit; they were never revised, so the defaults hold.
        val needed = ((chase.target ?: (first.runs + 1)) - chase.runs).coerceAtLeast(0)
        val balls = ((chase.maxBalls ?: (match.oversPerInnings * 6)) - chase.legalBalls).coerceAtLeast(0)
        return pluralStringResource(Res.plurals.match_need_runs, balls, match.team(chase.battingSide).name, needed, balls)
    }
    val toss = if (match.tossDecision == TossDecision.BAT) Res.string.match_toss_chose_bat else Res.string.match_toss_chose_bowl
    return stringResource(toss, match.team(match.tossWinner).name)
}

/** "20 overs". */
@Composable
fun oversText(overs: Int): String = pluralStringResource(Res.plurals.match_overs, overs, overs)

/** The scorebook line under a batter: "c Rohit b Bumrah", "run out (Jadeja)", "not out". */
@Composable
fun dismissalText(dismissal: Dismissal?): String {
    if (dismissal == null) return stringResource(Res.string.scorecard_not_out)
    val bowler = dismissal.bowler?.name.orEmpty()
    val fielder = dismissal.fielder?.name
    return when (dismissal.kind) {
        DismissalKind.BOWLED -> stringResource(Res.string.scorecard_out_bowled, bowler)
        DismissalKind.CAUGHT -> when {
            fielder == null -> stringResource(Res.string.scorecard_out_caught_no_fielder, bowler)
            dismissal.fielder == dismissal.bowler -> stringResource(Res.string.scorecard_out_caught_and_bowled, bowler)
            else -> stringResource(Res.string.scorecard_out_caught, fielder, bowler)
        }
        DismissalKind.LBW -> stringResource(Res.string.scorecard_out_lbw, bowler)
        DismissalKind.RUN_OUT ->
            if (fielder == null) stringResource(Res.string.scorecard_out_run_out_no_fielder)
            else stringResource(Res.string.scorecard_out_run_out, fielder)
        DismissalKind.STUMPED ->
            if (fielder == null) stringResource(Res.string.scorecard_out_stumped_no_fielder, bowler)
            else stringResource(Res.string.scorecard_out_stumped, fielder, bowler)
        DismissalKind.HIT_WICKET -> stringResource(Res.string.scorecard_out_hit_wicket, bowler)
        DismissalKind.OBSTRUCTING_FIELD -> stringResource(Res.string.scorecard_out_obstructing)
        DismissalKind.HIT_BALL_TWICE -> stringResource(Res.string.scorecard_out_hit_twice)
        DismissalKind.OTHER -> stringResource(Res.string.scorecard_out_other)
    }
}

/** One line of commentary: "Dev to Rohan, FOUR", "Dev to Rohan, OUT! Rohan c Esha b Dev". */
@Composable
fun commentaryText(ball: BallEvent): String {
    val d = ball.delivery
    val event = when {
        d.wicket != null -> {
            val kind = d.wicket!!.kind
            val dismissal = dismissalText(Dismissal(kind, ball.bowler.takeIf { kind.creditedToBowler }, ball.fielder))
            val out = ball.playerOut?.name.orEmpty()
            if (d.runsRun > 0) pluralStringResource(Res.plurals.commentary_out_after_runs, d.runsRun, d.runsRun, out, dismissal)
            else stringResource(Res.string.commentary_out, out, dismissal)
        }
        d.extra == ExtraType.WIDE ->
            if (d.extraRuns <= d.extraPenalty) stringResource(Res.string.commentary_wide)
            else pluralStringResource(Res.plurals.commentary_wide_runs, d.extraRuns, d.extraRuns)
        d.extra == ExtraType.NO_BALL ->
            if (d.runsOffBat == 0) stringResource(Res.string.commentary_no_ball)
            else pluralStringResource(Res.plurals.commentary_no_ball_runs, d.runsOffBat, d.runsOffBat)
        d.extra == ExtraType.BYE -> pluralStringResource(Res.plurals.commentary_byes, d.extraRuns, d.extraRuns)
        d.extra == ExtraType.LEG_BYE -> pluralStringResource(Res.plurals.commentary_leg_byes, d.extraRuns, d.extraRuns)
        d.runsOffBat == 4 -> stringResource(Res.string.commentary_four)
        d.runsOffBat == 6 -> stringResource(Res.string.commentary_six)
        d.runsOffBat == 0 -> stringResource(Res.string.commentary_dot)
        else -> pluralStringResource(Res.plurals.commentary_runs, d.runsOffBat, d.runsOffBat)
    }
    return stringResource(Res.string.commentary_line, ball.bowler.name, ball.striker.name, event)
}

val FieldZone.label: StringResource
    get() = when (this) {
        FieldZone.THIRD_MAN -> Res.string.zone_third_man
        FieldZone.POINT -> Res.string.zone_point
        FieldZone.COVER -> Res.string.zone_cover
        FieldZone.LONG_OFF -> Res.string.zone_long_off
        FieldZone.LONG_ON -> Res.string.zone_long_on
        FieldZone.MID_WICKET -> Res.string.zone_mid_wicket
        FieldZone.SQUARE_LEG -> Res.string.zone_square_leg
        FieldZone.FINE_LEG -> Res.string.zone_fine_leg
    }

val DeliveryError.label: StringResource
    get() = when (this) {
        DeliveryError.MATCH_OVER -> Res.string.scoring_error_match_over
        DeliveryError.END_INVALID -> Res.string.scoring_error_end_invalid
        DeliveryError.STALE -> Res.string.scoring_error_stale
        DeliveryError.WRONG_INNINGS -> Res.string.scoring_error_wrong_innings
        DeliveryError.SAME_BATTER -> Res.string.scoring_error_same_batter
        DeliveryError.BATTER_NOT_IN_TEAM -> Res.string.scoring_error_batter_not_in_team
        DeliveryError.BATTER_ALREADY_OUT -> Res.string.scoring_error_batter_already_out
        DeliveryError.WRONG_STRIKER -> Res.string.scoring_error_wrong_striker
        DeliveryError.WRONG_NON_STRIKER -> Res.string.scoring_error_wrong_non_striker
        DeliveryError.BOWLER_NOT_IN_TEAM -> Res.string.scoring_error_bowler_not_in_team
        DeliveryError.OVER_NOT_FINISHED -> Res.string.scoring_error_over_not_finished
        DeliveryError.RUNS_OUT_OF_RANGE -> Res.string.scoring_error_runs_out_of_range
        DeliveryError.EXTRA_RUNS_ON_LEGAL_BALL -> Res.string.scoring_error_extra_runs_on_legal_ball
        DeliveryError.INVALID_WIDE -> Res.string.scoring_error_invalid_wide
        DeliveryError.INVALID_NO_BALL -> Res.string.scoring_error_invalid_no_ball
        DeliveryError.INVALID_BYES -> Res.string.scoring_error_invalid_byes
        DeliveryError.OUT_PLAYER_NOT_AT_CREASE -> Res.string.scoring_error_out_player_not_at_crease
        DeliveryError.ONLY_STRIKER_CAN_BE_OUT -> Res.string.scoring_error_only_striker_can_be_out
        DeliveryError.DISMISSAL_NOT_POSSIBLE_ON_EXTRA -> Res.string.scoring_error_dismissal_not_possible
        DeliveryError.NO_RUNS_ON_DISMISSAL -> Res.string.scoring_error_no_runs_on_dismissal
        DeliveryError.FIELDER_NOT_ALLOWED -> Res.string.scoring_error_fielder_not_allowed
        DeliveryError.FIELDER_NOT_IN_TEAM -> Res.string.scoring_error_fielder_not_in_team
        DeliveryError.BATTER_NOT_AT_CREASE -> Res.string.scoring_error_batter_not_at_crease
        DeliveryError.BATTERS_MISSING -> Res.string.scoring_error_batters_missing
        DeliveryError.NO_BOWLER_TO_REPLACE -> Res.string.scoring_error_no_bowler_to_replace
        DeliveryError.PENALTY_OUT_OF_RANGE -> Res.string.scoring_error_penalty_out_of_range
        DeliveryError.EXTRA_NOT_PER_RULES -> Res.string.scoring_error_extra_not_per_rules
        DeliveryError.NON_STRIKER_MISSING -> Res.string.scoring_error_non_striker_missing
        DeliveryError.RETIRED_CANNOT_RETURN -> Res.string.scoring_error_retired_cannot_return
        DeliveryError.MUST_RETIRE -> Res.string.scoring_error_must_retire
        DeliveryError.BOWLER_OVER_LIMIT -> Res.string.scoring_error_bowler_over_limit
        DeliveryError.CONSECUTIVE_OVERS -> Res.string.scoring_error_consecutive_overs
        DeliveryError.NOT_OUT_ON_FREE_HIT -> Res.string.scoring_error_not_out_on_free_hit
        DeliveryError.REVISED_OVERS_INVALID -> Res.string.scoring_error_revised_overs_invalid
        DeliveryError.REVISED_TARGET_INVALID -> Res.string.scoring_error_revised_target_invalid
        DeliveryError.ZONE_WITHOUT_RUNS -> Res.string.scoring_error_zone_without_runs
    }

// Previews: sample teams and players only; every line of text comes from string resources.

private val Rohit = Player("p1", "Rohit")
private val Bumrah = Player("p2", "Bumrah")
private val Jadeja = Player("p3", "Jadeja")

private val PreviewMatch = Match(
    id = "preview",
    createdBy = "preview",
    createdAt = 0,
    teamA = Team("Andheri Strikers", emptyList()),
    teamB = Team("Powai XI", emptyList()),
    oversPerInnings = 20,
    tossWinner = TeamSide.A,
    tossDecision = TossDecision.BAT,
)

private val PreviewDismissals = listOf(
    null,
    Dismissal(DismissalKind.BOWLED, Bumrah, null),
    Dismissal(DismissalKind.CAUGHT, Bumrah, Rohit),
    Dismissal(DismissalKind.CAUGHT, Bumrah, Bumrah),
    Dismissal(DismissalKind.CAUGHT, Bumrah, null),
    Dismissal(DismissalKind.LBW, Bumrah, null),
    Dismissal(DismissalKind.RUN_OUT, null, Jadeja),
    Dismissal(DismissalKind.RUN_OUT, null, null),
    Dismissal(DismissalKind.STUMPED, Bumrah, Rohit),
    Dismissal(DismissalKind.HIT_WICKET, Bumrah, null),
)

private val PreviewResults = listOf(
    MatchResult(TeamSide.A, 24, MarginUnit.RUNS),
    MatchResult(TeamSide.B, 1, MarginUnit.WICKETS),
    MatchResult.Tie,
)

@Preview
@Composable
private fun ScoringTextPreview() = DesiTheme(darkTheme = false) {
    Column(
        Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        PreviewDismissals.forEach { Text(dismissalText(it), style = DesiTheme.type.body, color = DesiTheme.colors.ink) }
        PreviewResults.forEach { Text(resultText(PreviewMatch, it), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.brand) }
        Text(stringResource(DeliveryError.OVER_NOT_FINISHED.label), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
    }
}
