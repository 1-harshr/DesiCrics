package com.harsh.desicrics.ui.newmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NewMatchError {
    data class Invalid(val error: MatchSetupError) : NewMatchError
    data object CreateFailed : NewMatchError
    data object SaveTeamFailed : NewMatchError
    data object LoadTeamFailed : NewMatchError
}

/** One side of the new match: typed-in names, or a squad picked from a saved team. */
data class SideForm(
    val name: String = "",
    val players: List<SquadPlayer> = emptyList(),
    val teamId: String? = null,
    val inviteLink: String? = null,
    /** Indexes into [players]. */
    val captain: Int? = null,
    val keeper: Int? = null,
) {
    fun toRequest() = SquadRequest(name.trim(), players, teamId, captain, keeper)

    /** Drops the player at [index], keeping the captain and keeper pointing at the same people. */
    fun without(index: Int): SideForm {
        fun shift(i: Int?) = when {
            i == null || i == index -> null
            i > index -> i - 1
            else -> i
        }
        return copy(players = players.filterIndexed { i, _ -> i != index }, captain = shift(captain), keeper = shift(keeper))
    }
}

data class NewMatchFormState(
    val teamA: SideForm = SideForm(),
    val teamB: SideForm = SideForm(),
    val scheduledAt: Long? = null,
    val venue: String = "",
    val overs: Int = 10,
    val tossWinner: TeamSide = TeamSide.A,
    val tossDecision: TossDecision = TossDecision.BAT,
    val rules: MatchRules = MatchRules(),
    val saving: Boolean = false,
    val error: NewMatchError? = null,
    /** The caller's saved teams, loaded when they first open the team picker. */
    val myTeams: List<TeamSummary>? = null,
    /** Loading [myTeams] failed; the picker offers a retry. */
    val myTeamsFailed: Boolean = false,
    /** The saved team whose squad is being picked, and for which side. */
    val squadTeam: SavedTeam? = null,
    val squadSide: TeamSide? = null,
) {
    fun side(side: TeamSide) = if (side == TeamSide.A) teamA else teamB
    fun name(side: TeamSide) = side(side).name
    fun players(side: TeamSide) = side(side).players

    /** Anything typed or picked, so leaving the form would lose it. Loading and errors don't count. */
    val hasInput: Boolean
        get() = copy(saving = false, error = null, myTeams = null, myTeamsFailed = false, squadTeam = null, squadSide = null) != NewMatchFormState()

    /** [teams] that can go on [side]: a saved team can't play itself, so the other side's is left out. */
    fun pickable(side: TeamSide, teams: List<TeamSummary>) = side(side.other).teamId.let { taken -> teams.filter { it.id != taken } }
}

class NewMatchViewModel(
    private val matchRepository: MatchRepository,
    private val teamRepository: TeamRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NewMatchFormState())
    val state: StateFlow<NewMatchFormState> = _state

    fun update(transform: (NewMatchFormState) -> NewMatchFormState) = _state.update { transform(it).copy(error = null) }

    private fun updateSide(side: TeamSide, transform: (SideForm) -> SideForm) = update {
        if (side == TeamSide.A) it.copy(teamA = transform(it.teamA)) else it.copy(teamB = transform(it.teamB))
    }

    fun setTeamName(side: TeamSide, name: String) = updateSide(side) { it.copy(name = name) }

    /** Adds a guest by name; blank names and repeats within the team are ignored. */
    fun addPlayer(side: TeamSide, name: String) {
        val trimmed = name.trim()
        val players = _state.value.players(side)
        if (trimmed.isEmpty() || players.size >= CreateMatchRequest.MAX_PLAYERS) return
        if (players.any { it.name.equals(trimmed, ignoreCase = true) }) return
        updateSide(side) { it.copy(players = it.players + SquadPlayer(trimmed)) }
    }

    fun removePlayer(side: TeamSide, index: Int) = updateSide(side) { it.without(index) }

    suspend fun findTeams(q: String) = teamRepository.searchTeams(q)
    suspend fun findPlayers(q: String) = teamRepository.search(q)
    suspend fun byPhone(side: TeamSide, name: String, phone: String) = linkPlayer(side, teamRepository.placeholder(name, phone))
    fun linkPlayer(side: TeamSide, player: PlayerSummary) {
        updateSide(side) { form ->
            if (form.players.any { it.profileId == player.profileId } || form.players.size >= CreateMatchRequest.MAX_PLAYERS) form
            else form.copy(players = form.players + SquadPlayer(player.name, player.profileId))
        }
    }

    fun saveTeam(side: TeamSide) {
        val form = state.value.side(side)
        if (form.teamId != null || state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val team = teamRepository.create(form.name, "", form.players)
                updateSide(side) { form.copy(teamId = team.id, inviteLink = team.joinCode?.let(teamRepository::joinLink),
                    players = form.players.map { p ->
                        if (p.profileId != null) p else p.copy(profileId = team.members.firstOrNull { it.player.isPlaceholder && it.player.name == p.name }?.player?.profileId)
                    }) }
                _state.update { it.copy(myTeams = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = NewMatchError.SaveTeamFailed) } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }

    /** Loads the caller's teams for the picker, once it works; a failure is shown and tried again on the next call. */
    fun loadMyTeams() {
        if (_state.value.myTeams != null) return
        _state.update { it.copy(myTeamsFailed = false) }
        viewModelScope.launch {
            try {
                val teams = teamRepository.myTeams()
                _state.update { it.copy(myTeams = teams) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(myTeamsFailed = true) }
            }
        }
    }

    /** Opens the squad picker for [teamId] on [side]. */
    fun pickTeam(side: TeamSide, teamId: String) {
        viewModelScope.launch {
            val team = try {
                teamRepository.team(teamId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = NewMatchError.LoadTeamFailed) }
                return@launch
            }
            _state.update { it.copy(squadTeam = team, squadSide = side) }
        }
    }

    /** Reopens the squad picker for the saved team already on [side]. */
    fun changeSquad(side: TeamSide) {
        _state.value.side(side).teamId?.let { pickTeam(side, it) }
    }

    fun closeSquad() = _state.update { it.copy(squadTeam = null, squadSide = null) }

    /** Uses the picked members as [side]'s squad, under the saved team's name. */
    fun useSquad(players: List<PlayerSummary>, captainId: String?, keeperId: String?) {
        val form = _state.value
        val team = form.squadTeam ?: return
        val side = form.squadSide ?: return
        updateSide(side) {
            SideForm(
                name = team.name,
                players = players.map { SquadPlayer(it.name, it.profileId) },
                teamId = team.id,
                inviteLink = team.joinCode?.let(teamRepository::joinLink),
                captain = players.indexOfFirst { it.profileId == captainId }.takeIf { it >= 0 },
                keeper = players.indexOfFirst { it.profileId == keeperId }.takeIf { it >= 0 },
            )
        }
        closeSquad()
    }

    /** Back to typed-in names, keeping the players as guests. */
    fun clearTeam(side: TeamSide) = updateSide(side) { it.copy(teamId = null, inviteLink = null) }

    fun create(onCreated: (matchId: String) -> Unit) {
        val form = _state.value
        if (form.saving) return
        val request = CreateMatchRequest(
            pending = true, scheduledAt = form.scheduledAt, venue = form.venue,
            teamA = form.teamA.toRequest(),
            teamB = form.teamB.toRequest(),
            oversPerInnings = form.overs,
            tossWinner = form.tossWinner,
            tossDecision = form.tossDecision,
            rules = form.rules,
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
