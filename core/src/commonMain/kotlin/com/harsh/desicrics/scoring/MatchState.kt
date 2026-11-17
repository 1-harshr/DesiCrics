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
) {
    val scoreText: String get() = "$runs/$wickets"
    val oversText: String get() = formatOvers(legalBalls)
    val runRate: Double get() = if (legalBalls == 0) 0.0 else runs * 6.0 / legalBalls
    val runsNeeded: Int? get() = target?.let { (it - runs).coerceAtLeast(0) }
    val ballsRemaining: Int get() = (maxBalls - legalBalls).coerceAtLeast(0)
    val allOutWickets: Int get() = battingTeam.players.size - 1

    /** Batting side players who haven't come out to bat yet. */
    val yetToBat: List<Player>
        get() = battingTeam.players.filter { player -> batters.none { it.player.id == player.id } }
}

data class Extras(
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
) {
    val total: Int get() = wides + noBalls + byes + legByes

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
) {
    val isOut: Boolean get() = dismissal != null
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
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
