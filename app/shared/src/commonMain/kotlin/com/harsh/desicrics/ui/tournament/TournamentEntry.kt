package com.harsh.desicrics.ui.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
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
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.api.PickupTeamsRequest
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.PoolMode
import com.harsh.desicrics.model.SquadChange
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.social.PlayerLine
import com.harsh.desicrics.ui.text.dayLabel
import com.harsh.desicrics.ui.text.epochMillisAt
import com.harsh.desicrics.ui.text.formatDayAndTime
import com.harsh.desicrics.ui.text.label
import com.harsh.desicrics.ui.text.pickerMoment
import com.harsh.desicrics.util.nowMillis
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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

/**
 * The organisers' sheet for who can enter, how many, and until when, until the tournament starts. Picking
 * a closing day swaps the sheet to a calendar; entry closes at the end of that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySettingsSheet(tournament: Tournament, onSave: (EntrySettingsRequest) -> Unit, onDismiss: () -> Unit) {
    var teams by remember { mutableStateOf(tournament.teamEntry) }
    var players by remember { mutableStateOf(tournament.playerEntry) }
    var maxTeams by remember { mutableStateOf(tournament.maxTeams?.toString().orEmpty()) }
    var maxPlayers by remember { mutableStateOf(tournament.maxPlayers?.toString().orEmpty()) }
    var closesAt by remember { mutableStateOf(tournament.entryClosesAt) }
    var poolMode by remember { mutableStateOf(tournament.poolMode) }
    var picking by remember { mutableStateOf(false) }
    DesiSheet(stringResource(Res.string.entry_title), onDismiss) {
        if (picking) {
            val date = rememberDatePickerState(initialSelectedDateMillis = closesAt?.let { pickerMoment(it).dayUtcMillis })
            DatePicker(date, title = null, headline = null, showModeToggle = false)
            PrimaryButton(
                stringResource(Res.string.teams_save_action),
                onClick = {
                    closesAt = date.selectedDateMillis?.let { epochMillisAt(it, 23, 59) }
                    picking = false
                },
                enabled = date.selectedDateMillis != null,
                modifier = Modifier.fillMaxWidth(),
            )
            return@DesiSheet
        }
        EntrySettings(teams, players, onChange = { t, p -> teams = t; players = p })
        Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            Text(stringResource(Res.string.entry_pool_mode), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
            SegmentedControl(
                PoolMode.entries,
                selected = poolMode,
                label = { stringResource(if (it == PoolMode.TEAMS) Res.string.entry_pool_mode_teams else Res.string.entry_organisers) },
                onSelect = { poolMode = it },
            )
            Text(
                stringResource(if (poolMode == PoolMode.TEAMS) Res.string.entry_pool_mode_teams_hint else Res.string.entry_pool_mode_organisers_hint),
                style = DesiTheme.type.caption,
                color = DesiTheme.colors.muted,
            )
        }
        SectionHeader(stringResource(Res.string.entry_limits), icon = DesiIcons.Teams)
        Text(stringResource(Res.string.entry_limits_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        val number = KeyboardOptions(keyboardType = KeyboardType.Number)
        DesiTextField(maxTeams, { maxTeams = it.filter(Char::isDigit).take(3) }, stringResource(Res.string.entry_max_teams), keyboardOptions = number)
        DesiTextField(maxPlayers, { maxPlayers = it.filter(Char::isDigit).take(3) }, stringResource(Res.string.entry_max_players), keyboardOptions = number)
        ListCard {
            ListRow(
                title = stringResource(Res.string.entry_closes),
                subtitle = closesAt?.let { formatDayAndTime(it) } ?: stringResource(Res.string.entry_closes_never),
                leading = { IconTile(DesiIcons.Calendar) },
                trailing = if (closesAt == null) null else ({
                    TextButton(onClick = { closesAt = null }) {
                        Text(stringResource(Res.string.entry_closes_clear), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                    }
                }),
                onClick = { picking = true },
            )
        }
        val settings = EntrySettingsRequest(teams, players, maxTeams.toIntOrNull(), maxPlayers.toIntOrNull(), closesAt, poolMode)
        val invalid = settings.validate() != null
        if (invalid) Text(stringResource(Res.string.entry_limits_invalid), style = DesiTheme.type.caption, color = DesiTheme.colors.event.wicket)
        PrimaryButton(stringResource(Res.string.teams_save_action), onClick = { onSave(settings) }, enabled = !invalid, modifier = Modifier.fillMaxWidth())
    }
}

/** "Teams: On approval · Players: Open · Up to 8 teams · Closes Sat 12 Dec", for the organisers' settings row. */
@Composable
internal fun entrySummary(t: Tournament): String = listOfNotNull(
    stringResource(Res.string.entry_summary, stringResource(t.teamEntry.label), stringResource(t.playerEntry.label)),
    t.maxTeams?.let { pluralStringResource(Res.plurals.entry_up_to_teams, it, it) },
    t.maxPlayers?.let { pluralStringResource(Res.plurals.entry_up_to_players, it, it) },
    t.entryClosesAt?.let { stringResource(Res.string.entry_closes_on, formatDayAndTime(it)) },
    stringResource(Res.string.entry_pool_mode_teams_short).takeIf { t.poolMode == PoolMode.TEAMS },
).joinToString(" · ")

