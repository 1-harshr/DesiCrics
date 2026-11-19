package com.harsh.desicrics.auction

import com.harsh.desicrics.model.AuctionBid
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionLot
import com.harsh.desicrics.model.AuctionOrder
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionState
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.TeamPurse
import kotlin.random.Random

/**
 * What an auction is played with: its settings, the teams in it before it starts (the tournament's confirmed
 * teams; [AuctionEvent.Start] fixes them), and the player pool.
 */
data class AuctionSetup(
    val settings: AuctionSettings,
    val teamIds: List<String>,
    val players: List<AuctionPlayer>,
)

/**
 * The auction's rules, as pure functions. The state is a fold over the append-only event list, just like
 * [com.harsh.desicrics.scoring.ScoringEngine] over deliveries, and [check] says whether an event may go next.
 * The server checks every action with it, and the app can use it to grey out what it can't do.
 */
object AuctionEngine {

    fun compute(setup: AuctionSetup, events: List<AuctionEvent>): AuctionState {
        val effective = effective(events)
        val fold = Fold(setup)
        effective.forEach(fold::apply)
        return fold.state(
            lastSeq = events.lastOrNull()?.seq ?: -1,
            canUndo = fold.status != AuctionStatus.FINISHED && effective.any { it.undoable },
        )
    }

    /** Why [event] can't come next after [state], or null if it can. */
    fun check(setup: AuctionSetup, state: AuctionState, event: AuctionEvent): AuctionError? {
        if (event is AuctionEvent.Start) {
            return when {
                state.status != AuctionStatus.SETUP -> AuctionError.ALREADY_STARTED
                event.teamIds.size < 2 -> AuctionError.TOO_FEW_TEAMS
                event.order.isEmpty() -> AuctionError.NO_PLAYERS
                else -> retainedError(setup.copy(teamIds = event.teamIds))
            }
        }
        when (state.status) {
            AuctionStatus.SETUP -> return AuctionError.NOT_STARTED
            AuctionStatus.FINISHED -> return AuctionError.FINISHED
            else -> Unit
        }
        val paused = state.status == AuctionStatus.PAUSED
        val lot = state.lot
        return when (event) {
            is AuctionEvent.Start -> null
            is AuctionEvent.NextPlayer -> when {
                lot != null -> AuctionError.LOT_OPEN
                paused -> AuctionError.PAUSED
                state.players[event.profileId] !in setOf(AuctionPlayerStatus.WAITING, AuctionPlayerStatus.UNSOLD) -> AuctionError.NOT_AVAILABLE
                else -> null
            }
            is AuctionEvent.Bid -> {
                val team = state.team(event.teamId)
                when {
                    lot == null -> AuctionError.NO_LOT
                    paused -> AuctionError.PAUSED
                    team == null -> AuctionError.TEAM_UNKNOWN
                    team.full -> AuctionError.TEAM_FULL
                    lot.leading?.teamId == event.teamId -> AuctionError.ALREADY_LEADING
                    event.amount < lot.nextBid -> AuctionError.BID_TOO_LOW
                    event.amount > team.maxBid -> AuctionError.BID_TOO_HIGH
                    else -> null
                }
            }
            is AuctionEvent.Sold -> when {
                lot == null -> AuctionError.NO_LOT
                paused -> AuctionError.PAUSED
                lot.profileId != event.profileId -> AuctionError.WRONG_PLAYER
                lot.leading == null -> AuctionError.NO_BIDS
                lot.leading != AuctionBid(event.teamId, event.price) -> AuctionError.NOT_HIGHEST_BID
                else -> null
            }
            is AuctionEvent.Unsold -> when {
                lot == null -> AuctionError.NO_LOT
                paused -> AuctionError.PAUSED
                lot.profileId != event.profileId -> AuctionError.WRONG_PLAYER
                else -> null
            }
            is AuctionEvent.Undo -> if (state.canUndo) null else AuctionError.NOTHING_TO_UNDO
            is AuctionEvent.Pause -> if (paused) AuctionError.PAUSED else null
            is AuctionEvent.Resume -> if (paused) null else AuctionError.NOT_PAUSED
            is AuctionEvent.ReverseSale -> if (state.sales.any { it.profileId == event.profileId }) null else AuctionError.NOT_SOLD
            is AuctionEvent.Finish -> if (lot != null) AuctionError.LOT_OPEN else null
        }
    }

