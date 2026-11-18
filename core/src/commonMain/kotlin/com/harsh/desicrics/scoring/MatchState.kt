package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide

data class MatchState(
    val match: Match,
    /** Innings started so far: one, or two once the first is complete. */
    val innings: List<InningsState>,
    val isComplete: Boolean,
    val result: MatchResult?,
) {
    val current: InningsState get() = innings.last()

    val scoreSummary: String
        get() = innings.joinToString(" · ") { "${it.battingTeam.name} ${it.scoreText} (${it.oversText})" }
}

data class InningsState(
    val number: Int,
    val battingSide: TeamSide,
    val battingTeam: Team,
    val bowlingTeam: Team,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    val maxBalls: Int,
    val extras: Extras,
    val batters: List<BatterCard>,
    val bowlers: List<BowlerCard>,
    /** Deliveries in the over in progress, or the over just finished until the next ball is bowled. */
    val currentOver: List<Delivery>,
    /** Who should be at the crease / bowling for the next ball; null means the scorer must pick. */
    val strikerId: String?,
    val nonStrikerId: String?,
    val bowlerId: String?,
    /** Bowled the previous over, so can't bowl the next one. */
    val previousOverBowlerId: String?,
    val target: Int?,
    val isComplete: Boolean,
    /** Every over started so far, oldest first, for the over-by-over view and commentary. */
    val overs: List<OverSummary> = emptyList(),
    /**
     * Batters who can walk in now: those yet to bat and retired batters the rules let back, not counting
     * the two at the crease.
     */
    val canComeIn: List<Player> = emptyList(),
    /** Wickets that end the innings: one fewer than the side, or all of it when the last man stands. */
    val allOutWickets: Int = battingTeam.players.size - 1,
    /** Retired batters the rules don't let back yet. Left out of [canComeIn] only when the rule is enforced. */
    val returnBlocked: Set<String> = emptySet(),
    /** The next ball is a free hit. */
    val isFreeHit: Boolean = false,
) {
    val scoreText: String get() = "$runs/$wickets"
    val oversText: String get() = formatOvers(legalBalls)
    val runRate: Double get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
    val runsNeeded: Int? get() = target?.let { (it - runs).coerceAtLeast(0) }
    val ballsRemaining: Int get() = (maxBalls - legalBalls).coerceAtLeast(0)

    /** Batting side players who haven't come out to bat yet. */
    val yetToBat: List<Player>
        get() = battingTeam.players.filter { player -> batters.none { it.player.id == player.id } }
}

/** One over: who bowled it, each ball in order, and the score once it ended (or so far). */
data class OverSummary(
    /** 1-based. */
    val number: Int,
    val bowler: Player,
    val balls: List<BallEvent>,
    val runs: Int,
    val wickets: Int,
    /** Innings total after the last ball of this over. */
    val scoreAfter: String,
)

/** A delivery with the players it involved, ready to describe. */
data class BallEvent(
    /** Over and ball as scorers write it, e.g. "2.4". Extras share the number of the ball that follows. */
    val ballNumber: String,
    val delivery: Delivery,
    val striker: Player,
    val bowler: Player,
    val playerOut: Player?,
    val fielder: Player?,
)

data class Extras(
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
    val penalties: Int = 0,
) {
    val total: Int get() = wides + noBalls + byes + legByes + penalties

    operator fun plus(delivery: Delivery): Extras = when (delivery.extra) {
        ExtraType.WIDE -> copy(wides = wides + delivery.extraRuns)
        ExtraType.NO_BALL -> copy(noBalls = noBalls + delivery.extraRuns)
        ExtraType.BYE -> copy(byes = byes + delivery.extraRuns)
        ExtraType.LEG_BYE -> copy(legByes = legByes + delivery.extraRuns)
        null -> this
    }
}

data class BatterCard(
    val player: Player,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    /** Null while not out. */
    val dismissal: Dismissal? = null,
    /** Set while retired; cleared if a retired batter comes back. */
    val retired: Retirement? = null,
    /**
     * The innings' wickets when they last retired, for [com.harsh.desicrics.model.RetiredReturn.AFTER_WICKET].
     * Kept if they come back, so a batter who already retired at the run limit isn't asked to again.
     */
    val retiredAtWickets: Int? = null,
) {
    val isOut: Boolean get() = dismissal != null || retired == Retirement.OUT
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
}

/** Retired hurt or not out can bat again, if the rules allow; retired out counts as a wicket. */
enum class Retirement {
    HURT,
    /** Made to retire on reaching the match's run limit. */
    NOT_OUT,
    OUT;

    val canReturn: Boolean get() = this != OUT
}

/** How a batter got out. The app turns it into "c Rohit b Bumrah" with its own strings. */
data class Dismissal(
    val kind: DismissalKind,
    /** Null for a run out, which isn't credited to the bowler. */
    val bowler: Player?,
    val fielder: Player?,
)

data class BowlerCard(
    val player: Player,
    val legalBalls: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0,
) {
    val oversText: String get() = formatOvers(legalBalls)
    val economy: Double get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
}

fun formatOvers(legalBalls: Int): String = "${legalBalls / 6}.${legalBalls % 6}"
