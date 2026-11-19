package com.harsh.desicrics.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How bids come in. In [HALL] everyone is in one room and the auctioneer taps the team that bid; in [ONLINE]
 * team owners bid from their own phones against a countdown the server runs.
 */
@Serializable
enum class AuctionMode { HALL, ONLINE }

/** The order players come up: by [AuctionSettings.categories], shuffled within each, or all shuffled. */
@Serializable
enum class AuctionOrder { CATEGORY, RANDOM }

@Serializable
enum class AuctionStatus { SETUP, LIVE, PAUSED, FINISHED }

/** Bids go up by [step] while the current bid is below [upTo]; the last step has no [upTo] and applies from then on. */
@Serializable
data class BidStep(val upTo: Int? = null, val step: Int)

/**
 * The rules of one auction. Money is points with a label the organiser picks ("₹ lakh", "pts"); nothing real
 * moves through the app.
 */
@Serializable
data class AuctionSettings(
    val mode: AuctionMode = AuctionMode.HALL,
    /** Every team starts with the same purse. */
    val purse: Int = 1000,
    val purseLabel: String = "pts",
    /**
     * A new player's base price, and the price a team keeps aside for each player it still needs: a team can
     * bid at most purse − (minimum squad still to fill × this), so it can always complete its squad.
     */
    val basePrice: Int = 10,
    /** The fewest players a team must end with, retained players included. */
    val minSquad: Int = 11,
    /** The most players a team can have; a full team can't bid. */
    val maxSquad: Int = 15,
    val bidSteps: List<BidStep> = listOf(BidStep(upTo = 50, step = 5), BidStep(step = 10)),
    val order: AuctionOrder = AuctionOrder.CATEGORY,
    /** Category names in the order they come up. Players without one come last. */
    val categories: List<String> = DEFAULT_CATEGORIES,
    /** Players nobody bought come back once more after everyone has been up. */
    val unsoldRound: Boolean = true,
    /** Online: seconds on the clock when a player comes up. */
    val lotSeconds: Int = 15,
    /** Online: what the clock goes back to after each bid. */
    val bidSeconds: Int = 10,
) {
    /** How much the next bid goes up from [current]. */
    fun stepAt(current: Int): Int = bidSteps.first { it.upTo == null || current < it.upTo }.step

    fun validate(): AuctionError? = when {
        purse !in 1..MAX_PURSE -> AuctionError.PURSE_INVALID
        purseLabel.isBlank() || purseLabel.trim().length > MAX_LABEL -> AuctionError.LABEL_INVALID
        basePrice !in 1..purse -> AuctionError.BASE_PRICE_INVALID
        maxSquad !in 1..MAX_SQUAD || minSquad !in 0..maxSquad -> AuctionError.SQUAD_INVALID
        minSquad.toLong() * basePrice > purse -> AuctionError.PURSE_TOO_SMALL
        bidSteps.isEmpty() || bidSteps.size > 10 || bidSteps.any { it.step !in 1..purse } -> AuctionError.STEPS_INVALID
        bidSteps.last().upTo != null || bidSteps.dropLast(1).any { it.upTo == null } -> AuctionError.STEPS_INVALID
        bidSteps.dropLast(1).zipWithNext().any { (a, b) -> a.upTo!! >= b.upTo!! } -> AuctionError.STEPS_INVALID
        categories.size > MAX_CATEGORIES || categories.any { it.isBlank() || it.length > MAX_CATEGORY } ||
            categories.map { it.trim().lowercase() }.toSet().size != categories.size -> AuctionError.CATEGORIES_INVALID
        lotSeconds !in 5..120 || bidSeconds !in 3..60 -> AuctionError.TIMER_INVALID
        else -> null
    }

    companion object {
        const val MAX_PURSE = 10_000_000
        const val MAX_LABEL = 12
        const val MAX_SQUAD = 30
        const val MAX_CATEGORIES = 12
        const val MAX_CATEGORY = 30
        val DEFAULT_CATEGORIES = listOf("Marquee", "Batter", "Bowler", "All-rounder", "Wicketkeeper")
    }
}

/**
 * A player in the auction's pool. [requested] entries asked to join from the tournament page and wait for the
 * organiser; they don't come up until accepted. A [retainedBy] team kept the player before the auction at
 * [retainedPrice], which comes off its purse.
 */
