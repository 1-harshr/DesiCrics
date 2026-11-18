package com.harsh.desicrics.ui.newmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NewMatchError {
    data class Invalid(val error: MatchSetupError) : NewMatchError
    data object CreateFailed : NewMatchError
}

data class NewMatchFormState(
    val teamAName: String = "",
    val teamAPlayers: List<String> = emptyList(),
    val teamBName: String = "",
    val teamBPlayers: List<String> = emptyList(),
    val overs: Int = 10,
    val tossWinner: TeamSide = TeamSide.A,
    val tossDecision: TossDecision = TossDecision.BAT,
    val saving: Boolean = false,
    val error: NewMatchError? = null,
) {
    fun name(side: TeamSide) = if (side == TeamSide.A) teamAName else teamBName
    fun players(side: TeamSide) = if (side == TeamSide.A) teamAPlayers else teamBPlayers
}

class NewMatchViewModel(private val matchRepository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewMatchFormState())
    val state: StateFlow<NewMatchFormState> = _state

    fun update(transform: (NewMatchFormState) -> NewMatchFormState) = _state.update { transform(it).copy(error = null) }

    fun setTeamName(side: TeamSide, name: String) = update {
        if (side == TeamSide.A) it.copy(teamAName = name) else it.copy(teamBName = name)
    }

    /** Adds a player; blank names and repeats within the team are ignored. */
    fun addPlayer(side: TeamSide, name: String) {
        val trimmed = name.trim()
        val players = _state.value.players(side)
        if (trimmed.isEmpty() || players.size >= CreateMatchRequest.MAX_PLAYERS) return
        if (players.any { it.equals(trimmed, ignoreCase = true) }) return
        setPlayers(side, players + trimmed)
    }

    fun removePlayer(side: TeamSide, index: Int) = setPlayers(side, _state.value.players(side).filterIndexed { i, _ -> i != index })

    private fun setPlayers(side: TeamSide, players: List<String>) = update {
        if (side == TeamSide.A) it.copy(teamAPlayers = players) else it.copy(teamBPlayers = players)
    }

    fun create(onCreated: (matchId: String) -> Unit) {
        val form = _state.value
        val request = CreateMatchRequest(
            teamAName = form.teamAName.trim(),
            teamAPlayers = form.teamAPlayers,
            teamBName = form.teamBName.trim(),
            teamBPlayers = form.teamBPlayers,
            oversPerInnings = form.overs,
            tossWinner = form.tossWinner,
            tossDecision = form.tossDecision,
        )
        // Same check the server runs, for instant feedback.
        request.validate()?.let { error ->
            _state.update { it.copy(error = NewMatchError.Invalid(error)) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val matchId = matchRepository.createMatch(request)
                onCreated(matchId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = NewMatchError.CreateFailed) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
