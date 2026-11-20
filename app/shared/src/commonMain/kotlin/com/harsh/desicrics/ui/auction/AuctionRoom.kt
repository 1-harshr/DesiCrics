package com.harsh.desicrics.ui.auction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.harsh.desicrics.designsystem.component.ChoiceChips
import com.harsh.desicrics.designsystem.component.DesiScaffold
import com.harsh.desicrics.designsystem.component.DesiSheet
import com.harsh.desicrics.designsystem.component.ErrorBanner
import com.harsh.desicrics.designsystem.component.GhostButton
import com.harsh.desicrics.designsystem.component.PrimaryButton
import com.harsh.desicrics.designsystem.component.StatGrid
import com.harsh.desicrics.designsystem.component.StatItem
import com.harsh.desicrics.designsystem.component.TonalButton
import com.harsh.desicrics.designsystem.icon.DesiIcons
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The live room on a phone. Everyone sees the player up, the bidding, every team's purse and the latest
 * sales. In an online auction, team owners bid from the bar at the bottom against the clock, and organisers
 * run the room from theirs. Watchers of a hall auction see the same room, read-only.
 */
@Composable
internal fun AuctionRoomContent(state: AuctionUiState, actions: AuctionActions, millisLeft: Long?) {
    val details = state.details ?: return
    val space = DesiTheme.space
    val online = details.auction.settings.mode == AuctionMode.ONLINE
    var reversing by remember { mutableStateOf<AuctionSale?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val bidding = online && state.myTeams.isNotEmpty()
    val running = online && state.isOrganiser
    DesiScaffold(
        title = details.auction.tournamentName.ifBlank { stringResource(Res.string.auction_title) },
        onBack = actions::back,
        actions = { AuctionStatusTag(details.state.status, Modifier.padding(end = space.s4)) },
        bottomBar = {
            if (bidding || running) BottomPanel {
                if (bidding) BidControls(state, actions)
                if (running) OrganiserControls(state, actions, onFinish = { finishing = true })
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen).padding(bottom = space.s8),
            verticalArrangement = Arrangement.spacedBy(space.s4),
        ) {
            if (state.offline) ErrorBanner(stringResource(Res.string.auction_offline))
            if (state.actionFailed) ErrorBanner(state.actionError ?: stringResource(Res.string.auction_action_failed))
            RoomStage(details, millisLeft)
            if (bidding) MyTeams(state)
            Purses(details, mine = state.myTeams.map { it.teamId }.toSet())
            RecentSales(details, onReverse = if (running) ({ reversing = it }) else null)
        }
    }
    reversing?.let { sale -> ReverseSheet(details, sale, onConfirm = { actions.reverseSale(sale.profileId); reversing = null }, onDismiss = { reversing = null }) }
    if (finishing) FinishSheet(details, onConfirm = { actions.finish(); finishing = false }, onDismiss = { finishing = false })
}

/** The player up and the bidding, or the moment between lots, with the paused state over either. */
@Composable
internal fun RoomStage(details: AuctionDetails, millisLeft: Long?, large: Boolean = false, modifier: Modifier = Modifier) {
    val lot = details.state.lot
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s3)) {
        if (details.state.status == AuctionStatus.PAUSED) PausedStrip()
        if (lot == null) {
            BetweenLots(details, large = large)
        } else {
            LotStage(
                details,
                lot,
                large = large,
                clock = millisLeft?.let { left ->
                    { CountdownRing(left, clockTotalMillis(details.auction.settings, lot), size = if (large) DesiTheme.size.touchTarget * 2 else DesiTheme.size.touchTarget + DesiTheme.space.s4) }
                },
            )
        }
    }
}

@Composable
private fun PausedStrip() {
    val colors = DesiTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.event.extra.copy(alpha = 0.14f), DesiTheme.shapes.medium).padding(DesiTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2),
    ) {
        androidx.compose.material3.Icon(
            org.jetbrains.compose.resources.painterResource(DesiIcons.Pause),
            null,
            tint = colors.event.extra,
            modifier = Modifier.padding(end = DesiTheme.space.s1),
        )
        Column {
            Text(stringResource(Res.string.auction_paused_title), style = DesiTheme.type.bodyStrong, color = colors.ink)
            Text(stringResource(Res.string.auction_paused_body), style = DesiTheme.type.caption, color = colors.muted)
        }
    }
}

/** An owner's own team(s): what's left, the most they can bid now, and how full the squad is. */
@Composable
private fun MyTeams(state: AuctionUiState) {
    val details = state.details ?: return
    val settings = details.auction.settings
    state.myTeams.forEach { team ->
        val purse = details.state.team(team.teamId) ?: return@forEach
        Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            com.harsh.desicrics.designsystem.component.SectionHeader(team.name, icon = DesiIcons.Shield)
            StatGrid(
                listOf(
                    StatItem(points(purse.purseLeft, settings), stringResource(Res.string.auction_your_purse)),
                    StatItem(points(purse.maxBid, settings), stringResource(Res.string.auction_max_bid)),
                    StatItem(stringResource(Res.string.auction_squad_value, purse.squadSize, settings.maxSquad), stringResource(Res.string.auction_squad)),
                ),
                featured = 1,
            )
        }
    }
}

