package com.harsh.desicrics.ui.newmatch

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.designsystem.component.BrandHeader
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RemovableChip
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.system.LightStatusBarIcons
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
        ),
    )
    pickingFor?.let { side ->
        TeamPickerSheet(
            teams = state.myTeams,
            search = viewModel::findTeams,
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
    val back = { if (step == 0) actions.onBack() else step-- ; Unit }
    androidx.navigationevent.compose.NavigationBackHandler(androidx.navigationevent.compose.rememberNavigationEventState(androidx.navigationevent.NavigationEventInfo.None), isBackEnabled = step > 0, onBackCompleted = back)
    if (scheduling) com.harsh.desicrics.ui.tournament.ScheduleSheet(state.scheduledAt, { at ->
        actions.onUpdate { it.copy(scheduledAt = at) }; scheduling = false
    }, { scheduling = false })
    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        BrandStatusBar()
        Column(Modifier.weight(1f).verticalScroll(androidx.compose.runtime.key(step) { rememberScrollState() })) {
            BrandHeader(
                title = stringResource(if (step == 2) Res.string.setup_details else TeamSide.entries[step].label),
                subtitle = stringResource(Res.string.setup_step, step + 1),
                onBack = back,
            )
            Column(
                Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s8),
            ) {
                if (step < 2) {
                    androidx.compose.runtime.key(step) { TeamCard(TeamSide.entries[step], state, actions) }
                } else {
                TeamSide.entries.forEachIndexed { index, side ->
                    TonalButton(state.name(side) + " · " + pluralStringResource(Res.plurals.new_match_player_count, state.players(side).size, state.players(side).size),
                        onClick = { step = index }, modifier = Modifier.fillMaxWidth())
                }
                DesiTextField(state.venue, { text -> actions.onUpdate { it.copy(venue = text.take(120)) } }, stringResource(Res.string.setup_venue))
                GhostButton(state.scheduledAt?.let { com.harsh.desicrics.ui.text.formatDayAndTime(it) } ?: stringResource(Res.string.setup_later), { scheduling = true })
                if (state.scheduledAt != null) GhostButton(stringResource(Res.string.setup_now), { actions.onUpdate { it.copy(scheduledAt = null) } })

                Section(Res.string.new_match_section_overs) {
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
            step == 2 || state.name(TeamSide.entries[step]).isNotBlank()) { if (step < 2) step++ else actions.onStart() }
    }
}

@Composable
private fun TeamCard(side: TeamSide, state: NewMatchFormState, actions: NewMatchActions) {
    val colors = DesiTheme.colors
    val form = state.side(side)
    val players = form.players
    val invalid = (state.error as? NewMatchError.Invalid)?.error
    var adding by rememberSaveable(side) { mutableStateOf(false) }
    val share = com.harsh.desicrics.ui.system.rememberShareText()
    if (adding) LineupPlayerSheet(side, actions) { adding = false }
    DesiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(side.label), Modifier.weight(1f))
            Text(pluralStringResource(Res.plurals.new_match_player_count, players.size, players.size), style = DesiTheme.type.caption, color = colors.muted)
        }
        DesiTextField(
            value = state.name(side),
            onValueChange = { actions.onTeamName(side, it) },
            label = stringResource(Res.string.new_match_team_name),
            enabled = form.teamId == null && !state.saving,
            error = invalid?.takeIf { state.name(side).isBlank() && it == MatchSetupError.TEAM_NAME_MISSING }?.let { stringResource(it.label) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        if (form.teamId == null) {
            TonalButton(stringResource(Res.string.setup_find), onClick = { actions.onUseTeam(side) }, modifier = Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                TonalButton(stringResource(Res.string.new_match_squad_change), onClick = { actions.onUseTeam(side) }, modifier = Modifier.weight(1f))
                GhostButton(stringResource(Res.string.new_match_squad_clear), onClick = { actions.onClearTeam(side) }, modifier = Modifier.weight(1f))
            }
        }
        if (form.teamId == null && form.name.isNotBlank()) {
            GhostButton(stringResource(Res.string.setup_save), { actions.onSaveTeam(side) }, enabled = !state.saving)
        }
        form.inviteLink?.let { link -> GhostButton(stringResource(Res.string.team_invite_share), { share(link) }) }
        players.forEachIndexed { i, p ->
            com.harsh.desicrics.designsystem.component.PlayerRow(p.name,
                subtitle = stringResource(if (p.profileId == null) Res.string.setup_guest else if (p.profileId?.startsWith("p-") == true) Res.string.team_placeholder else Res.string.setup_linked),
                trailing = { androidx.compose.material3.IconButton({ actions.onRemovePlayer(side, i) }) {
                    Icon(painterResource(DesiIcons.Close), stringResource(Res.string.new_match_remove_player, p.name))
                } })
        }
        PrimaryButton(stringResource(Res.string.new_match_add_player), { adding = true }, enabled = players.size < CreateMatchRequest.MAX_PLAYERS)
        if (players.size < CreateMatchRequest.MIN_PLAYERS) {
            Text(stringResource(Res.string.new_match_players_hint), style = DesiTheme.type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun LineupPlayerSheet(side: TeamSide, actions: NewMatchActions, close: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) }
    var found by androidx.compose.runtime.remember(query) { mutableStateOf(emptyList<com.harsh.desicrics.model.PlayerSummary>()) }
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    var failed by androidx.compose.runtime.remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(query, mode) {
        failed = false
        if (mode == 0 && query.trim().length >= 2) try {
            kotlinx.coroutines.delay(250)
            found = actions.searchPlayers(query)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true }
    }
    com.harsh.desicrics.designsystem.component.DesiSheet(stringResource(Res.string.new_match_add_player), close) {
        val labels = listOf(Res.string.home_search, Res.string.setup_phone, Res.string.setup_guest)
        SegmentedControl(labels.indices.toList(), mode, label = { stringResource(labels[it]) }, onSelect = { mode = it })
        DesiTextField(query, { query = it.take(60) }, stringResource(Res.string.team_add_placeholder_name))
        if (mode == 0) found.forEach { p ->
            com.harsh.desicrics.designsystem.component.PlayerRow(p.name, subtitle = p.city, onClick = { actions.onLinkPlayer(side, p); close() })
        } else {
            if (mode == 1) DesiTextField(phone, { phone = it }, stringResource(Res.string.team_add_placeholder_phone),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
            PrimaryButton(stringResource(Res.string.new_match_add_player), {
                if (mode == 2) { actions.onAddPlayer(side, query); close() }
                else scope.launch {
                    busy = true
                    try { actions.byPhone(side, query.trim(), phone); close() }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true }
                    finally { busy = false }
                }
            }, enabled = query.isNotBlank() && (mode == 2 || phone.count(Char::isDigit) in 10..13), loading = busy)
        }
        if (failed) Text(stringResource(Res.string.teams_action_failed))
    }
}

@Composable
private fun RulesSection(state: NewMatchFormState, actions: NewMatchActions) {
    var customising by rememberSaveable { mutableStateOf(false) }
    val preset = RulesPreset.matching(state.rules)

    Section(Res.string.new_match_section_rules) {
        Text(stringResource(Res.string.new_match_rules_hint), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        ChoiceChips(
            options = RulesPreset.entries.filter { it != RulesPreset.CUSTOM || preset == RulesPreset.CUSTOM },
            selected = preset,
            label = { stringResource(it.label) },
            onSelect = { picked -> picked.rules?.let { rules -> actions.onUpdate { it.copy(rules = rules) } } },
        )
        Text(stringResource(preset.hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        GhostButton(stringResource(Res.string.new_match_rules_customise), onClick = { customising = true }, modifier = Modifier.fillMaxWidth())
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
private fun Section(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        Text(stringResource(title), style = DesiTheme.type.titleMd, color = DesiTheme.colors.ink)
        content()
    }
}

/** Pinned under the form, above the keyboard. Form errors show here, next to the button they block. */
@Composable
private fun StartBar(state: NewMatchFormState, label: StringResource, enabled: Boolean, onStart: () -> Unit) {
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
                }
                Text(text, style = DesiTheme.type.body, color = colors.event.wicket)
            }
            PrimaryButton(
                text = stringResource(label),
                enabled = enabled,
                onClick = onStart,
                loading = state.saving,
                trailingIcon = DesiIcons.Forward,
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
