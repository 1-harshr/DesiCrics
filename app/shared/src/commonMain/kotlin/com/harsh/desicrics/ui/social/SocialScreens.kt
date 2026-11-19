package com.harsh.desicrics.ui.social
import com.harsh.desicrics.designsystem.component.ErrorState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.harsh.desicrics.designsystem.component.EmptyState
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiTextField
import com.harsh.desicrics.designsystem.component.FullScreenLoading
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PlayerRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.SectionLabel
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.teams.TeamRow
import com.harsh.desicrics.ui.teams.TeamList
import com.harsh.desicrics.ui.text.icon
import com.harsh.desicrics.designsystem.component.HeroButton
import com.harsh.desicrics.designsystem.component.HeroChip
import com.harsh.desicrics.designsystem.component.HeroChips
import com.harsh.desicrics.designsystem.component.HeroIdentity
import com.harsh.desicrics.designsystem.component.HeroScaffold
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.ui.text.formatDayAndTime
import com.harsh.desicrics.ui.text.label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SearchScreen(
    onOpenTeam: (String) -> Unit,
    onOpenPlayer: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = DesiTheme.space
    val results = state.results
    DesiScaffold(title = stringResource(Res.string.search_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                DesiTextField(
                    value = state.query,
                    onValueChange = viewModel::search,
                    label = stringResource(Res.string.search_hint),
                    leadingIcon = DesiIcons.Search,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                )
            }
            val nothing = results.teams.isEmpty() && results.players.isEmpty() && results.matches.isEmpty()
            if (state.query.trim().length >= 2 && !state.searching && nothing) {
                item { EmptyState(DesiIcons.Search, stringResource(Res.string.search_empty)) }
            }
            if (results.teams.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.search_section_teams), icon = DesiIcons.Shield) }
                item { TeamList(results.teams, onOpenTeam) }
            }
            if (results.players.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.search_section_players), icon = DesiIcons.Person) }
                item { PlayerList(results.players) { onOpenPlayer(it.profileId) } }
            }
            if (results.matches.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.search_section_matches), icon = DesiIcons.Cricket) }
                items(results.matches, key = { "m-${it.id}" }) { HomeMatchCard(it, isMine = false, onClick = { onOpenMatch(it.id) }) }
            }
        }
    }
}

/** Players on one card, a row each: photo, name, role and city. */
@Composable
internal fun PlayerList(players: List<PlayerSummary>, trailing: (@Composable RowScope.(PlayerSummary) -> Unit)? = null, onClick: (PlayerSummary) -> Unit) {
    ListCard {
        players.forEachIndexed { index, player ->
            ListRow(
                title = player.name,
                subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }).joinToString(" · ").ifEmpty { null },
                leading = { PhotoAvatar(player.name, player.photoUrl, size = 44) },
                trailing = trailing?.let { content -> { content(player) } },
                divider = index > 0,
                onClick = { onClick(player) },
            )
        }
    }
}

@Composable
internal fun PlayerLine(player: PlayerSummary, onClick: () -> Unit) {
    PlayerRow(
        name = player.name,
        subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }).joinToString(" · ").ifEmpty { null },
        avatar = { PhotoAvatar(player.name, player.photoUrl) },
        onClick = onClick,
    )
}

@Composable
fun NotificationsScreen(
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsContent(state, onOpenMatch, onBack, onRetry = viewModel::retry)
}

@Composable
fun NotificationsContent(state: NotificationsUiState, onOpenMatch: (String) -> Unit, onBack: () -> Unit, onRetry: () -> Unit = {}) {
    val space = DesiTheme.space
    DesiScaffold(title = stringResource(Res.string.notifications_title), onBack = onBack) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                if (state.loadFailed) {
                    item { ErrorState(stringResource(Res.string.error_notifications_title), stringResource(Res.string.error_connection_body), onRetry = onRetry, compact = true) }
                } else if (state.notifications.isEmpty()) {
                    item { EmptyState(DesiIcons.Notifications, stringResource(Res.string.notifications_empty_title), body = stringResource(Res.string.notifications_empty)) }
                }
                items(state.notifications, key = { it.id }) { NotificationRow(it, onClick = { onOpenMatch(it.matchId) }) }
            }
        }
    }
}

/** A sentence that can wrap, with an icon for the kind of news, and a tint and dot while unread. */
@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    val colors = DesiTheme.colors
    val unread = !notification.read
    Row(
        Modifier.fillMaxWidth().clip(DesiTheme.radius.md)
            .background(if (unread) colors.surface else Color.Transparent)
            .border(1.dp, if (unread) colors.line.copy(alpha = 0.7f) else Color.Transparent, DesiTheme.radius.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(DesiTheme.space.s3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3),
    ) {
        val (icon, tint) = when (notification.kind) {
            NotificationKind.MATCH_STARTED -> DesiIcons.Bolt to colors.event.wicket
            NotificationKind.MATCH_RESULT -> DesiIcons.Trophy to colors.brand
            NotificationKind.MILESTONE_RUNS -> DesiIcons.Bat to colors.event.four
            NotificationKind.MILESTONE_WICKETS -> DesiIcons.Ball to colors.event.six
            NotificationKind.FIXTURE_RESCHEDULED -> DesiIcons.Calendar to colors.event.extra
        }
        IconTile(icon, tint = tint, container = tint.copy(alpha = 0.12f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(notificationText(notification), style = if (unread) DesiTheme.type.bodyStrong else DesiTheme.type.body, color = colors.ink)
            Text(formatDayAndTime(notification.createdAt), style = DesiTheme.type.caption, color = colors.muted)
        }
        if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(DesiTheme.radius.full).background(colors.brand))
    }
}

