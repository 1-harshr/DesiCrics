package com.harsh.desicrics.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
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

data class MatchUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val match: Match? = null,
    val deliveries: List<Delivery> = emptyList(),
    val state: MatchState? = null,
    val isScorer: Boolean = false,
    val selection: Selection = Selection(),
    /** A ball or undo is being saved; scoring is paused until the server confirms. */
    val saving: Boolean = false,
    /** Set while the latest refresh failed; the last good scorecard stays on screen. */
    val connectionError: String? = null,
    val error: String? = null,
) {
    val strikerId: String? get() = state?.current?.strikerId ?: selection.strikerId
    val nonStrikerId: String? get() = state?.current?.nonStrikerId ?: selection.nonStrikerId
    val bowlerId: String? get() = state?.current?.bowlerId ?: selection.bowlerId

    val canScore: Boolean
        get() = isScorer && state?.isComplete == false && !saving &&
            strikerId != null && nonStrikerId != null && bowlerId != null

    val canUndo: Boolean get() = isScorer && deliveries.isNotEmpty() && !saving
}

class MatchViewModel(
    private val matchId: String,
    private val matchRepository: MatchRepository,
    authRepository: AuthRepository,
) : ViewModel() {

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
                                else -> current.withSnapshot(snapshot.match, snapshot.deliveries).copy(connectionError = null)
                            }
                        }
                    },
                    onFailure = { e ->
                        _state.update {
                            if (it.match == null) {
                                it.copy(loading = false, error = e.message ?: "Couldn't load the match")
                            } else {
                                it.copy(connectionError = "Can't reach the server, retrying…")
                            }
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

    fun recordRuns(runs: Int) = record(runsOffBat = runs)

    fun recordExtra(extra: ExtraType, runs: Int) = when (extra) {
        // A wide is worth 1 plus whatever the batters ran.
        ExtraType.WIDE -> record(extra = extra, extraRuns = 1 + runs)
        // A no-ball is a 1-run penalty plus whatever came off the bat.
        ExtraType.NO_BALL -> record(runsOffBat = runs, extra = extra, extraRuns = 1)
        ExtraType.BYE, ExtraType.LEG_BYE -> record(extra = extra, extraRuns = runs)
    }

    fun recordWicket(kind: DismissalKind, nonStrikerOut: Boolean) {
        val ui = _state.value
        val outId = (if (kind == DismissalKind.RUN_OUT && nonStrikerOut) ui.nonStrikerId else ui.strikerId) ?: return
        record(wicket = Wicket(kind, outId))
    }

    private fun record(
        runsOffBat: Int = 0,
        extra: ExtraType? = null,
        extraRuns: Int = 0,
        wicket: Wicket? = null,
    ) {
        val ui = _state.value
        if (!ui.canScore) return
        val match = ui.match ?: return
        val delivery = Delivery(
            seq = (ui.deliveries.maxOfOrNull { it.seq } ?: -1) + 1,
            innings = ui.state!!.current.number,
            strikerId = ui.strikerId!!,
            nonStrikerId = ui.nonStrikerId!!,
            bowlerId = ui.bowlerId!!,
            runsOffBat = runsOffBat,
            extra = extra,
            extraRuns = extraRuns,
            wicket = wicket,
            timestamp = nowMillis(),
        )
        val deliveries = ui.deliveries + delivery
        val updated = match.withScore(ScoringEngine.compute(match, deliveries))
        save(updated, deliveries) { matchRepository.recordDelivery(updated, delivery) }
    }

    fun undo() {
        val ui = _state.value
        if (!ui.canUndo) return
        val match = ui.match ?: return
        val last = ui.deliveries.maxBy { it.seq }
        val deliveries = ui.deliveries - last
        val updated = match.withScore(ScoringEngine.compute(match, deliveries))
        save(updated, deliveries) { matchRepository.undoDelivery(updated, last) }
    }

    /** Writes to the server, then shows the result locally straight away instead of waiting for the next poll. */
    private fun save(match: Match, deliveries: List<Delivery>, write: suspend () -> Unit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                write()
                _state.update { it.withSnapshot(match, deliveries).copy(selection = Selection(), saving = false) }
                refreshes.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the scorer's selection so they can just tap again.
                _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save that ball") }
            }
        }
    }

    private fun MatchUiState.withSnapshot(match: Match, deliveries: List<Delivery>) = copy(
        loading = false,
        match = match,
        deliveries = deliveries,
        state = ScoringEngine.compute(match, deliveries),
        isScorer = match.createdBy == uid,
    )

    private fun Match.withScore(state: MatchState) = copy(
        scoreSummary = state.scoreSummary,
        status = if (state.isComplete) MatchStatus.COMPLETED else MatchStatus.IN_PROGRESS,
        result = state.result,
    )
}
