package com.harsh.desicrics.ui.newmatch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.designsystem.component.ActionTile
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.BrandHeader
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.heroBackground
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.common_cancel
import com.harsh.desicrics.resources.home_search
import com.harsh.desicrics.resources.new_match_add_player
import com.harsh.desicrics.resources.new_match_add_done
import com.harsh.desicrics.resources.new_match_add_names
import com.harsh.desicrics.resources.team_add_added
import com.harsh.desicrics.resources.new_match_discard
import com.harsh.desicrics.resources.new_match_discard_body
import com.harsh.desicrics.resources.new_match_discard_title
import com.harsh.desicrics.resources.new_match_error_captain
import com.harsh.desicrics.resources.new_match_error_create_failed
import com.harsh.desicrics.resources.new_match_error_load_team_failed
import com.harsh.desicrics.resources.new_match_error_save_team_failed
import com.harsh.desicrics.resources.new_match_error_overs
import com.harsh.desicrics.resources.new_match_error_player_name_invalid
import com.harsh.desicrics.resources.new_match_error_player_twice
import com.harsh.desicrics.resources.new_match_error_rules
import com.harsh.desicrics.resources.new_match_error_same_team_names
import com.harsh.desicrics.resources.new_match_error_team_name_missing
import com.harsh.desicrics.resources.new_match_error_team_name_too_long
import com.harsh.desicrics.resources.new_match_error_too_few_players
import com.harsh.desicrics.resources.new_match_error_too_many_players
import com.harsh.desicrics.resources.new_match_overs_fewer
import com.harsh.desicrics.resources.new_match_overs_label
import com.harsh.desicrics.resources.new_match_overs_more
import com.harsh.desicrics.resources.new_match_player_count
import com.harsh.desicrics.resources.new_match_remove_player
import com.harsh.desicrics.resources.new_match_rules_customise
import com.harsh.desicrics.resources.new_match_rules_hint
import com.harsh.desicrics.resources.new_match_section_overs
import com.harsh.desicrics.resources.new_match_section_rules
import com.harsh.desicrics.resources.new_match_section_teams
import com.harsh.desicrics.resources.new_match_squad_change
import com.harsh.desicrics.resources.new_match_squad_clear
import com.harsh.desicrics.resources.new_match_team_a
import com.harsh.desicrics.resources.new_match_team_b
import com.harsh.desicrics.resources.new_match_team_name
import com.harsh.desicrics.resources.setup_add_player_hint
import com.harsh.desicrics.resources.setup_captain_tag
import com.harsh.desicrics.resources.setup_change_time
import com.harsh.desicrics.resources.setup_continue
import com.harsh.desicrics.resources.setup_details
import com.harsh.desicrics.resources.setup_edit_lineup
import com.harsh.desicrics.resources.setup_find
import com.harsh.desicrics.resources.setup_guest
import com.harsh.desicrics.resources.setup_keeper_tag
import com.harsh.desicrics.resources.setup_later_hint
import com.harsh.desicrics.resources.setup_lineup
import com.harsh.desicrics.resources.setup_linked
import com.harsh.desicrics.resources.setup_min_players
import com.harsh.desicrics.resources.setup_mode_guest_hint
import com.harsh.desicrics.resources.setup_mode_phone_hint
import com.harsh.desicrics.resources.setup_mode_search_hint
import com.harsh.desicrics.resources.setup_now
import com.harsh.desicrics.resources.setup_phone
import com.harsh.desicrics.resources.setup_phone_number
import com.harsh.desicrics.resources.setup_save
import com.harsh.desicrics.resources.setup_saved_team
import com.harsh.desicrics.resources.setup_schedule
import com.harsh.desicrics.resources.setup_step
import com.harsh.desicrics.resources.setup_venue
import com.harsh.desicrics.resources.setup_vs
import com.harsh.desicrics.resources.setup_when_where
import com.harsh.desicrics.resources.team_add_placeholder_name
import com.harsh.desicrics.resources.team_invite_share
import com.harsh.desicrics.resources.team_invite_text
import com.harsh.desicrics.resources.team_placeholder
import com.harsh.desicrics.resources.teams_action_failed
import com.harsh.desicrics.ui.system.LightStatusBarIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NewMatchScreen(
    onCreated: (matchId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewMatchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingFor by rememberSaveable { mutableStateOf<TeamSide?>(null) }
    LightStatusBarIcons()
    NewMatchContent(
        state = state,
        actions = NewMatchActions(
            onSaveTeam = viewModel::saveTeam, onLinkPlayer = viewModel::linkPlayer, searchPlayers = viewModel::findPlayers, byPhone = viewModel::byPhone,
            onTeamName = viewModel::setTeamName,
            onAddPlayer = viewModel::addPlayer,
            onRemovePlayer = viewModel::removePlayer,
            onUpdate = viewModel::update,
            onStart = { viewModel.create(onCreated) },
            onBack = onBack,
            onUseTeam = { side ->
                viewModel.loadMyTeams()
                pickingFor = side
            },
            onClearTeam = viewModel::clearTeam,
            onChangeSquad = viewModel::changeSquad,
        ),
    )
    pickingFor?.let { side ->
        TeamPickerSheet(
            teams = state.myTeams?.let { state.pickable(side, it) },
            failed = state.myTeamsFailed,
            othersTaken = !state.myTeams.isNullOrEmpty(),
            onRetry = viewModel::loadMyTeams,
            search = { q -> state.pickable(side, viewModel.findTeams(q)) },
            onPick = { team ->
                pickingFor = null
                viewModel.pickTeam(side, team.id)
            },
            onDismiss = { pickingFor = null },
        )
    }
    state.squadTeam?.let { team ->
        val side = state.squadSide ?: return@let
        val current = state.side(side)
        // The squad already picked for this team, or else its last match's, or else everyone.
        val initial = when {
            current.teamId == team.id -> current.players.mapNotNull { it.profileId }
            team.lastSquad.isNotEmpty() -> team.lastSquad
            else -> team.members.map { it.player.profileId }
        }
        SquadSheet(
            team = team,
            initial = initial.toSet(),
            initialCaptain = current.captain?.let { current.players.getOrNull(it)?.profileId }.takeIf { current.teamId == team.id },
            initialKeeper = current.keeper?.let { current.players.getOrNull(it)?.profileId }.takeIf { current.teamId == team.id },
            onDone = viewModel::useSquad,
            onDismiss = viewModel::closeSquad,
        )
    }
}

/** Everything the new match form can ask for, kept together so the content signature stays short. */
class NewMatchActions(
    val onTeamName: (TeamSide, String) -> Unit,
    val onAddPlayer: (TeamSide, String) -> Unit,
    val onRemovePlayer: (TeamSide, index: Int) -> Unit,
    val onUpdate: ((NewMatchFormState) -> NewMatchFormState) -> Unit,
    val onStart: () -> Unit,
    val onBack: () -> Unit,
    val onUseTeam: (TeamSide) -> Unit = {},
    val onClearTeam: (TeamSide) -> Unit = {},
    val onChangeSquad: (TeamSide) -> Unit = {},
    val onSaveTeam: (TeamSide) -> Unit = {},
    val onLinkPlayer: (TeamSide, com.harsh.desicrics.model.PlayerSummary) -> Unit = { _, _ -> },
    val searchPlayers: suspend (String) -> List<com.harsh.desicrics.model.PlayerSummary> = { emptyList() },
    val byPhone: suspend (TeamSide, String, String) -> Unit = { _, _, _ -> },
)

/** Overs most local matches use, offered as one-tap picks next to the stepper. */
private val QuickOvers = listOf(5, 6, 8, 10, 12, 20)

/** The new match form without its ViewModel, so previews and tests can render any state. */
@Composable
fun NewMatchContent(state: NewMatchFormState, actions: NewMatchActions, initialStep: Int = 0) {
    val colors = DesiTheme.colors
    var step by rememberSaveable { mutableStateOf(initialStep) }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    // Set while a team is edited from the details step: Back and Continue both return there.
    var editingFromDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        when {
            editingFromDetails -> { step = 2; editingFromDetails = false }
            step > 0 -> step--
            state.hasInput -> confirmDiscard = true
            else -> actions.onBack()
        }
    }
    androidx.navigationevent.compose.NavigationBackHandler(
        androidx.navigationevent.compose.rememberNavigationEventState(androidx.navigationevent.NavigationEventInfo.None),
        isBackEnabled = step > 0 || editingFromDetails || state.hasInput,
        onBackCompleted = back,
    )
    if (confirmDiscard) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(Res.string.new_match_discard_title)) },
            text = { Text(stringResource(Res.string.new_match_discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.onBack() }) { Text(stringResource(Res.string.new_match_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(Res.string.common_cancel)) } },
        )
    }
    if (scheduling) com.harsh.desicrics.ui.tournament.ScheduleSheet(state.scheduledAt, { at ->
        actions.onUpdate { it.copy(scheduledAt = at) }; scheduling = false
    }, { scheduling = false })
    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        BrandStatusBar()
        // Pinned above the scrolling form, so the back arrow and progress stay in reach on the long details step.
        BrandHeader(
            title = stringResource(if (step == 2) Res.string.setup_details else TeamSide.entries[step].label),
            subtitle = stringResource(Res.string.setup_step, step + 1),
            onBack = back,
        ) { StepProgress(step) }
        Column(Modifier.weight(1f).verticalScroll(androidx.compose.runtime.key(step) { rememberScrollState() })) {
            Column(
                Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s8),
            ) {
                if (step < 2) {
                    androidx.compose.runtime.key(step) { TeamStep(TeamSide.entries[step], state, actions) }
                } else {
                    Section(Res.string.new_match_section_teams, DesiIcons.Teams) {
                        FixturePreview(state, onEdit = { step = it; editingFromDetails = true })
                    }
                    Section(Res.string.setup_when_where, DesiIcons.Stadium) {
                        DesiTextField(
                            state.venue,
                            { text -> actions.onUpdate { it.copy(venue = text.take(120)) } },
                            stringResource(Res.string.setup_venue),
                            leadingIcon = DesiIcons.Location,
                        )
                        ListCard {
                            ListRow(
                                title = state.scheduledAt?.let { com.harsh.desicrics.ui.text.formatDayAndTime(it) } ?: stringResource(Res.string.setup_now),
                                subtitle = stringResource(if (state.scheduledAt == null) Res.string.setup_later_hint else Res.string.setup_change_time),
                                leading = { IconTile(if (state.scheduledAt == null) DesiIcons.Play else DesiIcons.Calendar) },
                                trailing = if (state.scheduledAt != null) ({
                                    androidx.compose.material3.IconButton({ actions.onUpdate { it.copy(scheduledAt = null) } }) {
                                        Icon(painterResource(DesiIcons.Close), stringResource(Res.string.setup_now), tint = colors.muted)
                                    }
                                }) else null,
                                onClick = { scheduling = true },
                            )
                        }
                    }
                    Section(Res.string.new_match_section_overs, DesiIcons.Clock) {
                        NumberStepper(
                            value = state.overs,
                            onValueChange = { overs -> actions.onUpdate { it.copy(overs = overs) } },
                            label = stringResource(Res.string.new_match_overs_label),
                            decreaseLabel = stringResource(Res.string.new_match_overs_fewer),
                            increaseLabel = stringResource(Res.string.new_match_overs_more),
                            range = CreateMatchRequest.OVERS,
                        )
                        ChoiceChips(
                            options = QuickOvers,
                            selected = state.overs.takeIf { it in QuickOvers },
                            label = { "$it" },
                            onSelect = { overs -> actions.onUpdate { it.copy(overs = overs) } },
                        )
                    }
                    RulesSection(state, actions)
                }
            }
        }
        StartBar(state, if (step < 2) Res.string.setup_continue else if (state.scheduledAt == null) Res.string.setup_now else Res.string.setup_schedule,
            step == 2 || state.name(TeamSide.entries[step]).isNotBlank(),
            if (step < 2) DesiIcons.Forward else if (state.scheduledAt == null) DesiIcons.Play else DesiIcons.Calendar,
        ) {
            when {
                editingFromDetails -> { step = 2; editingFromDetails = false }
                step < 2 -> step++
                else -> actions.onStart()
            }
        }
    }
}

