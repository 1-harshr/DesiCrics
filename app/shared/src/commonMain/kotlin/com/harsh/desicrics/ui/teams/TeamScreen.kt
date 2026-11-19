package com.harsh.desicrics.ui.teams
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.material3.TextButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import com.harsh.desicrics.util.format2
import com.harsh.desicrics.ui.match.label
import com.harsh.desicrics.model.WinRecord
import com.harsh.desicrics.model.TeamInsights
import com.harsh.desicrics.model.PhaseStat
import com.harsh.desicrics.model.Performer
import com.harsh.desicrics.model.InningsPhase
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
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.HeroButton
import com.harsh.desicrics.designsystem.component.HeroChip
import com.harsh.desicrics.designsystem.component.HeroChips
import com.harsh.desicrics.designsystem.component.HeroIconButton
import com.harsh.desicrics.designsystem.component.HeroIdentity
import com.harsh.desicrics.designsystem.component.HeroScaffold
import com.harsh.desicrics.designsystem.component.HeroStats
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.PillButton
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.StatGrid
import com.harsh.desicrics.designsystem.component.StatItem
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.component.RankBadge
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamMember
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.match.OptionsSheet
import com.harsh.desicrics.ui.match.SheetOption
import com.harsh.desicrics.ui.system.rememberImagePicker
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
    onOpenPlayer: (String) -> Unit = {},
    viewModel: TeamViewModel = koinViewModel { parametersOf(teamId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<TeamSheet?>(null) }
    val share = rememberShareText()
    val pickLogo = rememberImagePicker { jpeg -> jpeg?.let(viewModel::uploadLogo) }
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
        onMember = { member ->
            val canManage = state.isAdmin && member.player.profileId != state.myId
            if (canManage) sheet = TeamSheet.Member(member) else if (!member.player.isPlaceholder) onOpenPlayer(member.player.profileId)
        },
        onFollow = viewModel::toggleFollow,
        onToggleJoinRequest = viewModel::toggleJoinRequest,
        onAcceptRequest = viewModel::acceptJoinRequest,
        onDeclineRequest = viewModel::declineJoinRequest,
        onChangeLogo = pickLogo,
        onCompare = viewModel::compareWith,
        onLeave = { viewModel.leave(onBack) },
        onRetry = viewModel::retry,
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
                    if (!member.player.isPlaceholder) {
                        add(SheetOption(stringResource(Res.string.team_view_player), stringResource(Res.string.team_view_player_hint)) {
                            close(); onOpenPlayer(member.player.profileId)
                        })
                    }
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
    onFollow: () -> Unit = {},
    onToggleJoinRequest: () -> Unit = {},
    onAcceptRequest: (profileId: String) -> Unit = {},
    onDeclineRequest: (profileId: String) -> Unit = {},
    onChangeLogo: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val team = state.team
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    var settings by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    if (settings) DesiSheet(stringResource(Res.string.team_settings), { settings = false }) {
        ListCard {
            if (state.isAdmin) {
                ListRow(stringResource(Res.string.team_logo_change), leading = { IconTile(DesiIcons.Shield) }, onClick = { settings = false; onChangeLogo() })
                ListRow(stringResource(Res.string.team_rename), leading = { IconTile(DesiIcons.Edit) }, divider = true, onClick = { settings = false; onRename() })
            }
            ListRow(
                stringResource(Res.string.team_leave),
                leading = { IconTile(DesiIcons.Logout, tint = colors.event.wicket, container = colors.event.wicket.copy(alpha = 0.12f)) },
                titleColor = colors.event.wicket,
                divider = state.isAdmin,
                onClick = { settings = false; confirmLeave = true },
            )
        }
    }
    if (confirmLeave) DesiSheet(stringResource(Res.string.team_confirm_leave, team?.name.orEmpty()), { confirmLeave = false }) {
        GhostButton(stringResource(Res.string.team_leave), { confirmLeave = false; onLeave() }, leadingIcon = DesiIcons.Logout)
    }
    when {
        team == null -> DesiScaffold(title = stringResource(Res.string.teams_title), onBack = onBack) { padding ->
            if (state.loading) FullScreenLoading(Modifier.padding(padding))
            else ErrorState(
                stringResource(Res.string.error_team_title),
                stringResource(Res.string.error_connection_body),
                Modifier.padding(padding),
                onRetry = onRetry,
            )
        }
        else -> HeroScaffold(
            title = team.name,
            onBack = onBack,
            actions = {
                if (state.myRole != null) HeroIconButton(DesiIcons.Settings, stringResource(Res.string.team_settings), { settings = true })
            },
            hero = {
                HeroIdentity(
                    name = team.name,
                    eyebrow = stringResource(Res.string.team_eyebrow),
                    avatar = { PhotoAvatar(team.name, team.logoUrl, size = 72) },
                )
                HeroChips(
                    listOf(
                        HeroChip(team.city, DesiIcons.Location),
                        HeroChip(pluralStringResource(Res.plurals.teams_member_count, team.members.size, team.members.size), DesiIcons.Teams),
                        HeroChip(stringResource(Res.string.teams_role_admin).takeIf { state.isAdmin }.orEmpty(), DesiIcons.Verified),
                    ),
                )
                state.insights?.takeIf { it.played > 0 }?.let { record ->
                    HeroStats(
                        listOf(
                            StatItem("${record.played}", stringResource(Res.string.team_stat_played)),
                            StatItem("${record.won}", stringResource(Res.string.team_stat_won)),
                            StatItem("${record.lost}", stringResource(Res.string.team_stat_lost)),
                        ),
                    )
                }
                if (state.isAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                        HeroButton(stringResource(Res.string.team_add_player), onAdd, Modifier.weight(1f), icon = DesiIcons.PersonAdd)
                        HeroButton(stringResource(Res.string.team_invite_short), onInvite, Modifier.weight(1f), icon = DesiIcons.QrCode, filled = false)
                    }
                }
                // Someone outside the team can ask to join it, and follow it either way.
                if (state.myRole == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                        HeroButton(
                            stringResource(if (team.requested) Res.string.team_requested else Res.string.team_ask_to_join),
                            onToggleJoinRequest,
                            Modifier.weight(1f),
                            icon = if (team.requested) DesiIcons.Clock else DesiIcons.PersonAdd,
                            filled = !team.requested,
                        )
                        state.following?.let { following ->
                            HeroButton(
                                stringResource(if (following) Res.string.following else Res.string.follow),
                                onFollow,
                                Modifier.weight(1f),
                                icon = if (following) DesiIcons.CheckCircle else DesiIcons.Add,
                                filled = false,
                            )
                        }
                    }
                }
            },
        ) {
            if (state.actionFailed) {
                ErrorBanner(stringResource(Res.string.teams_action_failed))
            }
            if (team.joinRequests.isNotEmpty()) {
                JoinRequests(team.joinRequests, enabled = !state.saving, onAccept = onAcceptRequest, onDecline = onDeclineRequest)
            }
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.team_members), icon = DesiIcons.Teams)
                ListCard {
                    team.members.forEachIndexed { index, member ->
                        val canOpen = (state.isAdmin && member.player.profileId != state.myId) || !member.player.isPlaceholder
                        ListRow(
                            title = member.player.name,
                            subtitle = memberSubtitle(member),
                            leading = { PhotoAvatar(member.player.name, member.player.photoUrl, size = 44) },
                            trailing = {
                                when {
                                    member.role == TeamRole.ADMIN -> Tag(stringResource(Res.string.teams_role_admin), tone = TagTone.BRAND, icon = DesiIcons.Verified)
                                    member.player.isPlaceholder -> Tag(stringResource(Res.string.team_tag_invited), tone = TagTone.WARN)
                                }
                                if (canOpen) Icon(painterResource(DesiIcons.ChevronRight), null, tint = colors.muted, modifier = Modifier.size(DesiTheme.size.iconSmall))
                            },
                            divider = index > 0,
                            onClick = if (canOpen) ({ onMember(member) }) else null,
                        )
                    }
                }
            }
            if (state.isAdmin) state.insights?.let { Insights(it) }
            if (state.otherTeams.isNotEmpty()) {
                RecordAgainst(state.otherTeams, state.headToHead?.let { h2h -> state.otherTeams.firstOrNull { it.id == h2h.opponentId } }, state, onCompare)
            }
        }
    }
}