/** What people do about their entries; the screen wires each to the view model. */
internal class EntryActions(
    val join: () -> Unit = {},
    val acceptInvite: () -> Unit = {},
    val leave: () -> Unit = {},
    val acceptTeam: (teamId: String) -> Unit = {},
    val withdrawTeam: (teamId: String) -> Unit = {},
    val confirmPlayer: (profileId: String) -> Unit = {},
    val removePlayer: (profileId: String) -> Unit = {},
    val offer: (profileId: String, teamId: String) -> Unit = { _, _ -> },
    val withdrawOffer: (profileId: String, teamId: String) -> Unit = { _, _ -> },
    val acceptOffer: (teamId: String) -> Unit = {},
    val declineOffer: (teamId: String) -> Unit = {},
)

/**
 * Near the top of the tournament for anyone who isn't organising it: invites to answer, a way in while
 * entries are open, and afterwards where the caller stands as a player.
 */
@Composable
internal fun EntryCard(state: TournamentUiState, actions: EntryActions, onEnterTeam: () -> Unit) {
    val t = state.details?.tournament ?: return
    val me = state.myEntry
    val invites = state.myInvites
    val offers = state.offersToMe
    if (state.isOrganiser || (me == null && invites.isEmpty() && !state.canEnterTeam && !state.canJoinAsPlayer)) return
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth(), padding = DesiTheme.space.s4) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            IconTile(DesiIcons.PersonAdd)
            Column(Modifier.weight(1f)) {
                val team = me?.teamId?.let { id -> t.teams.firstOrNull { it.teamId == id }?.name }
                val (title, line) = when {
                    me?.status == EntryStatus.INVITED -> Res.string.entry_card_invited to stringResource(Res.string.entry_card_invited_hint)
                    offers.isNotEmpty() -> Res.string.entry_card_offers to stringResource(Res.string.entry_card_offers_hint)
                    invites.isNotEmpty() -> Res.string.entry_card_team_invited to stringResource(Res.string.entry_card_team_invited_hint, invites.joinToString { it.name })
                    me == null -> Res.string.entry_card_open to stringResource(
                        when {
                            state.canEnterTeam && state.canJoinAsPlayer -> Res.string.entry_card_open_both
                            state.canEnterTeam -> Res.string.entry_card_open_teams
                            else -> Res.string.entry_card_open_players
                        },
                    )
                    me.status == EntryStatus.PENDING -> Res.string.entry_card_asked to stringResource(Res.string.entry_card_asked_hint)
                    me.status == EntryStatus.WAITLISTED -> Res.string.entry_card_waitlisted to stringResource(Res.string.entry_card_waitlisted_hint)
                    team != null -> Res.string.entry_card_in to stringResource(Res.string.entry_card_in_squad, team)
                    else -> Res.string.entry_card_in to stringResource(Res.string.entry_card_in_pool)
                }
                Text(stringResource(title), style = DesiTheme.type.bodyStrong, color = colors.ink)
                Text(line, style = DesiTheme.type.caption, color = colors.muted)
            }
        }
        if (me?.status == EntryStatus.INVITED) {
            AcceptOrDecline(state.saving, onAccept = actions.acceptInvite, onDecline = actions.leave)
        }
        offers.forEach { team ->
            Text(team.name, style = DesiTheme.type.bodyStrong, color = colors.ink)
            AcceptOrDecline(state.saving, onAccept = { actions.acceptOffer(team.teamId) }, onDecline = { actions.declineOffer(team.teamId) })
        }
        invites.forEach { invite ->
            if (invites.size > 1) Text(invite.name, style = DesiTheme.type.bodyStrong, color = colors.ink)
            AcceptOrDecline(state.saving, onAccept = { actions.acceptTeam(invite.teamId) }, onDecline = { actions.withdrawTeam(invite.teamId) })
        }
        if (state.canEnterTeam) {
            TonalButton(stringResource(Res.string.tournament_register_team), onClick = onEnterTeam, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Shield)
        }
        if (state.canJoinAsPlayer) {
            // An invite's Accept is the main action when there is one.
            if (invites.isEmpty()) {
                PrimaryButton(stringResource(Res.string.entry_join_player), onClick = actions.join, loading = state.saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd)
            } else {
                TonalButton(stringResource(Res.string.entry_join_player), onClick = actions.join, enabled = !state.saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd)
            }
        }
        if (me != null && me.status != EntryStatus.INVITED && !t.started) {
            TextButton(onClick = actions.leave, enabled = !state.saving) {
                Text(stringResource(Res.string.entry_leave), style = DesiTheme.type.button, color = colors.muted)
            }
        }
    }
}

