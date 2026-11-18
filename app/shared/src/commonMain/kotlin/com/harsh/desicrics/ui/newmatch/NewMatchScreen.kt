package com.harsh.desicrics.ui.newmatch

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
import com.harsh.desicrics.designsystem.component.BrandHeader
import com.harsh.desicrics.designsystem.component.BrandStatusBar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
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
    LightStatusBarIcons()
    NewMatchContent(
        state = state,
        actions = NewMatchActions(
            onTeamName = viewModel::setTeamName,
            onAddPlayer = viewModel::addPlayer,
            onRemovePlayer = viewModel::removePlayer,
            onUpdate = viewModel::update,
            onStart = { viewModel.create(onCreated) },
            onBack = onBack,
        ),
    )
}

/** Everything the new match form can ask for, kept together so the content signature stays short. */
class NewMatchActions(
    val onTeamName: (TeamSide, String) -> Unit,
    val onAddPlayer: (TeamSide, String) -> Unit,
    val onRemovePlayer: (TeamSide, index: Int) -> Unit,
    val onUpdate: ((NewMatchFormState) -> NewMatchFormState) -> Unit,
    val onStart: () -> Unit,
    val onBack: () -> Unit,
)

/** Overs most local matches use, offered as one-tap picks next to the stepper. */
private val QuickOvers = listOf(5, 6, 8, 10, 12, 20)

/** The new match form without its ViewModel, so previews and tests can render any state. */
@Composable
fun NewMatchContent(state: NewMatchFormState, actions: NewMatchActions) {
    val colors = DesiTheme.colors
    Column(Modifier.fillMaxSize().background(colors.bg).imePadding()) {
        BrandStatusBar()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BrandHeader(
                title = stringResource(Res.string.new_match_title),
                subtitle = stringResource(Res.string.new_match_subtitle),
                onBack = actions.onBack,
            )
            Column(
                Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s6),
                verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s8),
            ) {
                Section(Res.string.new_match_section_teams) {
                    TeamSide.entries.forEach { side -> TeamCard(side, state, actions) }
                }
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
                TossSection(state, actions)
                RulesSection(state, actions)
            }
        }
        StartBar(state, actions.onStart)
    }
}

@Composable
private fun TeamCard(side: TeamSide, state: NewMatchFormState, actions: NewMatchActions) {
    val colors = DesiTheme.colors
    val players = state.players(side)
    val invalid = (state.error as? NewMatchError.Invalid)?.error
    var newPlayer by rememberSaveable(side) { mutableStateOf("") }
    val add = {
        actions.onAddPlayer(side, newPlayer)
        newPlayer = ""
    }

    DesiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(side.label), Modifier.weight(1f))
            Text(pluralStringResource(Res.plurals.new_match_player_count, players.size, players.size), style = DesiTheme.type.caption, color = colors.muted)
        }
        DesiTextField(
            value = state.name(side),
            onValueChange = { actions.onTeamName(side, it) },
            label = stringResource(Res.string.new_match_team_name),
            error = invalid?.takeIf { state.name(side).isBlank() && it == MatchSetupError.TEAM_NAME_MISSING }?.let { stringResource(it.label) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        if (players.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                players.forEachIndexed { index, name ->
                    RemovableChip(name, stringResource(Res.string.new_match_remove_player, name), onRemove = { actions.onRemovePlayer(side, index) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            DesiTextField(
                value = newPlayer,
                onValueChange = { newPlayer = it },
                label = stringResource(Res.string.new_match_add_player),
                modifier = Modifier.weight(1f),
                // Enter adds the name and keeps the keyboard up for the next one.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            Box(
                Modifier
                    .size(DesiTheme.size.touchTarget)
                    .clip(DesiTheme.radius.full)
                    .background(if (newPlayer.isBlank()) colors.sunk else colors.brand)
                    .clickable(enabled = newPlayer.isNotBlank(), role = Role.Button, onClick = add),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(DesiIcons.Add),
                    contentDescription = stringResource(Res.string.new_match_add_player_action),
                    tint = if (newPlayer.isBlank()) colors.muted else colors.onBrand,
                )
            }
        }
        if (players.size < CreateMatchRequest.MIN_PLAYERS) {
            Text(stringResource(Res.string.new_match_players_hint), style = DesiTheme.type.caption, color = colors.muted)
        }
    }
}

@Composable
private fun TossSection(state: NewMatchFormState, actions: NewMatchActions) {
    val teamName: @Composable (TeamSide) -> String = { side -> state.name(side).trim().ifEmpty { stringResource(side.label) } }
    val battingFirst = if (state.tossDecision == TossDecision.BAT) state.tossWinner else state.tossWinner.other

    Section(Res.string.new_match_section_toss) {
        Text(stringResource(Res.string.new_match_toss_won), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        SegmentedControl(
            options = TeamSide.entries,
            selected = state.tossWinner,
            label = teamName,
            onSelect = { side -> actions.onUpdate { it.copy(tossWinner = side) } },
        )
        Text(stringResource(Res.string.new_match_toss_chose), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
        SegmentedControl(
            options = TossDecision.entries,
            selected = state.tossDecision,
            label = { stringResource(if (it == TossDecision.BAT) Res.string.new_match_toss_bat else Res.string.new_match_toss_bowl) },
            onSelect = { decision -> actions.onUpdate { it.copy(tossDecision = decision) } },
        )
        Text(
            stringResource(Res.string.new_match_bats_first, teamName(battingFirst)),
            style = DesiTheme.type.bodyStrong,
            color = DesiTheme.colors.brand,
        )
    }
}

/** A preset to start from, with a line on what it means; Customise opens every rule. */
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
private fun StartBar(state: NewMatchFormState, onStart: () -> Unit) {
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
                text = stringResource(Res.string.new_match_start),
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
    }

// Previews: sample team and player names only; all labels come from string resources.

private val PreviewActions = NewMatchActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})

private val PreviewFilled = NewMatchFormState(
    teamAName = "Andheri Strikers",
    teamAPlayers = listOf("Rohan", "Aarav", "Kabir", "Ishaan"),
    teamBName = "Powai XI",
    teamBPlayers = listOf("Dev"),
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
