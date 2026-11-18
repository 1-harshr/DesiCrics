package com.harsh.desicrics.ui.tournament

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
import androidx.compose.runtime.remember
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

private enum class TournamentTab { FIXTURES, TABLE, TEAMS, STATS }

private sealed interface TournamentSheet {
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
    )
    when (val current = sheet) {
        null -> Unit
        TournamentSheet.EnterTeam -> DesiSheet(stringResource(Res.string.tournament_add_team), close) {
            if (state.registrable.isEmpty()) Text(stringResource(Res.string.new_match_no_teams), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
            state.registrable.forEach { team -> TeamRow(team, onClick = { viewModel.enterTeam(team.id); close() }) }
        }
        TournamentSheet.AddOrganiser -> DesiSheet(stringResource(Res.string.tournament_add_organiser), close) {
            DesiTextField(
                value = state.organiserQuery,
                onValueChange = viewModel::searchOrganiser,
                label = stringResource(Res.string.team_add_search),
                keyboardOptions = KeyboardOptions.Default,
            )
            state.organiserResults.forEach { player -> PlayerLine(player, onClick = { viewModel.addOrganiser(player.profileId); close() }) }
        }
        TournamentSheet.AddFixture -> state.details?.let { details ->
            AddFixtureSheet(details.tournament.confirmedTeams, onAdd = { a, b -> viewModel.addFixture(a, b); close() }, onDismiss = close)
        }
        is TournamentSheet.Schedule -> ScheduleSheet(current.match, onSave = { at -> viewModel.schedule(current.match.id, at); close() }, onDismiss = close)
    }
}