/** Three bars under the header title: done and current steps filled. */
@Composable
private fun StepProgress(step: Int) {
    val colors = DesiTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = DesiTheme.space.s2), horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        repeat(3) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(DesiTheme.radius.full)
                    .background(colors.onBrandStrong.copy(alpha = if (i <= step) 1f else 0.25f)),
            )
        }
    }
}

/** One side: the team (typed, found or saved), then its lineup as a roster with an add row at the end. */
@Composable
private fun TeamStep(side: TeamSide, state: NewMatchFormState, actions: NewMatchActions) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    val form = state.side(side)
    val players = form.players
    val invalid = (state.error as? NewMatchError.Invalid)?.error
    var adding by rememberSaveable(side) { mutableStateOf(false) }
    val share = com.harsh.desicrics.ui.system.rememberShareText()
    if (adding) LineupPlayerSheet(side, players, actions) { adding = false }

    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        DesiCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                Avatar(state.name(side), size = 52)
                Column(Modifier.weight(1f)) {
                    DesiTextField(
                        value = state.name(side),
                        onValueChange = { actions.onTeamName(side, it) },
                        label = stringResource(Res.string.new_match_team_name),
                        enabled = form.teamId == null && !state.saving,
                        error = invalid?.takeIf { state.name(side).isBlank() && it == MatchSetupError.TEAM_NAME_MISSING }?.let { stringResource(it.label) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                }
            }
            if (form.teamId != null) Tag(stringResource(Res.string.setup_saved_team), tone = TagTone.BRAND, icon = DesiIcons.Verified)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
            if (form.teamId == null) {
                ActionTile(DesiIcons.Search, stringResource(Res.string.setup_find), { actions.onUseTeam(side) }, Modifier.weight(1f))
                if (form.name.isNotBlank()) ActionTile(DesiIcons.Shield, stringResource(Res.string.setup_save), { if (!state.saving) actions.onSaveTeam(side) }, Modifier.weight(1f))
            } else {
                ActionTile(DesiIcons.Teams, stringResource(Res.string.new_match_squad_change), { actions.onChangeSquad(side) }, Modifier.weight(1f))
                ActionTile(DesiIcons.Close, stringResource(Res.string.new_match_squad_clear), { actions.onClearTeam(side) }, Modifier.weight(1f))
            }
            if (form.inviteLink != null && form.joinCode != null) {
                val invite = stringResource(Res.string.team_invite_text, form.name, form.inviteLink, form.joinCode)
                ActionTile(DesiIcons.Share, stringResource(Res.string.team_invite_share), { share(invite) }, Modifier.weight(1f))
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(
            stringResource(Res.string.setup_lineup) + " · " + pluralStringResource(Res.plurals.new_match_player_count, players.size, players.size),
            icon = DesiIcons.Teams,
        )
        ListCard {
            players.forEachIndexed { i, p ->
                ListRow(
                    title = p.name,
                    subtitle = stringResource(
                        when {
                            p.profileId == null -> Res.string.setup_guest
                            p.profileId?.startsWith("p-") == true -> Res.string.team_placeholder
                            else -> Res.string.setup_linked
                        },
                    ),
                    leading = { LineupNumber(i + 1, linked = p.profileId != null) },
                    trailing = {
                        if (form.captain == i) Tag(stringResource(Res.string.setup_captain_tag), tone = TagTone.BRAND, icon = DesiIcons.Star)
                        if (form.keeper == i) Tag(stringResource(Res.string.setup_keeper_tag), tone = TagTone.NEUTRAL, icon = DesiIcons.Glove)
                        androidx.compose.material3.IconButton({ actions.onRemovePlayer(side, i) }) {
                            Icon(painterResource(DesiIcons.Close), stringResource(Res.string.new_match_remove_player, p.name), tint = colors.muted, modifier = Modifier.size(18.dp))
                        }
                    },
                    divider = i > 0,
                )
            }
            if (players.size < CreateMatchRequest.MAX_PLAYERS) {
                ListRow(
                    title = stringResource(Res.string.new_match_add_player),
                    subtitle = stringResource(Res.string.setup_add_player_hint),
                    leading = { IconTile(DesiIcons.PersonAdd, tint = colors.onBrand, container = colors.brand) },
                    titleColor = colors.brand,
                    divider = players.isNotEmpty(),
                    trailing = {},
                    onClick = { adding = true },
                )
            }
        }
        if (players.size < CreateMatchRequest.MIN_PLAYERS) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                Icon(painterResource(DesiIcons.Info), null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.setup_min_players), style = DesiTheme.type.caption, color = colors.muted)
            }
        }
    }
}

