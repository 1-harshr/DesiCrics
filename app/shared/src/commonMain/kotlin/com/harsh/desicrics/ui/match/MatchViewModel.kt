package com.harsh.desicrics.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.TournamentRepository
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.DeliveryError
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.validate
import com.harsh.desicrics.scoring.warnings
import com.harsh.desicrics.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Players the scorer picked for slots the engine left empty (innings start, after a wicket, new over). */
data class Selection(
    val strikerId: String? = null,
    val nonStrikerId: String? = null,
    val bowlerId: String? = null,
)

/** Why the last action didn't go through. The screen picks the text. */
sealed interface ScoringError {
    /** The engine rejected the ball before it was sent. */
    data class Rejected(val error: DeliveryError) : ScoringError
    data object SaveFailed : ScoringError
    data object LoadFailed : ScoringError
}

/** What starting a scheduled fixture needs: the teams to pick squads from, and whether the caller organises it. */
data class FixtureSetup(
    val canStart: Boolean,
    val teamA: SavedTeam?,
    val teamB: SavedTeam?,
    val starting: Boolean = false,
    val failed: Boolean = false,
)

/** A ball that breaks match rules set to warn, waiting for the scorer to record it anyway or drop it. */
data class PendingBall(val delivery: Delivery, val warnings: List<DeliveryError>)

data class MatchUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val match: Match? = null,
    val deliveries: List<Delivery> = emptyList(),
    val events: List<MatchEvent> = emptyList(),
    val state: MatchState? = null,
    val isScorer: Boolean = false,
    val selection: Selection = Selection(),
    /** A ball or undo is being saved; scoring is paused until the server confirms. */
    val saving: Boolean = false,
    /** The latest refresh failed; the last good scorecard stays on screen. */
    val offline: Boolean = false,
    val reconciling: Boolean = false,
    val confirmedAt: Long? = null,
    val error: ScoringError? = null,
    val pendingBall: PendingBall? = null,
    /** Shown to the scorer until dismissed, so someone else can take over. */
    val handoverCode: HandoverCode? = null,
    val handoverFailed: Boolean = false,
    /** For a tournament fixture that hasn't started: whether the caller can start it, and its two saved teams. */
    val fixture: FixtureSetup? = null,
) {
    val strikerId: String? get() = state?.current?.strikerId ?: selection.strikerId
    val nonStrikerId: String? get() = state?.current?.nonStrikerId ?: selection.nonStrikerId
    val bowlerId: String? get() = state?.current?.bowlerId ?: selection.bowlerId

    /** The last batter is on their own, which the match's rules allow. */
    val battingAlone: Boolean
        get() {
            val innings = state?.current ?: return false
            return state.match.rules.lastManStands && strikerId != null && nonStrikerId == null &&
                innings.canComeIn.none { it.id != strikerId }
        }

    val canScore: Boolean
        get() = isScorer && state?.isComplete == false && !saving && !reconciling &&
            strikerId != null && (nonStrikerId != null || battingAlone) && bowlerId != null

    val canUndo: Boolean get() = isScorer && (deliveries.isNotEmpty() || events.isNotEmpty()) && !saving && !reconciling

    /** The scorer can retire batters, swap ends and so on between balls. */
    val canEdit: Boolean get() = isScorer && state?.isComplete == false && !saving && !reconciling

    val nextSeq: Int get() = (deliveries.map { it.seq } + events.map { it.seq }).maxOrNull()?.plus(1) ?: 0
}

class MatchViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository,
    authRepository: AuthRepository,
    private val social: SocialRepository,
    private val tournaments: TournamentRepository,
    private val teams: TeamRepository,
) : ViewModel() {

    /** The public scorecard anyone can open. */
    val shareLink: String = social.matchLink(matchId)
    val overlayLink: String = social.overlayLink(matchId)


    private val uid = authRepository.currentUser?.uid

    private val _state = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = _state

    /** Bumped after each write to restart polling, so an in-flight poll can't roll back what we just saved. */
    private val refreshes = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            refreshes.flatMapLatest { matchRepository.observeMatch(matchId) }.collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        _state.update { current ->
                            when {
                                snapshot == null -> current.copy(loading = false, notFound = true)
                                // Don't let a poll overwrite the local state mid-save.
                                current.saving -> current
                                else -> current.withSnapshot(snapshot).copy(offline = false).also {
                                    if (snapshot.match.status == MatchStatus.SCHEDULED) loadFixtureSetup(snapshot.match)
                                }
                            }
                        }
                    },
                    onFailure = {
                        _state.update {
                            if (it.match == null) it.copy(loading = false, error = ScoringError.LoadFailed) else it.copy(offline = true)
                        }
                    },
                )
            }
        }
    }

    fun selectStriker(playerId: String) = _state.update { it.copy(selection = it.selection.copy(strikerId = playerId)) }

    fun selectNonStriker(playerId: String) = _state.update { it.copy(selection = it.selection.copy(nonStrikerId = playerId)) }

    fun selectBowler(playerId: String) = _state.update { it.copy(selection = it.selection.copy(bowlerId = playerId)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    /** Starts watching the match again after it failed to load. */
    fun retry() {
        _state.update { it.copy(loading = true, error = null) }
        refreshes.update { it + 1 }
    }

    /** [zone] is where the shot went, when the match keeps a wagon wheel. */
    fun recordRuns(runs: Int, zone: FieldZone? = null) = record(runsOffBat = runs, zone = zone)

    fun recordExtra(extra: ExtraType, runs: Int) {
        val penalty = _state.value.match?.rules?.penalty(extra) ?: return
        when (extra) {
            // A wide is worth its penalty plus whatever the batters ran.
            ExtraType.WIDE -> record(extra = extra, extraRuns = penalty + runs)
            // A no-ball is its penalty plus whatever came off the bat.
            ExtraType.NO_BALL -> record(runsOffBat = runs, extra = extra, extraRuns = penalty)
            ExtraType.BYE, ExtraType.LEG_BYE -> record(extra = extra, extraRuns = runs)
        }
    }

    /** [runsCompleted] only counts for a run out; every other dismissal ends the ball with no runs. */
    fun recordWicket(kind: DismissalKind, playerOutId: String, fielderId: String?, runsCompleted: Int = 0) =
        record(runsOffBat = if (kind.allowsRuns) runsCompleted else 0, wicket = Wicket(kind, playerOutId, fielderId))

    private fun record(
        runsOffBat: Int = 0,
        extra: ExtraType? = null,
        extraRuns: Int = 0,
        wicket: Wicket? = null,
        zone: FieldZone? = null,
    ) {
        val ui = _state.value
        if (!ui.canScore) return
        val match = ui.match ?: return
        val wideOrNoBall = extra == ExtraType.WIDE || extra == ExtraType.NO_BALL
        val delivery = Delivery(
            seq = ui.nextSeq,
            innings = ui.state!!.current.number,
            strikerId = ui.strikerId!!,
            nonStrikerId = ui.nonStrikerId,
            bowlerId = ui.bowlerId!!,
            runsOffBat = runsOffBat,
            extra = extra,
            extraRuns = extraRuns,
            wicket = wicket,
            timestamp = nowMillis(),
            extraPenalty = if (extra != null && wideOrNoBall) match.rules.penalty(extra) else 1,
            countsAsBall = wideOrNoBall && match.rules.countsAsBall(extra),
            zone = zone,
        )
        // The same check the server runs, so a mistake shows straight away and in the user's language.
        ui.state.validate(delivery, delivery.seq)?.let { error ->
            _state.update { it.copy(error = ScoringError.Rejected(error)) }
            return
        }
        val warnings = ui.state.warnings(delivery)
        if (warnings.isNotEmpty()) {
            _state.update { it.copy(pendingBall = PendingBall(delivery, warnings)) }
            return
        }
        save { matchRepository.recordDelivery(match.id, delivery) }
    }

    /** The scorer read the warning and wants the ball recorded anyway. */
    fun recordPendingBall() {
        val ui = _state.value
        val pending = ui.pendingBall ?: return
        val match = ui.match ?: return
        _state.update { it.copy(pendingBall = null) }
        // Still checked: the scorecard may have changed while the warning was up.
        ui.state?.validate(pending.delivery, ui.nextSeq)?.let { error ->
            _state.update { it.copy(error = ScoringError.Rejected(error)) }
            return
        }
        save { matchRepository.recordDelivery(match.id, pending.delivery) }
    }

    fun dropPendingBall() = _state.update { it.copy(pendingBall = null) }

    fun startHandover() {
        val match = _state.value.match ?: return
        if (!_state.value.isScorer) return
        viewModelScope.launch {
            try {
                val code = matchRepository.startHandover(match.id)
                _state.update { it.copy(handoverCode = code, handoverFailed = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(handoverFailed = true) }
            }
        }
    }

    fun dismissHandover() = _state.update { it.copy(handoverCode = null, handoverFailed = false) }

    /** Loads what starting a scheduled fixture needs, once. */
    private fun loadFixtureSetup(match: Match) {
        val tournamentId = match.fixture?.tournamentId
        if (_state.value.fixture != null) return
        _state.update { it.copy(fixture = FixtureSetup(canStart = false, teamA = null, teamB = null)) }
        viewModelScope.launch {
            val organiser = try {
                if (tournamentId == null) match.createdBy == uid else tournaments.details(tournamentId).tournament.isOrganiser(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            suspend fun team(id: String?) = id?.let {
                try {
                    teams.team(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            val setup = FixtureSetup(organiser, team(match.teamA.teamId), team(match.teamB.teamId))
            _state.update { it.copy(fixture = setup) }
        }
    }

    /** Starts the fixture with these squads and toss; the caller becomes its scorer. */
    fun startFixture(teamA: SquadRequest, teamB: SquadRequest, tossWinner: TeamSide, tossDecision: TossDecision) {
        val ui = _state.value
        val setup = ui.fixture ?: return
        if (setup.starting) return
        _state.update { it.copy(fixture = setup.copy(starting = true, failed = false)) }
        viewModelScope.launch {
            try {
                val details = tournaments.startFixture(matchId, StartFixtureRequest(teamA, teamB, tossWinner, tossDecision))
                _state.update { it.withSnapshot(details).copy(fixture = null) }
                refreshes.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(fixture = setup.copy(starting = false, failed = true)) }
            }
        }
    }

    fun undo() {
        val ui = _state.value
        if (!ui.canUndo) return
        val match = ui.match ?: return
        save { matchRepository.undoLast(match.id) }
    }

    /** Retired hurt can bat again later; retired out counts as a wicket. */
    fun retire(playerId: String, out: Boolean) = recordEvent { seq, innings -> MatchEvent.Retire(seq, innings, playerId, out) }

    /** Penalty runs to the batting side go on this innings; to the fielding side, on their own innings. */
    fun awardPenalty(toBattingSide: Boolean, runs: Int) =
        recordEvent { seq, innings -> MatchEvent.Penalty(seq, innings, toBattingSide, runs) }

    fun swapEnds() = recordEvent { seq, innings -> MatchEvent.SwapEnds(seq, innings) }

    /** Ends the current innings; in the chase that concedes the match. */
    fun declare() = recordEvent { seq, innings -> MatchEvent.Declare(seq, innings) }

    /** Rain: new overs for this innings (both, in the first), and in the chase a new target. */
    fun reviseTarget(overs: Int, target: Int?) = recordEvent { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs, target) }

    /** Clears the bowler mid-over (an injury, say) so the scorer can pick who finishes it. */
    fun replaceBowler() = recordEvent { seq, innings -> MatchEvent.ReplaceBowler(seq, innings) }

    /** A latecomer or substitute joins [side]; they show up in the player pickers straight away. */
    fun addPlayer(side: TeamSide, name: String) {
        val ui = _state.value
        val match = ui.match ?: return
        if (!ui.isScorer || name.isBlank()) return
        save { matchRepository.addPlayer(match.id, side, name.trim()) }
    }

    private fun recordEvent(make: (seq: Int, innings: Int) -> MatchEvent) {
        val ui = _state.value
        if (!ui.canEdit) return
        val match = ui.match ?: return
        val state = ui.state ?: return
        val event = make(ui.nextSeq, state.current.number)
        state.validate(event, event.seq)?.let { error ->
            _state.update { it.copy(error = ScoringError.Rejected(error)) }
            return
        }
        save { matchRepository.recordEvent(match.id, event) }
    }

    /** Sends the change and shows the server's scorecard straight away instead of waiting for the next poll. */
    private fun save(write: suspend () -> MatchDetails) {
        if (_state.value.saving || _state.value.reconciling) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val details = write()
                _state.update { current ->
                    val next = current.withSnapshot(details)
                    val ballAdded = details.deliveries.size > current.deliveries.size
                    next.copy(selection = next.state?.current?.let { current.selection.stillValid(it, ballAdded) } ?: Selection(), saving = false)
                }
                refreshes.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, reconciling = true, error = ScoringError.SaveFailed) }
                refreshes.update { it + 1 }
            }
        }
    }

    /**
     * Keeps the scorer's picks that still apply after a save, e.g. a new striker picked before the other
     * batter retired. A pick is dropped once the engine fills that slot or the player can't come in, and
     * the bowler pick after any new ball, so a finished over always asks who bowls next.
     */
    private fun Selection.stillValid(innings: InningsState, ballAdded: Boolean): Selection {
        val canComeIn = innings.canComeIn.map { it.id }.toSet()
        return Selection(
            strikerId = strikerId.takeIf { innings.strikerId == null && it in canComeIn },
            nonStrikerId = nonStrikerId.takeIf { innings.nonStrikerId == null && it in canComeIn },
            bowlerId = bowlerId.takeIf { innings.bowlerId == null && !ballAdded },
        )
    }

    private fun MatchUiState.withSnapshot(details: MatchDetails) = copy(
        loading = false,
        match = details.match,
        confirmedAt = nowMillis(),
        reconciling = false,
        deliveries = details.deliveries,
        events = details.events,
        state = ScoringEngine.compute(details.match, details.deliveries, details.events),
        // Nobody scores a fixture until it's started with squads and a toss.
        isScorer = details.match.scorerUid == uid && details.match.status != MatchStatus.SCHEDULED,
    )
}
