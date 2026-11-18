package com.harsh.desicrics.ui.teams

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
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TeamsScreen(
    onOpenTeam: (teamId: String) -> Unit,
    onBack: () -> Unit,
    /** From a join link: open the join sheet with this code filled in. */
    joinCode: String? = null,
    viewModel: TeamsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(if (joinCode != null) JOIN else null) }
    TeamsContent(state, onOpenTeam, onCreate = { sheet = CREATE }, onJoin = { sheet = JOIN }, onBack = onBack)
    val close = {
        sheet = null
        viewModel.clearErrors()
    }
    when (sheet) {
        CREATE -> TeamDetailsSheet(
            title = stringResource(Res.string.teams_create_title),
            action = stringResource(Res.string.teams_create_action),
            saving = state.saving,
            failed = state.actionFailed,
            onSave = { name, city -> viewModel.create(name, city) { close(); onOpenTeam(it) } },
            onDismiss = close,
        )
        JOIN -> JoinTeamSheet(
            initialCode = joinCode.orEmpty(),
            saving = state.saving,
            failed = state.joinFailed,
            onJoin = { code -> viewModel.join(code) { close(); onOpenTeam(it) } },
            onDismiss = close,
        )
    }
}

private const val CREATE = "create"
private const val JOIN = "join"

@Composable
fun TeamsContent(
    state: TeamsUiState,
    onOpenTeam: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
) {
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.teams_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    PrimaryButton(stringResource(Res.string.teams_create), onClick = onCreate, modifier = Modifier.weight(1f))
                    GhostButton(stringResource(Res.string.teams_join), onClick = onJoin, modifier = Modifier.weight(1f))
                }
            }
            if (state.loadFailed) {
                item { Text(stringResource(Res.string.teams_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
            }
            if (!state.loading && state.teams.isEmpty() && !state.loadFailed) {
                item { Text(stringResource(Res.string.teams_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted) }
            }
            items(state.teams, key = { it.id }) { TeamRow(it, onClick = { onOpenTeam(it.id) }) }
        }
    }
}

@Composable
internal fun TeamRow(team: TeamSummary, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth().clip(DesiTheme.radius.lg).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            Avatar(team.name, size = 44)
            Column(Modifier.weight(1f)) {
                Text(team.name, style = DesiTheme.type.bodyStrong, color = colors.ink)
                val count = pluralStringResource(Res.plurals.teams_member_count, team.memberCount, team.memberCount)
                Text(listOf(team.city, count).filter { it.isNotBlank() }.joinToString(" · "), style = DesiTheme.type.caption, color = colors.muted)
            }
            if (team.myRole == TeamRole.ADMIN) {
                Text(stringResource(Res.string.teams_role_admin), style = DesiTheme.type.label, color = colors.brand)
            }
        }
    }
}

/** Name and city, for a new team or renaming one. */
@Composable
internal fun TeamDetailsSheet(
    title: String,
    action: String,
    saving: Boolean,
    failed: Boolean,
    onSave: (name: String, city: String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialCity: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var city by rememberSaveable { mutableStateOf(initialCity) }
    DesiSheet(title, onDismiss) {
        DesiTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = stringResource(Res.string.teams_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        DesiTextField(
            value = city,
            onValueChange = { city = it.take(100) },
            label = stringResource(Res.string.teams_city),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        if (failed) Text(stringResource(Res.string.teams_action_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        PrimaryButton(action, onClick = { onSave(name, city) }, enabled = name.isNotBlank(), loading = saving, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun JoinTeamSheet(saving: Boolean, failed: Boolean, onJoin: (String) -> Unit, onDismiss: () -> Unit, initialCode: String = "") {
    var code by rememberSaveable { mutableStateOf(initialCode) }
    DesiSheet(stringResource(Res.string.teams_join_title), onDismiss, stringResource(Res.string.teams_join_hint)) {
        DesiTextField(
            value = code,
            onValueChange = { new -> code = new.filter { it.isLetterOrDigit() }.uppercase().take(8) },
            label = stringResource(Res.string.teams_join_code),
            error = stringResource(Res.string.teams_join_failed).takeIf { failed },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
        )
        PrimaryButton(
            stringResource(Res.string.teams_join_action),
            onClick = { onJoin(code) },
            enabled = code.length >= 6,
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Previews: sample team names only; all labels come from string resources.

@Preview
@Composable
private fun TeamsPreview() = DesiTheme(darkTheme = false) {
    TeamsContent(
        TeamsUiState(loading = false, teams = listOf(TeamSummary("t1", "Andheri Strikers", "Mumbai", 14, TeamRole.ADMIN), TeamSummary("t2", "Powai XI", "", 9))),
        {}, {}, {}, {},
    )
}

@Preview
@Composable
private fun TeamsEmptyDarkPreview() = DesiTheme(darkTheme = true) { TeamsContent(TeamsUiState(loading = false), {}, {}, {}, {}) }
