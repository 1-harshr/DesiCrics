package com.harsh.desicrics.ui.teams

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamMember
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.match.OptionsSheet
import com.harsh.desicrics.ui.match.SheetOption
import com.harsh.desicrics.ui.system.rememberShareText
import com.harsh.desicrics.ui.text.label
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the team screen has open on top of it. */
private sealed interface TeamSheet {
    data object Add : TeamSheet
    data object Invite : TeamSheet
    data object Rename : TeamSheet
    data class Member(val member: TeamMember) : TeamSheet
}

@Composable
fun TeamScreen(
    teamId: String,
    onBack: () -> Unit,
    viewModel: TeamViewModel = koinViewModel { parametersOf(teamId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<TeamSheet?>(null) }
    val share = rememberShareText()
    val close = {
        sheet = null
        viewModel.clearError()
    }

    TeamContent(
        state = state,
        onBack = onBack,
        onAdd = { sheet = TeamSheet.Add },
        onInvite = { sheet = TeamSheet.Invite },
        onRename = { sheet = TeamSheet.Rename },
        onMember = { sheet = TeamSheet.Member(it) },
        onCompare = viewModel::compareWith,
        onLeave = { viewModel.leave(onBack) },
    )

    val team = state.team ?: return
    when (val current = sheet) {
        null -> Unit
        TeamSheet.Add -> AddMemberSheet(
            state = state,
            onSearch = viewModel::search,
            onPick = { viewModel.addPlayer(it.profileId); close() },
            onPlaceholder = { name, phone -> viewModel.addPlaceholder(name, phone); close() },
            onDismiss = close,
        )
        TeamSheet.Invite -> InviteSheet(
            team = team,
            link = team.joinCode?.let(viewModel::joinLink),
            onShare = { text -> share(text) },
            onNewCode = viewModel::newJoinCode,
            onDismiss = close,
        )
        TeamSheet.Rename -> TeamDetailsSheet(
            title = stringResource(Res.string.team_rename),
            action = stringResource(Res.string.teams_save_action),
            saving = state.saving,
            failed = state.actionFailed,
            initialName = team.name,
            initialCity = team.city,
            onSave = { name, city -> viewModel.rename(name, city); close() },
            onDismiss = close,
        )
        is TeamSheet.Member -> {
            val member = current.member
            val claimText = member.claimCode?.let { stringResource(Res.string.team_share_claim_text, team.name, it) }
            OptionsSheet(
                title = member.player.name,
                options = buildList {
                    if (member.role == TeamRole.MEMBER && !member.player.isPlaceholder) {
                        add(SheetOption(stringResource(Res.string.team_make_admin), stringResource(Res.string.team_make_admin_hint)) {
                            viewModel.setRole(member.player.profileId, TeamRole.ADMIN); close()
                        })
                    }
                    if (member.role == TeamRole.ADMIN) {
                        add(SheetOption(stringResource(Res.string.team_remove_admin), stringResource(Res.string.team_remove_admin_hint)) {
                            viewModel.setRole(member.player.profileId, TeamRole.MEMBER); close()
                        })
                    }
                    if (claimText != null) {
                        add(SheetOption(stringResource(Res.string.team_share_claim), stringResource(Res.string.team_share_claim_hint)) {
                            share(claimText); close()
                        })
                    }
                    add(SheetOption(stringResource(Res.string.team_remove), stringResource(Res.string.team_remove_hint)) {
                        viewModel.remove(member.player.profileId); close()
                    })
                },
                onDismiss = close,
            )
        }
    }
}

@Composable
fun TeamContent(
    state: TeamUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onInvite: () -> Unit,
    onRename: () -> Unit,
    onMember: (TeamMember) -> Unit,
    onCompare: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val team = state.team
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    DesiScaffold(title = team?.name ?: stringResource(Res.string.teams_title), onBack = onBack) { padding ->
        when {
            team == null && state.loading -> FullScreenLoading(Modifier.padding(padding))
            team == null -> Text(
                stringResource(Res.string.teams_load_failed),
                style = DesiTheme.type.body,
                color = colors.event.wicket,
                modifier = Modifier.padding(padding).padding(space.screen),
            )
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = space.screen, vertical = space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s4),
            ) {
                if (team.city.isNotBlank()) Text(team.city, style = DesiTheme.type.body, color = colors.muted)
                if (state.actionFailed) {
                    Text(stringResource(Res.string.teams_action_failed), style = DesiTheme.type.body, color = colors.event.wicket)
                }
                if (state.isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                        PrimaryButton(stringResource(Res.string.team_add_player), onClick = onAdd, modifier = Modifier.weight(1f))
                        TonalButton(stringResource(Res.string.team_invite), onClick = onInvite, modifier = Modifier.weight(1f))
                    }
                }
                SectionLabel(stringResource(Res.string.team_members))
                DesiCard(Modifier.fillMaxWidth()) {
                    team.members.forEach { member ->
                        val canManage = state.isAdmin && member.player.profileId != state.myId
                        PlayerRow(
                            name = member.player.name,
                            subtitle = memberSubtitle(member),
                            avatar = { Avatar(member.player.name) },
                            onClick = if (canManage) ({ onMember(member) }) else null,
                        )
                    }
                }
                if (state.otherTeams.isNotEmpty()) {
                    RecordAgainst(state.otherTeams, state.headToHead?.let { h2h -> state.otherTeams.firstOrNull { it.id == h2h.opponentId } }, state, onCompare)
                }
                if (state.isAdmin) GhostButton(stringResource(Res.string.team_rename), onClick = onRename, modifier = Modifier.fillMaxWidth())
                if (state.myRole != null) GhostButton(stringResource(Res.string.team_leave), onClick = onLeave, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun memberSubtitle(member: TeamMember): String? {
    val parts = buildList {
        if (member.role == TeamRole.ADMIN) add(stringResource(Res.string.teams_role_admin))
        member.player.role?.let { add(stringResource(it.label)) }
        if (member.player.isPlaceholder) add(stringResource(Res.string.team_placeholder))
        member.claimCode?.let { add(stringResource(Res.string.team_claim_code, it)) }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** Pick another of the caller's teams to see how this one has done against it. */
@Composable
private fun RecordAgainst(others: List<TeamSummary>, selected: TeamSummary?, state: TeamUiState, onCompare: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionLabel(stringResource(Res.string.team_record_against))
        ChoiceChips(options = others, selected = selected, label = { it.name }, onSelect = { onCompare(it.id) })
        state.headToHead?.let { h2h ->
            Text(
                stringResource(Res.string.team_record, h2h.played, h2h.won, h2h.lost, h2h.tied),
                style = DesiTheme.type.data,
                color = DesiTheme.colors.ink,
            )
        }
    }
}

/** Search for someone on the app, or add a placeholder for someone who isn't. */
@Composable
private fun AddMemberSheet(
    state: TeamUiState,
    onSearch: (String) -> Unit,
    onPick: (PlayerSummary) -> Unit,
    onPlaceholder: (name: String, phone: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val memberIds = state.team?.members.orEmpty().map { it.player.profileId }.toSet()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    DesiSheet(stringResource(Res.string.team_add_title), onDismiss) {
        DesiTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            label = stringResource(Res.string.team_add_search),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
        val results = state.searchResults.filter { it.profileId !in memberIds }
        if (state.searchQuery.trim().length >= 2 && !state.searching && results.isEmpty()) {
            Text(stringResource(Res.string.team_add_search_empty), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        }
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            results.forEach { player ->
                PlayerRow(
                    name = player.name,
                    subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }).joinToString(" · ").ifEmpty { null },
                    avatar = { Avatar(player.name) },
                    onClick = { onPick(player) },
                )
            }
        }
        SectionLabel(stringResource(Res.string.team_add_placeholder_title), Modifier.padding(top = DesiTheme.space.s2))
        Text(stringResource(Res.string.team_add_placeholder_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        DesiTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = stringResource(Res.string.team_add_placeholder_name),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        DesiTextField(
            value = phone,
            onValueChange = { new -> phone = new.filter { it.isDigit() || it == '+' || it == ' ' }.take(16) },
            label = stringResource(Res.string.team_add_placeholder_phone),
            supportingText = stringResource(Res.string.team_add_placeholder_phone_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
        PrimaryButton(
            stringResource(Res.string.team_add_placeholder_action, name.trim()),
            onClick = { onPlaceholder(name, phone) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The team's QR code and join code, with a share button for WhatsApp and a way to retire the code. */
@Composable
private fun InviteSheet(team: SavedTeam, link: String?, onShare: (String) -> Unit, onNewCode: () -> Unit, onDismiss: () -> Unit) {
    val code = team.joinCode ?: return
    val inviteText = stringResource(Res.string.team_invite_text, team.name, link.orEmpty(), code)
    DesiSheet(stringResource(Res.string.team_invite_title, team.name), onDismiss, stringResource(Res.string.team_invite_hint)) {
        if (link != null) {
            // Always black on white, whatever the theme: scanners read that best.
            Box(
                Modifier.fillMaxWidth(0.7f).aspectRatio(1f).align(Alignment.CenterHorizontally)
                    .clip(DesiTheme.radius.md).background(Color.White).padding(12.dp),
            ) {
                Image(rememberQrCodePainter(link), contentDescription = stringResource(Res.string.team_qr_description), modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            code,
            style = DesiTheme.type.scoreXl,
            color = DesiTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(stringResource(Res.string.team_invite_share), onClick = { onShare(inviteText) }, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(Res.string.team_invite_new_code), onClick = onNewCode, modifier = Modifier.fillMaxWidth())
        Text(stringResource(Res.string.team_invite_new_code_hint), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

// Previews: sample players only; all labels come from string resources.

private val PreviewTeam = SavedTeam(
    id = "t1", name = "Andheri Strikers", city = "Mumbai", createdBy = "me",
    members = listOf(
        TeamMember(PlayerSummary("me", "Rohan Iyer", "Mumbai", PlayerRole.ALL_ROUNDER), TeamRole.ADMIN),
        TeamMember(PlayerSummary("p2", "Aarav Shah", role = PlayerRole.BOWLER), TeamRole.MEMBER),
        TeamMember(PlayerSummary("p3", "Kabir", isPlaceholder = true), TeamRole.MEMBER, claimCode = "K7M2QX"),
    ),
    joinCode = "AB3XY9",
)

@Preview
@Composable
private fun TeamAdminPreview() = DesiTheme(darkTheme = false) {
    TeamContent(TeamUiState(myId = "me", loading = false, team = PreviewTeam), {}, {}, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun TeamMemberDarkPreview() = DesiTheme(darkTheme = true) {
    TeamContent(TeamUiState(myId = "p2", loading = false, team = PreviewTeam.copy(joinCode = null)), {}, {}, {}, {}, {}, {}, {})
}
