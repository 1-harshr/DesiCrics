package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.desicrics.designsystem.component.Avatar
import com.harsh.desicrics.designsystem.component.DesiCard
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.IconTile
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.PillButton
import com.harsh.desicrics.designsystem.component.SectionHeader
import com.harsh.desicrics.designsystem.component.StatGrid
import com.harsh.desicrics.designsystem.component.StatItem
import com.harsh.desicrics.designsystem.component.Tag
import com.harsh.desicrics.designsystem.component.TagTone
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.TeamPurse
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * After the auction: the totals, then each team's haul (retained players, then everyone bought with the
 * price), what it spent and what's left, and the players nobody bought. Sold players are already in their
 * teams' squads. [onSharePoster] is the seam for the "Team after auction" poster; null hides the button.
 */
@Composable
internal fun AuctionSummaryContent(state: AuctionUiState, actions: AuctionActions, onSharePoster: ((AuctionPoster) -> Unit)?) {
    val details = state.details ?: return
    val space = DesiTheme.space
    val settings = details.auction.settings
    val sales = details.state.sales
    val unsold = details.state.order.filter { details.state.players[it] == AuctionPlayerStatus.UNSOLD || details.state.players[it] == AuctionPlayerStatus.WAITING }
    DesiScaffold(title = stringResource(Res.string.auction_results_title), onBack = actions::back) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen).padding(bottom = space.s8),
            verticalArrangement = Arrangement.spacedBy(space.s4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                Text(details.auction.tournamentName, style = DesiTheme.type.caption, color = DesiTheme.colors.muted, modifier = Modifier.weight(1f))
                AuctionStatusTag(details.state.status)
            }
            StatGrid(
                listOf(
                    StatItem("${sales.size}", stringResource(Res.string.auction_summary_sold)),
                    StatItem(points(sales.sumOf { it.price }, settings), stringResource(Res.string.auction_summary_spent)),
                    StatItem("${unsold.size}", stringResource(Res.string.auction_summary_unsold)),
                ),
                featured = 1,
            )
            DesiCard(padding = space.s4) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    IconTile(DesiIcons.Teams)
                    Text(stringResource(Res.string.auction_summary_joined), style = DesiTheme.type.body, color = DesiTheme.colors.ink, modifier = Modifier.weight(1f))
                }
            }
            details.state.teams.sortedByDescending { it.spent }.forEach { purse ->
                TeamHaul(details, purse, onShare = onSharePoster?.let { share -> { share(AuctionPoster.TeamAfterAuction(details, purse.teamId)) } })
            }
            if (unsold.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    SectionHeader(stringResource(Res.string.auction_summary_unsold_title), icon = DesiIcons.PersonRemove)
                    ListCard {
                        unsold.forEachIndexed { i, id ->
                            val player = details.player(id) ?: return@forEachIndexed
                            ListRow(
                                title = player.player.name,
                                subtitle = playerLine(player, stringResource(Res.string.auction_player_line_base, points(player.basePrice, settings))),
                                leading = { PhotoAvatar(player.player.name, player.player.photoUrl) },
                                divider = i > 0,
                                onClick = if (player.player.isPlaceholder) null else ({ actions.openPlayer(id) }),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One team's result: totals in the header, then its players with what each cost. */
@Composable
private fun TeamHaul(details: AuctionDetails, purse: TeamPurse, onShare: (() -> Unit)?) {
    val settings = details.auction.settings
    val name = details.teamName(purse.teamId)
    val prices = details.state.sales.filter { it.teamId == purse.teamId }.associate { it.profileId to it.price }
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
            Avatar(name, size = 44)
            Column(Modifier.weight(1f)) {
                Text(name, style = DesiTheme.type.titleMd, color = DesiTheme.colors.ink)
                Text(
                    stringResource(Res.string.auction_summary_team, points(purse.spent, settings), points(purse.purseLeft, settings)) + " · " +
                        pluralStringResource(Res.plurals.auction_players_count, purse.squadSize, purse.squadSize),
                    style = DesiTheme.type.caption,
                    color = DesiTheme.colors.muted,
                )
            }
            if (onShare != null) PillButton(stringResource(Res.string.auction_share), onClick = onShare)
        }
        ListCard {
            val rows = purse.retained.map { it to true } + purse.bought.map { it to false }
            if (rows.isEmpty()) {
                ListRow(title = stringResource(Res.string.auction_summary_no_players), titleColor = DesiTheme.colors.muted)
            }
            rows.forEachIndexed { i, (id, retained) ->
                val player = details.player(id) ?: return@forEachIndexed
                ListRow(
                    title = player.player.name,
                    subtitle = playerLine(player).ifEmpty { null },
                    leading = { PhotoAvatar(player.player.name, player.player.photoUrl) },
                    trailing = {
                        if (retained) Tag(stringResource(Res.string.auction_summary_retained), tone = TagTone.NEUTRAL)
                        Text(
                            points(if (retained) player.retainedPrice ?: 0 else prices[id] ?: 0, settings),
                            style = DesiTheme.type.bodyStrong,
                            color = DesiTheme.colors.ink,
                            modifier = Modifier.padding(start = DesiTheme.space.s2),
                        )
                    },
                    divider = i > 0,
                )
            }
        }
    }
}