/** A bar pinned to the bottom of the room, above the system navigation bar. */
@Composable
private fun BottomPanel(content: @Composable () -> Unit) {
    val colors = DesiTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(color = colors.line)
        Column(Modifier.padding(horizontal = DesiTheme.space.screen, vertical = DesiTheme.space.s3), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            content()
        }
    }
}

/**
 * The owner's bid button, for the next bid the rules allow. It says why it can't be pressed when it can't
 * (leading already, can't afford, squad full), using the same checks as the server.
 */
@Composable
private fun BidControls(state: AuctionUiState, actions: AuctionActions) {
    val details = state.details ?: return
    val lot = details.state.lot
    var teamId by rememberSaveable { mutableStateOf(state.myTeams.first().teamId) }
    val team = state.myTeams.firstOrNull { it.teamId == teamId } ?: state.myTeams.first()
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        if (state.myTeams.size > 1) {
            Text(stringResource(Res.string.auction_bid_for), style = DesiTheme.type.label, color = DesiTheme.colors.muted)
            ChoiceChips(state.myTeams, selected = team, label = { it.name }, onSelect = { teamId = it.teamId })
        }
        if (lot == null || details.state.status == AuctionStatus.PAUSED) {
            Text(
                stringResource(if (lot == null) Res.string.auction_bid_wait else Res.string.auction_paused_title),
                style = DesiTheme.type.body,
                color = DesiTheme.colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = DesiTheme.space.s3),
            )
            return@Column
        }
        val error = state.bidError(team.teamId, lot.nextBid)
        PrimaryButton(
            stringResource(Res.string.auction_bid_action, points(lot.nextBid, details.auction.settings)),
            onClick = { actions.bid(team.teamId, lot.nextBid) },
            enabled = error == null,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = DesiIcons.Auction,
        )
        if (error != null) {
            val reason = if (error == com.harsh.desicrics.model.AuctionError.ALREADY_LEADING) Res.string.auction_you_lead else error.text
            Text(stringResource(reason), style = DesiTheme.type.caption, color = DesiTheme.colors.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The online organiser's controls: bring up the next player, end a lot early, pause, undo and finish. */
@Composable
private fun OrganiserControls(state: AuctionUiState, actions: AuctionActions, onFinish: () -> Unit) {
    val details = state.details ?: return
    val auction = details.state
    val paused = auction.status == AuctionStatus.PAUSED
    Column(verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
        if (auction.lot == null) {
            if (auction.upNext != null) {
                PrimaryButton(
                    stringResource(Res.string.auction_next_named, details.playerName(auction.upNext!!)),
                    onClick = { actions.next() },
                    enabled = !paused,
                    loading = state.saving,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = DesiIcons.Play,
                )
            } else {
                PrimaryButton(stringResource(Res.string.auction_finish), onClick = onFinish, loading = state.saving, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Flag)
            }
        } else {
            PrimaryButton(
                stringResource(Res.string.auction_close_lot),
                onClick = actions::closeLot,
                enabled = !paused,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = DesiIcons.Auction,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesiTheme.space.s2)) {
            TonalButton(
                stringResource(if (paused) Res.string.auction_resume else Res.string.auction_pause),
                onClick = if (paused) actions::resume else actions::pause,
                modifier = Modifier.weight(1f),
                leadingIcon = if (paused) DesiIcons.Play else DesiIcons.Pause,
            )
            TonalButton(
                stringResource(Res.string.auction_undo),
                onClick = actions::undo,
                enabled = auction.canUndo,
                modifier = Modifier.weight(1f),
                leadingIcon = DesiIcons.Undo,
            )
            if (auction.lot == null && auction.upNext != null) {
                TonalButton(stringResource(Res.string.auction_finish_short), onClick = onFinish, modifier = Modifier.weight(1f), leadingIcon = DesiIcons.Flag)
            }
        }
    }
}

@Composable
internal fun ReverseSheet(details: AuctionDetails, sale: AuctionSale, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = details.playerName(sale.profileId)
    DesiSheet(
        stringResource(Res.string.auction_reverse_title, name),
        onDismiss,
        hint = stringResource(Res.string.auction_reverse_body, details.teamName(sale.teamId), points(sale.price, details.auction.settings), name),
    ) {
        PrimaryButton(stringResource(Res.string.auction_reverse_action), onClick = onConfirm, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Undo)
        GhostButton(stringResource(Res.string.auction_keep), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun FinishSheet(details: AuctionDetails, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val waiting = details.state.order.count { details.state.players[it] == com.harsh.desicrics.model.AuctionPlayerStatus.WAITING }
    DesiSheet(stringResource(Res.string.auction_finish_title), onDismiss, hint = stringResource(Res.string.auction_finish_body)) {
        if (waiting > 0) Text(pluralStringResource(Res.plurals.auction_finish_waiting, waiting, waiting), style = DesiTheme.type.body, color = DesiTheme.colors.event.extra)
        PrimaryButton(stringResource(Res.string.auction_finish), onClick = onConfirm, modifier = Modifier.fillMaxWidth(), leadingIcon = DesiIcons.Flag)
        GhostButton(stringResource(Res.string.auction_keep_going), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
