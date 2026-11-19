package com.harsh.desicrics.ui.tournament
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.harsh.desicrics.designsystem.component.ActionTile
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.HeroChip
import com.harsh.desicrics.designsystem.component.HeroChips
import com.harsh.desicrics.designsystem.component.HeroIconButton
import com.harsh.desicrics.designsystem.component.HeroIdentity
import com.harsh.desicrics.designsystem.component.HeroScaffold
import com.harsh.desicrics.designsystem.component.HeroStats
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.RankBadge
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.StatItem
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import org.jetbrains.compose.resources.DrawableResource

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.KeyStats
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PointsRow
import com.harsh.desicrics.model.StatLeader
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.social.PlayerLine
import com.harsh.desicrics.ui.stats.mvpText
import com.harsh.desicrics.ui.system.rememberShareText
import com.harsh.desicrics.ui.teams.TeamRow
import com.harsh.desicrics.ui.text.epochMillisAt
import com.harsh.desicrics.ui.text.formatDayAndTime
import com.harsh.desicrics.ui.text.oversText
import com.harsh.desicrics.util.format2
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal enum class TournamentTab { FIXTURES, TABLE, TEAMS, STATS }

internal sealed interface TournamentSheet {
    data object EnterTeam : TournamentSheet
    data object AddOrganiser : TournamentSheet
    data object AddFixture : TournamentSheet
    data class Schedule(val match: Match) : TournamentSheet
}

@Composable
fun TournamentScreen(
    tournamentId: String,
    onOpenMatch: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onBack: () -> Unit,
    onLeaderboard: () -> Unit = {},
    viewModel: TournamentViewModel = koinViewModel { parametersOf(tournamentId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<TournamentSheet?>(null) }
    val share = rememberShareText()
    val shareText = state.details?.let { stringResource(Res.string.tournament_share_text, it.tournament.name, viewModel.link) }
    // Results come in while the user is away scoring a fixture.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val close = {
        sheet = null
        viewModel.clearError()
        viewModel.searchTeams("")
    }

    TournamentContent(
        state = state,
        onBack = onBack,
        onShare = { shareText?.let(share) },
        onOpenMatch = onOpenMatch,
        onOpenPlayer = onOpenPlayer,
        onSheet = { sheet = it },
        onStart = viewModel::start,
        onReview = viewModel::review,
        onLeaderboard = onLeaderboard,
        onRetry = viewModel::refresh,
    )
    when (val current = sheet) {
        null -> Unit
        TournamentSheet.EnterTeam -> DesiSheet(stringResource(Res.string.tournament_add_team), close, fullHeight = state.isOrganiser) {
            if (state.isOrganiser) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                DesiTextField(
                    value = state.teamQuery,
                    onValueChange = { viewModel.searchTeams(it.take(60)) },
                    label = stringResource(Res.string.tournament_team_search),
                    modifier = Modifier.focusRequester(focus),
                    leadingIcon = DesiIcons.Search,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                )
            }
            if (state.registrable.isEmpty() && !state.isOrganiser) EmptyState(DesiIcons.Shield, stringResource(Res.string.new_match_no_teams))
            state.registrable.forEach { team -> TeamRow(team, onClick = { viewModel.enterTeam(team.id); close() }) }
        }
        TournamentSheet.AddOrganiser -> DesiSheet(stringResource(Res.string.tournament_add_organiser), close, fullHeight = true) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            DesiTextField(
                value = state.organiserQuery,
                onValueChange = viewModel::searchOrganiser,
                label = stringResource(Res.string.team_add_search),
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions.Default,
            )
            state.organiserResults.forEach { player -> PlayerLine(player, onClick = { viewModel.addOrganiser(player.profileId); close() }) }
        }
        TournamentSheet.AddFixture -> state.details?.let { details ->
            AddFixtureSheet(details.tournament.confirmedTeams, onAdd = { a, b -> viewModel.addFixture(a, b); close() }, onDismiss = close)
        }
        is TournamentSheet.Schedule -> ScheduleSheet(current.match.scheduledAt, onSave = { at -> viewModel.schedule(current.match.id, at); close() }, onDismiss = close)
    }
}