@Composable
private fun AcceptOrDecline(saving: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        TonalButton(stringResource(Res.string.tournament_decline), onClick = onDecline, enabled = !saving, modifier = Modifier.weight(1f))
        PrimaryButton(stringResource(Res.string.entry_accept), onClick = onAccept, loading = saving, modifier = Modifier.weight(1f))
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
    actions: EntryActions,
    onOpenPlayer: (String) -> Unit,
) {
    val onConfirm = actions.confirmPlayer
    val onRemove = actions.removePlayer
    val t = state.details?.tournament ?: return
    val organiser = state.isOrganiser
    if (organiser) {
        TonalButton(stringResource(Res.string.entry_add_player), onClick = { onSheet(TournamentSheet.AddPlayer(null)) }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.PersonAdd)
    }
    if (t.players.isEmpty()) {
        EmptyState(DesiIcons.Teams, stringResource(if (t.takesPlayers(nowMillis())) Res.string.entry_no_players_open else Res.string.entry_no_players))
    }
    val waiting = t.players.filter { it.status != EntryStatus.CONFIRMED }
    if (waiting.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.entry_waiting, waiting.size), icon = DesiIcons.Clock)
        ListCard {
            waiting.forEachIndexed { i, entry ->
                PlayerEntryRow(entry, i > 0, onOpenPlayer) {
                    val invited = entry.status == EntryStatus.INVITED
                    when {
                        entry.status == EntryStatus.WAITLISTED && !organiser ->
                            Tag(stringResource(Res.string.entry_waitlisted_tag), tone = TagTone.NEUTRAL, icon = DesiIcons.Clock)
                        organiser && invited -> TextButton(onClick = { onRemove(entry.player.profileId) }) {
                            Text(stringResource(Res.string.entry_cancel_invite), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                        }
                        organiser -> {
                            TextButton(onClick = { onRemove(entry.player.profileId) }) {
                                Text(stringResource(Res.string.tournament_decline), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                            }
                            TextButton(onClick = { onConfirm(entry.player.profileId) }) {
                                Text(stringResource(Res.string.tournament_confirm), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                            }
                        }
                        invited -> Tag(stringResource(Res.string.entry_invited_tag), tone = TagTone.BRAND, icon = DesiIcons.Mail)
                        else -> Tag(stringResource(Res.string.tournament_pending_short), tone = TagTone.WARN, icon = DesiIcons.Clock)
                    }
                }
            }
        }
    }
    val pool = t.pool.filter { it.status == EntryStatus.CONFIRMED }
    if (organiser && !t.started && pool.size >= 2) {
        TonalButton(stringResource(Res.string.pickup_action), onClick = { onSheet(TournamentSheet.Pickup) }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Teams)
    }
    if (pool.isNotEmpty()) {
        val teamsPick = t.poolMode == PoolMode.TEAMS
        // While teams pick, a team's admin asks players; with more than one team they pick which in a sheet.
        val asking = if (teamsPick && !t.started) state.myEnteredTeams else emptyList()
        SectionHeader(stringResource(Res.string.entry_pool, pool.size), icon = DesiIcons.Person)
        Text(stringResource(if (teamsPick) Res.string.entry_pool_teams_hint else Res.string.entry_pool_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        ListCard {
            pool.forEachIndexed { i, entry ->
                val id = entry.player.profileId
                PlayerEntryRow(entry, i > 0, onOpenPlayer) {
                    val asked = asking.filter { team -> t.offers.any { it.profileId == id && it.teamId == team.teamId } }
                    when {
                        organiser -> TextButton(onClick = { onSheet(TournamentSheet.PlacePlayer(entry)) }) {
                            Text(stringResource(Res.string.entry_pick_team), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                        asking.size > 1 -> TextButton(onClick = { onSheet(TournamentSheet.AskPlayer(entry)) }) {
                            Text(stringResource(Res.string.entry_ask), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
                        }
                        asking.size == 1 && asked.isNotEmpty() -> {
                            Tag(stringResource(Res.string.entry_asked_tag), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
                            TextButton(onClick = { actions.withdrawOffer(id, asking.single().teamId) }) {
                                Text(stringResource(Res.string.entry_take_back), style = DesiTheme.type.button, color = DesiTheme.colors.muted)
                            }
                        }
                        asking.size == 1 -> TextButton(onClick = { actions.offer(id, asking.single().teamId) }) {
                            Text(stringResource(Res.string.entry_ask), style = DesiTheme.type.button, color = DesiTheme.colors.brand)
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
        SquadChanges(t.squadChanges.filter { it.teamId == team.teamId })
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
    onAdd: (profileId: String, invite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var invite by remember { mutableStateOf(false) }
    val t = state.details?.tournament ?: return
    val team = teamId?.let { id -> t.teams.firstOrNull { it.teamId == id } }
    val entered = t.players.map { it.player.profileId }.toSet()
    DesiSheet(team?.let { stringResource(Res.string.entry_add_to_squad, it.name) } ?: stringResource(Res.string.entry_add_player), onDismiss, fullHeight = state.isOrganiser) {
        // Organisers adding to the pool can ask instead; people on the app then accept from the tournament.
        if (state.isOrganiser && teamId == null) InviteChoice(invite, onSelect = { invite = it })
        val members = state.squadSource?.takeIf { it.id == teamId }?.members.orEmpty().map { it.player }.filter { it.profileId !in entered }
        if (members.isNotEmpty()) {
            SectionHeader(stringResource(Res.string.entry_from_team), icon = DesiIcons.Shield)
            members.forEach { player -> PlayerLine(player, onClick = { onAdd(player.profileId, false) }) }
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
            state.peopleResults.filter { it.profileId !in entered && !(invite && it.isPlaceholder) }
                .forEach { player -> PlayerLine(player, onClick = { onAdd(player.profileId, invite) }) }
        }
    }
}

/** Add straight in, or send an invite to accept. */
@Composable
internal fun InviteChoice(invite: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SegmentedControl(
            listOf(false, true),
            selected = invite,
            label = { stringResource(if (it) Res.string.entry_mode_invite else Res.string.entry_mode_add) },
            onSelect = onSelect,
        )
        Text(stringResource(if (invite) Res.string.entry_mode_invite_hint else Res.string.entry_mode_add_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

/** A team's admin running more than one team picks which ones ask the player. */
@Composable
internal fun AskPlayerSheet(state: TournamentUiState, entry: TournamentPlayer, onAsk: (teamId: String) -> Unit, onTakeBack: (teamId: String) -> Unit, onDismiss: () -> Unit) {
    val t = state.details?.tournament ?: return
    DesiSheet(stringResource(Res.string.entry_ask_title, entry.player.name), onDismiss) {
        ListCard {
            state.myEnteredTeams.forEachIndexed { i, team ->
                val asked = t.offers.any { it.profileId == entry.player.profileId && it.teamId == team.teamId }
                ListRow(
                    title = team.name,
                    leading = { Avatar(team.name, size = 40) },
                    trailing = if (asked) ({ Tag(stringResource(Res.string.entry_asked_tag), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle) }) else null,
                    divider = i > 0,
                    onClick = { if (asked) onTakeBack(team.teamId) else onAsk(team.teamId) },
                )
            }
        }
    }
}

/**
 * Pick-up tournaments: the organiser names some teams for the players who came on their own, and has the
 * app share them out evenly or places them by hand afterwards.
 */
@Composable
internal fun PickupSheet(poolSize: Int, onMake: (names: List<String>, split: Boolean) -> Unit, onDismiss: () -> Unit) {
    var count by remember { mutableStateOf(2) }
    var names by remember { mutableStateOf(List(PickupTeamsRequest.MAX_TEAMS) { "" }) }
    var split by remember { mutableStateOf(true) }
    val defaults = List(PickupTeamsRequest.MAX_TEAMS) { stringResource(Res.string.pickup_default_name, ('A' + it).toString()) }
    DesiSheet(stringResource(Res.string.pickup_action), onDismiss, fullHeight = true) {
        Text(stringResource(Res.string.pickup_teams), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
        ChoiceChips((2..minOf(8, poolSize)).toList(), selected = count, label = { "$it" }, onSelect = { count = it })
        repeat(count) { i ->
            DesiTextField(
                names[i],
                { value -> names = names.toMutableList().also { it[i] = value.take(PickupTeamsRequest.MAX_NAME) } },
                stringResource(Res.string.pickup_team_name, i + 1),
                placeholder = defaults[i],
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        SegmentedControl(
            listOf(true, false),
            selected = split,
            label = { stringResource(if (it) Res.string.pickup_split else Res.string.pickup_by_hand) },
            onSelect = { split = it },
        )
        Text(
            if (split) pluralStringResource(Res.plurals.pickup_split_hint, poolSize / count, poolSize / count)
            else stringResource(Res.string.pickup_by_hand_hint),
            style = DesiTheme.type.caption,
            color = DesiTheme.colors.muted,
        )
        val chosen = List(count) { names[it].trim().ifEmpty { defaults[it] } }
        val valid = PickupTeamsRequest(chosen, split).validate() == null
        PrimaryButton(stringResource(Res.string.pickup_make), onClick = { onMake(chosen, split) }, enabled = valid, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Teams)
    }
}

/** What organisers changed in a squad after the start: who came in and who left, and when. */
@Composable
private fun SquadChanges(changes: List<SquadChange>) {
    if (changes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
        Text(stringResource(Res.string.entry_changes).uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
        changes.forEach { change ->
            val line = if (change.added) Res.string.entry_change_in else Res.string.entry_change_out
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                Icon(
                    painterResource(if (change.added) DesiIcons.Add else DesiIcons.Remove),
                    null,
                    tint = if (change.added) DesiTheme.colors.brand else DesiTheme.colors.muted,
                    modifier = Modifier.size(DesiTheme.size.iconSmall),
                )
                Text(stringResource(line, change.name, dayLabel(change.at)), style = DesiTheme.type.caption, color = DesiTheme.colors.ink)
            }
        }
    }
}
