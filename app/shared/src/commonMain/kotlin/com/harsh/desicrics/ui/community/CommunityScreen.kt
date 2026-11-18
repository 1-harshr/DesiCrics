package com.harsh.desicrics.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.SegmentedControl
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.social.PlayerLine
import com.harsh.desicrics.ui.teams.TeamRow
import com.harsh.desicrics.ui.text.epochMillisAt
import com.harsh.desicrics.ui.text.formatDayAndTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private sealed interface CommunitySheet {
    data object Post : CommunitySheet
    data class Respond(val post: LookingForPost) : CommunitySheet
    data object AddEntry : CommunitySheet
}

@Composable
fun CommunityScreen(
    city: String,
    myId: String,
    onOpenPlayer: (String) -> Unit,
    onOpenTeam: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CommunityViewModel = koinViewModel { parametersOf(city) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<CommunitySheet?>(null) }
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    val close = {
        sheet = null
        viewModel.clearError()
    }

    DesiScaffold(title = stringResource(Res.string.community_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            DesiTextField(
                value = state.city,
                onValueChange = viewModel::setCity,
                label = stringResource(Res.string.community_city),
                supportingText = stringResource(Res.string.community_city_hint).takeIf { state.city.isBlank() },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            SegmentedControl(CommunityTab.entries, selected = state.tab, label = { stringResource(it.label) }, onSelect = viewModel::setTab)
            if (state.failed) Text(stringResource(Res.string.community_load_failed), style = DesiTheme.type.body, color = colors.event.wicket)
            when (state.tab) {
                CommunityTab.LOOKING_FOR -> {
                    PrimaryButton(stringResource(Res.string.looking_for_post), onClick = { sheet = CommunitySheet.Post }, enabled = state.city.isNotBlank(), modifier = Modifier.fillMaxWidth())
                    if (!state.loading && state.posts.isEmpty()) Text(stringResource(Res.string.looking_for_empty), style = DesiTheme.type.body, color = colors.muted)
                    state.posts.forEach { post ->
                        PostCard(post) {
                            when {
                                post.author.profileId == myId -> Unit
                                post.id in state.answered -> Text(stringResource(Res.string.looking_for_responded), style = DesiTheme.type.caption, color = colors.brand)
                                else -> TextButton(onClick = { sheet = CommunitySheet.Respond(post) }) {
                                    Text(stringResource(Res.string.looking_for_respond), style = DesiTheme.type.button, color = colors.brand)
                                }
                            }
                        }
                    }
                    if (state.myPosts.isNotEmpty()) {
                        SectionLabel(stringResource(Res.string.looking_for_mine), Modifier.padding(top = space.s4))
                        state.myPosts.forEach { post ->
                            PostCard(post) {
                                if (post.open) {
                                    TextButton(onClick = { viewModel.close(post.id) }) {
                                        Text(stringResource(Res.string.looking_for_close), style = DesiTheme.type.button, color = colors.muted)
                                    }
                                } else Text(stringResource(Res.string.looking_for_closed), style = DesiTheme.type.caption, color = colors.muted)
                            }
                            // The replies, with numbers to call: only the author ever sees these.
                            post.responses.forEach { reply ->
                                Text("${reply.from.name} · ${reply.phone}: ${reply.message}", style = DesiTheme.type.body, color = colors.ink, modifier = Modifier.padding(start = space.s4))
                            }
                        }
                    }
                }
                CommunityTab.RANKINGS -> {
                    SegmentedControl(
                        listOf(false, true),
                        selected = state.rankPlayers,
                        label = { stringResource(if (it) Res.string.rankings_players else Res.string.rankings_teams) },
                        onSelect = viewModel::setRankPlayers,
                    )
                    Text(stringResource(Res.string.rankings_hint), style = DesiTheme.type.caption, color = colors.muted)
                    val empty = if (state.rankPlayers) state.playerRankings.isEmpty() else state.teamRankings.isEmpty()
                    if (!state.loading && empty) Text(stringResource(Res.string.rankings_empty), style = DesiTheme.type.body, color = colors.muted)
                    if (state.rankPlayers) {
                        state.playerRankings.forEachIndexed { i, row ->
                            RankRow(i + 1, "${row.score}") { PlayerLine(row.player, onClick = { onOpenPlayer(row.player.profileId) }) }
                        }
                    } else {
                        state.teamRankings.forEachIndexed { i, row ->
                            RankRow(i + 1, "${row.rating}") { TeamRow(row.team, onClick = { onOpenTeam(row.team.id) }) }
                        }
                    }
                }
                CommunityTab.DIRECTORY -> {
                    ChoiceChips(DirectoryKind.entries, selected = state.directoryKind, label = { stringResource(it.label) }, onSelect = viewModel::setDirectoryKind)
                    PrimaryButton(stringResource(Res.string.directory_add), onClick = { sheet = CommunitySheet.AddEntry }, enabled = state.city.isNotBlank(), modifier = Modifier.fillMaxWidth())
                    if (!state.loading && state.directory.isEmpty()) Text(stringResource(Res.string.directory_empty), style = DesiTheme.type.body, color = colors.muted)
                    state.directory.forEach { entry ->
                        DesiCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = DesiTheme.type.bodyStrong, color = colors.ink)
                                    val details = listOf(entry.phone, entry.notes).filter { it.isNotBlank() }.joinToString(" · ")
                                    if (details.isNotEmpty()) Text(details, style = DesiTheme.type.caption, color = colors.muted)
                                }
                                if (entry.addedBy == myId) {
                                    TextButton(onClick = { viewModel.removeEntry(entry.id) }) {
                                        Text(stringResource(Res.string.directory_remove), style = DesiTheme.type.button, color = colors.muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (val current = sheet) {
        null -> Unit
        CommunitySheet.Post -> PostSheet(state.adminTeams, state.saving, state.actionFailed, onPost = { kind, date, note, team ->
            viewModel.post(kind, date, note, team, onDone = close)
        }, onDismiss = close)
        is CommunitySheet.Respond -> RespondSheet(current.post, state.saving, state.actionFailed, onSend = { message, phone ->
            viewModel.respond(current.post.id, message, phone, onDone = close)
        }, onDismiss = close)
        CommunitySheet.AddEntry -> AddEntrySheet(state.saving, state.actionFailed, onAdd = { name, phone, notes ->
            viewModel.addEntry(name, phone, notes, onDone = close)
        }, onDismiss = close)
    }
}

@Composable
private fun PostCard(post: LookingForPost, action: @Composable () -> Unit) {
    val colors = DesiTheme.colors
    DesiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
                Text(stringResource(post.kind.label), style = DesiTheme.type.bodyStrong, color = colors.ink)
                Text(
                    stringResource(Res.string.looking_for_by, post.team?.name ?: post.author.name, formatDayAndTime(post.date).substringBefore(' '), post.city),
                    style = DesiTheme.type.caption,
                    color = colors.muted,
                )
                if (post.note.isNotBlank()) Text(post.note, style = DesiTheme.type.body, color = colors.ink)
                if (post.responseCount > 0) {
                    Text(pluralStringResource(Res.plurals.looking_for_responses, post.responseCount, post.responseCount), style = DesiTheme.type.caption, color = colors.brand)
                }
            }
            action()
        }
    }
}

@Composable
private fun RankRow(place: Int, value: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        Text("$place", style = DesiTheme.type.scoreMd, color = DesiTheme.colors.muted)
        Column(Modifier.weight(1f)) { content() }
        Text(value, style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostSheet(
    teams: List<TeamSummary>,
    saving: Boolean,
    failed: Boolean,
    onPost: (LookingForKind, Long, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(LookingForKind.OPPONENT) }
    var note by rememberSaveable { mutableStateOf("") }
    var team by remember { mutableStateOf<TeamSummary?>(null) }
    val date = rememberDatePickerState()
    DesiSheet(stringResource(Res.string.looking_for_post_title), onDismiss) {
        ChoiceChips(LookingForKind.entries, selected = kind, label = { stringResource(it.label) }, onSelect = { kind = it })
        if (teams.isNotEmpty()) {
            SectionLabel(stringResource(Res.string.looking_for_as_team))
            val myself = stringResource(Res.string.looking_for_myself)
            ChoiceChips(listOf<TeamSummary?>(null) + teams, selected = team, label = { it?.name ?: myself }, onSelect = { team = it })
        }
        SectionLabel(stringResource(Res.string.looking_for_date))
        DatePicker(date, title = null, headline = null, showModeToggle = false)
        DesiTextField(value = note, onValueChange = { note = it.take(500) }, label = stringResource(Res.string.looking_for_note), singleLine = false)
        if (failed) Text(stringResource(Res.string.community_action_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        PrimaryButton(
            stringResource(Res.string.looking_for_send),
            onClick = { date.selectedDateMillis?.let { onPost(kind, epochMillisAt(it, 0, 0), note, team?.id) } },
            enabled = date.selectedDateMillis != null,
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RespondSheet(post: LookingForPost, saving: Boolean, failed: Boolean, onSend: (String, String) -> Unit, onDismiss: () -> Unit) {
    var message by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.looking_for_respond_title, post.team?.name ?: post.author.name), onDismiss) {
        DesiTextField(value = message, onValueChange = { message = it.take(500) }, label = stringResource(Res.string.looking_for_message), singleLine = false)
        DesiTextField(
            value = phone,
            onValueChange = { new -> phone = new.filter { it.isDigit() || it == '+' || it == ' ' }.take(16) },
            label = stringResource(Res.string.looking_for_phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        if (failed) Text(stringResource(Res.string.community_action_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        PrimaryButton(
            stringResource(Res.string.looking_for_respond_send),
            onClick = { onSend(message, phone) },
            enabled = message.isNotBlank() && phone.count { it.isDigit() } >= 10,
            loading = saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AddEntrySheet(saving: Boolean, failed: Boolean, onAdd: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.directory_add), onDismiss) {
        DesiTextField(value = name, onValueChange = { name = it.take(100) }, label = stringResource(Res.string.directory_name))
        DesiTextField(
            value = phone,
            onValueChange = { new -> phone = new.filter { it.isDigit() || it == '+' || it == ' ' }.take(16) },
            label = stringResource(Res.string.directory_phone),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        DesiTextField(value = notes, onValueChange = { notes = it.take(500) }, label = stringResource(Res.string.directory_notes), singleLine = false)
        if (failed) Text(stringResource(Res.string.community_action_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket)
        PrimaryButton(stringResource(Res.string.directory_save), onClick = { onAdd(name, phone, notes) }, enabled = name.isNotBlank(), loading = saving, modifier = Modifier.fillMaxWidth())
    }
}

private val CommunityTab.label: StringResource
    get() = when (this) {
        CommunityTab.LOOKING_FOR -> Res.string.community_tab_looking_for
        CommunityTab.RANKINGS -> Res.string.community_tab_rankings
        CommunityTab.DIRECTORY -> Res.string.community_tab_directory
    }

private val LookingForKind.label: StringResource
    get() = when (this) {
        LookingForKind.OPPONENT -> Res.string.looking_for_opponent
        LookingForKind.PLAYER -> Res.string.looking_for_player
        LookingForKind.GROUND -> Res.string.looking_for_ground
        LookingForKind.UMPIRE -> Res.string.looking_for_umpire
        LookingForKind.SCORER -> Res.string.looking_for_scorer
    }

private val DirectoryKind.label: StringResource
    get() = when (this) {
        DirectoryKind.GROUND -> Res.string.directory_ground
        DirectoryKind.UMPIRE -> Res.string.directory_umpire
        DirectoryKind.SCORER -> Res.string.directory_scorer
    }
