package com.harsh.desicrics.simple

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.RemovableChip
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.resources.Res
import com.harsh.desicrics.resources.new_match_add_player
import com.harsh.desicrics.resources.new_match_bats_first
import com.harsh.desicrics.resources.new_match_error_create_failed
import com.harsh.desicrics.resources.new_match_overs_fewer
import com.harsh.desicrics.resources.new_match_overs_label
import com.harsh.desicrics.resources.new_match_overs_more
import com.harsh.desicrics.resources.new_match_remove_player
import com.harsh.desicrics.resources.new_match_section_overs
import com.harsh.desicrics.resources.new_match_section_toss
import com.harsh.desicrics.resources.new_match_start
import com.harsh.desicrics.resources.new_match_team_a
import com.harsh.desicrics.resources.new_match_team_b
import com.harsh.desicrics.resources.new_match_team_name
import com.harsh.desicrics.resources.new_match_title
import com.harsh.desicrics.resources.new_match_toss_bat
import com.harsh.desicrics.resources.new_match_toss_bowl
import com.harsh.desicrics.resources.new_match_toss_chose
import com.harsh.desicrics.resources.new_match_toss_won
import com.harsh.desicrics.resources.simple_players_hint
import com.harsh.desicrics.ui.newmatch.NewMatchError
import com.harsh.desicrics.ui.newmatch.NewMatchFormState
import com.harsh.desicrics.ui.newmatch.NewMatchViewModel
import com.harsh.desicrics.ui.newmatch.SideForm
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Simple mode's matches: the relaxed rules, without the wagon wheel to tap after every shot. */
private val SimpleRules = MatchRules.Relaxed.copy(wagonWheel = false)

@Composable
internal fun SimpleNewMatchScreen(
    onCreated: (matchId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewMatchViewModel = koinViewModel(),
) {
    val form by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.update { it.copy(rules = SimpleRules) } }
    SimpleNewMatchContent(
        form = form,
        onTeamName = viewModel::setTeamName,
        onAddPlayer = viewModel::addPlayer,
        onRemovePlayer = viewModel::removePlayer,
        onOvers = { overs -> viewModel.update { it.copy(overs = overs) } },
        onTossWinner = { side -> viewModel.update { it.copy(tossWinner = side) } },
        onTossDecision = { decision -> viewModel.update { it.copy(tossDecision = decision) } },
        onStart = { viewModel.create(onCreated) },
        onBack = onBack,
    )
}

/** One page: two teams typed in, the overs and the toss. Everything else takes Simple mode's defaults. */
@Composable
internal fun SimpleNewMatchContent(
    form: NewMatchFormState,
    onTeamName: (TeamSide, String) -> Unit,
    onAddPlayer: (TeamSide, String) -> Unit,
    onRemovePlayer: (TeamSide, Int) -> Unit,
    onOvers: (Int) -> Unit,
    onTossWinner: (TeamSide) -> Unit,
    onTossDecision: (TossDecision) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.new_match_title), onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(space.screen),
            verticalArrangement = Arrangement.spacedBy(space.s5),
        ) {
            TeamSide.entries.forEach { side ->
                TeamFields(side, form.side(side), onName = { onTeamName(side, it) }, onAdd = { onAddPlayer(side, it) }, onRemove = { onRemovePlayer(side, it) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.new_match_section_overs))
                NumberStepper(
                    value = form.overs,
                    onValueChange = onOvers,
                    label = stringResource(Res.string.new_match_overs_label),
                    decreaseLabel = stringResource(Res.string.new_match_overs_fewer),
                    increaseLabel = stringResource(Res.string.new_match_overs_more),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.new_match_section_toss))
                Text(stringResource(Res.string.new_match_toss_won), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
                val names = TeamSide.entries.associateWith { side -> form.name(side).ifBlank { stringResource(side.placeholder) } }
                SegmentedControl(TeamSide.entries, form.tossWinner, label = { names.getValue(it) }, onSelect = onTossWinner)
                Text(stringResource(Res.string.new_match_toss_chose), style = DesiTheme.type.body, color = DesiTheme.colors.muted)
                SegmentedControl(
                    TossDecision.entries,
                    form.tossDecision,
                    label = { stringResource(if (it == TossDecision.BAT) Res.string.new_match_toss_bat else Res.string.new_match_toss_bowl) },
                    onSelect = onTossDecision,
                )
                val battingFirst = if (form.tossDecision == TossDecision.BAT) form.tossWinner else form.tossWinner.other
                Text(stringResource(Res.string.new_match_bats_first, names.getValue(battingFirst)), style = DesiTheme.type.bodyStrong, color = DesiTheme.colors.ink)
            }
            form.error?.let { error ->
                val text = when (error) {
                    is NewMatchError.Invalid -> stringResource(error.error.label)
                    else -> stringResource(Res.string.new_match_error_create_failed)
                }
                Text(text, style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
            }
            PrimaryButton(stringResource(Res.string.new_match_start), onClick = onStart, loading = form.saving, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamFields(side: TeamSide, team: SideForm, onName: (String) -> Unit, onAdd: (String) -> Unit, onRemove: (Int) -> Unit) {
    val space = DesiTheme.space
    var player by rememberSaveable(side) { mutableStateOf("") }
    val add = {
        onAdd(player)
        player = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(stringResource(side.placeholder))
        DesiTextField(
            value = team.name,
            onValueChange = onName,
            label = stringResource(Res.string.new_match_team_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        if (team.players.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                team.players.forEachIndexed { index, it ->
                    RemovableChip(it.name, stringResource(Res.string.new_match_remove_player, it.name), onRemove = { onRemove(index) })
                }
            }
        }
        DesiTextField(
            value = player,
            onValueChange = { player = it },
            label = stringResource(Res.string.new_match_add_player),
            supportingText = stringResource(Res.string.simple_players_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
    }
}

private val TeamSide.placeholder get() = if (this == TeamSide.A) Res.string.new_match_team_a else Res.string.new_match_team_b

// Previews: sample team and player names only; all labels come from string resources.

internal val SimplePreviewForm = NewMatchFormState(
    teamA = SideForm("Andheri Strikers", listOf("Rohan", "Aarav", "Kabir").map { SquadPlayer(it) }),
    teamB = SideForm("Powai XI", listOf("Dev", "Esha").map { SquadPlayer(it) }),
    overs = 8,
)

@Preview
@Composable
private fun SimpleNewMatchPreview() = DesiTheme(darkTheme = false) {
    SimpleNewMatchContent(SimplePreviewForm, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {})
}