    /** Retained players must leave each team within its purse and squad limit. */
    fun retainedError(setup: AuctionSetup): AuctionError? {
        val settings = setup.settings
        val byTeam = setup.players.filter { !it.requested && it.retainedBy in setup.teamIds }.groupBy { it.retainedBy }
        return if (byTeam.values.any { kept -> kept.size > settings.maxSquad || kept.sumOf { it.retainedPrice ?: 0 }.toLong() > settings.purse }) {
            AuctionError.RETAINED_INVALID
        } else {
            null
        }
    }

    /**
     * The order players come up in: by category (in the settings' order, players without one last) and shuffled
     * within each, or all shuffled. Requested and retained players don't come up.
     */
    fun lotOrder(setup: AuctionSetup, random: Random): List<String> {
        val settings = setup.settings
        val eligible = setup.players.filter { !it.requested && it.retainedBy !in setup.teamIds }.sortedBy { it.addedAt }
        return when (settings.order) {
            AuctionOrder.RANDOM -> eligible.shuffled(random)
            AuctionOrder.CATEGORY -> {
                val rank = { player: AuctionPlayer ->
                    settings.categories.indexOfFirst { it.equals(player.category?.trim(), ignoreCase = true) }.let { if (it < 0) settings.categories.size else it }
                }
                eligible.groupBy(rank).entries.sortedBy { it.key }.flatMap { it.value.shuffled(random) }
            }
        }.map { it.profileId }
    }

    /** The events still in force once each Undo has taken back the latest undoable event before it. */
    private fun effective(events: List<AuctionEvent>): List<AuctionEvent> {
        val kept = ArrayList<AuctionEvent>(events.size)
        for (event in events) {
            if (event is AuctionEvent.Undo) {
                val last = kept.indexOfLast { it.undoable }
                if (last >= 0) kept.removeAt(last)
            } else {
                kept += event
            }
        }
        return kept
    }

    private val AuctionEvent.undoable: Boolean
        get() = this is AuctionEvent.NextPlayer || this is AuctionEvent.Bid || this is AuctionEvent.Sold ||
            this is AuctionEvent.Unsold || this is AuctionEvent.ReverseSale

    private class Fold(private val setup: AuctionSetup) {
        private val settings = setup.settings
        private val pool = setup.players.filter { !it.requested }.associateBy { it.profileId }
        var status = AuctionStatus.SETUP
        private var paused = false
        private var teamIds = setup.teamIds
        private var startOrder = emptyList<String>()
        private val statuses = LinkedHashMap<String, AuctionPlayerStatus>()
        /** Times each player has come up, so the unsold round brings them back only once. */
        private val attempts = HashMap<String, Int>()
        private var lot: AuctionLot? = null
        private val sales = ArrayList<AuctionSale>()

        init {
            resetStatuses()
        }

        private fun resetStatuses() {
            statuses.clear()
            for (player in pool.values) {
                statuses[player.profileId] = if (player.retainedBy in teamIds) AuctionPlayerStatus.RETAINED else AuctionPlayerStatus.WAITING
            }
        }