@Composable
private fun memberSubtitle(member: TeamMember): String? {
    val parts = buildList {
        member.player.role?.let { add(stringResource(it.label)) }
        if (member.player.isPlaceholder) add(stringResource(Res.string.team_placeholder))
        member.claimCode?.let { add(stringResource(Res.string.team_claim_code, it)) }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

/** The organiser view: how the team wins, where it scores and concedes, how it gets out, who stands out. */
@Composable
internal fun Insights(insights: TeamInsights) {
    val colors = DesiTheme.colors
    val space = DesiTheme.space
    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        SectionHeader(stringResource(Res.string.insights_title), icon = DesiIcons.Insights)
        if (insights.played == 0) {
            EmptyState(DesiIcons.Insights, stringResource(Res.string.insights_empty))
            return@Column
        }
        StatGrid(
            listOfNotNull(
                StatItem("${insights.won}/${insights.played}", stringResource(Res.string.team_stat_won)),
                insights.averageFirstInnings?.let { StatItem("$it", stringResource(Res.string.insights_average_first_short)) },
                StatItem("${insights.tied}", stringResource(Res.string.team_stat_tied)),
            ),
            featured = 1,
        )
        ListCard {
            listOf(
                Triple(Res.string.insights_batting_first, insights.battingFirst, DesiIcons.Bat),
                Triple(Res.string.insights_chasing, insights.chasing, DesiIcons.Target),
                Triple(Res.string.insights_toss, insights.tossWon, DesiIcons.Swap),
            ).filter { it.second.played > 0 }.forEachIndexed { index, (label, record, icon) ->
                ListRow(
                    stringResource(label, record.won, record.played),
                    leading = { IconTile(icon, size = 36.dp) },
                    trailing = { WinShare(record) },
                    divider = index > 0,
                )
            }
        }
        DesiCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.insights_phases), style = DesiTheme.type.bodyStrong, color = colors.ink)
            Row {
                Text("", Modifier.weight(1f))
                Text(stringResource(Res.string.insights_scored), Modifier.weight(1f), style = DesiTheme.type.label, color = colors.muted)
                Text(stringResource(Res.string.insights_conceded), Modifier.weight(1f), style = DesiTheme.type.label, color = colors.muted)
            }
            insights.batting.zip(insights.bowling).forEach { (bat, bowl) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(bat.phase.label), Modifier.weight(1f), style = DesiTheme.type.bodyStrong, color = colors.ink)
                    PhaseCell(bat, Modifier.weight(1f))
                    PhaseCell(bowl, Modifier.weight(1f))
                }
            }
            Text(stringResource(Res.string.insights_phases_hint), style = DesiTheme.type.caption, color = colors.muted)
        }
        if (insights.howOut.isNotEmpty()) {
            val total = insights.howOut.values.sum()
            DesiCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.insights_how_out), style = DesiTheme.type.bodyStrong, color = colors.ink)
                insights.howOut.entries.take(4).forEach { (kind, count) -> ShareBar(stringResource(kind.label), count * 100 / total) }
            }
        }
        Performers(Res.string.insights_top_runs, insights.topRunScorers, DesiIcons.Bat)
        Performers(Res.string.insights_top_wickets, insights.topWicketTakers, DesiIcons.Ball)
    }
}