/** The inbox stores facts; this words them in the user's language. */
@Composable
fun notificationText(n: Notification): String = when (n.kind) {
    NotificationKind.MATCH_STARTED -> stringResource(Res.string.notification_started, n.matchTitle)
    NotificationKind.MATCH_RESULT -> stringResource(Res.string.notification_result, n.matchTitle, n.detail.orEmpty())
    NotificationKind.MILESTONE_RUNS -> stringResource(Res.string.notification_runs, n.playerName.orEmpty(), n.value ?: 0, n.matchTitle)
    NotificationKind.MILESTONE_WICKETS -> stringResource(Res.string.notification_wickets, n.playerName.orEmpty(), n.value ?: 0, n.matchTitle)
    NotificationKind.FIXTURE_RESCHEDULED -> n.detail?.toLongOrNull()
        ?.let { stringResource(Res.string.notification_rescheduled, n.matchTitle, formatDayAndTime(it)) }
        ?: stringResource(Res.string.notification_rescheduled_unset, n.matchTitle)
}

@Composable
fun PlayerScreen(
    profileId: String,
    myId: String?,
    onOpenTeam: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel { parametersOf(profileId, myId) },
    /** Extra sections below the header, e.g. career stats. */
    extra: @Composable (PlayerPage) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlayerContent(state, onFollow = viewModel::toggleFollow, onOpenTeam, onOpenMatch, onBack, extra, onRetry = viewModel::retry)
}

@Composable
fun PlayerContent(
    state: PlayerUiState,
    onFollow: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    extra: @Composable (PlayerPage) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val page = state.page
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    if (page == null) {
        DesiScaffold(title = "", onBack = onBack) { padding ->
            if (state.loading) FullScreenLoading(Modifier.padding(padding))
            else ErrorState(
                stringResource(Res.string.error_player_title),
                stringResource(Res.string.error_connection_body),
                Modifier.padding(padding),
                onRetry = onRetry,
            )
        }
        return
    }
    HeroScaffold(
        title = page.player.name,
        onBack = onBack,
        hero = {
            HeroIdentity(
                name = page.player.name,
                eyebrow = stringResource(Res.string.player_eyebrow),
                avatar = { PhotoAvatar(page.player.name, page.player.photoUrl, size = 72) },
            )
            HeroChips(
                listOf(
                    HeroChip(page.player.role?.let { stringResource(it.label) }.orEmpty(), page.player.role?.icon),
                    HeroChip(page.player.city, DesiIcons.Location),
                    HeroChip(pluralStringResource(Res.plurals.player_followers, page.followers, page.followers), DesiIcons.Teams),
                    HeroChip(stringResource(Res.string.team_placeholder).takeIf { page.player.isPlaceholder }.orEmpty(), DesiIcons.Info),
                ),
            )
            if (!state.isMe && !page.player.isPlaceholder) {
                HeroButton(
                    stringResource(if (page.following) Res.string.following else Res.string.follow),
                    onFollow,
                    Modifier.fillMaxWidth(),
                    icon = if (page.following) DesiIcons.CheckCircle else DesiIcons.Add,
                    filled = !page.following,
                )
            }
        },
    ) {
        extra(page)
        if (page.teams.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.player_section_teams), icon = DesiIcons.Shield)
                TeamList(page.teams, onOpenTeam)
            }
        }
        if (page.matches.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                SectionHeader(stringResource(Res.string.player_section_matches), icon = DesiIcons.History)
                page.matches.forEach { HomeMatchCard(it, isMine = false, onClick = { onOpenMatch(it.id) }) }
            }
        }
    }
}

// Previews: sample players only; all labels come from string resources.

@Preview
@Composable
private fun PlayerPreview() = DesiTheme(darkTheme = false) {
    PlayerContent(
        PlayerUiState(loading = false, page = PlayerPage(PlayerSummary("p1", "Aarav Shah", "Mumbai", PlayerRole.BOWLER), followers = 12)),
        {}, {}, {}, {},
    )
}

@Preview
@Composable
private fun NotificationsPreview() = DesiTheme(darkTheme = true) {
    NotificationsContent(
        NotificationsUiState(
            loading = false,
            notifications = listOf(
                Notification("1", NotificationKind.MILESTONE_RUNS, "m1", "Strikers vs Royals", "Aarav", 50, createdAt = 0),
                Notification("2", NotificationKind.MATCH_STARTED, "m1", "Strikers vs Royals", createdAt = 0, read = true),
            ),
        ),
        {}, {},
    )
}
