package com.harsh.desicrics.ui.tournament

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.NumberStepper
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.newmatch.RulesPreset
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TournamentsScreen(
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TournamentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    TournamentsContent(state, onOpen, onCreate = { creating = true }, onBack)
    if (creating) {
        CreateTournamentSheet(
            saving = state.saving,
            failed = state.createFailed,
            onCreate = { request -> viewModel.create(request) { creating = false; onOpen(it) } },
            onDismiss = {
                creating = false
                viewModel.clearError()
            },
        )
    }
}

@Composable
fun TournamentsContent(state: TournamentsUiState, onOpen: (String) -> Unit, onCreate: () -> Unit, onBack: () -> Unit) {
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.tournaments_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { PrimaryButton(stringResource(Res.string.tournaments_create), onClick = onCreate, modifier = Modifier.fillMaxWidth()) }
            if (state.loadFailed) {
                item { Text(stringResource(Res.string.tournaments_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
            } else if (!state.loading && state.tournaments.isEmpty()) {
                item { Text(stringResource(Res.string.tournaments_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted) }
            }
            items(state.tournaments, key = { it.id }) { TournamentRow(it, onClick = { onOpen(it.id) }) }
        }
    }
}

@Composable
private fun TournamentRow(tournament: TournamentSummary, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            Avatar(tournament.name, size = 44)
            Column(Modifier.weight(1f)) {
                Text(tournament.name, style = DesiTheme.type.bodyStrong, color = colors.ink)
                val details = listOf(
                    stringResource(tournament.format.label),
                    pluralStringResource(Res.plurals.tournaments_team_count, tournament.teams, tournament.teams),
                    tournament.city,
                )
                Text(details.filter { it.isNotBlank() }.joinToString(" · "), style = DesiTheme.type.caption, color = colors.muted)
            }
            if (tournament.isOrganiser) Text(stringResource(Res.string.tournaments_organiser), style = DesiTheme.type.label, color = colors.brand)
        }
    }
}

/** Name, format, overs and how the format's playoffs work; match rules start from a preset. */
@Composable
private fun CreateTournamentSheet(saving: Boolean, failed: Boolean, onCreate: (CreateTournamentRequest) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(TournamentFormat.KNOCKOUT) }
    var overs by rememberSaveable { mutableStateOf(10) }
    var groups by rememberSaveable { mutableStateOf(2) }
    var advance by rememberSaveable { mutableStateOf(2) }
    var preset by rememberSaveable { mutableStateOf(RulesPreset.RELAXED) }
    var registration by rememberSaveable { mutableStateOf(false) }
    val advanceOptions = when (format) {
        TournamentFormat.ROUND_ROBIN -> listOf(0, 2, 4)
        TournamentFormat.GROUPS_KNOCKOUT -> listOf(1, 2, 4)
        else -> emptyList()
    }

    DesiSheet(stringResource(Res.string.tournament_create_title), onDismiss) {
        DesiTextField(
            value = name,
            onValueChange = { name = it.take(CreateTournamentRequest.MAX_NAME) },
            label = stringResource(Res.string.tournament_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        DesiTextField(
            value = city,
            onValueChange = { city = it.take(100) },
            label = stringResource(Res.string.tournament_city),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        SectionLabel(stringResource(Res.string.tournament_format))
        ChoiceChips(TournamentFormat.entries, selected = format, label = { stringResource(it.label) }, onSelect = {
            format = it
            advance = if (it == TournamentFormat.ROUND_ROBIN) 0 else 2
        })
        Text(stringResource(format.hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        NumberStepper(
            value = overs,
            onValueChange = { overs = it },
            label = stringResource(Res.string.new_match_overs_label),
            decreaseLabel = stringResource(Res.string.new_match_overs_fewer),
            increaseLabel = stringResource(Res.string.new_match_overs_more),
            range = CreateMatchRequest.OVERS,
        )
        if (format == TournamentFormat.GROUPS_KNOCKOUT) {
            NumberStepper(
                value = groups,
                onValueChange = { groups = it },
                label = stringResource(Res.string.tournament_groups),
                decreaseLabel = stringResource(Res.string.tournament_groups_fewer),
                increaseLabel = stringResource(Res.string.tournament_groups_more),
                range = 2..8,
            )
        }
        if (advanceOptions.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.tournament_advance))
            val none = stringResource(Res.string.tournament_advance_none)
            ChoiceChips(advanceOptions, selected = advance, label = { if (it == 0) none else "$it" }, onSelect = { advance = it })
        }
        SectionLabel(stringResource(Res.string.new_match_section_rules))
        ChoiceChips(RulesPreset.entries.filter { it.rules != null }, selected = preset, label = { stringResource(it.label) }, onSelect = { preset = it })
        Text(stringResource(preset.hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        SectionLabel(stringResource(Res.string.tournament_registration))
        Text(stringResource(Res.string.tournament_registration_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        SegmentedControl(
            options = listOf(false, true),
            selected = registration,
            label = { stringResource(if (it) Res.string.rules_on else Res.string.rules_mode_off) },
            onSelect = { registration = it },
        )
        if (failed) Text(stringResource(Res.string.tournament_create_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        PrimaryButton(
            stringResource(Res.string.tournament_create_action),
            onClick = {
                onCreate(
                    CreateTournamentRequest(
                        name = name.trim(),
                        city = city.trim(),
                        format = format,
                        oversPerInnings = overs,
                        rules = preset.rules!!,
                        groups = if (format == TournamentFormat.GROUPS_KNOCKOUT) groups else 1,
                        advance = if (advanceOptions.isEmpty()) 0 else advance,
                        registrationOpen = registration,
                    ),
                )
            },
            enabled = name.isNotBlank(),
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun TournamentsPreview() = DesiTheme(darkTheme = false) {
    TournamentsContent(
        TournamentsUiState(loading = false, tournaments = listOf(TournamentSummary("t", "Andheri Premier League", "Mumbai", TournamentFormat.GROUPS_KNOCKOUT, 8, true))),
        {}, {}, {},
    )
}
