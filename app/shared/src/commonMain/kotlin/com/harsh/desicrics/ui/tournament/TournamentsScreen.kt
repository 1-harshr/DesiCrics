package com.harsh.desicrics.ui.tournament
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.component.heroBackground
import com.harsh.desicrics.designsystem.icon.DesiIcons
import org.jetbrains.compose.resources.painterResource

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
    TournamentsContent(state, onOpen, onCreate = { creating = true }, onBack, onRetry = viewModel::retry)
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
fun TournamentsContent(state: TournamentsUiState, onOpen: (String) -> Unit, onCreate: () -> Unit, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.tournaments_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { CreateTournamentCard(onCreate) }
            if (state.loadFailed) {
                item { ErrorState(stringResource(Res.string.error_tournaments_title), stringResource(Res.string.error_connection_body), onRetry = onRetry, compact = true) }
            } else if (!state.loading && state.tournaments.isEmpty()) {
                item { EmptyState(DesiIcons.Trophy, stringResource(Res.string.tournaments_empty_title), body = stringResource(Res.string.tournaments_empty)) }
            }
            if (state.tournaments.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.tournaments_yours), icon = DesiIcons.Trophy, modifier = Modifier.padding(top = space.s2)) }
                item {
                    ListCard {
                        state.tournaments.forEachIndexed { index, t -> TournamentRow(t, divider = index > 0, onClick = { onOpen(t.id) }) }
                    }
                }
            }
        }
    }
}

/** The way into running a tournament: a green card like Home's Start a match. */
@Composable
private fun CreateTournamentCard(onCreate: () -> Unit) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).heroBackground().clickable(role = Role.Button, onClick = onCreate).padding(space.s5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.s4),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s1)) {
            Text(stringResource(Res.string.tournaments_create), style = DesiTheme.type.titleMd, color = colors.onBrandStrong)
            Text(stringResource(Res.string.tournaments_create_body), style = DesiTheme.type.body, color = colors.onBrandStrong.copy(alpha = 0.8f))
        }
        IconTile(DesiIcons.TrophyFilled, size = 56.dp, tint = colors.brandStrong, container = colors.onBrandStrong)
    }
}

@Composable
private fun TournamentRow(tournament: TournamentSummary, divider: Boolean, onClick: () -> Unit) {
    val details = listOf(
        stringResource(tournament.format.label),
        pluralStringResource(Res.plurals.tournaments_team_count, tournament.teams, tournament.teams),
        tournament.city,
    )
    ListRow(
        title = tournament.name,
        subtitle = details.filter { it.isNotBlank() }.joinToString(" · "),
        leading = { IconTile(DesiIcons.Trophy, size = 44.dp) },
        trailing = {
            if (tournament.isOrganiser) Tag(stringResource(Res.string.tournaments_organiser), tone = TagTone.BRAND, icon = DesiIcons.Whistle)
            Icon(painterResource(DesiIcons.ChevronRight), null, tint = DesiTheme.colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
        },
        divider = divider,
        onClick = onClick,
    )
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
        if (failed) ErrorBanner(stringResource(Res.string.tournament_create_failed))
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
