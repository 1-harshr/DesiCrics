package com.harsh.desicrics.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.BatterCard
import com.harsh.desicrics.scoring.BowlerCard
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.stats.MvpPoints
import com.harsh.desicrics.ui.text.resultText
import org.jetbrains.compose.resources.stringResource

/** The top MVP once the match is over; null while it's on or if nobody did anything. */
internal fun MatchState.playerOfTheMatch(): Player? {
    if (!isComplete) return null
    val id = MvpPoints.forMatch(this).maxByOrNull { it.value }?.key ?: return null
    return (match.teamA.players + match.teamB.players).firstOrNull { it.id == id }
}

/** The innings that have started, or the first one before a ball is bowled. */
internal val MatchState.playedInnings: List<InningsState>
    get() = innings.filter { it.legalBalls > 0 || it.runs > 0 }.ifEmpty { innings.take(1) }

/** "Andheri Strikers v Powai XI". */
@Composable
internal fun MatchState.posterTitle() = stringResource(Res.string.match_title, match.teamA.name, match.teamB.name)

/** How it ended, or the title while it's on. */
@Composable
internal fun MatchState.statusText() = result?.let { resultText(match, it) } ?: posterTitle()

/** One team's score on a result poster; [won] lights it up. */
@Immutable
data class PosterScore(val team: String, val score: String, val overs: String, val won: Boolean)

/** A match for the Big score poster. [finished] is false for a live score, which has no winner yet. */
@Immutable
data class BigScoreFigures(
    val scores: List<PosterScore>,
    val result: String,
    val playerOfTheMatch: String?,
    val caption: String,
    val finished: Boolean,
)

@Composable
internal fun MatchState.bigScoreFigures(): BigScoreFigures {
    val winner = result?.winner
    return BigScoreFigures(
        scores = playedInnings.map {
            PosterScore(it.battingTeam.name, it.scoreText, stringResource(Res.string.share_overs_detail, it.oversText), won = winner != null && it.battingSide == winner)
        },
        result = statusText(),
        playerOfTheMatch = playerOfTheMatch()?.name,
        caption = posterTitle(),
        finished = isComplete,
    )
}

// ---- Scorecard ----

/**
 * One line of the Full card poster. Every row has a fixed height in design dp, so the pages can be
 * worked out before anything is drawn.
 */
sealed interface CardRow {
    val innings: InningsState
    val height: Float

    /** "Andheri Strikers innings · 27/1 (2.0 ov)", or "…, continued" at the top of a later page. */
    data class Head(override val innings: InningsState, val continued: Boolean = false) : CardRow {
        override val height = 36f
    }
    data class BattingHead(override val innings: InningsState) : CardRow {
        override val height = 22f
    }
    data class Batter(override val innings: InningsState, val card: BatterCard) : CardRow {
        override val height = 28f
    }
    data class Extras(override val innings: InningsState) : CardRow {
        override val height = 22f
    }
    data class BowlingHead(override val innings: InningsState) : CardRow {
        override val height = 28f
    }
    data class Bowler(override val innings: InningsState, val card: BowlerCard) : CardRow {
        override val height = 23f
    }
    data class Gap(override val innings: InningsState) : CardRow {
        override val height = 12f
    }
}

/** Each innings' heading, batters who batted, extras and bowlers who bowled, in order. */
internal fun MatchState.cardRows(): List<CardRow> = buildList {
    playedInnings.forEachIndexed { i, innings ->
        if (i > 0) add(CardRow.Gap(innings))
        add(CardRow.Head(innings))
        add(CardRow.BattingHead(innings))
        innings.batters.filter { it.balls > 0 || it.runs > 0 || it.isOut }.forEach { add(CardRow.Batter(innings, it)) }
        add(CardRow.Extras(innings))
        val bowled = innings.bowlers.filter { it.legalBalls > 0 || it.runs > 0 }
        if (bowled.isNotEmpty()) {
            add(CardRow.BowlingHead(innings))
            bowled.forEach { add(CardRow.Bowler(innings, it)) }
        }
    }
}

/** The Full card's green header band, in design dp. */
internal const val FULL_CARD_HEADER = 116f

/** What the Full card leaves for rows on a story, after its header band, the gap under it and the footer. */
private val FULL_CARD_BUDGET = ShareFormat.STORY.designHeight.value - FULL_CARD_HEADER - 12f - 52f

