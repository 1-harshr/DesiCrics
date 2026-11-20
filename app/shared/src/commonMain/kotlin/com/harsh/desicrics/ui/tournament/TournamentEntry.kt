package com.harsh.desicrics.ui.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.social.PlayerLine
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal val EntryPolicy.label: StringResource
    get() = when (this) {
        EntryPolicy.ORGANISERS -> Res.string.entry_organisers
        EntryPolicy.APPROVAL -> Res.string.entry_approval
        EntryPolicy.OPEN -> Res.string.entry_open
    }

private fun EntryPolicy.teamHint(): StringResource = when (this) {
    EntryPolicy.ORGANISERS -> Res.string.entry_teams_organisers_hint
    EntryPolicy.APPROVAL -> Res.string.entry_teams_approval_hint
    EntryPolicy.OPEN -> Res.string.entry_teams_open_hint
}

private fun EntryPolicy.playerHint(): StringResource = when (this) {
    EntryPolicy.ORGANISERS -> Res.string.entry_players_organisers_hint
    EntryPolicy.APPROVAL -> Res.string.entry_players_approval_hint
    EntryPolicy.OPEN -> Res.string.entry_players_open_hint
}

/** Who can enter, for teams and for players, each with a line saying what the choice means. */
@Composable
internal fun EntrySettings(
    teamEntry: EntryPolicy,
    playerEntry: EntryPolicy,
    onChange: (teamEntry: EntryPolicy, playerEntry: EntryPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
        EntryChoice(stringResource(Res.string.entry_teams), teamEntry, teamEntry.teamHint()) { onChange(it, playerEntry) }
        EntryChoice(stringResource(Res.string.entry_players), playerEntry, playerEntry.playerHint()) { onChange(teamEntry, it) }
    }
}