/** "4 of 6" as a percentage pill. */
@Composable
private fun WinShare(record: WinRecord) {
    val percent = record.won * 100 / record.played
    Tag("$percent%", tone = if (percent >= 50) TagTone.BRAND else TagTone.NEUTRAL)
}

/** A label, its share as a bar, and the percentage. */
@Composable
private fun ShareBar(label: String, percent: Int) {
    val colors = DesiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1)) {
        Row {
            Text(label, style = DesiTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text("$percent%", style = DesiTheme.type.data, color = colors.muted)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(DesiTheme.radius.full).background(colors.sunk)) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(DesiTheme.radius.full).background(colors.brand))
        }
    }
}

@Composable
private fun PhaseCell(stat: PhaseStat, modifier: Modifier) {
    Column(modifier) {
        if (stat.balls == 0) {
            Text("–", style = DesiTheme.type.data, color = DesiTheme.colors.muted)
            return@Column
        }
        Text(stringResource(Res.string.insights_rpo, stat.runRate.format2()), style = DesiTheme.type.data, color = DesiTheme.colors.ink)
        Text(pluralStringResource(Res.plurals.insights_wickets, stat.wickets, stat.wickets), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
    }
}

@Composable
private fun Performers(title: StringResource, people: List<Performer>, icon: DrawableResource) {
    if (people.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        SectionHeader(stringResource(title), icon = icon)
        ListCard {
            people.forEachIndexed { index, person ->
                ListRow(
                    person.name,
                    leading = { RankBadge(index + 1) },
                    trailing = { Text("${person.value}", style = DesiTheme.type.scoreMd, color = DesiTheme.colors.ink) },
                    divider = index > 0,
                )
            }
        }
    }
}

private val InningsPhase.label: StringResource
    get() = when (this) {
        InningsPhase.START -> Res.string.insights_phase_start
        InningsPhase.MIDDLE -> Res.string.insights_phase_middle
        InningsPhase.FINISH -> Res.string.insights_phase_finish
    }

/** Pick another of the caller's teams to see how this one has done against it. */
@Composable
private fun RecordAgainst(others: List<TeamSummary>, selected: TeamSummary?, state: TeamUiState, onCompare: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        SectionHeader(stringResource(Res.string.team_record_against), icon = DesiIcons.Swap)
        ChoiceChips(options = others, selected = selected, label = { it.name }, onSelect = { onCompare(it.id) })
        state.headToHead?.let { h2h ->
            StatGrid(
                listOf(
                    StatItem("${h2h.played}", stringResource(Res.string.team_stat_played)),
                    StatItem("${h2h.won}", stringResource(Res.string.team_stat_won)),
                    StatItem("${h2h.lost}", stringResource(Res.string.team_stat_lost)),
                    StatItem("${h2h.tied}", stringResource(Res.string.team_stat_tied)),
                ),
                columns = 4,
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                Icon(painterResource(DesiIcons.Info), null, tint = DesiTheme.colors.muted, modifier = Modifier.size(16.dp))
                Text(stringResource(Res.string.team_add_search_empty), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
            }
        }
        Column(Modifier.fillMaxWidth().clip(DesiTheme.radius.md)) {
            results.forEach { player ->
                PlayerRow(
                    name = player.name,
                    subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }).joinToString(" · ").ifEmpty { null },
                    avatar = { PhotoAvatar(player.name, player.photoUrl) },
                    onClick = { onPick(player) },
                )
            }
        }
        SectionHeader(stringResource(Res.string.team_add_placeholder_title), Modifier.padding(top = DesiTheme.space.s2), icon = DesiIcons.PersonAdd)
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

/** For admins: players who found the team and asked to join, oldest first. */
@Composable
private fun JoinRequests(players: List<PlayerSummary>, enabled: Boolean, onAccept: (String) -> Unit, onDecline: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        SectionHeader(stringResource(Res.string.team_join_requests), icon = DesiIcons.PersonAdd)
        ListCard {
            players.forEachIndexed { index, player ->
                ListRow(
                    title = player.name,
                    subtitle = player.city.ifBlank { null },
                    leading = { PhotoAvatar(player.name, player.photoUrl, size = 44) },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
                            PillButton(stringResource(Res.string.team_join_decline), { onDecline(player.profileId) }, enabled = enabled)
                            PillButton(stringResource(Res.string.team_join_accept), { onAccept(player.profileId) }, selected = true, enabled = enabled)
                        }
                    },
                    divider = index > 0,
                )
            }
        }
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
