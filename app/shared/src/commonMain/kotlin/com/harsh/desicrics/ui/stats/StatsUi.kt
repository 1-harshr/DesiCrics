package com.harsh.desicrics.ui.stats
import com.harsh.desicrics.designsystem.component.BallSpinner
import com.harsh.desicrics.designsystem.component.PickerSheet
import com.harsh.desicrics.designsystem.component.PickerPill
import androidx.compose.foundation.layout.Box
import org.jetbrains.compose.resources.DrawableResource
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.designsystem.component.heroBackground
import com.harsh.desicrics.designsystem.component.FilterRow
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.RankBadge
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.social.PlayerList

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.harsh.desicrics.designsystem.component.ActionTile
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.StatGrid
import com.harsh.desicrics.designsystem.component.StatItem
import com.harsh.desicrics.designsystem.icon.DesiIcons
import org.jetbrains.compose.resources.painterResource

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.harsh.desicrics.ui.match.ShareImageSheet
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

/** The form chart always has room for this many matches, the most it shows. */
private const val FORM_SLOTS = 10

/** MVP points are kept in tenths: 123 is "12.3". */
fun mvpText(tenths: Int): String = "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"

@OptIn(ExperimentalTime::class)
private fun thisYear(): Int = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).year

/** Season and match length as two picker pills. Seasons offered: all time, this year and the last two. */
@Composable
fun StatsFilters(filter: StatsFilter, onFilter: (StatsFilter) -> Unit) {
    val year = thisYear()
    val seasons = listOf<Int?>(null, year, year - 1, year - 2)
    val all = stringResource(Res.string.stats_season_all)
    val seasonLabels = seasons.map { it?.toString() ?: all }
    val formatLabels = FormatFilter.entries.map { stringResource(it.label) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        PickerPill(seasonLabels[seasons.indexOf(filter.season).coerceAtLeast(0)], { picking = "season" }, icon = DesiIcons.Calendar)
        PickerPill(formatLabels[filter.format.ordinal], { picking = "format" }, icon = DesiIcons.Clock)
    }
    when (picking) {
        "season" -> PickerSheet(
            stringResource(Res.string.leaderboard_season),
            seasonLabels,
            selected = seasons.indexOf(filter.season),
            onSelect = { onFilter(filter.copy(season = seasons[it])); picking = null },
            onDismiss = { picking = null },
        )
        "format" -> PickerSheet(
            stringResource(Res.string.stats_match_length),
            formatLabels,
            selected = filter.format.ordinal,
            onSelect = { onFilter(filter.copy(format = FormatFilter.entries[it])); picking = null },
            onDismiss = { picking = null },
        )
    }
}

/** A player's career with filters: summary, batting, bowling, fielding, form, zones and badges. */
@Composable
fun CareerSection(
    profileId: String,
    onCompare: (() -> Unit)? = null,
    name: String = "",
    onOpenMatch: ((String) -> Unit)? = null,
    /** False where a tab already says Stats. */
    heading: Boolean = true,
) {
    val viewModel: CareerViewModel = koinViewModel(key = "career-$profileId") { parametersOf(profileId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sharing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
        if (heading) SectionHeader(stringResource(Res.string.profile_tab_stats), icon = DesiIcons.Leaderboard)
        StatsFilters(state.filter, viewModel::setFilter)
        val stats = state.stats
        when {
            stats == null && state.failed -> ErrorState(stringResource(Res.string.error_stats_title), stringResource(Res.string.error_connection_body), onRetry = viewModel::retry, compact = true)
            stats == null -> Unit
            stats.matches == 0 -> EmptyState(DesiIcons.Leaderboard, stringResource(Res.string.stats_none_title), body = stringResource(Res.string.stats_none))
            else -> CareerCards(stats, onOpenMatch)
        }
        val canShare = stats != null && stats.matches > 0 && name.isNotBlank()
        if (canShare || onCompare != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                if (canShare) ActionTile(DesiIcons.Share, stringResource(Res.string.stats_share), { sharing = true }, Modifier.weight(1f))
                if (onCompare != null) ActionTile(DesiIcons.Swap, stringResource(Res.string.stats_compare), onCompare, Modifier.weight(1f))
            }
        }
        if (sharing && stats != null) {
            ShareImageSheet(stringResource(Res.string.stats_share_title), onDismiss = { sharing = false }) { StatsShareCard(name, stats) }
        }
    }
}

