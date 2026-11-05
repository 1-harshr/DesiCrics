package com.harsh.desicrics.ui.newmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewMatchFormState(
    val teamAName: String = "",
    val teamAPlayers: String = "",
    val teamBName: String = "",
    val teamBPlayers: String = "",
    val overs: String = "10",
    val tossWinner: TeamSide = TeamSide.A,
    val tossDecision: TossDecision = TossDecision.BAT,
    val saving: Boolean = false,
    val error: String? = null,
)

class NewMatchViewModel(private val matchRepository: MatchRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewMatchFormState())
    val state: StateFlow<NewMatchFormState> = _state

    fun update(transform: (NewMatchFormState) -> NewMatchFormState) = _state.update { transform(it).copy(error = null) }

    fun create(onCreated: (matchId: String) -> Unit) {
        val form = _state.value
        val playersA = parsePlayers(form.teamAPlayers)
        val playersB = parsePlayers(form.teamBPlayers)
        val overs = form.overs.toIntOrNull()
        val error = when {
            form.teamAName.isBlank() || form.teamBName.isBlank() -> "Give both teams a name"
            form.teamAName.trim().equals(form.teamBName.trim(), ignoreCase = true) -> "Teams need different names"
            playersA.size < 2 || playersB.size < 2 -> "Each team needs at least 2 players"
            overs == null || overs !in 1..50 -> "Overs must be between 1 and 50"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val matchId = matchRepository.createMatch(
                    teamAName = form.teamAName.trim(),
                    teamAPlayers = playersA,
                    teamBName = form.teamBName.trim(),
                    teamBPlayers = playersB,
                    oversPerInnings = overs!!,
                    tossWinner = form.tossWinner,
                    tossDecision = form.tossDecision,
                )
                onCreated(matchId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't create the match") }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** One player per line (commas work too). */
    private fun parsePlayers(text: String): List<String> =
        text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
}
