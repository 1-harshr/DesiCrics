package com.harsh.desicrics.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.WagonWheel
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Badge
import com.harsh.desicrics.model.BattingStats
import com.harsh.desicrics.model.BowlingStats
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.FormEntry
import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardScope
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.scoring.formatOvers
import com.harsh.desicrics.ui.social.PlayerLine
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.util.format2
import com.harsh.desicrics.util.nowMillis
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** MVP points are kept in tenths: 123 is "12.3". */
fun mvpText(tenths: Int): String = "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"

@OptIn(ExperimentalTime::class)
private fun thisYear(): Int = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).year

/** Season and match-length chips. Seasons offered: all time, this year and the last two. */
@Composable
fun StatsFilters(filter: StatsFilter, onFilter: (StatsFilter) -> Unit) {
    val year = thisYear()
    val all = stringResource(Res.string.stats_season_all)
    ChoiceChips(listOf<Int?>(null, year, year - 1, year - 2), selected = filter.season, label = { it?.toString() ?: all }, onSelect = { onFilter(filter.copy(season = it)) })
    ChoiceChips(FormatFilter.entries, selected = filter.format, label = { stringResource(it.label) }, onSelect = { onFilter(filter.copy(format = it)) })
}

/** A player's career with filters: summary, batting, bowling, fielding, form, zones and badges. */
@Composable
fun CareerSection(profileId: String, onCompare: (() -> Unit)? = null) {
    val viewModel: CareerViewModel = koinViewModel(key = "career-$profileId") { parametersOf(profileId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        StatsFilters(state.filter, viewModel::setFilter)
        val stats = state.stats
        when {
            stats == null && state.failed -> Text(stringResource(Res.string.stats_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
            stats == null -> Unit
            stats.matches == 0 -> Text(stringResource(Res.string.stats_none), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
            else -> CareerCards(stats)
        }
        if (onCompare != null) GhostButton(stringResource(Res.string.stats_compare), onClick = onCompare, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CareerCards(stats: CareerStats) {
    val colors = DesiTheme.colors
    Text(
        stringResource(Res.string.stats_summary, stats.matches, stats.won, mvpText(stats.mvpTenths)),
        style = DesiTheme.type.bodyStrong,
        color = colors.ink,
    )
    StatCard(Res.string.stats_batting, battingCells(stats.batting))
    if (stats.bowling.balls > 0) StatCard(Res.string.stats_bowling, bowlingCells(stats.bowling))
    StatCard(
        Res.string.stats_fielding,
        listOf(
            Res.string.stats_catches to "${stats.fielding.catches}",
            Res.string.stats_run_outs to "${stats.fielding.runOuts}",
            Res.string.stats_stumpings to "${stats.fielding.stumpings}",
        ),
    )
    if (stats.form.any { it.batted }) FormChart(stats.form)
    if (stats.zones.isNotEmpty()) {
        DesiCard(Modifier.fillMaxWidth()) {
            SectionLabel(stringResource(Res.string.stats_zones))
            WagonWheel(
                labels = FieldZone.Clockwise.map { stringResource(it.label) },
                values = FieldZone.Clockwise.map { stats.zones[it] ?: 0 },
                description = stringResource(Res.string.scorecard_wagon_wheel_description),
            )
        }
    }
    if (stats.badges.isNotEmpty()) {
        SectionLabel(stringResource(Res.string.stats_badges))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            stats.badges.forEach { badge ->
                Text(
                    "🏅 " + stringResource(badge.label),
                    style = DesiTheme.type.label,
                    color = colors.brand,
                    modifier = Modifier.padding(end = DesiTheme.space.s2),
                )
            }
        }
    }
}

@Composable
private fun battingCells(b: BattingStats): List<Pair<StringResource, String>> {
    val dash = stringResource(Res.string.stats_dash)
    return listOf(
        Res.string.stats_innings to "${b.innings}",
        Res.string.stats_runs to "${b.runs}",
        Res.string.stats_highest to "${b.highest}${if (b.highestNotOut) "*" else ""}",
        Res.string.stats_average to (b.average?.format2() ?: dash),
        Res.string.stats_strike_rate to b.strikeRate.format2(),
        Res.string.stats_fifties to "${b.fifties}",
        Res.string.stats_hundreds to "${b.hundreds}",
        Res.string.stats_fours to "${b.fours}",
        Res.string.stats_sixes to "${b.sixes}",
    )
}

@Composable
private fun bowlingCells(b: BowlingStats): List<Pair<StringResource, String>> {
    val dash = stringResource(Res.string.stats_dash)
    return listOf(
        Res.string.stats_innings to "${b.innings}",
        Res.string.stats_overs to formatOvers(b.balls),
        Res.string.stats_wickets to "${b.wickets}",
        Res.string.stats_best to "${b.bestWickets}/${b.bestRuns}",
        Res.string.stats_average to (b.average?.format2() ?: dash),
        Res.string.stats_economy to b.economy.format2(),
        Res.string.stats_five_wickets to "${b.fiveWickets}",
    )
}

/** A titled grid of figures, three to a row. */
@Composable
private fun StatCard(title: StringResource, cells: List<Pair<StringResource, String>>) {
    DesiCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(title))
        cells.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f).padding(vertical = DesiTheme.space.s1)) {
                        Text(value, style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
                        Text(stringResource(label), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                    }
                }
                repeat(3 - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

/** Bars of runs per match, oldest on the left; fifties in the brand colour. */
@Composable
private fun FormChart(form: List<FormEntry>) {
    val colors = DesiTheme.colors
    val batted = form.filter { it.batted }
    val most = batted.maxOf { it.runs }.coerceAtLeast(1)
    val description = stringResource(Res.string.stats_form_description, batted.size)
    DesiCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(Res.string.stats_form, batted.size))
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
            val slot = size.width / batted.size
            val bar = slot * 0.6f
            batted.forEachIndexed { i, entry ->
                val height = (entry.runs.toFloat() / most) * size.height
                drawRoundRect(
                    color = if (entry.runs >= 50) colors.brand else colors.brand.copy(alpha = 0.45f),
                    topLeft = Offset(i * slot + (slot - bar) / 2, size.height - height.coerceAtLeast(2f)),
                    size = Size(bar, height.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            batted.forEach {
                Text(
                    "${it.runs}${if (it.notOut) "*" else ""}",
                    style = DesiTheme.type.caption,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Two careers side by side, picked by search. */
@Composable
fun CompareScreen(profileId: String, name: String, onBack: () -> Unit, viewModel: CompareViewModel = koinViewModel { parametersOf(profileId) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.compare_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                DesiTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    label = state.other?.name ?: stringResource(Res.string.compare_pick),
                    keyboardOptions = KeyboardOptions.Default,
                )
            }
            items(state.results, key = { it.profileId }) { PlayerLine(it, onClick = { viewModel.pick(it) }) }
            val pair = state.stats
            val other = state.other
            if (other != null) {
                item { StatsFilters(state.filter, viewModel::setFilter) }
            }
            if (state.failed) item { Text(stringResource(Res.string.stats_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
            if (pair != null && other != null) {
                item { CompareTable(name, other.name, pair.first, pair.second) }
            }
        }
    }
}

@Composable
private fun CompareTable(nameA: String, nameB: String, a: CareerStats, b: CareerStats) {
    val dash = stringResource(Res.string.stats_dash)
    val rows = listOf(
        Res.string.profile_tab_matches to (a.matches to b.matches).map { "$it" },
        Res.string.stats_runs to (a.batting.runs to b.batting.runs).map { "$it" },
        Res.string.stats_average to (a.batting.average to b.batting.average).map { it?.format2() ?: dash },
        Res.string.stats_strike_rate to (a.batting.strikeRate to b.batting.strikeRate).map { it.format2() },
        Res.string.stats_highest to (a.batting.highest to b.batting.highest).map { "$it" },
        Res.string.stats_sixes to (a.batting.sixes to b.batting.sixes).map { "$it" },
        Res.string.stats_wickets to (a.bowling.wickets to b.bowling.wickets).map { "$it" },
        Res.string.stats_economy to (a.bowling.economy to b.bowling.economy).map { it.format2() },
        Res.string.stats_catches to (a.fielding.catches to b.fielding.catches).map { "$it" },
        Res.string.metric_mvp to (a.mvpTenths to b.mvpTenths).map { mvpText(it) },
    )
    DesiCard(Modifier.fillMaxWidth()) {
        Row {
            Text("", Modifier.weight(1f))
            listOf(nameA, nameB).forEach {
                Text(it, style = DesiTheme.type.label, color = DesiTheme.colors.muted, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        rows.forEach { (label, values) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(stringResource(label), style = DesiTheme.type.body, color = DesiTheme.colors.muted, modifier = Modifier.weight(1f))
                Text(values.first, style = DesiTheme.type.data, color = DesiTheme.colors.ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                Text(values.second, style = DesiTheme.type.data, color = DesiTheme.colors.ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun <T> Pair<T, T>.map(transform: (T) -> String): Pair<String, String> = transform(first) to transform(second)

/** Friends, city or one tournament, by runs, wickets, MVP points, sixes or catches. */
@Composable
fun LeaderboardScreen(
    tournamentId: String?,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = koinViewModel(key = "leaderboard-$tournamentId") { parametersOf(tournamentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = DesiTheme.space
    val year = thisYear()
    val allTime = stringResource(Res.string.stats_season_all)
    DesiScaffold(
        title = stringResource(if (tournamentId != null) Res.string.tournament_leaderboard else Res.string.leaderboards_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (tournamentId == null) {
                item {
                    ChoiceChips(
                        listOf(LeaderboardScope.FRIENDS, LeaderboardScope.CITY),
                        selected = state.scope,
                        label = { stringResource(if (it == LeaderboardScope.CITY) Res.string.leaderboard_city else Res.string.leaderboard_friends) },
                        onSelect = { scope -> viewModel.update { it.copy(scope = scope) } },
                    )
                }
                item {
                    ChoiceChips(listOf<Int?>(null, year, year - 1), selected = state.season, label = { it?.toString() ?: allTime }, onSelect = { season ->
                        viewModel.update { it.copy(season = season) }
                    })
                }
            }
            item {
                ChoiceChips(LeaderboardMetric.entries, selected = state.metric, label = { stringResource(it.label) }, onSelect = { metric ->
                    viewModel.update { it.copy(metric = metric) }
                })
            }
            if (state.failed) item { Text(stringResource(Res.string.stats_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
            else if (!state.loading && state.rows.isEmpty()) item { Text(stringResource(Res.string.leaderboard_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted) }
            items(state.rows, key = { it.player.profileId }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${state.rows.indexOf(row) + 1}", style = DesiTheme.type.scoreMd, color = DesiTheme.colors.muted, modifier = Modifier.padding(end = space.s3))
                    Column(Modifier.weight(1f)) {
                        PlayerLine(row.player, onClick = { onOpenPlayer(row.player.profileId) })
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (state.metric == LeaderboardMetric.MVP) mvpText(row.value) else "${row.value}", style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
                        Text(pluralStringResource(Res.plurals.leaderboard_matches, row.matches, row.matches), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                    }
                }
            }
        }
    }
}

private val FormatFilter.label: StringResource
    get() = when (this) {
        FormatFilter.ALL -> Res.string.stats_format_all
        FormatFilter.SHORT -> Res.string.stats_format_short
        FormatFilter.T20 -> Res.string.stats_format_t20
        FormatFilter.LONG -> Res.string.stats_format_long
    }

private val LeaderboardMetric.label: StringResource
    get() = when (this) {
        LeaderboardMetric.RUNS -> Res.string.metric_runs
        LeaderboardMetric.WICKETS -> Res.string.metric_wickets
        LeaderboardMetric.MVP -> Res.string.metric_mvp
        LeaderboardMetric.SIXES -> Res.string.metric_sixes
        LeaderboardMetric.CATCHES -> Res.string.metric_catches
    }

private val Badge.label: StringResource
    get() = when (this) {
        Badge.FIRST_FIFTY -> Res.string.badge_first_fifty
        Badge.CENTURY -> Res.string.badge_century
        Badge.FIVE_WICKET_HAUL -> Res.string.badge_five_wickets
        Badge.RUNS_500 -> Res.string.badge_runs_500
        Badge.RUNS_1000 -> Res.string.badge_runs_1000
        Badge.WICKETS_25 -> Res.string.badge_wickets_25
        Badge.WICKETS_50 -> Res.string.badge_wickets_50
        Badge.MATCHES_25 -> Res.string.badge_matches_25
        Badge.MATCHES_50 -> Res.string.badge_matches_50
        Badge.PLAYER_OF_THE_MATCH -> Res.string.badge_player_of_the_match
    }

@Preview
@Composable
private fun CareerPreview() = DesiTheme(darkTheme = false) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CareerCards(
            CareerStats(
                "p", matches = 12, won = 7,
                batting = BattingStats(12, 2, 412, 330, 40, 12, 88, true, 3),
                bowling = BowlingStats(9, 150, 190, 11, 1, 3, 14),
                mvpTenths = 5124,
                form = (1..10).map { FormEntry("m$it", it.toLong(), it * 9 % 70, it % 3 == 0, 1, true) },
                badges = listOf(Badge.FIRST_FIFTY, Badge.PLAYER_OF_THE_MATCH),
            ),
        )
    }
}
