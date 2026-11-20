package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.ListCard
import com.harsh.desicrics.designsystem.component.ListRow
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.resources.*
import com.harsh.desicrics.ui.components.PhotoAvatar
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The hall: one device runs the auction for a room, usually turned sideways on a TV or projector. The player
 * up and the bid fill the left; the right is a button per team, and the auctioneer taps whichever team bid
 * (it can't when the rules say that team can't). SOLD, UNSOLD, undo, next and pause sit under the stage.
 * Upright, the same parts stack.
 */
@Composable
internal fun AuctionHallContent(state: AuctionUiState, actions: AuctionActions) {
    val details = state.details ?: return
    val space = DesiTheme.space
    var reversing by remember { mutableStateOf<AuctionSale?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val controls = @Composable { HallControls(state, actions, onFinish = { finishing = true }, onPick = { picking = true }) }
    BoxWithConstraints(Modifier.fillMaxSize().background(DesiTheme.colors.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            HallTopBar(details, actions)
            if (state.offline) ErrorBanner(stringResource(Res.string.auction_offline), Modifier.padding(horizontal = space.s4))
            if (state.actionFailed) ErrorBanner(state.actionError ?: stringResource(Res.string.auction_action_failed), Modifier.padding(horizontal = space.s4))
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(start = space.s4, end = space.s4, bottom = space.s4), horizontalArrangement = Arrangement.spacedBy(space.s4)) {
                    Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space.s3)) {
                        RoomStage(details, millisLeft = null, large = true)
                        controls()
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space.s3)) {
                        TeamGrid(state, actions, columns = 2)
                        RecentSales(details, limit = 3, onReverse = { reversing = it })
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = space.s4, end = space.s4, bottom = space.s6),
                    verticalArrangement = Arrangement.spacedBy(space.s3),
                ) {
                    RoomStage(details, millisLeft = null)
                    controls()
                    TeamGrid(state, actions, columns = 2)
                    RecentSales(details, limit = 3, onReverse = { reversing = it })
                }
            }
        }
    }
    reversing?.let { sale -> ReverseSheet(details, sale, onConfirm = { actions.reverseSale(sale.profileId); reversing = null }, onDismiss = { reversing = null }) }
    if (finishing) FinishSheet(details, onConfirm = { actions.finish(); finishing = false }, onDismiss = { finishing = false })
    if (picking) PickPlayerSheet(details, onPick = { actions.next(it); picking = false }, onDismiss = { picking = false })
}

