package com.harsh.desicrics.ui.share

import androidx.compose.runtime.Composable
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.BatterCard
import com.harsh.desicrics.scoring.BowlerCard
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.stats.MvpPoints
import com.harsh.desicrics.ui.text.dismissalText
import com.harsh.desicrics.ui.text.resultText
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.stringResource

/**
 * What a match offers the studio: the score or result first, then each batter's innings and each
 * bowler's spell, in batting order across both innings.
 */
fun matchShareContents(state: MatchState): List<ShareContent> = buildList {
    add(ResultShare(state))
    state.innings.forEach { innings ->
        innings.batters.filter { it.balls > 0 || it.runs > 0 }.forEach { add(BattingShare(state, innings, it)) }
    }
    state.innings.forEach { innings ->
        innings.bowlers.filter { it.legalBalls > 0 }.forEach { add(BowlingShare(state, innings, it)) }
    }
}

/** The content key for a batter's innings, so a scorecard row can open the studio on it. */
fun battingShareKey(innings: Int, playerId: String) = "batting-$innings-$playerId"

/** The content key for a bowler's spell. */
fun bowlingShareKey(innings: Int, playerId: String) = "bowling-$innings-$playerId"

@Composable
private fun MatchState.caption() = stringResource(Res.string.match_title, match.teamA.name, match.teamB.name)

/** Both totals and the result, or while it's still on, where the match stands. */
class ResultShare(private val state: MatchState) : ShareContent {
    override val key = KEY

    @Composable
    override fun label() = stringResource(if (state.result != null) Res.string.share_content_result else Res.string.share_content_score)

    override val templates: List<ShareTemplate> = summaryTemplates { summary() } + ShareTemplate(
        "overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY,
    ) { format ->
        val summary = summary()
        ResultOverlay(ResultFigures(summary.headlines, summary.lines.first(), playerOfTheMatch(), summary.caption), format)
    }

    @Composable
    private fun summary(): ShareSummary {
        val match = state.match
        val played = state.innings.filter { it.legalBalls > 0 || it.runs > 0 }.ifEmpty { state.innings.take(1) }
        val status = state.result?.let { resultText(match, it) } ?: stringResource(Res.string.match_title, match.teamA.name, match.teamB.name)
        return ShareSummary(
            title = null,
            headlines = played.map { ShareHeadline(it.battingTeam.name, it.scoreText, stringResource(Res.string.share_overs_detail, it.oversText)) },
            lines = listOf(status),
            caption = state.caption(),
        )
    }

    companion object {
        const val KEY = "result"
    }

    /** The top MVP once the match is over. */
    private fun playerOfTheMatch(): String? {
        if (!state.isComplete) return null
        val id = MvpPoints.forMatch(state).maxByOrNull { it.value }?.key ?: return null
        return (state.match.teamA.players + state.match.teamB.players).firstOrNull { it.id == id }?.name
    }
}

/** One batter's innings: runs (balls), strike rate, boundaries, and where the runs went. */
class BattingShare(private val state: MatchState, private val innings: InningsState, private val card: BatterCard) : ShareContent {
    override val key = battingShareKey(innings.number, card.player.id)

    @Composable
    override fun label() = stringResource(Res.string.share_content_batting, card.player.name)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { format ->
            val zones = innings.zoneRuns(card.player.id)
            BattingOverlay(
                BattingFigures(
                    name = card.player.name,
                    runs = card.runs,
                    balls = card.balls,
                    notOut = !card.isOut,
                    strikeRate = card.strikeRate.format2(),
                    fours = card.fours,
                    sixes = card.sixes,
                    zones = zones.takeIf { it.isNotEmpty() }?.let { z -> FieldZone.Clockwise.map { z[it] ?: 0 } },
                    caption = state.caption(),
                ),
                format,
            )
        },
    ) + summaryTemplates { summary() }

    @Composable
    private fun summary() = ShareSummary(
        title = card.player.name,
        headlines = listOf(
            ShareHeadline(innings.battingTeam.name, "${card.runs}${if (card.isOut) "" else "*"}", stringResource(Res.string.share_balls_detail, card.balls)),
        ),
        stats = listOf(
            ShareStat(card.strikeRate.format2(), stringResource(Res.string.stats_strike_rate)),
            ShareStat("${card.fours}", stringResource(Res.string.stats_fours)),
            ShareStat("${card.sixes}", stringResource(Res.string.stats_sixes)),
        ),
        lines = listOf(dismissalText(card.dismissal)),
        caption = state.caption(),
    )
}

/** One bowler's spell: figures, overs and economy, and the batters they got out and how. */
class BowlingShare(private val state: MatchState, private val innings: InningsState, private val card: BowlerCard) : ShareContent {
    override val key = bowlingShareKey(innings.number, card.player.id)

    @Composable
    override fun label() = stringResource(Res.string.share_content_bowling, card.player.name)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { format ->
            BowlingOverlay(
                BowlingFigures(
                    name = card.player.name,
                    figures = "${card.wickets}/${card.runs}",
                    overs = card.oversText,
                    economy = card.economy.format2(),
                    maidens = card.maidens,
                    wickets = wicketLines(),
                    caption = state.caption(),
                ),
                format,
            )
        },
    ) + summaryTemplates { summary() }

    /** "Rohan · c Esha b Dev" for each batter this bowler got out, in the order they fell. */
    @Composable
    private fun wicketLines(): List<String> = innings.batters
        .filter { it.dismissal?.let { d -> d.kind.creditedToBowler && d.bowler?.id == card.player.id } == true }
        .map { stringResource(Res.string.share_wicket_line, it.player.name, dismissalText(it.dismissal)) }

    @Composable
    private fun summary() = ShareSummary(
        title = card.player.name,
        headlines = listOf(
            ShareHeadline(innings.bowlingTeam.name, "${card.wickets}/${card.runs}", stringResource(Res.string.share_overs_detail, card.oversText)),
        ),
        stats = listOf(
            ShareStat(card.oversText, stringResource(Res.string.stats_overs)),
            ShareStat(card.economy.format2(), stringResource(Res.string.stats_economy)),
            ShareStat("${card.maidens}", stringResource(Res.string.share_maidens)),
        ),
        lines = wicketLines(),
        caption = state.caption(),
    )
}