/** A batting-order number; green once the player is a real profile, grey for a guest. */
@Composable
private fun LineupNumber(number: Int, linked: Boolean) {
    val colors = DesiTheme.colors
    Box(
        Modifier.size(40.dp).clip(DesiTheme.radius.full).background(if (linked) colors.brandSoft else colors.sunk),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = DesiTheme.type.titleMd, color = if (linked) colors.brand else colors.muted)
    }
}

/** Both teams facing each other, each tappable to go back and edit it. */
@Composable
private fun FixturePreview(state: NewMatchFormState, onEdit: (Int) -> Unit) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).heroBackground().padding(vertical = space.s5, horizontal = space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamSide.entries.forEachIndexed { index, side ->
            if (index == 1) {
                Text(stringResource(Res.string.setup_vs).uppercase(), style = DesiTheme.type.scoreMd, color = colors.onBrandStrong.copy(alpha = 0.6f))
            }
            Column(
                Modifier.weight(1f).clip(DesiTheme.radius.md).clickable(role = Role.Button) { onEdit(index) }.padding(space.s2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                Box(Modifier.clip(DesiTheme.radius.full).border(2.dp, colors.onBrandStrong, DesiTheme.radius.full).padding(2.dp)) {
                    Avatar(state.name(side), size = 52)
                }
                Text(
                    state.name(side),
                    style = DesiTheme.type.bodyStrong,
                    color = colors.onBrandStrong,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        pluralStringResource(Res.plurals.new_match_player_count, state.players(side).size, state.players(side).size),
                        style = DesiTheme.type.caption,
                        color = colors.onBrandStrong.copy(alpha = 0.8f),
                    )
                    Icon(painterResource(DesiIcons.Edit), stringResource(Res.string.setup_edit_lineup), tint = colors.onBrandStrong.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * Adds players to one side's lineup, as many as needed: every add keeps the sheet open and clears the
 * field for the next name. Guests can go in several at once, separated by commas or new lines.
 */
@Composable
private fun LineupPlayerSheet(side: TeamSide, players: List<SquadPlayer>, actions: NewMatchActions, close: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) }
    var found by androidx.compose.runtime.remember(query) { mutableStateOf(emptyList<com.harsh.desicrics.model.PlayerSummary>()) }
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var failed by androidx.compose.runtime.remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val full = players.size >= CreateMatchRequest.MAX_PLAYERS
    val linked = players.mapNotNull { it.profileId }.toSet()
    val guests = query.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    androidx.compose.runtime.LaunchedEffect(query, mode) {
        failed = false
        if (mode == 0 && query.trim().length >= 2) try {
            kotlinx.coroutines.delay(250)
            found = actions.searchPlayers(query)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true }
    }
    val addGuests = {
        guests.forEach { actions.onAddPlayer(side, it) }
        query = ""
    }
    com.harsh.desicrics.designsystem.component.DesiSheet(stringResource(Res.string.new_match_add_player), close) {
        val labels = listOf(Res.string.home_search, Res.string.setup_phone, Res.string.setup_guest)
        SegmentedControl(labels.indices.toList(), mode, label = { stringResource(labels[it]) }, onSelect = { mode = it })
        Text(stringResource(listOf(Res.string.setup_mode_search_hint, Res.string.setup_mode_phone_hint, Res.string.setup_mode_guest_hint)[mode]), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        DesiTextField(
            query,
            { query = it.take(if (mode == 2) 600 else 60) },
            stringResource(Res.string.team_add_placeholder_name),
            leadingIcon = if (mode == 0) DesiIcons.Search else DesiIcons.Person,
            // Enter adds the guest and leaves the keyboard up for the next name.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = if (mode == 2) ImeAction.Done else ImeAction.Next),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (mode == 2 && !full) addGuests() }),
        )
        if (mode == 0) {
            if (found.isNotEmpty()) com.harsh.desicrics.ui.social.PlayerList(
                found,
                trailing = { p ->
                    if (p.profileId in linked) Tag(stringResource(Res.string.team_add_added), tone = TagTone.BRAND, icon = DesiIcons.CheckCircle)
                    else Icon(painterResource(DesiIcons.PersonAdd), null, tint = DesiTheme.colors.brand, modifier = Modifier.size(18.dp))
                },
            ) { p -> if (p.profileId !in linked && !full) actions.onLinkPlayer(side, p) }
        } else {
            if (mode == 1) DesiTextField(phone, { phone = it }, stringResource(Res.string.setup_phone_number), leadingIcon = DesiIcons.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            PrimaryButton(
                if (mode == 2 && guests.size > 1) pluralStringResource(Res.plurals.new_match_add_names, guests.size, guests.size) else stringResource(Res.string.new_match_add_player),
                {
                    if (mode == 2) addGuests()
                    else scope.launch {
                        busy = true
                        try {
                            actions.byPhone(side, query.trim(), phone)
                            query = ""
                            phone = ""
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true }
                        finally { busy = false }
                    }
                },
                enabled = !full && guests.isNotEmpty() && (mode == 2 || phone.count(Char::isDigit) in 10..13),
                loading = busy,
                leadingIcon = DesiIcons.PersonAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (failed) ErrorBanner(stringResource(Res.string.teams_action_failed))
        GhostButton(pluralStringResource(Res.plurals.new_match_add_done, players.size, players.size), onClick = close, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RulesSection(state: NewMatchFormState, actions: NewMatchActions) {
    var customising by rememberSaveable { mutableStateOf(false) }
    val preset = RulesPreset.matching(state.rules)

    Section(Res.string.new_match_section_rules, DesiIcons.Whistle) {
        Text(stringResource(Res.string.new_match_rules_hint), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        ChoiceChips(
            options = RulesPreset.entries.filter { it != RulesPreset.CUSTOM || preset == RulesPreset.CUSTOM },
            selected = preset,
            label = { stringResource(it.label) },
            onSelect = { picked -> picked.rules?.let { rules -> actions.onUpdate { it.copy(rules = rules.withExtraRunsOf(it.rules)) } } },
        )
        Text(stringResource(preset.hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        ExtraRunQuestions(state.rules, onChange = { rules -> actions.onUpdate { it.copy(rules = rules) } })
        GhostButton(stringResource(Res.string.new_match_rules_customise), onClick = { customising = true }, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Settings)
    }
    if (customising) {
        RulesSheet(
            rules = state.rules,
            oversPerInnings = state.overs,
            onChange = { rules -> actions.onUpdate { it.copy(rules = rules) } },
            onDismiss = { customising = false },
        )
    }
}

@Composable
private fun Section(title: StringResource, icon: DrawableResource, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        SectionHeader(stringResource(title), icon = icon)
        content()
    }
}

/** Pinned under the form, above the keyboard. Form errors show here, next to the button they block. */
@Composable
private fun StartBar(state: NewMatchFormState, label: StringResource, enabled: Boolean, icon: DrawableResource, onStart: () -> Unit) {
    val colors = DesiTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.line)
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s3),
            verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
        ) {
            state.error?.let { error ->
                val text = when (error) {
                    is NewMatchError.Invalid -> stringResource(error.error.label)
                    NewMatchError.CreateFailed -> stringResource(Res.string.new_match_error_create_failed)
                    NewMatchError.SaveTeamFailed -> stringResource(Res.string.new_match_error_save_team_failed)
                    NewMatchError.LoadTeamFailed -> stringResource(Res.string.new_match_error_load_team_failed)
                }
                ErrorBanner(text)
            }
            PrimaryButton(
                text = stringResource(label),
                enabled = enabled,
                onClick = onStart,
                loading = state.saving,
                trailingIcon = icon,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val TeamSide.label: StringResource
    get() = if (this == TeamSide.A) Res.string.new_match_team_a else Res.string.new_match_team_b

private val MatchSetupError.label: StringResource
    get() = when (this) {
        MatchSetupError.TEAM_NAME_MISSING -> Res.string.new_match_error_team_name_missing
        MatchSetupError.SAME_TEAM_NAMES -> Res.string.new_match_error_same_team_names
        MatchSetupError.TEAM_NAME_TOO_LONG -> Res.string.new_match_error_team_name_too_long
        MatchSetupError.TOO_FEW_PLAYERS -> Res.string.new_match_error_too_few_players
        MatchSetupError.TOO_MANY_PLAYERS -> Res.string.new_match_error_too_many_players
        MatchSetupError.PLAYER_NAME_INVALID -> Res.string.new_match_error_player_name_invalid
        MatchSetupError.OVERS_OUT_OF_RANGE -> Res.string.new_match_error_overs
        MatchSetupError.RULES_INVALID -> Res.string.new_match_error_rules
        MatchSetupError.PLAYER_TWICE -> Res.string.new_match_error_player_twice
        MatchSetupError.CAPTAIN_INVALID -> Res.string.new_match_error_captain
    }

// Previews: sample team and player names only; all labels come from string resources.

private val PreviewActions = NewMatchActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})

private val PreviewFilled = NewMatchFormState(
    teamA = SideForm("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir", "Ishaan").map { SquadPlayer(it, "id-$it") }, teamId = "t1", captain = 0, keeper = 2),
    teamB = SideForm("Powai XI", listOf(SquadPlayer("Dev"))),
    overs = 12,
    tossWinner = TeamSide.B,
    tossDecision = TossDecision.BOWL,
)

@Preview
@Composable
private fun NewMatchEmptyPreview() = DesiTheme(darkTheme = false) { NewMatchContent(NewMatchFormState(), PreviewActions) }

@Preview
@Composable
private fun NewMatchFilledPreview() = DesiTheme(darkTheme = false) { NewMatchContent(PreviewFilled, PreviewActions) }

@Preview
@Composable
private fun NewMatchDarkPreview() = DesiTheme(darkTheme = true) { NewMatchContent(PreviewFilled, PreviewActions) }

@Preview
@Composable
private fun NewMatchErrorPreview() = DesiTheme(darkTheme = false) {
    NewMatchContent(PreviewFilled.copy(error = NewMatchError.Invalid(MatchSetupError.TOO_FEW_PLAYERS)), PreviewActions)
}

@Preview
@Composable
private fun NewMatchSavingPreview() = DesiTheme(darkTheme = false) { NewMatchContent(PreviewFilled.copy(saving = true), PreviewActions) }
