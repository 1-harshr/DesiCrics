package com.harsh.desicrics.ui.community
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.RankBadge
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.ui.components.PhotoAvatar
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

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
                leadingIcon = DesiIcons.Location,
                supportingText = stringResource(Res.string.community_city_hint).takeIf { state.city.isBlank() },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            SegmentedControl(CommunityTab.entries, selected = state.tab, label = { stringResource(it.label) }, onSelect = viewModel::setTab)
            if (state.failed) ErrorState(stringResource(Res.string.error_community_title), stringResource(Res.string.error_connection_body), onRetry = viewModel::retry, compact = true)
            when (state.tab) {
                CommunityTab.LOOKING_FOR -> {
                    PrimaryButton(stringResource(Res.string.looking_for_post), onClick = { sheet = CommunitySheet.Post }, enabled = state.city.isNotBlank(), modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Add)
                    // Your own posts are listed below, with their replies.
                    val others = state.posts.filter { it.author.profileId != myId }
                    if (!state.loading && state.city.isNotBlank() && others.isEmpty()) {
                        EmptyState(DesiIcons.Search, stringResource(Res.string.looking_for_empty))
                    }
                    others.forEach { post ->
                        PostCard(post) {
                            when {
                                post.id in state.answered -> Text(stringResource(Res.string.looking_for_responded), style = DesiTheme.type.caption, color = colors.brand)
                                else -> TextButton(onClick = { sheet = CommunitySheet.Respond(post) }) {
                                    Text(stringResource(Res.string.looking_for_respond), style = DesiTheme.type.button, color = colors.brand)
                                }
                            }
                        }
                    }
                    if (state.myPosts.isNotEmpty()) {
                        SectionHeader(stringResource(Res.string.looking_for_mine), Modifier.padding(top = space.s4), icon = DesiIcons.History)
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
                    if (!state.loading && state.city.isNotBlank() && empty) EmptyState(DesiIcons.Leaderboard, stringResource(Res.string.rankings_empty))
                    if (!empty) ListCard {
                        if (state.rankPlayers) {
                            state.playerRankings.forEachIndexed { i, row ->
                                RankRow(i + 1, "${row.score}", row.player.name, row.player.city, row.player.photoUrl) { onOpenPlayer(row.player.profileId) }
                            }
                        } else {
                            state.teamRankings.forEachIndexed { i, row ->
                                RankRow(i + 1, "${row.rating}", row.team.name, row.team.city, row.team.logoUrl) { onOpenTeam(row.team.id) }
                            }
                        }
                    }
                }
                CommunityTab.DIRECTORY -> {
                    ChoiceChips(DirectoryKind.entries, selected = state.directoryKind, label = { stringResource(it.label) }, onSelect = viewModel::setDirectoryKind)
                    PrimaryButton(stringResource(Res.string.directory_add), onClick = { sheet = CommunitySheet.AddEntry }, enabled = state.city.isNotBlank(), modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Add)
                    if (!state.loading && state.city.isNotBlank() && state.directory.isEmpty()) EmptyState(state.directoryKind.icon, stringResource(Res.string.directory_empty))
                    if (state.directory.isNotEmpty()) ListCard {
                        state.directory.forEachIndexed { index, entry ->
                            ListRow(
                                title = entry.name,
                                subtitle = listOf(entry.phone, entry.notes).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null },
                                leading = { IconTile(entry.kind.icon) },
                                trailing = if (entry.addedBy == myId) ({
                                    androidx.compose.material3.IconButton(onClick = { viewModel.removeEntry(entry.id) }) {
                                        Icon(painterResource(DesiIcons.Delete), stringResource(Res.string.directory_remove), tint = colors.muted)
                                    }
                                }) else null,
                                divider = index > 0,
                            )
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
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            IconTile(post.kind.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
                Text(stringResource(post.kind.label), style = DesiTheme.type.bodyStrong, color = colors.ink)
                Text(
                    stringResource(Res.string.looking_for_by, post.team?.name ?: post.author.name, formatDayAndTime(post.date).substringBefore(' '), post.city),
                    style = DesiTheme.type.caption,
                    color = colors.muted,
                )
                if (post.note.isNotBlank()) Text(post.note, style = DesiTheme.type.body, color = colors.ink)
                if (post.responseCount > 0) {
                    Tag(pluralStringResource(Res.plurals.looking_for_responses, post.responseCount, post.responseCount), tone = TagTone.BRAND)
                }
            }
        }
        Box(Modifier.align(Alignment.End)) { action() }
    }
}

/** A place in a city ranking: badge, photo or logo, name and city, then the score. */
@Composable
private fun RankRow(place: Int, value: String, name: String, city: String, photo: String?, onClick: () -> Unit) {
    ListRow(
        title = name,
        subtitle = city.ifBlank { null },
        leading = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
                RankBadge(place, size = 28.dp)
                PhotoAvatar(name, photo, size = 40)
            }
        },
        trailing = { Text(value, style = DesiTheme.type.scoreMd, color = if (place == 1) DesiTheme.colors.brand else DesiTheme.colors.ink) },
        divider = place > 1,
        onClick = onClick,
    )
}

private val LookingForKind.icon: DrawableResource
    get() = when (this) {
        LookingForKind.OPPONENT -> DesiIcons.Swap
        LookingForKind.PLAYER -> DesiIcons.PersonAdd
        LookingForKind.GROUND -> DesiIcons.Stadium
        LookingForKind.UMPIRE -> DesiIcons.Whistle
        LookingForKind.SCORER -> DesiIcons.Edit
    }

private val DirectoryKind.icon: DrawableResource
    get() = when (this) {
        DirectoryKind.GROUND -> DesiIcons.Stadium
        DirectoryKind.UMPIRE -> DesiIcons.Whistle
        DirectoryKind.SCORER -> DesiIcons.Edit
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
            SectionHeader(stringResource(Res.string.looking_for_as_team), icon = DesiIcons.Shield)
            val myself = stringResource(Res.string.looking_for_myself)
            ChoiceChips(listOf<TeamSummary?>(null) + teams, selected = team, label = { it?.name ?: myself }, onSelect = { team = it })
        }
        SectionHeader(stringResource(Res.string.looking_for_date), icon = DesiIcons.Calendar)
        DatePicker(date, title = null, headline = null, showModeToggle = false)
        DesiTextField(value = note, onValueChange = { note = it.take(500) }, label = stringResource(Res.string.looking_for_note), singleLine = false)
        if (failed) ErrorBanner(stringResource(Res.string.community_action_failed))
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
        if (failed) ErrorBanner(stringResource(Res.string.community_action_failed))
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
        if (failed) ErrorBanner(stringResource(Res.string.community_action_failed))
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