@Composable
private fun EntryChoice(title: String, selected: EntryPolicy, hint: StringResource, onSelect: (EntryPolicy) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Text(title, style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
        SegmentedControl(EntryPolicy.entries, selected = selected, label = { stringResource(it.label) }, onSelect = onSelect)
        Text(stringResource(hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

/** The organisers' sheet for changing who can enter, until the tournament starts. */
@Composable
internal fun EntrySettingsSheet(tournament: Tournament, onSave: (EntryPolicy, EntryPolicy) -> Unit, onDismiss: () -> Unit) {
    var teams by remember { mutableStateOf(tournament.teamEntry) }
    var players by remember { mutableStateOf(tournament.playerEntry) }
    DesiSheet(stringResource(Res.string.entry_title), onDismiss) {
        EntrySettings(teams, players, onChange = { t, p -> teams = t; players = p })
        PrimaryButton(stringResource(Res.string.teams_save_action), onClick = { onSave(teams, players) }, modifier = Modifier.fillMaxWidth())
    }
}

/** "Teams: On approval · Players: Open", for the organisers' settings row. */
@Composable
internal fun entrySummary(t: Tournament): String =
    stringResource(Res.string.entry_summary, stringResource(t.teamEntry.label), stringResource(t.playerEntry.label))

/**
 * Near the top of the tournament for anyone who isn't organising it: a way in while entries are open, and
 * afterwards where the caller stands as a player.
 */
@Composable
internal fun EntryCard(state: TournamentUiState, onEnterTeam: () -> Unit, onJoin: () -> Unit) {
    val t = state.details?.tournament ?: return
    val me = state.myEntry
    if (state.isOrganiser || (me == null && !state.canEnterTeam && !state.canJoinAsPlayer)) return
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth(), padding = DesiTheme.space.s4) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            IconTile(DesiIcons.PersonAdd)
            Column(Modifier.weight(1f)) {
                val team = me?.teamId?.let { id -> t.teams.firstOrNull { it.teamId == id }?.name }
                val (title, line) = when {
                    me == null -> Res.string.entry_card_open to stringResource(
                        when {
                            state.canEnterTeam && state.canJoinAsPlayer -> Res.string.entry_card_open_both
                            state.canEnterTeam -> Res.string.entry_card_open_teams
                            else -> Res.string.entry_card_open_players
                        },
                    )
                    me.status == EntryStatus.PENDING -> Res.string.entry_card_asked to stringResource(Res.string.entry_card_asked_hint)
                    team != null -> Res.string.entry_card_in to stringResource(Res.string.entry_card_in_squad, team)
                    else -> Res.string.entry_card_in to stringResource(Res.string.entry_card_in_pool)
                }
                Text(stringResource(title), style = DesiTheme.type.bodyStrong, color = colors.ink)
                Text(line, style = DesiTheme.type.caption, color = colors.muted)
            }
        }
        if (state.canEnterTeam) {
            TonalButton(stringResource(Res.string.tournament_register_team), onClick = onEnterTeam, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Shield)
        }
        if (state.canJoinAsPlayer) {
            PrimaryButton(stringResource(Res.string.entry_join_player), onClick = onJoin, loading = state.saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd)
        }
    }
}

/**
 * Everyone in the tournament as a player: those waiting for an organiser, the pool, and each team's squad.
 * Organisers confirm, place and remove; a team's admins add their members and drop players until the start.
 */
@Composable
internal fun ColumnScope.Players(
    state: TournamentUiState,
    onSheet: (TournamentSheet) -> Unit,
    onConfirm: (profileId: String) -> Unit,
    onRemove: (profileId: String) -> Unit,
    onOpenPlayer: (String) -> Unit,
) {
    val t = state.details?.tournament ?: return
    val organiser = state.isOrganiser
    if (organiser) {
        TonalButton(stringResource(Res.string.entry_add_player), onClick = { onSheet(TournamentSheet.AddPlayer(null)) }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd)
    }
    if (t.players.isEmpty()) {
        EmptyState(DesiIcons.Teams, stringResource(if (t.takesPlayers) Res.string.entry_no_players_open else Res.string.entry_no_players))
    }
    val waiting = t.players.filter { it.status == EntryStatus.PENDING }
    if (waiting.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.entry_waiting, waiting.size), icon = DesiIcons.Clock)
        ListCard {
            waiting.forEachIndexed { i, entry ->
                PlayerEntryRow(entry, i > 0, onOpenPlayer) {
                    if (organiser) {
                        TextButton(onClick = { onRemove(entry.player.profileId) }) {
                            Text(stringResource(Res.string.tournament_decline), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                        }
                        TextButton(onClick = { onConfirm(entry.player.profileId) }) {
                            Text(stringResource(Res.string.tournament_confirm), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                    } else {
                        Tag(stringResource(Res.string.tournament_pending_short), tone = TagTone.WARN, icon = DesiIcons.Clock)
                    }
                }
            }
        }
    }
    val pool = t.pool.filter { it.status == EntryStatus.CONFIRMED }
    if (pool.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.entry_pool, pool.size), icon = DesiIcons.Person)
        Text(stringResource(Res.string.entry_pool_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        ListCard {
            pool.forEachIndexed { i, entry ->
                PlayerEntryRow(entry, i > 0, onOpenPlayer) {
                    if (organiser) {
                        TextButton(onClick = { onSheet(TournamentSheet.PlacePlayer(entry)) }) {
                            Text(stringResource(Res.string.entry_pick_team), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                    }
                }
            }
        }
    }
    for (team in t.confirmedTeams) {
        val squad = t.squad(team.teamId)
        val canEdit = organiser || (state.runs(team.teamId) && !t.started)
        SectionHeader(
            stringResource(Res.string.entry_squad, team.name, squad.size),
            icon = DesiIcons.Shield,
            action = if (canEdit) stringResource(Res.string.entry_squad_add) else null,
            onAction = if (canEdit) ({ onSheet(TournamentSheet.AddPlayer(team.teamId)) }) else null,
        )
        // Teams entered before squads existed play from their whole team.
        if (squad.isEmpty()) {
            Text(stringResource(Res.string.entry_squad_empty), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
            continue
        }
        ListCard {
            squad.forEachIndexed { i, entry ->
                PlayerEntryRow(entry, i > 0, onOpenPlayer) {
                    if (organiser) {
                        TextButton(onClick = { onSheet(TournamentSheet.PlacePlayer(entry)) }) {
                            Text(stringResource(Res.string.entry_move), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                    }
                    if (canEdit) {
                        TextButton(onClick = { onRemove(entry.player.profileId) }) {
                            Text(stringResource(Res.string.tournament_remove), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                        }
                    }
                }
            }
        }
    }
    if (t.started && organiser) Text(stringResource(Res.string.entry_squads_locked), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
}

@Composable
private fun PlayerEntryRow(entry: TournamentPlayer, divider: Boolean, onOpenPlayer: (String) -> Unit, trailing: @Composable () -> Unit) {
    val player = entry.player
    ListRow(
        title = player.name,
        subtitle = player.role?.let { stringResource(it.label) },
        leading = { PhotoAvatar(player.name, player.photoUrl, size = 40) },
        trailing = { trailing() },
        divider = divider,
        onClick = { onOpenPlayer(player.profileId) },
    )
}

/** An organiser picks the team a player goes to, or sends them back to the pool. */
@Composable
internal fun PlacePlayerSheet(tournament: Tournament, entry: TournamentPlayer, onPlace: (teamId: String?) -> Unit, onDismiss: () -> Unit) {
    DesiSheet(stringResource(Res.string.entry_place_title, entry.player.name), onDismiss) {
        ListCard {
            tournament.confirmedTeams.forEachIndexed { i, team ->
                val count = tournament.squad(team.teamId).size
                ListRow(
                    title = team.name,
                    subtitle = pluralStringResource(Res.plurals.new_match_player_count, count, count),
                    leading = { Avatar(team.name, size = 40) },
                    trailing = if (team.teamId == entry.teamId) ({ Tag(stringResource(Res.string.entry_current), tone = TagTone.BRAND) }) else null,
                    divider = i > 0,
                    onClick = { onPlace(team.teamId) },
                )
            }
        }
        if (entry.teamId != null) {
            TonalButton(stringResource(Res.string.entry_back_to_pool), onClick = { onPlace(null) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Adds a player: to the pool ([teamId] null, organisers), or to a team's squad. A team's admins pick from its
 * members who aren't in yet; organisers can also search for anyone.
 */
@Composable
internal fun AddPlayerSheet(
    state: TournamentUiState,
    teamId: String?,
    onSearch: (String) -> Unit,
    onAdd: (profileId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = state.details?.tournament ?: return
    val team = teamId?.let { id -> t.teams.firstOrNull { it.teamId == id } }
    val entered = t.players.map { it.player.profileId }.toSet()
    DesiSheet(team?.let { stringResource(Res.string.entry_add_to_squad, it.name) } ?: stringResource(Res.string.entry_add_player), onDismiss, fullHeight = state.isOrganiser) {
        val members = state.squadSource?.takeIf { it.id == teamId }?.members.orEmpty().map { it.player }.filter { it.profileId !in entered }
        if (members.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.entry_from_team), icon = DesiIcons.Shield)
            members.forEach { player -> PlayerLine(player, onClick = { onAdd(player.profileId) }) }
        } else if (!state.isOrganiser) {
            EmptyState(DesiIcons.Teams, stringResource(Res.string.entry_all_members_in))
        }
        if (state.isOrganiser) {
            DesiTextField(
                value = state.peopleQuery,
                onValueChange = onSearch,
                label = stringResource(Res.string.team_add_search),
                leadingIcon = DesiIcons.Search,
                keyboardOptions = KeyboardOptions.Default,
            )
            state.peopleResults.filter { it.profileId !in entered }.forEach { player -> PlayerLine(player, onClick = { onAdd(player.profileId) }) }
        }
    }
}

