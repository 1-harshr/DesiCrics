package com.harsh.desicrics.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.DeliveryError
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.validate
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
    /** The latest refresh failed; the last good scorecard stays on screen. */
    val offline: Boolean = false,
    val error: ScoringError? = null,
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
                                else -> current.withSnapshot(snapshot.match, snapshot.deliveries).copy(offline = false)
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

    fun recordRuns(runs: Int) = record(runsOffBat = runs)

    fun recordExtra(extra: ExtraType, runs: Int) = when (extra) {
        // A wide is worth 1 plus whatever the batters ran.
        ExtraType.WIDE -> record(extra = extra, extraRuns = 1 + runs)
        // A no-ball is a 1-run penalty plus whatever came off the bat.
        ExtraType.NO_BALL -> record(runsOffBat = runs, extra = extra, extraRuns = 1)
        ExtraType.BYE, ExtraType.LEG_BYE -> record(extra = extra, extraRuns = runs)
    }

    /** [runsCompleted] only counts for a run out; every other dismissal ends the ball with no runs. */
    fun recordWicket(kind: DismissalKind, playerOutId: String, fielderId: String?, runsCompleted: Int = 0) =
        record(runsOffBat = if (kind.allowsRuns) runsCompleted else 0, wicket = Wicket(kind, playerOutId, fielderId))

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
        // The same check the server runs, so a mistake shows straight away and in the user's language.
        ui.state.validate(delivery, delivery.seq)?.let { error ->
            _state.update { it.copy(error = ScoringError.Rejected(error)) }
            return
        }
        save { matchRepository.recordDelivery(match.id, delivery) }
    }

    fun undo() {
        val ui = _state.value
        if (!ui.canUndo) return
        val match = ui.match ?: return
        save { matchRepository.undoLastDelivery(match.id) }
    }

    /** Sends the change and shows the server's scorecard straight away instead of waiting for the next poll. */
    private fun save(write: suspend () -> MatchDetails) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val details = write()
                _state.update { it.withSnapshot(details.match, details.deliveries).copy(selection = Selection(), saving = false) }
                refreshes.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the scorer's selection so they can just tap again.
                _state.update { it.copy(saving = false, error = ScoringError.SaveFailed) }
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
}
