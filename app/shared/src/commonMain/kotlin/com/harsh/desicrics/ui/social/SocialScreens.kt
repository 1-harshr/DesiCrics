package com.harsh.desicrics.ui.social

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
import com.harsh.desicrics.ui.home.HomeMatchCard
import com.harsh.desicrics.ui.teams.TeamRow
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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                )
            }
            val nothing = results.teams.isEmpty() && results.players.isEmpty() && results.matches.isEmpty()
            if (state.query.trim().length >= 2 && !state.searching && nothing) {
                item { Text(stringResource(Res.string.search_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted) }
            }
            if (results.teams.isNotEmpty()) {
                item { SectionLabel(stringResource(Res.string.search_section_teams)) }
                items(results.teams, key = { "t-${it.id}" }) { TeamRow(it, onClick = { onOpenTeam(it.id) }) }
            }
            if (results.players.isNotEmpty()) {
                item { SectionLabel(stringResource(Res.string.search_section_players)) }
                items(results.players, key = { "p-${it.profileId}" }) { PlayerLine(it, onClick = { onOpenPlayer(it.profileId) }) }
            }
            if (results.matches.isNotEmpty()) {
                item { SectionLabel(stringResource(Res.string.search_section_matches)) }
                items(results.matches, key = { "m-${it.id}" }) { HomeMatchCard(it, isMine = false, onClick = { onOpenMatch(it.id) }) }
            }
        }
    }
}

@Composable
internal fun PlayerLine(player: PlayerSummary, onClick: () -> Unit) {
    PlayerRow(
        name = player.name,
        subtitle = listOfNotNull(player.role?.let { stringResource(it.label) }, player.city.ifBlank { null }).joinToString(" · ").ifEmpty { null },
        avatar = { Avatar(player.name) },
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
    NotificationsContent(state, onOpenMatch, onBack)
}

@Composable
fun NotificationsContent(state: NotificationsUiState, onOpenMatch: (String) -> Unit, onBack: () -> Unit) {
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
                    item { Text(stringResource(Res.string.notifications_load_failed), style = DesiTheme.type.body, color = DesiTheme.colors.event.wicket) }
                } else if (state.notifications.isEmpty()) {
                    item { Text(stringResource(Res.string.notifications_empty), style = DesiTheme.type.body, color = DesiTheme.colors.muted) }
                }
                items(state.notifications, key = { it.id }) { NotificationRow(it, onClick = { onOpenMatch(it.matchId) }) }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: Notification, onClick: () -> Unit) {
    val text = notificationText(notification)
    PlayerRow(
        name = text,
        avatar = { Avatar(notification.playerName ?: notification.matchTitle) },
        onClick = onClick,
        modifier = Modifier,
        subtitle = null,
        trailing = {
            if (!notification.read) Text("●", color = DesiTheme.colors.brand, style = DesiTheme.type.label.copy(fontWeight = FontWeight.Bold))
        },
    )
}

/** The inbox stores facts; this words them in the user's language. */
@Composable
fun notificationText(n: Notification): String = when (n.kind) {
    NotificationKind.MATCH_STARTED -> stringResource(Res.string.notification_started, n.matchTitle)
    NotificationKind.MATCH_RESULT -> stringResource(Res.string.notification_result, n.matchTitle, n.detail.orEmpty())
    NotificationKind.MILESTONE_RUNS -> stringResource(Res.string.notification_runs, n.playerName.orEmpty(), n.value ?: 0, n.matchTitle)
    NotificationKind.MILESTONE_WICKETS -> stringResource(Res.string.notification_wickets, n.playerName.orEmpty(), n.value ?: 0, n.matchTitle)
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
    PlayerContent(state, onFollow = viewModel::toggleFollow, onOpenTeam, onOpenMatch, onBack, extra)
}

@Composable
fun PlayerContent(
    state: PlayerUiState,
    onFollow: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    extra: @Composable (PlayerPage) -> Unit = {},
) {
    val page = state.page
    val space = DesiTheme.space
    val colors = DesiTheme.colors
    DesiScaffold(title = page?.player?.name.orEmpty(), onBack = onBack) { padding ->
        when {
            page == null && state.loading -> FullScreenLoading(Modifier.padding(padding))
            page == null -> Text(
                stringResource(Res.string.player_load_failed),
                style = DesiTheme.type.body,
                color = colors.event.wicket,
                modifier = Modifier.padding(padding).padding(space.screen),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = space.screen, vertical = space.s4),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s4)) {
                        Avatar(page.player.name, size = 64)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.s1)) {
                            Text(page.player.name, style = DesiTheme.type.titleLg, color = colors.ink)
                            val details = listOfNotNull(
                                page.player.role?.let { stringResource(it.label) },
                                page.player.city.ifBlank { null },
                                pluralStringResource(Res.plurals.player_followers, page.followers, page.followers),
                            )
                            Text(details.joinToString(" · "), style = DesiTheme.type.body, color = colors.muted)
                        }
                    }
                }
                if (!state.isMe && !page.player.isPlaceholder) {
                    item {
                        if (page.following) GhostButton(stringResource(Res.string.following), onClick = onFollow, modifier = Modifier.fillMaxWidth())
                        else PrimaryButton(stringResource(Res.string.follow), onClick = onFollow, modifier = Modifier.fillMaxWidth())
                    }
                }
                item { extra(page) }
                if (page.teams.isNotEmpty()) {
                    item { SectionLabel(stringResource(Res.string.player_section_teams)) }
                    items(page.teams, key = { "t-${it.id}" }) { TeamRow(it, onClick = { onOpenTeam(it.id) }) }
                }
                if (page.matches.isNotEmpty()) {
                    item { SectionLabel(stringResource(Res.string.player_section_matches)) }
                    items(page.matches, key = { "m-${it.id}" }) { HomeMatchCard(it, isMine = false, onClick = { onOpenMatch(it.id) }) }
                }
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