/** A player's numbers as a 9:16 card for WhatsApp status: runs and wickets big, best figures and badges below. */
@Composable
private fun StatsShareCard(name: String, stats: CareerStats) {
    val colors = DesiTheme.colors
    val onGreen = colors.onBrandStrong
    Column(
        Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(DesiTheme.radius.lg).background(colors.brandStrong).padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = DesiTheme.type.titleLg, color = onGreen, maxLines = 2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(stats.batting.runs to Res.string.share_card_runs, stats.bowling.wickets to Res.string.share_card_wickets).forEach { (value, label) ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$value", style = DesiTheme.type.scoreXl, color = onGreen)
                    Text(stringResource(label), style = DesiTheme.type.data, color = onGreen.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            Text(
                stringResource(
                    Res.string.share_card_best,
                    "${stats.batting.highest}${if (stats.batting.highestNotOut) "*" else ""}",
                    "${stats.bowling.bestWickets}/${stats.bowling.bestRuns}",
                ),
                style = DesiTheme.type.bodyStrong,
                color = onGreen,
            )
            stats.badges.take(3).forEach { Text("🏅 " + stringResource(it.label), style = DesiTheme.type.body, color = onGreen) }
        }
        Text(stringResource(Res.string.share_card_footer).uppercase(), style = DesiTheme.type.label, color = onGreen.copy(alpha = 0.8f))
    }
}

@Composable
fun CareerCards(stats: CareerStats, onOpenMatch: ((String) -> Unit)? = null) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    StatGrid(
        listOf(
            StatItem("${stats.matches}", stringResource(Res.string.stats_matches)),
            StatItem("${stats.won}", stringResource(Res.string.team_stat_won)),
            StatItem(mvpText(stats.mvpTenths), stringResource(Res.string.stats_mvp_points)),
        ),
    )
    if (stats.form.any { it.batted }) FormChart(stats.form, onOpenMatch)
    val disciplines = listOf(Res.string.stats_batting, Res.string.stats_bowling, Res.string.stats_fielding)
    var discipline by rememberSaveable { mutableStateOf(0) }
    SegmentedControl(disciplines.indices.toList(), discipline, label = { stringResource(disciplines[it]) }, onSelect = { discipline = it })
    when (discipline) {
        0 -> StatGrid(battingCells(stats.batting), featured = 3)
        1 -> StatGrid(bowlingCells(stats.bowling), featured = 3)
        else -> StatGrid(
            listOf(
                StatItem("${stats.fielding.catches}", stringResource(Res.string.stats_catches)),
                StatItem("${stats.fielding.runOuts}", stringResource(Res.string.stats_run_outs)),
                StatItem("${stats.fielding.stumpings}", stringResource(Res.string.stats_stumpings)),
            ),
            featured = 3,
        )
    }
    var showDefinitions by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.clip(DesiTheme.radius.full).clickable(role = Role.Button) { showDefinitions = !showDefinitions }.padding(vertical = space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.s2),
    ) {
        Icon(painterResource(DesiIcons.Info), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
        Text(stringResource(Res.string.stats_terms), style = DesiTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.muted)
    }
    if (showDefinitions) Text(stringResource(Res.string.stats_definitions), style = DesiTheme.type.caption, color = colors.muted)
    if (stats.zones.isNotEmpty()) {
        DesiCard(Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(Res.string.stats_zones), icon = DesiIcons.Stadium)
            WagonWheel(
                labels = FieldZone.Clockwise.map { stringResource(it.label) },
                values = FieldZone.Clockwise.map { stats.zones[it] ?: 0 },
                description = stringResource(Res.string.scorecard_wagon_wheel_description),
            )
        }
    }
    if (stats.badges.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
            SectionHeader(stringResource(Res.string.stats_badges), icon = DesiIcons.Medal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                stats.badges.forEach { badge -> BadgeChip(stringResource(badge.label)) }
            }
        }
    }
}

