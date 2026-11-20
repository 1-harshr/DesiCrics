package com.harsh.desicrics.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.data.StatsRepository
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.model.LeaderboardScope
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.ui.teams.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which slice of a career to show: a season (calendar year) or all of them, and a match length. */
data class StatsFilter(val season: Int? = null, val format: FormatFilter = FormatFilter.ALL)

data class CareerUiState(
    val filter: StatsFilter = StatsFilter(),
    val stats: CareerStats? = null,
    val loading: Boolean = true,
    val loadError: String? = null,
)

class CareerViewModel(private val profileId: String, private val repository: StatsRepository) : ViewModel() {
    private val _state = MutableStateFlow(CareerUiState())
    val state: StateFlow<CareerUiState> = _state
    private var job: Job? = null

    init {
        load()
    }

    fun setFilter(filter: StatsFilter) {
        _state.update { it.copy(filter = filter) }
        load()
    }

    /** Loads again after a failure. */
    fun retry() = load()

    private fun load() {
        job?.cancel()
        val filter = _state.value.filter
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            attempt { repository.career(profileId, filter.season, filter.format) }.fold(
                onSuccess = { stats -> _state.update { it.copy(stats = stats, loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message ?: e::class.simpleName ?: "error") } },
            )
        }
    }
}

data class CompareUiState(
    val other: PlayerSummary? = null,
    val query: String = "",
    val results: List<PlayerSummary> = emptyList(),
    val filter: StatsFilter = StatsFilter(),
    val stats: Pair<CareerStats, CareerStats>? = null,
    val loadError: String? = null,
)

/** [profileId] against someone the user searches for. */
class CompareViewModel(
    private val profileId: String,
    private val stats: StatsRepository,
    private val social: SocialRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CompareUiState())
    val state: StateFlow<CompareUiState> = _state
    private var searchJob: Job? = null

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val players = attempt { social.search(query) }.getOrNull()?.players.orEmpty().filter { it.profileId != profileId }
            _state.update { it.copy(results = players) }
        }
    }

    fun pick(other: PlayerSummary) {
        _state.update { it.copy(other = other, query = "", results = emptyList()) }
        load()
    }

    fun setFilter(filter: StatsFilter) {
        _state.update { it.copy(filter = filter) }
        load()
    }

    /** Loads again after a failure. */
    fun retry() = load()

    private fun load() {
        val other = _state.value.other ?: return
        val filter = _state.value.filter
        viewModelScope.launch {
            attempt { stats.compare(profileId, other.profileId, filter.season, filter.format) }.fold(
                onSuccess = { pair -> _state.update { it.copy(stats = pair, loadError = null) } },
                onFailure = { e -> _state.update { it.copy(loadError = e.message ?: e::class.simpleName ?: "error") } },
            )
        }
    }
}

data class LeaderboardUiState(
    val scope: LeaderboardScope = LeaderboardScope.FRIENDS,
    val metric: LeaderboardMetric = LeaderboardMetric.RUNS,
    val season: Int? = null,
    val rows: List<LeaderboardRow> = emptyList(),
    val loading: Boolean = true,
    val loadError: String? = null,
)

/** [tournamentId], when set, fixes the scope to that tournament. */
class LeaderboardViewModel(private val tournamentId: String?, private val repository: StatsRepository) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState(scope = if (tournamentId != null) LeaderboardScope.TOURNAMENT else LeaderboardScope.FRIENDS))
    val state: StateFlow<LeaderboardUiState> = _state
    private var job: Job? = null

    init {
        load()
    }

    fun update(transform: (LeaderboardUiState) -> LeaderboardUiState) {
        _state.update(transform)
        load()
    }

    /** Loads again after a failure. */
    fun retry() = load()

    private fun load() {
        job?.cancel()
        val ui = _state.value
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, loadError = null) }
            // City uses the user's own city; the server fills it in.
            val id = if (ui.scope == LeaderboardScope.TOURNAMENT) tournamentId else null
            attempt { repository.leaderboard(ui.scope, id, ui.metric, ui.season) }.fold(
                onSuccess = { rows -> _state.update { it.copy(rows = rows, loading = false) } },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message ?: e::class.simpleName ?: "error") } },
            )
        }
    }
}
