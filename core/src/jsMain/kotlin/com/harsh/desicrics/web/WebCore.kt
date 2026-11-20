@file:OptIn(ExperimentalJsExport::class)

package com.harsh.desicrics.web

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The scoring engine for the web app. Everything goes in and out as JSON strings, so the TypeScript side
 * only deals with plain objects and the types in `web/src/core/types.ts` mirror the classes below.
 */
@JsExport
object WebCore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** [detailsJson] is a `MatchDetails` from the API; returns a [ScorecardView]. */
    fun scorecard(detailsJson: String): String {
        val details = json.decodeFromString<MatchDetails>(detailsJson)
        val state = ScoringEngine.compute(details.match, details.deliveries, details.events)
        return json.encodeToString(state.toView())
    }
}

@Serializable
data class ScorecardView(
    val innings: List<InningsView>,
    val isComplete: Boolean,
    val result: MatchResult?,
    val scoreSummary: String,
)

@Serializable
data class InningsView(
    val number: Int,
    val battingSide: TeamSide,
    val battingTeam: String,
    val bowlingTeam: String,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    val maxBalls: Int,
    val scoreText: String,
    val oversText: String,
    val runRate: Double,
    val target: Int?,
    val runsNeeded: Int?,
    val ballsRemaining: Int,
    val requiredRate: Double?,
    val isComplete: Boolean,
    val isFreeHit: Boolean,
    val isPowerplay: Boolean,
    val declared: Boolean,
    val isRevised: Boolean,
    val extras: ExtrasView,
    val batters: List<BatterView>,
    val bowlers: List<BowlerView>,
    val yetToBat: List<Player>,
    val currentOver: List<Delivery>,
    val strikerId: String?,
    val nonStrikerId: String?,
    val bowlerId: String?,
    val overs: List<OverView>,
)

@Serializable
data class ExtrasView(val wides: Int, val noBalls: Int, val byes: Int, val legByes: Int, val penalties: Int, val total: Int)

@Serializable
data class BatterView(
    val player: Player,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val isOut: Boolean,
    val dismissal: DismissalView?,
    /** `HURT`, `NOT_OUT` or `OUT` while retired. */
    val retired: String?,
)

@Serializable
data class DismissalView(val kind: DismissalKind, val bowler: String?, val fielder: String?)

@Serializable
data class BowlerView(
    val player: Player,
    val oversText: String,
    val legalBalls: Int,
    val maidens: Int,
    val runs: Int,
    val wickets: Int,
    val economy: Double,
)

@Serializable
data class OverView(val number: Int, val bowler: String, val runs: Int, val wickets: Int, val scoreAfter: String, val balls: List<BallView>)

@Serializable
data class BallView(
    val ballNumber: String,
    val delivery: Delivery,
    val striker: String,
    val bowler: String,
    val playerOut: String?,
    val fielder: String?,
)

private fun MatchState.toView() = ScorecardView(
    innings = innings.map { it.toView() },
    isComplete = isComplete,
    result = result,
    scoreSummary = scoreSummary,
)

private fun InningsState.toView() = InningsView(
    number = number,
    battingSide = battingSide,
    battingTeam = battingTeam.name,
    bowlingTeam = bowlingTeam.name,
    runs = runs,
    wickets = wickets,
    legalBalls = legalBalls,
    maxBalls = maxBalls,
    scoreText = scoreText,
    oversText = oversText,
    runRate = runRate,
    target = target,
    runsNeeded = runsNeeded,
    ballsRemaining = ballsRemaining,
    requiredRate = runsNeeded?.takeIf { ballsRemaining > 0 }?.let { it * 6.0 / ballsRemaining },
    isComplete = isComplete,
    isFreeHit = isFreeHit,
    isPowerplay = isPowerplay,
    declared = declared,
    isRevised = isRevised,
    extras = ExtrasView(extras.wides, extras.noBalls, extras.byes, extras.legByes, extras.penalties, extras.total),
    batters = batters.map { card ->
        BatterView(
            player = card.player,
            runs = card.runs,
            balls = card.balls,
            fours = card.fours,
            sixes = card.sixes,
            strikeRate = card.strikeRate,
            isOut = card.isOut,
            dismissal = card.dismissal?.let { DismissalView(it.kind, it.bowler?.name, it.fielder?.name) },
            retired = card.retired?.name,
        )
    },
    bowlers = bowlers.map { BowlerView(it.player, it.oversText, it.legalBalls, it.maidens, it.runs, it.wickets, it.economy) },
    yetToBat = yetToBat,
    currentOver = currentOver,
    strikerId = strikerId,
    nonStrikerId = nonStrikerId,
    bowlerId = bowlerId,
    overs = overs.map { over ->
        OverView(
            number = over.number,
            bowler = over.bowler.name,
            runs = over.runs,
            wickets = over.wickets,
            scoreAfter = over.scoreAfter,
            balls = over.balls.map { BallView(it.ballNumber, it.delivery, it.striker.name, it.bowler.name, it.playerOut?.name, it.fielder?.name) },
        )
    },
)