@Composable
private fun TournamentContent(
    state: TournamentUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenMatch: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onSheet: (TournamentSheet) -> Unit,
    onStart: () -> Unit,
    onReview: (teamId: String, confirm: Boolean) -> Unit,
    onLeaderboard: () -> Unit = {},
) {
    val details = state.details
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    var tab by rememberSaveable { mutableStateOf(TournamentTab.FIXTURES) }
    DesiScaffold(
        title = details?.tournament?.name.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = onShare) { Icon(painterResource(DesiIcons.Share), stringResource(Res.string.tournament_share)) }
        },
    ) { padding ->
        when {
            details == null && state.loading -> FullScreenLoading(Modifier.padding(padding))
            details == null -> Text(
                stringResource(Res.string.tournaments_load_failed),
                style = DesiTheme.type.body,
                color = colors.event.wicket,
                modifier = Modifier.padding(padding).padding(space.screen),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = space.screen, vertical = space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                val t = details.tournament
                Text(
                    listOf(stringResource(t.format.label), oversText(t.oversPerInnings), t.city)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = DesiTheme.type.body,
                    color = colors.muted,
                )
                val organisers = t.organisers.map { it.name }.filter { it.isNotBlank() }
                if (organisers.isNotEmpty()) {
                    Text(stringResource(Res.string.tournament_organisers, organisers.joinToString()), style = DesiTheme.type.caption, color = colors.muted)
                }
                details.championId?.let { id ->
                    Text(
                        stringResource(Res.string.tournament_champion, t.teams.firstOrNull { it.teamId == id }?.name.orEmpty()),
                        style = DesiTheme.type.titleMd,
                        color = colors.brand,
                    )
                }
                if (state.actionFailed) Text(stringResource(Res.string.tournament_action_failed), style = DesiTheme.type.body, color = colors.event.wicket)
                val tabs = TournamentTab.entries.filter { it != TournamentTab.TABLE || details.tables.isNotEmpty() }
                SegmentedControl(tabs, selected = tab.takeIf { it in tabs } ?: TournamentTab.FIXTURES, label = { stringResource(it.label) }, onSelect = { tab = it })
                when (tab.takeIf { it in tabs } ?: TournamentTab.FIXTURES) {
                    TournamentTab.FIXTURES -> Fixtures(details, state.isOrganiser, onOpenMatch, onSchedule = { onSheet(TournamentSheet.Schedule(it)) }, onAdd = { onSheet(TournamentSheet.AddFixture) })
                    TournamentTab.TABLE -> details.tables.forEachIndexed { group, table -> PointsTable(table, if (details.tables.size > 1) group else null) }
                    TournamentTab.TEAMS -> Teams(state, onSheet, onStart, onReview)
                    TournamentTab.STATS -> {
                        details.playerOfTournament?.let { best ->
                            Text(
                                stringResource(Res.string.tournament_player_of_tournament, best.name, mvpText(best.value)),
                                style = DesiTheme.type.bodyStrong,
                                color = colors.brand,
                            )
                        }
                        Stats(details.keyStats, onOpenPlayer)
                        GhostButton(stringResource(Res.string.tournament_leaderboard), onClick = onLeaderboard, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
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
        Text(stringResource(Res.string.tournament_not_started), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        return
    }
    if (isOrganiser && t.format == TournamentFormat.CUSTOM) {
        TonalButton(stringResource(Res.string.tournament_add_fixture), onClick = onAdd, modifier = Modifier.fillMaxWidth())
    }
    if (details.fixtures.isEmpty()) Text(stringResource(Res.string.tournament_no_fixtures), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
    // Latest round first: that's what's being played.
    for ((_, round) in details.fixtures.groupBy { it.fixture!!.round }.toSortedMap(compareByDescending { it })) {
        SectionLabel(stageText(round.first().fixture!!), Modifier.padding(top = DesiTheme.space.s2))
        round.forEach { match ->
            HomeMatchCard(match, isMine = false, onClick = { onOpenMatch(match.id) })
            if (match.status == MatchStatus.SCHEDULED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun PointsTable(table: List<PointsRow>, group: Int?) {
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth()) {
        if (group != null) SectionLabel(stringResource(Res.string.tournament_group, groupName(group)))
        TableRow(
            listOf(Res.string.table_team, Res.string.table_played, Res.string.table_won, Res.string.table_lost, Res.string.table_points, Res.string.table_nrr)
                .map { stringResource(it) },
            header = true,
        )
        table.forEach { row ->
            val nrr = row.netRunRate
            TableRow(listOf(row.name, "${row.played}", "${row.won}", "${row.lost}", "${row.points}", (if (nrr >= 0) "+" else "-") + kotlin.math.abs(nrr).format2()))
        }
        if (table.isEmpty()) Text(stringResource(Res.string.tournament_no_fixtures), style = DesiTheme.type.body, color = colors.muted)
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    val style = if (header) DesiTheme.type.label else DesiTheme.type.data
    val color = if (header) DesiTheme.colors.muted else DesiTheme.colors.ink
    Row {
        Text(cells.first(), style = if (header) style else DesiTheme.type.bodyStrong, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2.4f))
        cells.drop(1).forEachIndexed { i, cell ->
            Text(cell, style = style, color = color, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(if (i == cells.size - 2) 1.4f else 0.8f))
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
    val canEnter = !t.started && (state.isOrganiser || t.registrationOpen) && state.registrable.isNotEmpty()
    if (canEnter) {
        val action = if (state.isOrganiser) Res.string.tournament_add_team else Res.string.tournament_register_team
        TonalButton(stringResource(action), onClick = { onSheet(TournamentSheet.EnterTeam) }, modifier = Modifier.fillMaxWidth())
    }
    DesiCard(Modifier.fillMaxWidth()) {
        t.teams.forEach { team -> EntryRow(team, state.isOrganiser && !t.started, onReview) }
        if (t.teams.isEmpty()) Text(stringResource(Res.string.tournament_not_started), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
    }
    if (state.isOrganiser) {
        GhostButton(stringResource(Res.string.tournament_add_organiser), onClick = { onSheet(TournamentSheet.AddOrganiser) }, modifier = Modifier.fillMaxWidth())
        if (!t.started) {
            PrimaryButton(stringResource(Res.string.tournament_start), onClick = onStart, loading = state.saving, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.tournament_start_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
    }
}

@Composable
private fun EntryRow(team: TournamentTeam, canReview: Boolean, onReview: (String, Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = DesiTheme.space.s2)) {
            Text(team.name, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
            val note = listOfNotNull(
                team.group?.let { stringResource(Res.string.tournament_group, groupName(it)) },
                stringResource(Res.string.tournament_pending).takeIf { team.status == EntryStatus.PENDING },
            )
            if (note.isNotEmpty()) Text(note.joinToString(" · "), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
        if (canReview && team.status == EntryStatus.PENDING) {
            TextButton(onClick = { onReview(team.teamId, true) }) {
                Text(stringResource(Res.string.tournament_confirm), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
            }
            TextButton(onClick = { onReview(team.teamId, false) }) {
                Text(stringResource(Res.string.tournament_decline), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun ColumnScope.Stats(stats: KeyStats, onOpenPlayer: (String) -> Unit) {
    val lists = listOf(Res.string.tournament_most_runs to stats.mostRuns, Res.string.tournament_most_wickets to stats.mostWickets, Res.string.tournament_most_sixes to stats.mostSixes)
    if (lists.all { it.second.isEmpty() }) {
        Text(stringResource(Res.string.tournament_no_stats), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        return
    }
    for ((title, leaders) in lists) {
        if (leaders.isEmpty()) continue
        LeaderCard(title, leaders, onOpenPlayer)
    }
}

@Composable
internal fun LeaderCard(title: StringResource, leaders: List<StatLeader>, onOpenPlayer: (String) -> Unit) {
    DesiCard(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(title))
        leaders.forEach { leader ->
            Row(
                Modifier.fillMaxWidth().then(
                    leader.profileId?.let { id -> Modifier.clickable(role = Role.Button) { onOpenPlayer(id) } } ?: Modifier,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(leader.name, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
                    Text(leader.team, style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
                }
                Text("${leader.value}", style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
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
private fun ScheduleSheet(match: Match, onSave: (Long?) -> Unit, onDismiss: () -> Unit) {
    val date = rememberDatePickerState(initialSelectedDateMillis = match.scheduledAt)
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
