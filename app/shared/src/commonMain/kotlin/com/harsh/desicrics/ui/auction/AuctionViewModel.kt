package com.harsh.desicrics.ui.auction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.auction.AuctionEngine
import com.harsh.desicrics.auction.AuctionSetup
import com.harsh.desicrics.data.AuctionRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.ui.teams.attempt
import com.harsh.desicrics.ui.teams.refusal
import com.harsh.desicrics.util.nowMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuctionUiState(
    val myId: String? = null,
    val loading: Boolean = true,
    val details: AuctionDetails? = null,
    val loadFailed: Boolean = false,
    /** The latest update failed; the last good room stays on screen. */
    val offline: Boolean = false,
    /** An action is on its way to the server. */
    val saving: Boolean = false,
    val actionFailed: Boolean = false,
    /** Why the server turned the action down, when it said; otherwise the screen shows a connection message. */
    val actionError: String? = null,
    /** A sale that just happened, for the full-screen sold moment; cleared once it has played. */
    val soldMoment: AuctionEvent.Sold? = null,
    /** The server's clock minus the phone's at the latest update, so the countdown follows the server. */
    val clockSkew: Long = 0,
    val searchQuery: String = "",
    val searchResults: List<PlayerSummary> = emptyList(),
    val searching: Boolean = false,
) {
    val isOrganiser: Boolean get() = details?.isOrganiser(myId) == true

    /** Teams the caller bids for in an online auction. */
    val myTeams: List<AuctionTeam> get() = details?.teamsOwnedBy(myId).orEmpty()

    /** The caller's own place in the pool (or their request to join it), if any. */
    val me: AuctionPlayer? get() = myId?.let { details?.player(it) }

    /** Why [teamId] can't bid [amount] now, by the same rules the server checks; null if it can. */
    fun bidError(teamId: String, amount: Int): AuctionError? {
        val details = details ?: return AuctionError.NOT_STARTED
        return AuctionEngine.check(details.setup, details.state, AuctionEvent.Bid(0, teamId, amount))
    }

    /** Why the organiser can't start yet; null when they can. */
    val startError: AuctionError?
        get() {
            val details = details ?: return AuctionError.NOT_STARTED
            val setup = details.setup
            val order = AuctionEngine.lotOrder(setup, kotlin.random.Random(0))
            return AuctionEngine.check(setup, details.state, AuctionEvent.Start(0, setup.teamIds, order))
        }
}

/** What the engine needs to check an action, from what the server sent. */
internal val AuctionDetails.setup: AuctionSetup
    get() = AuctionSetup(auction.settings, teams.map { it.teamId }, players)