/** The Full card's pages: a story's worth of rows each. */
internal fun paginateCard(rows: List<CardRow>): List<List<CardRow>> = paginate(
    rows,
    FULL_CARD_BUDGET,
    height = { it.height },
    keepsWithNext = { it is CardRow.Head || it is CardRow.BattingHead || it is CardRow.BowlingHead || it is CardRow.Gap },
    carry = { row ->
        when (row) {
            is CardRow.Batter -> listOf(CardRow.Head(row.innings, continued = true), CardRow.BattingHead(row.innings))
            is CardRow.Bowler -> listOf(CardRow.Head(row.innings, continued = true), CardRow.BowlingHead(row.innings))
            is CardRow.Extras, is CardRow.BowlingHead -> listOf(CardRow.Head(row.innings, continued = true))
            else -> emptyList()
        }
    },
    dropAtTop = { it is CardRow.Gap },
)

/** Both innings as a full scorecard (split into pages when it's long), or the stars of each. */
class ScorecardShare(private val state: MatchState) : ShareContent {
    override val key = KEY

    @Composable
    override fun label() = stringResource(Res.string.share_content_scorecard)

    private val rows = state.cardRows()
    private val pages = paginateCard(rows)

    override val templates: List<ShareTemplate> = listOf(
        // A whole scorecard is tall, so it's a story only: a post would take twice the pages.
        ShareTemplate.paged("fullcard", Res.string.share_template_full_card, TemplateKind.OPAQUE, listOf(ShareFormat.STORY), pages = { pages.size }) { _, page ->
            FullCardTemplate(state.posterTitle(), state.statusText(), pages[page.coerceIn(pages.indices)], page, pages.size)
        },
        ShareTemplate("performers", Res.string.share_template_top_performers, TemplateKind.OPAQUE) { format ->
            TopPerformersTemplate(state.posterTitle(), state.statusText(), state.playedInnings, format)
        },
    )

    companion object {
        const val KEY = "scorecard"
    }
}

// ---- Player of the match ----

/** The player of the match for the Spotlight and overlay: what they did with bat, ball and in the field. */
@Immutable
data class SpotlightFigures(
    val name: String,
    val team: String,
    val photoUrl: String?,
    /** Their best contribution first. */
    val stats: List<ShareStat>,
    val result: String,
    val caption: String,
)

/** The top MVP of a finished match, with their bat, ball and fielding figures. */
class PlayerOfTheMatchShare(private val state: MatchState, private val player: Player) : ShareContent {
    override val key = KEY

    @Composable
    override fun label() = stringResource(Res.string.share_content_player_of_the_match)

    override val templates: List<ShareTemplate> = listOf(
        ShareTemplate("spotlight", Res.string.share_template_spotlight, TemplateKind.OPAQUE) { SpotlightTemplate(figures(), it) },
        ShareTemplate("overlay", Res.string.share_template_overlay, TemplateKind.OVERLAY) { PlayerOfTheMatchOverlay(figures(), it) },
    )

    @Composable
    private fun figures(): SpotlightFigures {
        val batting = state.innings.flatMap { it.batters }.filter { it.player.id == player.id && (it.balls > 0 || it.runs > 0 || it.isOut) }
        val bowling = state.innings.flatMap { it.bowlers }.filter { it.player.id == player.id && it.legalBalls > 0 }
        val fielding = state.innings.flatMap { it.batters }.mapNotNull { it.dismissal }
            .filter { it.fielder?.id == player.id && it.kind in FIELDED }
        val runs = batting.sumOf { it.runs }
        val wickets = bowling.sumOf { it.wickets }
        val bat = batting.takeIf { it.isNotEmpty() }?.let { cards ->
            val notOut = cards.none { it.isOut }
            ShareStat("$runs${if (notOut) "*" else ""} (${cards.sumOf { it.balls }})", stringResource(Res.string.stats_batting))
        }
        val bowl = bowling.takeIf { it.isNotEmpty() }?.let { cards ->
            ShareStat("$wickets/${cards.sumOf { it.runs }}", stringResource(Res.string.stats_bowling))
        }
        val field = fielding.takeIf { it.isNotEmpty() }?.let { dismissals ->
            val allCatches = dismissals.all { it.kind == DismissalKind.CAUGHT }
            ShareStat("${dismissals.size}", stringResource(if (allCatches) Res.string.stats_catches else Res.string.stats_fielding))
        }
        // A wicket is worth about 20 runs, as in MVP points.
        val stats = if (wickets * 20 > runs) listOfNotNull(bowl, bat, field) else listOfNotNull(bat, bowl, field)
        val team = if (state.match.teamA.players.any { it.id == player.id }) state.match.teamA else state.match.teamB
        return SpotlightFigures(player.name, team.name, photoUrl = null, stats, state.statusText(), state.posterTitle())
    }

    companion object {
        const val KEY = "player-of-the-match"
        private val FIELDED = setOf(DismissalKind.CAUGHT, DismissalKind.RUN_OUT, DismissalKind.STUMPED)
    }
}