        fun apply(event: AuctionEvent) {
            when (event) {
                is AuctionEvent.Start -> {
                    status = AuctionStatus.LIVE
                    teamIds = event.teamIds
                    startOrder = event.order
                    resetStatuses()
                }
                is AuctionEvent.NextPlayer -> {
                    val back = statuses[event.profileId] == AuctionPlayerStatus.UNSOLD
                    val base = pool[event.profileId]?.basePrice ?: settings.basePrice
                    statuses[event.profileId] = AuctionPlayerStatus.UP
                    attempts[event.profileId] = (attempts[event.profileId] ?: 0) + 1
                    lot = AuctionLot(event.profileId, base, round = if (back) 2 else 1, nextBid = base)
                }
                is AuctionEvent.Bid -> lot = lot?.let {
                    it.copy(bids = it.bids + AuctionBid(event.teamId, event.amount), nextBid = event.amount + settings.stepAt(event.amount))
                }
                is AuctionEvent.Sold -> {
                    statuses[event.profileId] = AuctionPlayerStatus.SOLD
                    sales += AuctionSale(event.profileId, event.teamId, event.price)
                    lot = null
                }
                is AuctionEvent.Unsold -> {
                    statuses[event.profileId] = AuctionPlayerStatus.UNSOLD
                    lot = null
                }
                is AuctionEvent.Pause -> paused = true
                is AuctionEvent.Resume -> paused = false
                is AuctionEvent.ReverseSale -> {
                    sales.removeAll { it.profileId == event.profileId }
                    statuses[event.profileId] = AuctionPlayerStatus.UNSOLD
                }
                is AuctionEvent.Finish -> {
                    status = AuctionStatus.FINISHED
                    lot = null
                }
                // Already applied by effective().
                is AuctionEvent.Undo -> Unit
            }
        }

        fun state(lastSeq: Int, canUndo: Boolean): AuctionState {
            val started = status != AuctionStatus.SETUP
            val order = if (!started) emptyList() else {
                val known = startOrder.filter { it in pool && statuses[it] != AuctionPlayerStatus.RETAINED }
                // Players added after the start come up after everyone else.
                known + pool.values.filter { it.profileId !in startOrder && statuses[it.profileId] != AuctionPlayerStatus.RETAINED }
                    .sortedBy { it.addedAt }.map { it.profileId }
            }
            val waiting = order.firstOrNull { statuses[it] == AuctionPlayerStatus.WAITING }
            val upNext = when {
                !started || status == AuctionStatus.FINISHED -> null
                waiting != null -> waiting
                settings.unsoldRound -> order.firstOrNull { statuses[it] == AuctionPlayerStatus.UNSOLD && (attempts[it] ?: 0) < 2 }
                else -> null
            }
            return AuctionState(
                status = if (status == AuctionStatus.LIVE && paused) AuctionStatus.PAUSED else status,
                round = lot?.round ?: if (started && waiting == null && upNext != null) 2 else 1,
                lot = lot,
                teams = teamIds.map(::purse),
                sales = sales.toList(),
                players = statuses.toMap(),
                upNext = upNext,
                order = order,
                canUndo = canUndo,
                lastSeq = lastSeq,
            )
        }

        private fun purse(teamId: String): TeamPurse {
            val retained = pool.values.filter { it.retainedBy == teamId }.sortedBy { it.addedAt }
            val bought = sales.filter { it.teamId == teamId }
            val spent = retained.sumOf { it.retainedPrice ?: 0 } + bought.sumOf { it.price }
            val left = settings.purse - spent
            val squad = retained.size + bought.size
            val full = squad >= settings.maxSquad
            // Buying this player leaves (minimum − squad − 1) still to fill, each at least the base price.
            val reserve = maxOf(0, settings.minSquad - squad - 1).toLong() * settings.basePrice
            return TeamPurse(
                teamId = teamId,
                purseLeft = left,
                spent = spent,
                retained = retained.map { it.profileId },
                bought = bought.map { it.profileId },
                maxBid = if (full) 0 else maxOf(0L, left - reserve).toInt(),
                full = full,
                needed = maxOf(0, settings.minSquad - squad),
            )
        }
    }
}