class AuctionViewModel(
    private val auctionId: String,
    private val repository: AuctionRepository,
    private val teams: TeamRepository,
    /** The signed-in user, to tell organisers and owners from watchers. */
    myId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(AuctionUiState(myId = myId))
    val state: StateFlow<AuctionUiState> = _state
    private val reloads = MutableStateFlow(0)
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            reloads.flatMapLatest { repository.observe(auctionId) }.collect { result ->
                result.fold(
                    onSuccess = ::receive,
                    onFailure = { _state.update { if (it.details == null) it.copy(loading = false, loadFailed = true) else it.copy(offline = true) } },
                )
            }
        }
    }

    /** Starts watching again after the first load failed. */
    fun retry() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        reloads.update { it + 1 }
    }

    /**
     * Takes in a new copy of the room, from the live feed or an action's answer. An older copy (a feed message
     * overtaken by an action's answer) is dropped, and a sale newer than what was on screen plays the sold moment.
     */
    internal fun receive(details: AuctionDetails) = _state.update { current ->
        val previous = current.details
        if (previous != null && details.state.lastSeq < previous.state.lastSeq) return@update current.copy(offline = false)
        val latest = details.latest
        val newSale = previous != null && latest is AuctionEvent.Sold && latest.seq > previous.state.lastSeq
        current.copy(
            loading = false,
            loadFailed = false,
            offline = false,
            details = details,
            clockSkew = if (details.serverTime > 0) details.serverTime - nowMillis() else 0,
            soldMoment = if (newSale) latest else current.soldMoment,
        )
    }

    fun dismissSoldMoment() = _state.update { it.copy(soldMoment = null) }

    fun clearError() = _state.update { it.copy(actionFailed = false, actionError = null) }

    // Setup (organisers).

    fun updateSettings(settings: AuctionSettings) {
        if (settings.validate() != null) return
        act { repository.updateSettings(auctionId, settings) }
    }

    fun addPlayer(profileId: String) = act { repository.addPlayer(auctionId, profileId) }

    fun addPlaceholder(name: String, phone: String) {
        if (name.isBlank()) return
        act { repository.addPlaceholder(auctionId, name, phone) }
    }

    fun updatePlayer(profileId: String, basePrice: Int, category: String?, retainedBy: String?, retainedPrice: Int?) {
        val request = AuctionPlayerRequest(basePrice, category?.trim()?.ifEmpty { null }, retainedBy, retainedPrice.takeIf { retainedBy != null })
        if (request.validate() != null) return
        act { repository.updatePlayer(auctionId, profileId, request) }
    }

    /** Lets a player who asked into the pool, at the auction's base price. */
    fun accept(profileId: String) {
        val player = _state.value.details?.player(profileId) ?: return
        act { repository.updatePlayer(auctionId, profileId, AuctionPlayerRequest(player.basePrice, player.category)) }
    }

    /** Takes a player out of the pool, or turns down their request. */
    fun remove(profileId: String) = act { repository.removePlayer(auctionId, profileId) }

    /** The caller asks to be put up for auction, or takes the request back. */
    fun setRequest(requested: Boolean) = act { repository.setRequest(auctionId, requested) }

    /** Searches players by name or code as the organiser types, waiting briefly between keystrokes. */
    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        _state.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = attempt { teams.search(query.trim()) }.getOrDefault(emptyList())
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    // The room.

    fun start() = act { repository.start(auctionId) }

    /** Brings [profileId] up, or whoever is next. */
    fun next(profileId: String? = null) = act { repository.next(auctionId, profileId) }

    /**
     * A bid for [teamId]. The hall taps a team and the bid goes up one step ([amount] null); online, owners
     * send the amount they saw, so two bids at once can't take it higher than they meant.
     */
    fun bid(teamId: String, amount: Int? = null) {
        val ui = _state.value
        val lot = ui.details?.state?.lot ?: return
        if (ui.bidError(teamId, amount ?: lot.nextBid) != null) return
        act { repository.bid(auctionId, teamId, amount) }
    }

    fun sold() = act { repository.sold(auctionId) }

    fun unsold() = act { repository.unsold(auctionId) }

    /** Ends the lot now: to the highest bid if there is one, otherwise unsold. */
    fun closeLot() {
        val lot = _state.value.details?.state?.lot ?: return
        if (lot.leading != null) sold() else unsold()
    }

    fun undo() = act { repository.undo(auctionId) }

    fun pause() = act { repository.pause(auctionId) }

    fun resume() = act { repository.resume(auctionId) }

    fun reverseSale(profileId: String) = act { repository.reverseSale(auctionId, profileId) }

    fun finish() = act { repository.finish(auctionId) }

    private fun act(call: suspend () -> AuctionDetails) {
        if (_state.value.saving) return
        if (_state.value.details?.state?.status == AuctionStatus.FINISHED) return
        _state.update { it.copy(saving = true, actionFailed = false, actionError = null) }
        viewModelScope.launch {
            attempt { call() }.fold(
                onSuccess = { details ->
                    receive(details)
                    _state.update { it.copy(saving = false) }
                },
                onFailure = { e -> _state.update { it.copy(saving = false, actionFailed = true, actionError = e.refusal()) } },
            )
        }
    }
}