/** Back, the tournament's name, the status, and how many players are still to come up. */
@Composable
private fun HallTopBar(details: AuctionDetails, actions: AuctionActions) {
    val space = DesiTheme.space
    val left = details.state.order.count { details.state.players[it] == AuctionPlayerStatus.WAITING }
    Row(Modifier.fillMaxWidth().padding(end = space.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
        IconButton(onClick = actions::back) {
            Icon(painterResource(DesiIcons.Back), contentDescription = stringResource(Res.string.auction_back), tint = DesiTheme.colors.ink)
        }
        Text(
            details.auction.tournamentName.ifBlank { stringResource(Res.string.auction_title) },
            style = DesiTheme.type.titleMd,
            color = DesiTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(pluralStringResource(Res.plurals.auction_players_left, left, left), style = DesiTheme.type.caption, color = DesiTheme.colors.muted)
        AuctionStatusTag(details.state.status)
    }
}

/** SOLD and UNSOLD while a player is up; next player (or finish) between lots; undo and pause always. */
@Composable
private fun HallControls(state: AuctionUiState, actions: AuctionActions, onFinish: () -> Unit, onPick: () -> Unit) {
    val details = state.details ?: return
    val auction = details.state
    val lot = auction.lot
    val paused = auction.status == AuctionStatus.PAUSED
    val space = DesiTheme.space
    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        if (lot != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                PrimaryButton(
                    stringResource(Res.string.auction_sell).uppercase(),
                    onClick = actions::sold,
                    enabled = lot.leading != null && !paused && !state.saving,
                    modifier = Modifier.weight(1.4f),
                    leadingIcon = DesiIcons.Auction,
                )
                GhostButton(
                    stringResource(Res.string.auction_unsold).uppercase(),
                    onClick = actions::unsold,
                    enabled = !paused && !state.saving,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (auction.upNext != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                PrimaryButton(
                    stringResource(Res.string.auction_next_named, details.playerName(auction.upNext!!)),
                    onClick = { actions.next() },
                    enabled = !paused,
                    loading = state.saving,
                    modifier = Modifier.weight(1.4f),
                    leadingIcon = DesiIcons.Play,
                )
                GhostButton(stringResource(Res.string.auction_pick), onClick = onPick, enabled = !paused, modifier = Modifier.weight(1f))
            }
        } else {
            PrimaryButton(stringResource(Res.string.auction_finish), onClick = onFinish, loading = state.saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Flag)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            TonalButton(
                stringResource(Res.string.auction_undo),
                onClick = actions::undo,
                enabled = auction.canUndo && !state.saving,
                modifier = Modifier.weight(1f),
                leadingIcon = DesiIcons.Undo,
            )
            TonalButton(
                stringResource(if (paused) Res.string.auction_resume else Res.string.auction_pause),
                onClick = if (paused) actions::resume else actions::pause,
                modifier = Modifier.weight(1f),
                leadingIcon = if (paused) DesiIcons.Play else DesiIcons.Pause,
            )
            if (lot == null && auction.upNext != null) {
                TonalButton(stringResource(Res.string.auction_finish_short), onClick = onFinish, modifier = Modifier.weight(1f), leadingIcon = DesiIcons.Flag)
            }
        }
    }
}

/** A button per team, in rows of [columns]. Tapping one raises the bid a step for that team. */
@Composable
private fun TeamGrid(state: AuctionUiState, actions: AuctionActions, columns: Int) {
    val details = state.details ?: return
    val space = DesiTheme.space
    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        Text(stringResource(Res.string.auction_hall_tap_hint).uppercase(), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
        details.teams.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                row.forEach { team -> TeamBidButton(state, team, Modifier.weight(1f).fillMaxHeight(), onBid = { actions.bid(team.teamId, null) }) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TeamBidButton(state: AuctionUiState, team: AuctionTeam, modifier: Modifier, onBid: () -> Unit) {
    val details = state.details ?: return
    val colors = DesiTheme.colors
    val settings = details.auction.settings
    val purse = details.state.team(team.teamId)
    val lot = details.state.lot
    val leading = lot?.leading?.teamId == team.teamId
    val error = lot?.let { state.bidError(team.teamId, it.nextBid) } ?: AuctionError.NO_LOT
    val enabled = error == null && !state.saving
    val status = when {
        leading -> stringResource(Res.string.auction_team_leading)
        lot == null -> null
        error == AuctionError.TEAM_FULL -> stringResource(Res.string.auction_team_full)
        error == AuctionError.BID_TOO_HIGH -> stringResource(Res.string.auction_team_cant_afford)
        error == null -> stringResource(Res.string.auction_bid_action, points(lot.nextBid, settings))
        else -> null
    }
    val container = if (leading) colors.brand else colors.surface
    val ink = if (leading) colors.onBrand else colors.ink
    val soft = if (leading) colors.onBrand.copy(alpha = 0.8f) else colors.muted
    Column(
        modifier.heightIn(min = DesiTheme.size.scoringTarget).clip(DesiTheme.shapes.medium).background(container)
            .border(DesiTheme.shapes.borderControl, if (leading) colors.brand else colors.line, DesiTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onBid)
            .padding(horizontal = DesiTheme.space.s3, vertical = DesiTheme.space.s2),
        verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s1),
    ) {
        Text(team.name, style = DesiTheme.type.bodyStrong, color = if (enabled || leading) ink else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (purse != null) {
            Text(
                stringResource(Res.string.auction_team_short, points(purse.purseLeft, settings), purse.squadSize, settings.maxSquad),
                style = DesiTheme.type.caption,
                color = soft,
                maxLines = 1,
            )
        }
        if (status != null) {
            Text(status, style = DesiTheme.type.label, color = if (leading) colors.onBrand else if (error == null) colors.brand else colors.muted, maxLines = 1)
        }
    }
}

/** Brings up a particular player instead of whoever is next. */
@Composable
private fun PickPlayerSheet(details: AuctionDetails, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ready = details.state.order.filter { details.state.players[it] == AuctionPlayerStatus.WAITING || details.state.players[it] == AuctionPlayerStatus.UNSOLD }
    DesiSheet(stringResource(Res.string.auction_pick_title), onDismiss, fullHeight = ready.size > 6) {
        ListCard {
            ready.forEachIndexed { i, id ->
                val player = details.player(id) ?: return@forEachIndexed
                val unsold = details.state.players[id] == AuctionPlayerStatus.UNSOLD
                ListRow(
                    title = player.player.name,
                    subtitle = playerLine(player, points(player.basePrice, details.auction.settings), if (unsold) stringResource(Res.string.auction_unsold) else null),
                    leading = { PhotoAvatar(player.player.name, player.player.photoUrl) },
                    divider = i > 0,
                    onClick = { onPick(id) },
                )
            }
        }
    }
}