@Composable
internal fun TournamentContent(
    state: TournamentUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onSheet: (TournamentSheet) -> Unit,
    onStart: () -> Unit,
    onReview: (teamId: String, confirm: Boolean) -> Unit,
    onLeaderboard: () -> Unit = {},
    initialTab: TournamentTab = TournamentTab.FIXTURES,
    onRetry: () -> Unit = {},
) {
    val details = state.details
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    var fixtureTeam by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    if (details == null) {
        DesiScaffold(title = stringResource(Res.string.tournaments_title), onBack = onBack) { padding ->
            if (state.loading) FullScreenLoading(Modifier.padding(padding))
            else ErrorState(
                stringResource(Res.string.error_tournament_title),
                stringResource(Res.string.error_connection_body),
                Modifier.padding(padding),
                onRetry = onRetry,
            )
        }
        return
    }
    val t = details.tournament
    HeroScaffold(
        title = t.name,
        onBack = onBack,
        actions = { HeroIconButton(DesiIcons.Share, stringResource(Res.string.tournament_share), onShare) },
        hero = {
            HeroIdentity(
                name = t.name,
                eyebrow = stringResource(Res.string.tournament_eyebrow),
                avatar = { IconTile(DesiIcons.TrophyFilled, Modifier.clip(DesiTheme.radius.full), size = 72.dp, tint = colors.brandStrong, container = colors.onBrandStrong) },
            )
            HeroChips(
                listOf(
                    HeroChip(stringResource(t.format.label), DesiIcons.Trophy),
                    HeroChip(oversText(t.oversPerInnings), DesiIcons.Clock),
                    HeroChip(t.city, DesiIcons.Location),
                ),
            )
            details.championId?.let { id ->
                HeroChips(listOf(HeroChip(stringResource(Res.string.tournament_champion, t.teams.firstOrNull { it.teamId == id }?.name.orEmpty()), DesiIcons.TrophyFilled)))
            }
            // Before the start there are no fixtures to count, so the strip would only show zeros.
            if (t.started) HeroStats(
                listOf(
                    StatItem("${t.confirmedTeams.size}", stringResource(Res.string.tournament_tab_teams)),
                    StatItem("${details.fixtures.size}", stringResource(Res.string.tournament_tab_fixtures)),
                    StatItem("${details.fixtures.count { it.status == MatchStatus.COMPLETED }}", stringResource(Res.string.tournament_stat_played)),
                ),
            )
        },
    ) {
        val organisers = t.organisers.map { it.name }.filter { it.isNotBlank() }
        if (organisers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                Icon(painterResource(DesiIcons.Whistle), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
                Text(stringResource(Res.string.tournament_organisers, organisers.joinToString()), style = DesiTheme.type.caption, color = colors.muted)
            }
        }
        if (state.actionFailed) ErrorBanner(stringResource(Res.string.tournament_action_failed))
        val tabs = TournamentTab.entries.filter { it != TournamentTab.TABLE || details.tables.isNotEmpty() }
        SegmentedControl(tabs, selected = tab.takeIf { it in tabs } ?: TournamentTab.FIXTURES, label = { stringResource(it.label) }, onSelect = { fixtureTeam = null; tab = it })
        Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
            when (tab.takeIf { it in tabs } ?: TournamentTab.FIXTURES) {
                TournamentTab.FIXTURES -> {
                    fixtureTeam?.let { id ->
                        SectionHeader(
                            t.teams.firstOrNull { it.teamId == id }?.name.orEmpty(),
                            icon = DesiIcons.Shield,
                            action = stringResource(Res.string.back_to_table),
                            onAction = { fixtureTeam = null; tab = TournamentTab.TABLE },
                        )
                    }
                    val filtered = fixtureTeam?.let { id -> details.copy(fixtures = details.fixtures.filter { it.teamA.teamId == id || it.teamB.teamId == id }) } ?: details
                    Fixtures(filtered, state.isOrganiser, onOpenMatch, onSchedule = { onSheet(TournamentSheet.Schedule(it)) }, onAdd = { onSheet(TournamentSheet.AddFixture) })
                }
                TournamentTab.TABLE -> details.tables.forEachIndexed { group, table ->
                    PointsTable(table, if (details.tables.size > 1) group else null, t.advance) { fixtureTeam = it; tab = TournamentTab.FIXTURES }
                }
                TournamentTab.TEAMS -> Teams(state, onSheet, onStart, onReview)
                TournamentTab.STATS -> {
                    details.playerOfTournament?.let { best -> PlayerOfTournament(best, onOpenPlayer) }
                    Stats(details.keyStats, onOpenPlayer)
                    ActionTile(DesiIcons.Leaderboard, stringResource(Res.string.tournament_leaderboard), onLeaderboard, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The tournament's best player by MVP points, as a highlighted card. */
@Composable
private fun PlayerOfTournament(best: StatLeader, onOpenPlayer: (String) -> Unit) {
    val colors = DesiTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.md).background(colors.brandSoft)
            .then(best.profileId?.let { id -> Modifier.clickable(role = Role.Button) { onOpenPlayer(id) } } ?: Modifier)
            .padding(DesiTheme.space.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        IconTile(DesiIcons.Star, size = 48.dp, tint = colors.onBrand, container = colors.brand)
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.tournament_player_of_tournament_title).uppercase(), style = DesiTheme.type.label, color = colors.brand)
            Text(best.name, style = DesiTheme.type.titleMd, color = colors.ink)
            if (best.team.isNotBlank()) Text(best.team, style = DesiTheme.type.caption, color = colors.muted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(mvpText(best.value), style = DesiTheme.type.scoreMd, color = colors.brand)
            Text(stringResource(Res.string.stats_mvp_points), style = DesiTheme.type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun ColumnScope.Fixtures(
    details: TournamentDetails,
    isOrganiser: Boolean,
    onOpenMatch: (String) -> Unit,
    onSchedule: (Match) -> Unit,
    onAdd: () -> Unit,
) {
    val t = details.tournament
    if (!t.started) {
        EmptyState(DesiIcons.Calendar, stringResource(Res.string.tournament_not_started))
        return
    }
    if (isOrganiser && t.format == TournamentFormat.CUSTOM) {
        TonalButton(stringResource(Res.string.tournament_add_fixture), onClick = onAdd, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Add)
    }
    if (details.fixtures.isEmpty()) EmptyState(DesiIcons.Calendar, stringResource(Res.string.tournament_no_fixtures))
    // Latest round first: that's what's being played.
    for ((_, round) in details.fixtures.groupBy { it.fixture!!.round }.toSortedMap(compareByDescending { it })) {
        SectionHeader(stageText(round.first().fixture!!), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.Calendar)
        round.forEach { match ->
            HomeMatchCard(match, isMine = false, onClick = { onOpenMatch(match.id) })
            if (match.status == MatchStatus.SCHEDULED) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                    Icon(painterResource(DesiIcons.Clock), null, tint = DesiTheme.colors.muted, modifier = Modifier.size(16.dp))
                    Text(
                        match.scheduledAt?.let { formatDayAndTime(it) } ?: stringResource(Res.string.fixture_not_scheduled),
                        style = DesiTheme.type.caption,
                        color = DesiTheme.colors.muted,
                        modifier = Modifier.weight(1f),
                    )
                    if (isOrganiser) {
                        TextButton(onClick = { onSchedule(match) }) {
                            Text(stringResource(Res.string.fixture_reschedule), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                    }
                }
            }
        }
    }
}

/** A league table: rank, team and figures, with the places that go through tinted. Tap a team for its fixtures. */
@Composable
private fun PointsTable(table: List<PointsRow>, group: Int?, advance: Int, select: (String) -> Unit) {
    val colors = DesiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        if (group != null) SectionHeader(stringResource(Res.string.tournament_group, groupName(group)), icon = DesiIcons.Leaderboard)
        if (table.isEmpty()) {
            EmptyState(DesiIcons.Leaderboard, stringResource(Res.string.tournament_no_fixtures))
            return@Column
        }
        ListCard {
            TableRow(
                null,
                listOf(Res.string.table_team, Res.string.table_played, Res.string.table_won, Res.string.table_lost, Res.string.table_points, Res.string.table_nrr)
                    .map { stringResource(it) },
            )
            table.forEachIndexed { index, row ->
                val nrr = row.netRunRate
                HorizontalDivider(color = colors.line.copy(alpha = 0.7f))
                TableRow(
                    index + 1,
                    listOf(row.name, "${row.played}", "${row.won}", "${row.lost}", "${row.points}", (if (nrr >= 0) "+" else "-") + kotlin.math.abs(nrr).format2()),
                    qualifies = index < advance,
                ) { select(row.teamId) }
            }
        }
        if (advance > 0 && table.size > advance) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                Box(Modifier.size(10.dp).clip(DesiTheme.radius.full).background(colors.brand))
                Text(stringResource(Res.string.table_qualify), style = DesiTheme.type.caption, color = colors.muted)
            }
        }
    }
}

/** A table line. [rank] null is the header. */
@Composable
private fun TableRow(rank: Int?, cells: List<String>, qualifies: Boolean = false, select: (() -> Unit)? = null) {
    val colors = DesiTheme.colors
    val header = rank == null
    val style = if (header) DesiTheme.type.label else DesiTheme.type.data
    val color = if (header) colors.muted else colors.ink
    Row(
        Modifier.fillMaxWidth()
            .background(if (qualifies) colors.brandSoft.copy(alpha = 0.5f) else Color.Transparent)
            .then(if (select == null) Modifier else Modifier.clickable(onClick = select))
            .padding(horizontal = DesiTheme.space.s4, vertical = if (header) DesiTheme.space.s3 else DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp)) { if (rank != null) RankBadge(rank, size = 26.dp) }
        Text(cells.first(), style = if (header) style else DesiTheme.type.bodyStrong, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2.4f))
        cells.drop(1).forEachIndexed { i, cell ->
            val points = i == cells.size - 3
            Text(
                cell,
                style = if (points && !header) style.copy(fontWeight = FontWeight.Bold) else style,
                color = if (points && !header) colors.brand else color,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.weight(if (i == cells.size - 2) 1.4f else 0.8f),
            )
        }
    }
}

@Composable
private fun ColumnScope.Teams(
    state: TournamentUiState,
    onSheet: (TournamentSheet) -> Unit,
    onStart: () -> Unit,
    onReview: (String, Boolean) -> Unit,
) {
    val t = state.details?.tournament ?: return
    // Organisers can always search for a team to add; others need a team of their own to register.
    val canEnter = !t.started && !t.full && (state.isOrganiser || (t.registrationOpen && state.registrable.isNotEmpty()))
    if (canEnter) {
        val action = if (state.isOrganiser) Res.string.tournament_add_team else Res.string.tournament_register_team
        TonalButton(stringResource(action), onClick = { onSheet(TournamentSheet.EnterTeam) }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Add)
    }
    if (t.teams.isEmpty()) EmptyState(DesiIcons.Shield, stringResource(Res.string.tournament_not_started))
    else ListCard {
        t.teams.forEachIndexed { index, team -> EntryRow(team, state.isOrganiser && !t.started, onReview, divider = index > 0) }
    }
    if (state.isOrganiser) {
        GhostButton(stringResource(Res.string.tournament_add_organiser), onClick = { onSheet(TournamentSheet.AddOrganiser) }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Whistle)
        if (!t.started) {
            PrimaryButton(
                stringResource(Res.string.tournament_start),
                onClick = onStart,
                enabled = t.canStart,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = DesiIcons.Play,
            )
            val hint = when {
                t.canStart -> stringResource(Res.string.tournament_start_hint)
                t.format == TournamentFormat.BEST_OF_THREE && t.teams.size > 2 -> stringResource(Res.string.tournament_best_of_three_too_many)
                else -> stringResource(Res.string.tournament_needs_teams, t.teamsNeeded)
            }
            Text(hint, style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
    }
}

@Composable
private fun EntryRow(team: TournamentTeam, canReview: Boolean, onReview: (String, Boolean) -> Unit, divider: Boolean) {
    val pending = team.status == EntryStatus.PENDING
    ListRow(
        title = team.name,
        subtitle = team.group?.let { stringResource(Res.string.tournament_group, groupName(it)) },
        leading = { Avatar(team.name, size = 40) },
        trailing = {
            if (canReview && pending) {
                TextButton(onClick = { onReview(team.teamId, false) }) {
                    Text(stringResource(Res.string.tournament_decline), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                }
                TextButton(onClick = { onReview(team.teamId, true) }) {
                    Text(stringResource(Res.string.tournament_confirm), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                }
            } else if (canReview) {
                // Before the start, organisers can take out a team they confirmed.
                TextButton(onClick = { onReview(team.teamId, false) }) {
                    Text(stringResource(Res.string.tournament_remove), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                }
            } else if (pending) {
                Tag(stringResource(Res.string.tournament_pending_short), tone = TagTone.WARN, icon = DesiIcons.Clock)
            }
        },
        divider = divider,
    )
}

@Composable
private fun ColumnScope.Stats(stats: KeyStats, onOpenPlayer: (String) -> Unit) {
    val lists = listOf(Res.string.tournament_most_runs to stats.mostRuns, Res.string.tournament_most_wickets to stats.mostWickets, Res.string.tournament_most_sixes to stats.mostSixes)
    if (lists.all { it.second.isEmpty() }) {
        EmptyState(DesiIcons.Leaderboard, stringResource(Res.string.tournament_no_stats))
        return
    }
    val icons = listOf(DesiIcons.Bat, DesiIcons.Ball, DesiIcons.Six)
    lists.forEachIndexed { i, (title, leaders) ->
        if (leaders.isNotEmpty()) LeaderCard(title, leaders, onOpenPlayer, icons[i])
    }
}

@Composable
internal fun LeaderCard(title: StringResource, leaders: List<StatLeader>, onOpenPlayer: (String) -> Unit, icon: DrawableResource = DesiIcons.Leaderboard) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(title), icon = icon)
        ListCard {
            leaders.forEachIndexed { index, leader ->
                ListRow(
                    title = leader.name,
                    subtitle = leader.team.ifBlank { null },
                    leading = { RankBadge(index + 1) },
                    trailing = { Text("${leader.value}", style = DesiTheme.type.scoreMd, color = if (index == 0) DesiTheme.colors.brand else DesiTheme.colors.ink) },
                    divider = index > 0,
                    onClick = leader.profileId?.let { id -> { onOpenPlayer(id) } },
                )
            }
        }
    }
}

/** Two of the tournament's teams for a fixture the organiser adds by hand. */
@Composable
private fun AddFixtureSheet(teams: List<TournamentTeam>, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<TournamentTeam?>(null) }
    var second by remember { mutableStateOf<TournamentTeam?>(null) }
    DesiSheet(stringResource(Res.string.tournament_add_fixture), onDismiss, stringResource(Res.string.tournament_pick_two)) {
        ChoiceChips(teams, selected = first, label = { it.name }, onSelect = { first = it })
        ChoiceChips(teams.filter { it != first }, selected = second, label = { it.name }, onSelect = { second = it })
        PrimaryButton(
            stringResource(Res.string.tournament_add_fixture),
            onClick = { onAdd(first!!.teamId, second!!.teamId) },
            enabled = first != null && second != null && first != second,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A day and a time for a fixture, in the phone's time zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleSheet(scheduledAt: Long?, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    val date = rememberDatePickerState(initialSelectedDateMillis = scheduledAt)
    val time = rememberTimePickerState(initialHour = 9, initialMinute = 0)
    DesiSheet(stringResource(Res.string.fixture_reschedule), onDismiss) {
        DatePicker(date, title = null, headline = null, showModeToggle = false)
        TimePicker(time)
        PrimaryButton(
            stringResource(Res.string.teams_save_action),
            onClick = { date.selectedDateMillis?.let { onSave(epochMillisAt(it, time.hour, time.minute)) } },
            enabled = date.selectedDateMillis != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val TournamentTab.label: StringResource
    get() = when (this) {
        TournamentTab.FIXTURES -> Res.string.tournament_tab_fixtures
        TournamentTab.TABLE -> Res.string.tournament_tab_table
        TournamentTab.TEAMS -> Res.string.tournament_tab_teams
        TournamentTab.STATS -> Res.string.tournament_tab_stats
    }

@Preview
@Composable
private fun TournamentPreview() = DesiTheme(darkTheme = false) {
    val teams = (1..4).map { TournamentTeam("t$it", "Team $it", it) }
    TournamentContent(
        TournamentUiState(
            loading = false,
            details = TournamentDetails(
                Tournament("x", "Andheri Premier League", "Mumbai", TournamentFormat.ROUND_ROBIN, 8, createdBy = "u", teams = teams, started = true),
                tables = listOf(teams.map { PointsRow(it.teamId, it.name, played = 2, won = 1, lost = 1, runsFor = 120, ballsFaced = 96, runsAgainst = 110, ballsBowled = 96) }),
            ),
        ),
        {}, {}, {}, {}, {}, {}, { _, _ -> },
    )
}