@Serializable
data class AuctionPlayer(
    val player: PlayerSummary,
    val basePrice: Int,
    val category: String? = null,
    val requested: Boolean = false,
    val retainedBy: String? = null,
    val retainedPrice: Int? = null,
    val addedAt: Long = 0,
) {
    val profileId: String get() = player.profileId
}

/**
 * Something that happened in an auction. The log is append-only: Undo is an event too, and the state is a fold
 * over the whole list (see [com.harsh.desicrics.auction.AuctionEngine]).
 */
@Serializable
sealed interface AuctionEvent {
    val seq: Int
    val timestamp: Long

    /** The auction opens with these teams, and players come up in [order]. */
    @Serializable
    @SerialName("start")
    data class Start(
        override val seq: Int,
        val teamIds: List<String>,
        val order: List<String>,
        override val timestamp: Long = 0,
    ) : AuctionEvent

    /** [profileId] comes up for bidding. */
    @Serializable
    @SerialName("next_player")
    data class NextPlayer(override val seq: Int, val profileId: String, override val timestamp: Long = 0) : AuctionEvent

    @Serializable
    @SerialName("bid")
    data class Bid(override val seq: Int, val teamId: String, val amount: Int, override val timestamp: Long = 0) : AuctionEvent

    /** The player up goes to the highest bidder. */
    @Serializable
    @SerialName("sold")
    data class Sold(
        override val seq: Int,
        val profileId: String,
        val teamId: String,
        val price: Int,
        override val timestamp: Long = 0,
    ) : AuctionEvent

    @Serializable
    @SerialName("unsold")
    data class Unsold(override val seq: Int, val profileId: String, override val timestamp: Long = 0) : AuctionEvent

    /** Takes back the latest bid, sale, unsold or player brought up that hasn't been undone already. */
    @Serializable
    @SerialName("undo")
    data class Undo(override val seq: Int, override val timestamp: Long = 0) : AuctionEvent

    @Serializable
    @SerialName("pause")
    data class Pause(override val seq: Int, override val timestamp: Long = 0) : AuctionEvent

    @Serializable
    @SerialName("resume")
    data class Resume(override val seq: Int, override val timestamp: Long = 0) : AuctionEvent

    /** The organiser cancels an earlier sale: the team gets its points back and the player can come up again. */
    @Serializable
    @SerialName("reverse_sale")
    data class ReverseSale(override val seq: Int, val profileId: String, override val timestamp: Long = 0) : AuctionEvent

    @Serializable
    @SerialName("finish")
    data class Finish(override val seq: Int, override val timestamp: Long = 0) : AuctionEvent
}

@Serializable
data class AuctionBid(val teamId: String, val amount: Int)

/** The player up for bidding. [round] is 2 when they're back in the unsold round. */
@Serializable
data class AuctionLot(
    val profileId: String,
    val basePrice: Int,
    val round: Int = 1,
    val bids: List<AuctionBid> = emptyList(),
    /** The least the next bid can be. */
    val nextBid: Int = basePrice,
) {
    val leading: AuctionBid? get() = bids.lastOrNull()
}

@Serializable
data class AuctionSale(val profileId: String, val teamId: String, val price: Int)

@Serializable
enum class AuctionPlayerStatus { WAITING, UP, SOLD, UNSOLD, RETAINED }

/** Where a team stands: also the summary at the end (players bought, points spent, points left). */
@Serializable
data class TeamPurse(
    val teamId: String,
    val purseLeft: Int,
    val spent: Int,
    val retained: List<String> = emptyList(),
    /** In the order they were bought. */
    val bought: List<String> = emptyList(),
    /** The most it can bid on the player up right now; 0 when full. */
    val maxBid: Int = 0,
    val full: Boolean = false,
    /** Players still needed to reach the minimum squad. */
    val needed: Int = 0,
) {
    val squadSize: Int get() = retained.size + bought.size
}

