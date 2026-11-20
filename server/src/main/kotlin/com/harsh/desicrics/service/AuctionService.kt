package com.harsh.desicrics.service

import com.harsh.desicrics.api.AddAuctionPlayerRequest
import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.api.BidRequest
import com.harsh.desicrics.api.CreateAuctionRequest
import com.harsh.desicrics.auction.AuctionEngine
import com.harsh.desicrics.auction.AuctionSetup
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.live.LiveAuctions
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Auction
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionState
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.store.AuctionStore
import com.harsh.desicrics.store.AuctionStore.PlayerRow
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.TournamentStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Player auctions: setting one up for a tournament, the player pool, and running the room. Every action is
 * checked by [AuctionEngine] against the state folded from the event log, then appended to it and sent to
 * everyone watching. In online mode the server runs the clock and sells (or passes) the player when it runs out.
 *
 * Organisers are the tournament's. Owners, who bid online, are the admins of its confirmed teams.
 */
class AuctionService(
    private val auctions: AuctionStore,
    private val tournaments: TournamentStore,
    private val teams: TeamStore,
    private val players: PlayerStore,
    private val users: UserStore,
    private val live: LiveAuctions,
    /** Where the online clocks run: the application, so they stop with it. */
    private val scope: CoroutineScope,
    /** How long one second of the online clock lasts; tests shorten it. */
    private val secondMillis: Long = 1000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val log = LoggerFactory.getLogger(AuctionService::class.java)

    /** One change at a time per auction, so two bids can't both be checked against the same state. */
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Online clocks for the player up, by auction. Only read and replaced while holding that auction's lock. */
    private val clocks = ConcurrentHashMap<String, Countdown>()

    private class Countdown(val endsAt: Long) {
        lateinit var job: Job
    }

    /** Everything an action needs: the stored auction, its tournament and pool, and the state from its events. */
    private data class Loaded(
        val row: AuctionStore.AuctionRow,
        val tournament: Tournament,
        val pool: List<AuctionPlayer>,
        val events: List<AuctionEvent>,
        val setup: AuctionSetup,
        val state: AuctionState,
    ) {
        val settings: AuctionSettings get() = row.settings
    }

    suspend fun create(user: FirebasePrincipal, tournamentId: String, request: CreateAuctionRequest): AuctionDetails {
        val tournament = tournaments.get(tournamentId) ?: throw ApiException(HttpStatusCode.NotFound, "Tournament not found")
        if (!tournament.isOrganiser(user.uid)) throw ApiException(HttpStatusCode.Forbidden, "Only the tournament's organisers can run an auction")
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; auctions come before it")
        val settings = request.settings.cleaned()
        settings.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        val row = AuctionStore.AuctionRow(UUID.randomUUID().toString(), tournamentId, tournament.name, settings, AuctionStatus.SETUP, user.uid, clock())
        if (!auctions.create(row)) throw ApiException(HttpStatusCode.Conflict, "This tournament already has an auction")
        // Players already in the tournament's pool, waiting for a team, are who the auction is for.
        for (entry in tournament.pool.filter { it.status == EntryStatus.CONFIRMED }) {
            auctions.addPlayer(row.id, PlayerRow(entry.player.profileId, settings.basePrice, null, false, null, null, clock()))
        }
        return details(row.id)
    }

    /**
     * A player joined the tournament's pool: they go up in its auction too, while it's being set up or run.
     * Nothing happens without an auction, or if they're in its pool already.
     */
    suspend fun addFromTournament(tournamentId: String, profileId: String) {
        val auctionId = auctions.idForTournament(tournamentId) ?: return
        locked(auctionId) {
            val loaded = load(auctionId)
            if (loaded.state.status == AuctionStatus.FINISHED) return@locked
            if (auctions.addPlayer(auctionId, PlayerRow(profileId, loaded.settings.basePrice, null, false, null, null, clock()))) changed(auctionId)
        }
    }

    suspend fun forTournament(tournamentId: String): AuctionDetails =
        details(auctions.idForTournament(tournamentId) ?: throw ApiException(HttpStatusCode.NotFound, "This tournament has no auction"))

    suspend fun details(auctionId: String): AuctionDetails = load(auctionId).let { details(it, it.events.lastOrNull()) }

    suspend fun updateSettings(user: FirebasePrincipal, auctionId: String, settings: AuctionSettings): AuctionDetails = locked(auctionId) {
        val loaded = organiserLoad(user, auctionId)
        if (loaded.state.status != AuctionStatus.SETUP) throw conflict(AuctionError.ALREADY_STARTED)
        val cleaned = settings.cleaned()
        cleaned.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        AuctionEngine.retainedError(loaded.setup.copy(settings = cleaned))?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        auctions.updateSettings(auctionId, cleaned)
        changed(auctionId)
    }

    /** Organisers add a signed-up player, or a placeholder for someone without the app. */
    suspend fun addPlayer(user: FirebasePrincipal, auctionId: String, request: AddAuctionPlayerRequest): AuctionDetails = locked(auctionId) {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        val loaded = organiserLoad(user, auctionId)
        if (loaded.state.status == AuctionStatus.FINISHED) throw conflict(AuctionError.FINISHED)
        val profileId = request.profileId ?: withFreshCode { code ->
            val id = "p-" + UUID.randomUUID()
            id.takeIf { players.createPlaceholder(id, request.placeholderName!!.trim(), request.placeholderPhone.orEmpty(), code, user.uid, clock()) }
        }
        if (players.summaries(listOf(profileId)).isEmpty()) throw ApiException(HttpStatusCode.NotFound, "Player not found")
        val row = PlayerRow(profileId, request.basePrice ?: loaded.settings.basePrice, request.category.cleanCategory(), false, null, null, clock())
        if (!auctions.addPlayer(auctionId, row)) throw ApiException(HttpStatusCode.Conflict, "They're already in the pool")
        changed(auctionId)
    }

    /** Sets a pool player's base price, category and retaining team. Also accepts a player's request to join. */
    suspend fun updatePlayer(user: FirebasePrincipal, auctionId: String, profileId: String, request: AuctionPlayerRequest): AuctionDetails = locked(auctionId) {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        val loaded = organiserLoad(user, auctionId)
        val existing = loaded.pool.firstOrNull { it.profileId == profileId } ?: throw ApiException(HttpStatusCode.NotFound, "They're not in the pool")
        val state = loaded.state
        if (state.status == AuctionStatus.FINISHED) throw conflict(AuctionError.FINISHED)
        if (state.status != AuctionStatus.SETUP) {
            if (request.retainedBy != existing.retainedBy || request.retainedPrice != existing.retainedPrice) throw conflict(AuctionError.ALREADY_STARTED)
            if (!existing.requested && state.players[profileId] != AuctionPlayerStatus.WAITING) {
                throw ApiException(HttpStatusCode.Conflict, "That player has already been up")
            }
        }
        if (request.retainedBy != null && request.retainedBy !in loaded.setup.teamIds) {
            throw ApiException(HttpStatusCode.BadRequest, "Retained players stay with one of the auction's teams")
        }
        val row = PlayerRow(profileId, request.basePrice, request.category.cleanCategory(), false, request.retainedBy, request.retainedPrice, existing.addedAt)
        val updated = loaded.pool.map { if (it.profileId == profileId) it.copy(requested = false, retainedBy = row.retainedBy, retainedPrice = row.retainedPrice) else it }
        AuctionEngine.retainedError(loaded.setup.copy(players = updated))?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        auctions.updatePlayer(auctionId, row)
        changed(auctionId)
    }

    /** Declines a request, or takes out a player who hasn't come up yet. */
    suspend fun removePlayer(user: FirebasePrincipal, auctionId: String, profileId: String): AuctionDetails = locked(auctionId) {
        val loaded = organiserLoad(user, auctionId)
        val existing = loaded.pool.firstOrNull { it.profileId == profileId } ?: throw ApiException(HttpStatusCode.NotFound, "They're not in the pool")
        val started = loaded.state.status != AuctionStatus.SETUP
        if (started && !existing.requested && loaded.state.players[profileId] != AuctionPlayerStatus.WAITING) {
            throw ApiException(HttpStatusCode.Conflict, "That player has already been up")
        }
        auctions.removePlayer(auctionId, profileId)
        changed(auctionId)
    }

    /** A player asks to be in the pool, from the tournament's page. An organiser accepts or declines. */
    suspend fun request(user: FirebasePrincipal, auctionId: String): AuctionDetails = locked(auctionId) {
        val loaded = load(auctionId)
        if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
        if (loaded.state.status == AuctionStatus.FINISHED) throw conflict(AuctionError.FINISHED)
        val row = PlayerRow(user.uid, loaded.settings.basePrice, null, true, null, null, clock())
        if (!auctions.addPlayer(auctionId, row)) throw ApiException(HttpStatusCode.Conflict, "You're already in the pool")
        changed(auctionId)
    }

    suspend fun withdrawRequest(user: FirebasePrincipal, auctionId: String): AuctionDetails = locked(auctionId) {
        val loaded = load(auctionId)
        if (loaded.pool.none { it.profileId == user.uid && it.requested }) throw ApiException(HttpStatusCode.NotFound, "You haven't asked to join")
        auctions.removePlayer(auctionId, user.uid)
        changed(auctionId)
    }

    /** Fixes the teams (the tournament's confirmed ones) and the order players come up in. */
    suspend fun start(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { loaded, seq, now ->
        val teamIds = loaded.setup.teamIds
        AuctionEvent.Start(seq, teamIds, AuctionEngine.lotOrder(loaded.setup.copy(teamIds = teamIds), random), now)
    }

    /** Brings [profileId] up, or whoever is next. */
    suspend fun next(user: FirebasePrincipal, auctionId: String, profileId: String?): AuctionDetails = record(user, auctionId) { loaded, seq, now ->
        val next = profileId ?: loaded.state.upNext
            ?: throw ApiException(HttpStatusCode.Conflict, if (loaded.state.status == AuctionStatus.SETUP) AuctionError.NOT_STARTED.message else "Everyone has been up")
        AuctionEvent.NextPlayer(seq, next, now)
    }

    /** Hall: the auctioneer bids for the team that raised its paddle. Online: a team's owner bids for it. */
    suspend fun bid(user: FirebasePrincipal, auctionId: String, request: BidRequest): AuctionDetails = record(user, auctionId, organiserOnly = false) { loaded, seq, now ->
        val allowed = when (loaded.settings.mode) {
            AuctionMode.HALL -> loaded.tournament.isOrganiser(user.uid)
            AuctionMode.ONLINE -> teams.get(request.teamId)?.roleOf(user.uid) == TeamRole.ADMIN
        }
        if (!allowed) {
            throw ApiException(
                HttpStatusCode.Forbidden,
                if (loaded.settings.mode == AuctionMode.HALL) "Only the auctioneer bids in the hall" else "Only the team's owners can bid for it",
            )
        }
        val lot = loaded.state.lot ?: throw conflict(AuctionError.NO_LOT)
        AuctionEvent.Bid(seq, request.teamId, request.amount ?: lot.nextBid, now)
    }

    /** Sells the player up to the highest bid. */
    suspend fun sold(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { loaded, seq, now ->
        val lot = loaded.state.lot ?: throw conflict(AuctionError.NO_LOT)
        val leading = lot.leading ?: throw conflict(AuctionError.NO_BIDS)
        AuctionEvent.Sold(seq, lot.profileId, leading.teamId, leading.amount, now)
    }

    suspend fun unsold(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { loaded, seq, now ->
        AuctionEvent.Unsold(seq, loaded.state.lot?.profileId ?: throw conflict(AuctionError.NO_LOT), now)
    }

    suspend fun undo(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { _, seq, now -> AuctionEvent.Undo(seq, now) }

    suspend fun pause(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { _, seq, now -> AuctionEvent.Pause(seq, now) }

    suspend fun resume(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { _, seq, now -> AuctionEvent.Resume(seq, now) }

    suspend fun reverseSale(user: FirebasePrincipal, auctionId: String, profileId: String): AuctionDetails =
        record(user, auctionId) { _, seq, now -> AuctionEvent.ReverseSale(seq, profileId, now) }

    /** Ends the auction. Every player bought or retained becomes a member of their team. */
    suspend fun finish(user: FirebasePrincipal, auctionId: String): AuctionDetails = record(user, auctionId) { _, seq, now -> AuctionEvent.Finish(seq, now) }

    /** Restarts the clocks of online auctions with a player up, e.g. after the server restarts. Each gets a fresh bid clock. */
    suspend fun resumeClocks() {
        for (auctionId in auctions.idsWithStatus(listOf(AuctionStatus.LIVE))) {
            locked(auctionId) {
                val loaded = load(auctionId)
                if (loaded.settings.mode == AuctionMode.ONLINE && loaded.state.lot != null && loaded.state.status == AuctionStatus.LIVE) {
                    startClock(auctionId, loaded.settings.bidSeconds)
                }
            }
        }
    }

    private suspend fun <T> locked(auctionId: String, block: suspend () -> T): T = locks.getOrPut(auctionId) { Mutex() }.withLock { block() }

    /** Checks the event [build] makes, appends it, and tells everyone. Organisers only unless [organiserOnly] is false. */
    private suspend fun record(
        user: FirebasePrincipal,
        auctionId: String,
        organiserOnly: Boolean = true,
        build: suspend (Loaded, seq: Int, now: Long) -> AuctionEvent,
    ): AuctionDetails = locked(auctionId) {
        val loaded = if (organiserOnly) organiserLoad(user, auctionId) else load(auctionId)
        commit(loaded, build(loaded, loaded.state.lastSeq + 1, clock()), user.uid)
    }

    /** Must hold the auction's lock. */
    private suspend fun commit(loaded: Loaded, event: AuctionEvent, by: String?): AuctionDetails {
        AuctionEngine.check(loaded.setup, loaded.state, event)?.let { throw rejection(it, loaded, event) }
        val events = loaded.events + event
        val state = AuctionEngine.compute(loaded.setup, events)
        if (!auctions.addEvent(loaded.row.id, event, state.status, by, clock())) {
            throw ApiException(HttpStatusCode.Conflict, "The auction changed meanwhile; try again")
        }
        if (event is AuctionEvent.Finish) joinTeams(loaded.tournament.id, state)
        rearm(loaded.row.id, loaded.settings, event, state)
        val details = details(loaded.copy(events = events, state = state), event)
        live.publish(details)
        return details
    }

    /** Must hold the auction's lock: sends the new state after a change to the setup. */
    private suspend fun changed(auctionId: String): AuctionDetails = details(auctionId).also(live::publish)

    /** Bought and retained players join their team, and its squad for the tournament. */
    private suspend fun joinTeams(tournamentId: String, state: AuctionState) {
        val now = clock()
        for (team in state.teams) {
            for (profileId in team.retained + team.bought) {
                teams.addMember(team.teamId, profileId, TeamRole.MEMBER, now)
                if (!tournaments.addPlayer(tournamentId, profileId, team.teamId, EntryStatus.CONFIRMED, fromPool = true, addedBy = "auction", at = now)) {
                    tournaments.setPlayer(tournamentId, profileId, team.teamId, EntryStatus.CONFIRMED)
                }
            }
        }
    }

    /**
     * Online, the clock starts when a player comes up and goes back to [AuctionSettings.bidSeconds] after each
     * bid, a resume or an undo that leaves a player up. It stops when nobody's up or the room is paused.
     */
    private fun rearm(auctionId: String, settings: AuctionSettings, event: AuctionEvent, state: AuctionState) {
        if (settings.mode != AuctionMode.ONLINE) return
        if (state.status != AuctionStatus.LIVE || state.lot == null) {
            clocks.remove(auctionId)?.job?.cancel()
            return
        }
        when (event) {
            is AuctionEvent.NextPlayer -> startClock(auctionId, settings.lotSeconds)
            is AuctionEvent.Bid, is AuctionEvent.Resume, is AuctionEvent.Undo -> startClock(auctionId, settings.bidSeconds)
            // E.g. an earlier sale reversed while a player is up: their clock keeps going.
            else -> if (!clocks.containsKey(auctionId)) startClock(auctionId, settings.bidSeconds)
        }
    }

    private fun startClock(auctionId: String, seconds: Int) {
        val countdown = Countdown(clock() + seconds * secondMillis)
        clocks.put(auctionId, countdown)?.job?.cancel()
        countdown.job = scope.launch {
            delay(countdown.endsAt - clock())
            try {
                expire(auctionId, countdown)
            } catch (e: Exception) {
                log.error("Auction $auctionId: the clock ran out but the lot couldn't be closed", e)
            }
        }
    }

    /** The clock ran out: the player goes to the highest bid, or unsold without one. */
    private suspend fun expire(auctionId: String, countdown: Countdown) = locked(auctionId) {
        // A newer clock replaced this one, or it was stopped, while this waited for the lock.
        if (!clocks.remove(auctionId, countdown)) return@locked
        val loaded = load(auctionId)
        val lot = loaded.state.lot ?: return@locked
        if (loaded.state.status != AuctionStatus.LIVE) return@locked
        val seq = loaded.state.lastSeq + 1
        val leading = lot.leading
        val event = if (leading != null) AuctionEvent.Sold(seq, lot.profileId, leading.teamId, leading.amount, clock())
        else AuctionEvent.Unsold(seq, lot.profileId, clock())
        commit(loaded, event, by = null)
    }

    private suspend fun load(auctionId: String): Loaded {
        val row = auctions.get(auctionId) ?: throw ApiException(HttpStatusCode.NotFound, "Auction not found")
        val tournament = tournaments.get(row.tournamentId) ?: throw ApiException(HttpStatusCode.NotFound, "Tournament not found")
        val rows = auctions.players(auctionId)
        val summaries = players.summaries(rows.map { it.profileId })
        val pool = rows.map {
            AuctionPlayer(
                player = summaries[it.profileId] ?: PlayerSummary(it.profileId, ""),
                basePrice = it.basePrice,
                category = it.category,
                requested = it.requested,
                retainedBy = it.retainedBy,
                retainedPrice = it.retainedPrice,
                addedAt = it.addedAt,
            )
        }
        val events = auctions.events(auctionId)
        val setup = AuctionSetup(row.settings, tournament.confirmedTeams.map { it.teamId }, pool)
        return Loaded(row, tournament, pool, events, setup, AuctionEngine.compute(setup, events))
    }

    private suspend fun organiserLoad(user: FirebasePrincipal, auctionId: String): Loaded {
        val loaded = load(auctionId)
        if (!loaded.tournament.isOrganiser(user.uid)) throw ApiException(HttpStatusCode.Forbidden, "Only the tournament's organisers can do that")
        return loaded
    }

    private suspend fun details(loaded: Loaded, latest: AuctionEvent?): AuctionDetails {
        val teamIds = if (loaded.state.status == AuctionStatus.SETUP) loaded.setup.teamIds else loaded.state.teams.map { it.teamId }
        val auctionTeams = teamIds.mapNotNull { teamId ->
            val team = teams.get(teamId) ?: return@mapNotNull null
            AuctionTeam(team.id, team.name, team.logoUrl, team.members.filter { it.role == TeamRole.ADMIN }.map { it.player.profileId })
        }
        val row = loaded.row
        return AuctionDetails(
            auction = Auction(row.id, row.tournamentId, loaded.tournament.name, row.settings, row.createdBy, row.createdAt),
            teams = auctionTeams,
            organiserIds = loaded.tournament.organisers.map { it.uid },
            players = loaded.pool,
            state = loaded.state,
            latest = latest,
            lotEndsAt = clocks[row.id]?.endsAt,
            serverTime = clock(),
        )
    }

    private fun conflict(error: AuctionError) = ApiException(HttpStatusCode.Conflict, error.message)

    /** The engine's reason, with the numbers that help when it's about money. */
    private fun rejection(error: AuctionError, loaded: Loaded, event: AuctionEvent): ApiException {
        val label = loaded.settings.purseLabel
        val lot = loaded.state.lot
        val message = when {
            error == AuctionError.BID_TOO_LOW && lot != null -> "Bid at least ${lot.nextBid} $label"
            error == AuctionError.BID_TOO_HIGH && event is AuctionEvent.Bid ->
                "That team can bid at most ${loaded.state.team(event.teamId)?.maxBid ?: 0} $label and still fill its squad"
            else -> error.message
        }
        val status = if (error == AuctionError.TEAM_UNKNOWN) HttpStatusCode.BadRequest else HttpStatusCode.Conflict
        return ApiException(status, message)
    }

    private fun AuctionSettings.cleaned() = copy(purseLabel = purseLabel.trim(), categories = categories.map { it.trim() })

    private fun String?.cleanCategory(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