/** An earned badge: a medal on the brand tint with its name. */
@Composable
private fun BadgeChip(label: String) {
    val colors = DesiTheme.colors
    Row(
        Modifier.clip(DesiTheme.radius.md).background(colors.surface).border(1.dp, colors.line.copy(alpha = 0.7f), DesiTheme.radius.md)
            .padding(start = 6.dp, end = DesiTheme.space.s3, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        IconTile(DesiIcons.Medal, size = 32.dp, tint = colors.event.extra, container = colors.event.extra.copy(alpha = 0.14f))
        Text(label, style = DesiTheme.type.bodyStrong, color = colors.ink)
    }
}

@Composable
private fun battingCells(b: BattingStats): List<StatItem> {
    val dash = stringResource(Res.string.stats_dash)
    return listOf(
        Res.string.stats_runs to "${b.runs}",
        Res.string.stats_average to (b.average?.format2() ?: dash),
        Res.string.stats_strike_rate to b.strikeRate.format2(),
        Res.string.stats_innings to "${b.innings}",
        Res.string.stats_highest to "${b.highest}${if (b.highestNotOut) "*" else ""}",
        Res.string.stats_fifties to "${b.fifties}",
        Res.string.stats_hundreds to "${b.hundreds}",
        Res.string.stats_fours to "${b.fours}",
        Res.string.stats_sixes to "${b.sixes}",
    ).map { (label, value) -> StatItem(value, stringResource(label)) }
}

@Composable
private fun bowlingCells(b: BowlingStats): List<StatItem> {
    val dash = stringResource(Res.string.stats_dash)
    return listOf(
        Res.string.stats_wickets to "${b.wickets}",
        Res.string.stats_economy to b.economy.format2(),
        Res.string.stats_best to "${b.bestWickets}/${b.bestRuns}",
        Res.string.stats_innings to "${b.innings}",
        Res.string.stats_overs to formatOvers(b.balls),
        Res.string.stats_average to (b.average?.format2() ?: dash),
        Res.string.stats_five_wickets to "${b.fiveWickets}",
    ).map { (label, value) -> StatItem(value, stringResource(label)) }
}

/** Bars of runs per match, oldest on the left; fifties in the brand colour. */
@Composable
private fun FormChart(form: List<FormEntry>, onOpenMatch: ((String) -> Unit)?) {
    val colors = DesiTheme.colors
    val batted = form.filter { it.batted }
    val most = batted.maxOf { it.runs }.coerceAtLeast(1)
    val description = stringResource(Res.string.stats_form_description, batted.size)
    DesiCard(Modifier.fillMaxWidth()) {
        SectionHeader(pluralStringResource(Res.plurals.stats_form, batted.size, batted.size), icon = DesiIcons.Trend)
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
            // Ten slots whatever the count, so one match is a slim bar, not a block across the card.
            val slot = size.width / FORM_SLOTS
            val bar = slot * 0.6f
            batted.forEachIndexed { i, entry ->
                val height = (entry.runs.toFloat() / most) * size.height
                drawRoundRect(
                    color = if (entry.runs >= 50) colors.brand else colors.brand.copy(alpha = 0.45f),
                    topLeft = Offset((FORM_SLOTS - batted.size + i) * slot + (slot - bar) / 2, size.height - height.coerceAtLeast(2f)),
                    size = Size(bar, height.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            // Labels line up under their bars, which sit at the right (newest) end.
            repeat(FORM_SLOTS - batted.size) { Text("", Modifier.weight(1f)) }
            batted.forEach {
                Text(
                    "${it.runs}${if (it.notOut) "*" else ""}",
                    style = DesiTheme.type.caption,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).then(if (onOpenMatch == null) Modifier else Modifier.clickable { onOpenMatch(it.matchId) }.padding(vertical = 10.dp)),
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
                    leadingIcon = DesiIcons.Search,
                    keyboardOptions = KeyboardOptions.Default,
                )
            }
            if (state.results.isNotEmpty()) item { PlayerList(state.results) { viewModel.pick(it) } }
            val pair = state.stats
            val other = state.other
            if (other != null) {
                item { StatsFilters(state.filter, viewModel::setFilter) }
            }
            if (state.failed) item { ErrorBanner(stringResource(Res.string.stats_load_failed)) }
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
    LeaderboardContent(state, tournamentId != null, viewModel::update, onOpenPlayer, onBack, viewModel::retry)
}

/**
 * The leaderboard without its ViewModel: who (friends or city) and which figure on two lines, the season as a
 * pill by the heading, then the top three on a podium and everyone else in a ranked list.
 */
@Composable
fun LeaderboardContent(
    state: LeaderboardUiState,
    forTournament: Boolean,
    onUpdate: ((LeaderboardUiState) -> LeaderboardUiState) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val space = DesiTheme.space
    var pickingSeason by rememberSaveable { mutableStateOf(false) }
    val year = thisYear()
    val seasons = listOf<Int?>(null, year, year - 1, year - 2)
    val allTime = stringResource(Res.string.stats_season_all)
    if (pickingSeason) {
        PickerSheet(
            stringResource(Res.string.leaderboard_season),
            seasons.map { it?.toString() ?: allTime },
            selected = seasons.indexOf(state.season),
            onSelect = { index -> onUpdate { it.copy(season = seasons[index]) }; pickingSeason = false },
            onDismiss = { pickingSeason = false },
        )
    }
    DesiScaffold(
        title = stringResource(if (forTournament) Res.string.tournament_leaderboard else Res.string.leaderboards_title),
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s2),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (!forTournament) {
                item {
                    SegmentedControl(
                        listOf(LeaderboardScope.FRIENDS, LeaderboardScope.CITY),
                        selected = state.scope,
                        label = { stringResource(if (it == LeaderboardScope.CITY) Res.string.leaderboard_city else Res.string.leaderboard_friends) },
                        onSelect = { scope -> onUpdate { it.copy(scope = scope) } },
                    )
                }
            }
            item {
                FilterRow(
                    LeaderboardMetric.entries,
                    state.metric,
                    label = { stringResource(it.label) },
                    onSelect = { metric -> onUpdate { it.copy(metric = metric) } },
                    icon = { it.icon },
                )
            }
            item {
                Row(Modifier.padding(top = space.s2), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(stringResource(state.metric.title), Modifier.weight(1f), icon = state.metric.icon)
                    if (!forTournament) PickerPill(state.season?.toString() ?: allTime, { pickingSeason = true }, icon = DesiIcons.Calendar)
                }
            }
            val value: (LeaderboardRow) -> String = { if (state.metric == LeaderboardMetric.MVP) mvpText(it.value) else "${it.value}" }
            when {
                state.failed -> item { ErrorState(stringResource(Res.string.error_stats_title), stringResource(Res.string.error_connection_body), onRetry = onRetry, compact = true) }
                state.loading && state.rows.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(space.s8), contentAlignment = Alignment.Center) { BallSpinner() }
                }
                state.rows.isEmpty() -> item {
                    EmptyState(
                        DesiIcons.Leaderboard,
                        stringResource(Res.string.leaderboard_empty_title),
                        body = stringResource(if (state.scope == LeaderboardScope.CITY && !forTournament) Res.string.leaderboard_empty_city else Res.string.leaderboard_empty_friends),
                    )
                }
                else -> {
                    item { Podium(state.rows.take(3), value, onOpenPlayer) }
                    val rest = state.rows.drop(3)
                    if (rest.isNotEmpty()) item {
                        ListCard {
                            rest.forEachIndexed { index, row ->
                                ListRow(
                                    title = row.player.name,
                                    subtitle = pluralStringResource(Res.plurals.leaderboard_matches, row.matches, row.matches),
                                    leading = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                                            RankBadge(index + 4, size = 28.dp)
                                            PhotoAvatar(row.player.name, row.player.photoUrl, size = 40)
                                        }
                                    },
                                    trailing = { Text(value(row), style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink) },
                                    divider = index > 0,
                                    onClick = { onOpenPlayer(row.player.profileId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The top three side by side, the leader raised in the middle on the green, second and third either side. */
@Composable
private fun Podium(top: List<LeaderboardRow>, value: (LeaderboardRow) -> String, onOpenPlayer: (String) -> Unit) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    // Visual order is 2nd, 1st, 3rd; empty places keep their slot so the leader stays centred.
    val order = listOf(1, 0, 2)
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).heroBackground().padding(horizontal = space.s2, vertical = space.s5),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { place ->
            val row = top.getOrNull(place)
            Column(
                Modifier.weight(1f).clip(DesiTheme.radius.md)
                    .then(if (row != null) Modifier.clickable(role = Role.Button) { onOpenPlayer(row.player.profileId) } else Modifier)
                    .padding(vertical = space.s2, horizontal = space.s1)
                    .padding(bottom = if (place == 0) space.s4 else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space.s1),
            ) {
                if (row == null) return@Column
                val size = if (place == 0) 72 else 56
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.padding(bottom = 10.dp).clip(DesiTheme.radius.full).border(3.dp, if (place == 0) colors.event.extra else colors.onBrandStrong.copy(alpha = 0.85f), DesiTheme.radius.full).padding(3.dp)) {
                        PhotoAvatar(row.player.name, row.player.photoUrl, size = size)
                    }
                    Box(
                        Modifier.size(24.dp).clip(DesiTheme.radius.full).background(if (place == 0) colors.event.extra else colors.onBrandStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${place + 1}", style = DesiTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.brandStrong)
                    }
                }
                Text(
                    row.player.name,
                    style = DesiTheme.type.bodyStrong,
                    color = colors.onBrandStrong,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(value(row), style = if (place == 0) DesiTheme.type.scoreLg else DesiTheme.type.scoreMd, color = colors.onBrandStrong)
            }
        }
    }
}

private val LeaderboardMetric.icon: DrawableResource
    get() = when (this) {
        LeaderboardMetric.RUNS -> DesiIcons.Bat
        LeaderboardMetric.WICKETS -> DesiIcons.Ball
        LeaderboardMetric.MVP -> DesiIcons.Star
        LeaderboardMetric.SIXES -> DesiIcons.Six
        LeaderboardMetric.CATCHES -> DesiIcons.Glove
    }

/** The list heading for each figure: "Most runs". */
private val LeaderboardMetric.title: StringResource
    get() = when (this) {
        LeaderboardMetric.RUNS -> Res.string.leaderboard_title_runs
        LeaderboardMetric.WICKETS -> Res.string.leaderboard_title_wickets
        LeaderboardMetric.MVP -> Res.string.leaderboard_title_mvp
        LeaderboardMetric.SIXES -> Res.string.leaderboard_title_sixes
        LeaderboardMetric.CATCHES -> Res.string.leaderboard_title_catches
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