/** Everything about an auction worked out from its events. */
@Serializable
data class AuctionState(
    val status: AuctionStatus = AuctionStatus.SETUP,
    /** 1 for the main round, 2 once only unsold players are left. */
    val round: Int = 1,
    val lot: AuctionLot? = null,
    val teams: List<TeamPurse> = emptyList(),
    /** In the order they happened. */
    val sales: List<AuctionSale> = emptyList(),
    val players: Map<String, AuctionPlayerStatus> = emptyMap(),
    /** Who comes up next if the organiser doesn't pick someone else; null when nobody's left. */
    val upNext: String? = null,
    /** Every player in the order they come up, once started. */
    val order: List<String> = emptyList(),
    /** There's a bid, sale, unsold or player brought up to take back. */
    val canUndo: Boolean = false,
    /** The latest event's seq; the next event takes this + 1. */
    val lastSeq: Int = -1,
) {
    fun team(teamId: String): TeamPurse? = teams.firstOrNull { it.teamId == teamId }
}

enum class AuctionError(val message: String) {
    PURSE_INVALID("Set a purse between 1 and 10,000,000"),
    LABEL_INVALID("Give the points a short label, like pts or ₹ lakh"),
    BASE_PRICE_INVALID("The base price has to fit in the purse"),
    SQUAD_INVALID("Squads can be up to 30 players, and the minimum can't be more than the maximum"),
    PURSE_TOO_SMALL("The purse can't buy a minimum squad at the base price"),
    STEPS_INVALID("Bid steps need rising limits and a last step with no limit"),
    CATEGORIES_INVALID("Categories need different, short names"),
    TIMER_INVALID("The clock runs 5 to 120 seconds, and 3 to 60 after a bid"),
    RETAINED_INVALID("Retained players have to fit in the team's purse and squad"),
    TOO_FEW_TEAMS("An auction needs at least two confirmed teams"),
    NO_PLAYERS("Add players to the pool first"),
    NOT_STARTED("The auction hasn't started"),
    ALREADY_STARTED("The auction has already started"),
    FINISHED("The auction is over"),
    PAUSED("The auction is paused"),
    NOT_PAUSED("The auction isn't paused"),
    LOT_OPEN("Finish with the player up first"),
    NO_LOT("Nobody is up for bidding"),
    NOT_AVAILABLE("That player isn't waiting to come up"),
    WRONG_PLAYER("That player isn't the one up for bidding"),
    TEAM_UNKNOWN("That team isn't in the auction"),
    TEAM_FULL("That team's squad is full"),
    ALREADY_LEADING("That team already has the highest bid"),
    BID_TOO_LOW("That bid is too low"),
    BID_TOO_HIGH("That team can't afford that bid"),
    NO_BIDS("Nobody has bid; mark the player unsold instead"),
    NOT_HIGHEST_BID("That isn't the highest bid"),
    NOTHING_TO_UNDO("There's nothing to undo"),
    NOT_SOLD("That player wasn't sold in the auction"),
}

/** One auction per tournament. */
@Serializable
data class Auction(
    val id: String,
    val tournamentId: String,
    val tournamentName: String = "",
    val settings: AuctionSettings,
    val createdBy: String,
    val createdAt: Long = 0,
)

/** A team in the auction: one of the tournament's confirmed teams. Its admins are its owners, who bid online. */
@Serializable
data class AuctionTeam(
    val teamId: String,
    val name: String,
    val logoUrl: String? = null,
    val ownerIds: List<String> = emptyList(),
)

/**
 * An auction as the app shows it, and what its live feed sends on every change. It's the same for everyone
 * watching, so the caller's own part (organiser, or owner of a team) comes from [organiserIds] and
 * [AuctionTeam.ownerIds].
 */
@Serializable
data class AuctionDetails(
    val auction: Auction,
    val teams: List<AuctionTeam>,
    val organiserIds: List<String>,
    /** The pool, requests included, in the order they were added. */
    val players: List<AuctionPlayer>,
    val state: AuctionState,
    /** The event that made this change, e.g. a sale for the sold moment; null before any. */
    val latest: AuctionEvent? = null,
    /** Online: when the clock runs out on the player up, in the server's time. */
    val lotEndsAt: Long? = null,
    /** The server's clock when this was sent, so the app can count down without trusting the phone's clock. */
    val serverTime: Long = 0,
) {
    fun player(profileId: String): AuctionPlayer? = players.firstOrNull { it.profileId == profileId }
    fun isOrganiser(uid: String?): Boolean = uid in organiserIds
    /** Teams [uid] can bid for in online mode. */
    fun teamsOwnedBy(uid: String?): List<AuctionTeam> = teams.filter { uid in it.ownerIds }
}
